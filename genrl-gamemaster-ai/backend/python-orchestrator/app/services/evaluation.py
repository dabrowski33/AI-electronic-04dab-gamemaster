# GenRL GameMaster AI - Evaluation Service

import asyncio
import json
import random
import time
from typing import Any, Dict, List, Optional

import structlog

from app.core.config import settings

logger = structlog.get_logger()

# Per-game simulation defaults used when the Java simulator is unavailable
_GAME_SIMULATION_PARAMS: Dict[str, Dict[str, Any]] = {
    "pacman": {
        "max_score":        10_000,
        "max_survival_ms":  120_000,
        "win_probability":  0.15,
    },
    "super_mario": {
        "max_score":        50_000,
        "max_survival_ms":  300_000,
        "win_probability":  0.08,
    },
}

_DEFAULT_GAME_PARAMS = {
    "max_score":       5_000,
    "max_survival_ms": 60_000,
    "win_probability": 0.10,
}


class EvaluationService:
    """Evaluates agent populations by calling the Java simulator (with fallback)."""

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    async def evaluate_generation(
        self,
        experiment_id: str,
        generation_number: int,
        population: List[Dict[str, Any]],
        game_type: str,
    ) -> List[Dict[str, Any]]:
        """Evaluate every agent in the population.

        Tries the Java simulator first; falls back to simulated scoring if
        the simulator is unreachable.

        Returns a list of dicts with keys:
            agent_id, genome, fitness_score, survival_time, score,
            win_status, execution_log, evaluation_duration_ms
        """
        simulator_available = await self._check_simulator()

        tasks = [
            self._evaluate_single(
                agent=agent,
                game_type=game_type,
                use_simulator=simulator_available,
            )
            for agent in population
        ]
        results = await asyncio.gather(*tasks, return_exceptions=True)

        evaluated: List[Dict[str, Any]] = []
        for i, result in enumerate(results):
            if isinstance(result, Exception):
                logger.warning(
                    "agent_evaluation_error",
                    agent_index=i,
                    experiment_id=experiment_id,
                    generation_number=generation_number,
                    error=str(result),
                )
                agent = population[i]
                evaluated.append(
                    {
                        "agent_id": agent.get("id", f"agent_{i}"),
                        "genome": agent.get("genome", {}),
                        "neural_weights": agent.get("neural_weights", {}),
                        "q_table": agent.get("q_table", {}),
                        "fitness_score": 0.0,
                        "survival_time": 0,
                        "score": 0,
                        "win_status": False,
                        "execution_log": f"Evaluation error: {result}",
                        "error_message": str(result),
                        "evaluation_duration_ms": 0,
                    }
                )
            else:
                evaluated.append(result)

        logger.info(
            "generation_evaluated",
            experiment_id=experiment_id,
            generation_number=generation_number,
            population_size=len(evaluated),
            used_simulator=simulator_available,
            best_fitness=max((r["fitness_score"] for r in evaluated), default=0.0),
        )
        return evaluated

    # ------------------------------------------------------------------
    # Private – single-agent evaluation
    # ------------------------------------------------------------------

    async def _evaluate_single(
        self,
        agent: Dict[str, Any],
        game_type: str,
        use_simulator: bool,
    ) -> Dict[str, Any]:
        agent_id = agent.get("id", "unknown")
        genome = agent.get("genome", {})

        if use_simulator:
            return await self._call_java_simulator(agent_id, genome, game_type)
        return self._simulate_evaluation(agent_id, genome, game_type)

    async def _call_java_simulator(
        self,
        agent_id: str,
        genome: Dict[str, Any],
        game_type: str,
    ) -> Dict[str, Any]:
        """POST the genome to the Java simulator and return the evaluation result."""
        import httpx

        url = f"{settings.JAVA_SIMULATOR_URL}/api/simulator/evaluate"
        payload = {
            "agentId": agent_id,
            "gameType": game_type,
            "genome": genome,
        }
        start_ms = int(time.time() * 1000)

        try:
            async with httpx.AsyncClient(timeout=120.0) as client:
                resp = await client.post(url, json=payload)
                if resp.status_code != 200:
                    raise RuntimeError(
                        f"Simulator returned HTTP {resp.status_code}: {resp.text[:200]}"
                    )
                data = resp.json()
        except Exception as exc:
            logger.warning("java_simulator_call_failed", agent_id=agent_id, error=str(exc))
            raise

        duration_ms = int(time.time() * 1000) - start_ms
        fitness = self._calculate_fitness(
            score=data.get("score", 0),
            survival_time=data.get("survivalTime", 0),
            win_status=data.get("winStatus", False),
            game_type=game_type,
        )

        return {
            "agent_id": agent_id,
            "genome": genome,
            "neural_weights": {},
            "q_table": {},
            "fitness_score": fitness,
            "survival_time": data.get("survivalTime", 0),
            "score": data.get("score", 0),
            "win_status": data.get("winStatus", False),
            "execution_log": data.get("executionLog", ""),
            "error_message": data.get("errorMessage"),
            "evaluation_duration_ms": duration_ms,
        }

    def _simulate_evaluation(
        self,
        agent_id: str,
        genome: Dict[str, Any],
        game_type: str,
    ) -> Dict[str, Any]:
        """Produce randomised scores for development / demo when Java simulator is down."""
        params = _GAME_SIMULATION_PARAMS.get(game_type.lower(), _DEFAULT_GAME_PARAMS)
        start_ms = int(time.time() * 1000)

        # Genome quality nudges the random result slightly
        lr_quality = 1.0 - abs(genome.get("learning_rate", 0.001) - 0.001) * 100
        epsilon_quality = 1.0 - genome.get("epsilon", 0.5)
        quality_factor = max(0.1, min(1.5, (lr_quality + epsilon_quality) / 2 + 0.5))

        raw_score = int(
            random.uniform(0, params["max_score"]) * quality_factor
        )
        raw_score = min(raw_score, params["max_score"])

        survival = int(
            random.uniform(0, params["max_survival_ms"]) * quality_factor
        )
        survival = min(survival, params["max_survival_ms"])

        win = random.random() < (params["win_probability"] * quality_factor)
        fitness = self._calculate_fitness(raw_score, survival, win, game_type)

        duration_ms = int(time.time() * 1000) - start_ms

        return {
            "agent_id": agent_id,
            "genome": genome,
            "neural_weights": {},
            "q_table": {},
            "fitness_score": fitness,
            "survival_time": survival,
            "score": raw_score,
            "win_status": win,
            "execution_log": f"[simulated] score={raw_score} survival={survival}ms win={win}",
            "error_message": None,
            "evaluation_duration_ms": duration_ms,
        }

    # ------------------------------------------------------------------
    # Private – helpers
    # ------------------------------------------------------------------

    async def _check_simulator(self) -> bool:
        """Return True if the Java simulator responds to a health-check."""
        import httpx

        url = f"{settings.JAVA_SIMULATOR_URL}/actuator/health"
        try:
            async with httpx.AsyncClient(timeout=3.0) as client:
                resp = await client.get(url)
                return resp.status_code == 200
        except Exception:
            return False

    @staticmethod
    def _calculate_fitness(
        score: int,
        survival_time: int,
        win_status: bool,
        game_type: str,
    ) -> float:
        """Combine raw metrics into a scalar fitness value in [0, 1]."""
        params = _GAME_SIMULATION_PARAMS.get(game_type.lower(), _DEFAULT_GAME_PARAMS)

        score_norm = min(1.0, score / max(1, params["max_score"]))
        survival_norm = min(1.0, survival_time / max(1, params["max_survival_ms"]))
        win_bonus = 0.3 if win_status else 0.0

        # Weighted combination
        fitness = 0.5 * score_norm + 0.3 * survival_norm + 0.2 * win_bonus
        return round(min(1.0, fitness), 6)

# GenRL GameMaster AI - Genome Operations Service

import copy
import math
import random
import uuid
from typing import Any, Dict, List

import structlog

logger = structlog.get_logger()

# ---------------------------------------------------------------------------
# Genome parameter ranges
# ---------------------------------------------------------------------------
_GENOME_RANGES: Dict[str, tuple] = {
    "learning_rate":      (1e-5, 1e-1),
    "discount_factor":    (0.8, 0.999),
    "epsilon":            (0.01, 1.0),
    "hidden_layers":      (1, 5),           # integer
    "neurons_per_layer":  (16, 512),        # integer
}

_ACTION_SPACES: Dict[str, int] = {
    "pacman":      4,
    "super_mario": 12,
}

# Gaussian mutation standard deviation as fraction of parameter range
_MUTATION_STDDEV_FRACTION = 0.1

# Tournament selection size
_TOURNAMENT_SIZE = 3


class GenomeService:
    """Creates and evolves agent genomes for the evolutionary RL loop."""

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    async def create_initial_population(
        self,
        size: int,
        game_type: str,
    ) -> List[Dict[str, Any]]:
        """Generate a random initial population.

        Each genome dict has the shape:
            {
                "id": str,
                "genome": {learning_rate, discount_factor, epsilon,
                           hidden_layers, neurons_per_layer},
                "neural_weights": {},
                "q_table": {},
            }
        """
        action_space = _ACTION_SPACES.get(game_type.lower(), 4)
        population = []
        for _ in range(size):
            genome = {
                "learning_rate":     _uniform(*_GENOME_RANGES["learning_rate"]),
                "discount_factor":   _uniform(*_GENOME_RANGES["discount_factor"]),
                "epsilon":           _uniform(*_GENOME_RANGES["epsilon"]),
                "hidden_layers":     _randint(*_GENOME_RANGES["hidden_layers"]),
                "neurons_per_layer": _randint(*_GENOME_RANGES["neurons_per_layer"]),
                "action_space":      action_space,
            }
            population.append(
                {
                    "id": str(uuid.uuid4()),
                    "genome": genome,
                    "neural_weights": {},
                    "q_table": {},
                }
            )

        logger.info(
            "initial_population_created",
            size=size,
            game_type=game_type,
            action_space=action_space,
        )
        return population

    async def evolve(
        self,
        population: List[Dict[str, Any]],
        mutation_rate: float,
        crossover_rate: float,
    ) -> List[Dict[str, Any]]:
        """Produce the next generation via tournament selection + uniform crossover
        + Gaussian mutation.

        Agents are expected to carry a ``fitness_score`` field (may be None
        for agents that were not evaluated — they are treated as 0.0).
        """
        if not population:
            return []

        pop_size = len(population)
        next_gen: List[Dict[str, Any]] = []

        # Elitism: carry the best individual unchanged
        best = max(population, key=lambda a: _fitness(a))
        elite = copy.deepcopy(best)
        elite["id"] = str(uuid.uuid4())
        # Reset evaluation artefacts for the new generation
        elite.pop("fitness_score", None)
        elite.pop("survival_time", None)
        elite.pop("score", None)
        elite.pop("win_status", None)
        elite.pop("execution_log", None)
        elite.pop("error_message", None)
        elite.pop("evaluation_duration_ms", None)
        next_gen.append(elite)

        while len(next_gen) < pop_size:
            parent_a = _tournament_select(population)
            parent_b = _tournament_select(population)

            if random.random() < crossover_rate:
                child_genome = _uniform_crossover(
                    parent_a["genome"], parent_b["genome"]
                )
            else:
                child_genome = copy.deepcopy(parent_a["genome"])

            if random.random() < mutation_rate:
                child_genome = _gaussian_mutate(child_genome)

            child = {
                "id": str(uuid.uuid4()),
                "genome": child_genome,
                "neural_weights": {},
                "q_table": {},
            }
            next_gen.append(child)

        logger.info(
            "population_evolved",
            pop_size=pop_size,
            mutation_rate=mutation_rate,
            crossover_rate=crossover_rate,
        )
        return next_gen[:pop_size]


# ---------------------------------------------------------------------------
# Private helpers
# ---------------------------------------------------------------------------

def _fitness(agent: Dict[str, Any]) -> float:
    v = agent.get("fitness_score")
    if v is None:
        return 0.0
    try:
        return float(v)
    except (TypeError, ValueError):
        return 0.0


def _uniform(lo: float, hi: float) -> float:
    return random.uniform(lo, hi)


def _randint(lo: float, hi: float) -> int:
    return random.randint(int(lo), int(hi))


def _tournament_select(population: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Return the fittest individual from a random sample."""
    sample = random.sample(population, min(_TOURNAMENT_SIZE, len(population)))
    return max(sample, key=_fitness)


def _uniform_crossover(
    genome_a: Dict[str, Any],
    genome_b: Dict[str, Any],
) -> Dict[str, Any]:
    """Produce a child genome by independently picking each gene from either parent."""
    child: Dict[str, Any] = {}
    keys = set(genome_a.keys()) | set(genome_b.keys())
    for key in keys:
        if key not in genome_a:
            child[key] = copy.deepcopy(genome_b[key])
        elif key not in genome_b:
            child[key] = copy.deepcopy(genome_a[key])
        else:
            child[key] = copy.deepcopy(
                genome_a[key] if random.random() < 0.5 else genome_b[key]
            )
    return child


def _gaussian_mutate(genome: Dict[str, Any]) -> Dict[str, Any]:
    """Apply Gaussian noise to each numeric parameter within its valid range."""
    mutated = copy.deepcopy(genome)

    for param, (lo, hi) in _GENOME_RANGES.items():
        if param not in mutated:
            continue

        is_int = isinstance(lo, int) and isinstance(hi, int)
        std = (hi - lo) * _MUTATION_STDDEV_FRACTION
        noise = random.gauss(0.0, std)
        new_val = mutated[param] + noise

        # Clamp to valid range
        new_val = max(lo, min(hi, new_val))

        mutated[param] = int(round(new_val)) if is_int else new_val

    return mutated

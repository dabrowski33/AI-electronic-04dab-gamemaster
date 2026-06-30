# GenRL GameMaster AI - LLM Cascade Service

import asyncio
import json
import uuid
from datetime import datetime
from typing import Any, Dict, Optional

import structlog
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage, SystemMessage

from app.core.config import settings

logger = structlog.get_logger()

# System prompt for algorithm mutation
_MUTATION_SYSTEM_PROMPT = """You are an expert in reinforcement learning and evolutionary algorithms.
Your task is to analyse the current agent code and fitness data, then propose a targeted mutation
to improve performance.

Respond ONLY with a valid JSON object containing exactly these fields:
{
  "mutated_code": "<complete Python source>",
  "mutation_type": "<one of: hyperparameter_tune, architecture_change, reward_shaping, exploration_strategy>",
  "reasoning": "<concise explanation of what was changed and why>",
  "diff": "<unified diff or short description of the change>"
}

Do not include markdown fences or any text outside the JSON object."""

_MUTATION_USER_TEMPLATE = """Experiment: {experiment_id}
Generation: {generation_number}
Best fitness so far: {best_fitness:.4f}
Average fitness: {avg_fitness:.4f}
Stagnation count: {stagnation_count}

Recent fitness values (last 10 agents):
{recent_fitness}

Current code:
```python
{current_code}
```

Mutation history (last 5):
{mutation_history}

{validation_error_section}Propose a single targeted mutation to break the stagnation and improve fitness."""


class LLMCascadeService:
    """LLM cascade: OpenRouter primary → OpenAI GPT-4o fallback."""

    def __init__(self) -> None:
        self._openrouter_client: Optional[ChatOpenAI] = None
        self._openai_client: Optional[ChatOpenAI] = None
        self._consecutive_openrouter_timeouts: int = 0
        self._using_fallback: bool = False

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    async def generate_mutation(self, context: Dict[str, Any]) -> Dict[str, Any]:
        """Generate a code mutation for the given evolutionary context.

        Returns a dict with keys: mutated_code, mutation_type, reasoning, diff.
        Falls back from OpenRouter to OpenAI GPT-4o after 2 consecutive timeouts.
        """
        messages = self._build_messages(context)

        if not self._using_fallback:
            try:
                result = await self._call_openrouter(messages)
                self._consecutive_openrouter_timeouts = 0
                return result
            except asyncio.TimeoutError:
                self._consecutive_openrouter_timeouts += 1
                logger.warning(
                    "openrouter_timeout",
                    consecutive=self._consecutive_openrouter_timeouts,
                )
                if self._consecutive_openrouter_timeouts >= 2:
                    await self._trigger_failover(context.get("experiment_id"))
            except Exception as exc:  # noqa: BLE001
                logger.warning("openrouter_error", error=str(exc))

        # Fallback path
        try:
            result = await self._call_openai(messages)
            return result
        except Exception as exc:
            logger.error("openai_fallback_error", error=str(exc))
            return self._safe_default_mutation(context)

    # ------------------------------------------------------------------
    # Private – LLM client factories
    # ------------------------------------------------------------------

    def _get_openrouter_client(self) -> ChatOpenAI:
        if self._openrouter_client is None:
            self._openrouter_client = ChatOpenAI(
                model=settings.OPENROUTER_MODEL,
                openai_api_key=settings.OPENROUTER_API_KEY,
                openai_api_base=settings.OPENROUTER_BASE_URL,
                temperature=0.7,
                max_tokens=2048,
                request_timeout=settings.LLM_TIMEOUT_SECONDS,
                max_retries=0,  # We handle retries ourselves
            )
        return self._openrouter_client

    def _get_openai_client(self) -> ChatOpenAI:
        if self._openai_client is None:
            self._openai_client = ChatOpenAI(
                model=settings.OPENAI_MODEL,
                openai_api_key=settings.OPENAI_API_KEY,
                temperature=0.7,
                max_tokens=2048,
                request_timeout=settings.LLM_TIMEOUT_SECONDS,
                max_retries=1,
            )
        return self._openai_client

    # ------------------------------------------------------------------
    # Private – LLM calls
    # ------------------------------------------------------------------

    async def _call_openrouter(self, messages: list) -> Dict[str, Any]:
        client = self._get_openrouter_client()
        response = await asyncio.wait_for(
            client.ainvoke(messages),
            timeout=settings.LLM_TIMEOUT_SECONDS,
        )
        return self._parse_response(response.content)

    async def _call_openai(self, messages: list) -> Dict[str, Any]:
        client = self._get_openai_client()
        response = await asyncio.wait_for(
            client.ainvoke(messages),
            timeout=settings.LLM_TIMEOUT_SECONDS,
        )
        return self._parse_response(response.content)

    # ------------------------------------------------------------------
    # Private – message construction
    # ------------------------------------------------------------------

    def _build_messages(self, context: Dict[str, Any]) -> list:
        validation_error_section = ""
        if context.get("validation_error"):
            validation_error_section = (
                f"Previous mutation failed validation: {context['validation_error']}\n"
                "Please ensure the new mutation is syntactically correct.\n\n"
            )

        mutation_history = context.get("mutation_history", [])
        history_text = (
            json.dumps(mutation_history[-5:], indent=2)
            if mutation_history
            else "No mutations yet."
        )

        recent_fitness = context.get("recent_fitness", [])
        fitness_text = ", ".join(
            f"{f:.4f}" if f is not None else "N/A" for f in recent_fitness
        )

        user_content = _MUTATION_USER_TEMPLATE.format(
            experiment_id=context.get("experiment_id", "unknown"),
            generation_number=context.get("generation_number", 0),
            best_fitness=float(context.get("best_fitness", 0.0)),
            avg_fitness=float(context.get("avg_fitness", 0.0)),
            stagnation_count=context.get("stagnation_count", 0),
            recent_fitness=fitness_text,
            current_code=context.get("current_code", "# No code provided"),
            mutation_history=history_text,
            validation_error_section=validation_error_section,
        )

        return [
            SystemMessage(content=_MUTATION_SYSTEM_PROMPT),
            HumanMessage(content=user_content),
        ]

    # ------------------------------------------------------------------
    # Private – response parsing
    # ------------------------------------------------------------------

    def _parse_response(self, content: str) -> Dict[str, Any]:
        """Parse the LLM JSON response. Handles markdown fences gracefully."""
        text = content.strip()

        # Strip markdown code fences if present
        if text.startswith("```"):
            lines = text.splitlines()
            # Remove first and last fence lines
            inner = lines[1:-1] if lines[-1].strip() == "```" else lines[1:]
            text = "\n".join(inner).strip()

        try:
            data = json.loads(text)
        except json.JSONDecodeError as exc:
            logger.warning("llm_response_parse_error", error=str(exc), content=text[:200])
            return {
                "mutated_code": "# Parse error — original code unchanged\n",
                "mutation_type": "hyperparameter_tune",
                "reasoning": f"LLM response could not be parsed: {exc}",
                "diff": "",
            }

        return {
            "mutated_code": data.get("mutated_code", ""),
            "mutation_type": data.get("mutation_type", "hyperparameter_tune"),
            "reasoning": data.get("reasoning", ""),
            "diff": data.get("diff", ""),
        }

    # ------------------------------------------------------------------
    # Private – failover
    # ------------------------------------------------------------------

    async def _trigger_failover(self, experiment_id: Optional[str]) -> None:
        self._using_fallback = True
        logger.warning(
            "llm_failover_activated",
            from_provider="openrouter",
            to_provider="openai",
            experiment_id=experiment_id,
        )
        await self._log_failover_event(experiment_id)

    async def _log_failover_event(self, experiment_id: Optional[str]) -> None:
        from app.core.database import get_raw_connection

        payload = json.dumps(
            {
                "from": "openrouter",
                "to": "openai_gpt4o",
                "reason": "2 consecutive timeouts",
                "timestamp": datetime.utcnow().isoformat(),
            }
        )
        try:
            conn = await get_raw_connection()
            try:
                await conn.execute(
                    """
                    INSERT INTO system_events
                        (id, event_type, service, experiment_id, payload, severity, created_at)
                    VALUES ($1, $2, $3, $4, $5::jsonb, $6, NOW())
                    """,
                    str(uuid.uuid4()),
                    "llm_failover",
                    "llm_cascade",
                    experiment_id,
                    payload,
                    "warning",
                )
            finally:
                await conn.close()
        except Exception as exc:  # noqa: BLE001
            logger.error("failover_event_log_error", error=str(exc))

    # ------------------------------------------------------------------
    # Private – safe default
    # ------------------------------------------------------------------

    @staticmethod
    def _safe_default_mutation(context: Dict[str, Any]) -> Dict[str, Any]:
        """Return a minimal no-op mutation when all LLM calls fail."""
        return {
            "mutated_code": context.get("current_code", "# fallback"),
            "mutation_type": "hyperparameter_tune",
            "reasoning": "All LLM providers failed; returning unchanged code.",
            "diff": "",
        }

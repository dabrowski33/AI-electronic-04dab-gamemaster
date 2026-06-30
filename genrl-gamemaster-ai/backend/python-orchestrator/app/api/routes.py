# GenRL GameMaster AI - API Routes

import uuid
from datetime import datetime
from typing import Any, Dict, List, Optional

import asyncpg
import structlog
from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Request
from pydantic import BaseModel, Field

from app.core.config import settings
from app.core.database import get_raw_connection

logger = structlog.get_logger()

router = APIRouter()


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------

class ExperimentCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=255)
    description: Optional[str] = None
    simulatorType: str = Field(..., alias="simulatorType")
    objectiveFunction: str = Field(..., alias="objectiveFunction")
    config: Optional[Dict[str, Any]] = None

    model_config = {"populate_by_name": True}


class ExperimentResponse(BaseModel):
    id: str
    name: str
    description: Optional[str]
    simulator_type: str
    objective_function: str
    config: Optional[Dict[str, Any]]
    status: str
    created_at: datetime
    started_at: Optional[datetime]
    completed_at: Optional[datetime]


class GenerationSummary(BaseModel):
    id: str
    experiment_id: str
    generation_number: int
    best_fitness: Optional[float]
    avg_fitness: Optional[float]
    worst_fitness: Optional[float]
    status: str
    created_at: datetime
    completed_at: Optional[datetime]


class AgentResult(BaseModel):
    id: str
    generation_id: str
    agent_index: int
    genome: Optional[Dict[str, Any]]
    neural_weights: Optional[Dict[str, Any]]
    q_table: Optional[Dict[str, Any]]
    fitness_score: Optional[float]
    survival_time: Optional[int]
    score: Optional[int]
    win_status: Optional[bool]
    execution_log: Optional[str]
    error_message: Optional[str]
    evaluation_duration_ms: Optional[int]
    evaluated_at: Optional[datetime]


class GenerationDetail(BaseModel):
    generation: GenerationSummary
    agents: List[AgentResult]


class FitnessDataPoint(BaseModel):
    generation_number: int
    best_fitness: Optional[float]
    avg_fitness: Optional[float]
    worst_fitness: Optional[float]
    completed_at: Optional[datetime]


class LrmSessionEntry(BaseModel):
    id: str
    experiment_id: str
    generation_number: int
    mutation_type: Optional[str]
    reasoning: Optional[str]
    code_before: Optional[str]
    code_after: Optional[str]
    validation_status: Optional[str]
    created_at: datetime


class SystemEvent(BaseModel):
    id: str
    event_type: str
    service: Optional[str]
    experiment_id: Optional[str]
    payload: Optional[Dict[str, Any]]
    severity: Optional[str]
    created_at: datetime


# ---------------------------------------------------------------------------
# Helper – raw asyncpg connection as FastAPI dependency
# ---------------------------------------------------------------------------

async def get_conn() -> asyncpg.Connection:
    conn = await get_raw_connection()
    try:
        yield conn
    finally:
        await conn.close()


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@router.post("/experiments", response_model=ExperimentResponse, status_code=201)
async def create_experiment(
    body: ExperimentCreate,
    conn: asyncpg.Connection = Depends(get_conn),
) -> ExperimentResponse:
    """Create a new evolution experiment."""
    import json

    experiment_id = str(uuid.uuid4())
    config_json = json.dumps(body.config) if body.config else None

    try:
        row = await conn.fetchrow(
            """
            INSERT INTO experiments
                (id, name, description, simulator_type, objective_function, config, status, created_at)
            VALUES ($1, $2, $3, $4, $5, $6::jsonb, 'pending', NOW())
            RETURNING id, name, description, simulator_type, objective_function,
                      config::text, status, created_at, started_at, completed_at
            """,
            experiment_id,
            body.name,
            body.description,
            body.simulatorType,
            body.objectiveFunction,
            config_json,
        )
    except asyncpg.PostgresError as exc:
        logger.error("db_error_create_experiment", error=str(exc))
        raise HTTPException(status_code=500, detail="Database error creating experiment")

    return _row_to_experiment(row)


@router.get("/experiments", response_model=List[ExperimentResponse])
async def list_experiments(
    conn: asyncpg.Connection = Depends(get_conn),
) -> List[ExperimentResponse]:
    """Return all experiments ordered by creation time descending."""
    try:
        rows = await conn.fetch(
            """
            SELECT id, name, description, simulator_type, objective_function,
                   config::text, status, created_at, started_at, completed_at
            FROM experiments
            ORDER BY created_at DESC
            """
        )
    except asyncpg.PostgresError as exc:
        logger.error("db_error_list_experiments", error=str(exc))
        raise HTTPException(status_code=500, detail="Database error listing experiments")

    return [_row_to_experiment(r) for r in rows]


@router.get("/experiments/{experiment_id}", response_model=ExperimentResponse)
async def get_experiment(
    experiment_id: str,
    conn: asyncpg.Connection = Depends(get_conn),
) -> ExperimentResponse:
    """Return detail for a single experiment."""
    row = await _fetch_experiment_or_404(conn, experiment_id)
    return _row_to_experiment(row)


@router.post("/experiments/{experiment_id}/start", response_model=Dict[str, Any])
async def start_experiment(
    experiment_id: str,
    request: Request,
    background_tasks: BackgroundTasks,
    conn: asyncpg.Connection = Depends(get_conn),
) -> Dict[str, Any]:
    """Start the evolution loop for an experiment in the background."""
    row = await _fetch_experiment_or_404(conn, experiment_id)

    if row["status"] not in ("pending", "failed"):
        raise HTTPException(
            status_code=409,
            detail=f"Experiment is already in state '{row['status']}' and cannot be started",
        )

    try:
        await conn.execute(
            "UPDATE experiments SET status = 'running', started_at = NOW() WHERE id = $1",
            experiment_id,
        )
    except asyncpg.PostgresError as exc:
        logger.error("db_error_start_experiment", experiment_id=experiment_id, error=str(exc))
        raise HTTPException(status_code=500, detail="Database error starting experiment")

    evolution_graph = request.app.state.evolution_graph

    async def run_evolution() -> None:
        try:
            initial_state = {
                "experiment_id": experiment_id,
                "generation_number": 0,
                "population": [],
                "best_fitness": 0.0,
                "avg_fitness": 0.0,
                "stagnation_count": 0,
                "current_code": "",
                "mutation_history": [],
                "reasoning_trace": [],
                "status": "initializing",
                "error": None,
                "game_type": row["simulator_type"],
            }
            config = {"configurable": {"thread_id": experiment_id}}
            async for _ in evolution_graph.astream(initial_state, config=config):
                pass
        except Exception as exc:  # noqa: BLE001
            logger.error("evolution_loop_error", experiment_id=experiment_id, error=str(exc))
            raw_conn = await get_raw_connection()
            try:
                await raw_conn.execute(
                    "UPDATE experiments SET status = 'failed' WHERE id = $1",
                    experiment_id,
                )
            finally:
                await raw_conn.close()

    background_tasks.add_task(run_evolution)

    return {"status": "started", "experiment_id": experiment_id}


@router.get("/experiments/{experiment_id}/generations", response_model=List[GenerationSummary])
async def list_generations(
    experiment_id: str,
    conn: asyncpg.Connection = Depends(get_conn),
) -> List[GenerationSummary]:
    """List all generations with fitness data for an experiment."""
    await _fetch_experiment_or_404(conn, experiment_id)

    try:
        rows = await conn.fetch(
            """
            SELECT id, experiment_id, generation_number,
                   best_fitness, avg_fitness, worst_fitness,
                   status, created_at, completed_at
            FROM generations
            WHERE experiment_id = $1
            ORDER BY generation_number ASC
            """,
            experiment_id,
        )
    except asyncpg.PostgresError as exc:
        logger.error("db_error_list_generations", experiment_id=experiment_id, error=str(exc))
        raise HTTPException(status_code=500, detail="Database error listing generations")

    return [_row_to_generation(r) for r in rows]


@router.get(
    "/experiments/{experiment_id}/generations/{generation_number}",
    response_model=GenerationDetail,
)
async def get_generation(
    experiment_id: str,
    generation_number: int,
    conn: asyncpg.Connection = Depends(get_conn),
) -> GenerationDetail:
    """Return generation detail including all agent results."""
    import json

    await _fetch_experiment_or_404(conn, experiment_id)

    gen_row = await conn.fetchrow(
        """
        SELECT id, experiment_id, generation_number,
               best_fitness, avg_fitness, worst_fitness,
               status, created_at, completed_at
        FROM generations
        WHERE experiment_id = $1 AND generation_number = $2
        """,
        experiment_id,
        generation_number,
    )
    if not gen_row:
        raise HTTPException(
            status_code=404,
            detail=f"Generation {generation_number} not found for experiment {experiment_id}",
        )

    agent_rows = await conn.fetch(
        """
        SELECT id, generation_id, agent_index,
               genome::text, neural_weights::text, q_table::text,
               fitness_score, survival_time, score, win_status,
               execution_log, error_message, evaluation_duration_ms, evaluated_at
        FROM agents
        WHERE generation_id = $1
        ORDER BY agent_index ASC
        """,
        gen_row["id"],
    )

    agents = []
    for r in agent_rows:
        agents.append(
            AgentResult(
                id=str(r["id"]),
                generation_id=str(r["generation_id"]),
                agent_index=r["agent_index"],
                genome=json.loads(r["genome"]) if r["genome"] else None,
                neural_weights=json.loads(r["neural_weights"]) if r["neural_weights"] else None,
                q_table=json.loads(r["q_table"]) if r["q_table"] else None,
                fitness_score=r["fitness_score"],
                survival_time=r["survival_time"],
                score=r["score"],
                win_status=r["win_status"],
                execution_log=r["execution_log"],
                error_message=r["error_message"],
                evaluation_duration_ms=r["evaluation_duration_ms"],
                evaluated_at=r["evaluated_at"],
            )
        )

    return GenerationDetail(generation=_row_to_generation(gen_row), agents=agents)


@router.get(
    "/experiments/{experiment_id}/fitness-history",
    response_model=List[FitnessDataPoint],
)
async def get_fitness_history(
    experiment_id: str,
    conn: asyncpg.Connection = Depends(get_conn),
) -> List[FitnessDataPoint]:
    """Time-series fitness data suitable for charting."""
    await _fetch_experiment_or_404(conn, experiment_id)

    try:
        rows = await conn.fetch(
            """
            SELECT generation_number, best_fitness, avg_fitness, worst_fitness, completed_at
            FROM generations
            WHERE experiment_id = $1
            ORDER BY generation_number ASC
            """,
            experiment_id,
        )
    except asyncpg.PostgresError as exc:
        logger.error("db_error_fitness_history", experiment_id=experiment_id, error=str(exc))
        raise HTTPException(status_code=500, detail="Database error fetching fitness history")

    return [
        FitnessDataPoint(
            generation_number=r["generation_number"],
            best_fitness=r["best_fitness"],
            avg_fitness=r["avg_fitness"],
            worst_fitness=r["worst_fitness"],
            completed_at=r["completed_at"],
        )
        for r in rows
    ]


@router.get("/lrm-sessions/{experiment_id}", response_model=List[LrmSessionEntry])
async def get_lrm_sessions(
    experiment_id: str,
    conn: asyncpg.Connection = Depends(get_conn),
) -> List[LrmSessionEntry]:
    """Return LRM mutation history for an experiment."""
    import json

    await _fetch_experiment_or_404(conn, experiment_id)

    try:
        rows = await conn.fetch(
            """
            SELECT id, experiment_id, generation_number,
                   mutation_type, reasoning, code_before, code_after,
                   validation_status, created_at
            FROM lrm_sessions
            WHERE experiment_id = $1
            ORDER BY created_at ASC
            """,
            experiment_id,
        )
    except asyncpg.PostgresError as exc:
        logger.error("db_error_lrm_sessions", experiment_id=experiment_id, error=str(exc))
        raise HTTPException(status_code=500, detail="Database error fetching LRM sessions")

    return [
        LrmSessionEntry(
            id=str(r["id"]),
            experiment_id=str(r["experiment_id"]),
            generation_number=r["generation_number"],
            mutation_type=r["mutation_type"],
            reasoning=r["reasoning"],
            code_before=r["code_before"],
            code_after=r["code_after"],
            validation_status=r["validation_status"],
            created_at=r["created_at"],
        )
        for r in rows
    ]


@router.get("/system-events", response_model=List[SystemEvent])
async def get_system_events(
    conn: asyncpg.Connection = Depends(get_conn),
    limit: int = 100,
    offset: int = 0,
) -> List[SystemEvent]:
    """Return system audit log events ordered by time descending."""
    import json

    try:
        rows = await conn.fetch(
            """
            SELECT id, event_type, service, experiment_id,
                   payload::text, severity, created_at
            FROM system_events
            ORDER BY created_at DESC
            LIMIT $1 OFFSET $2
            """,
            limit,
            offset,
        )
    except asyncpg.PostgresError as exc:
        logger.error("db_error_system_events", error=str(exc))
        raise HTTPException(status_code=500, detail="Database error fetching system events")

    return [
        SystemEvent(
            id=str(r["id"]),
            event_type=r["event_type"],
            service=r["service"],
            experiment_id=str(r["experiment_id"]) if r["experiment_id"] else None,
            payload=json.loads(r["payload"]) if r["payload"] else None,
            severity=r["severity"],
            created_at=r["created_at"],
        )
        for r in rows
    ]


# ---------------------------------------------------------------------------
# Private helpers
# ---------------------------------------------------------------------------

async def _fetch_experiment_or_404(conn: asyncpg.Connection, experiment_id: str):
    """Fetch an experiment row or raise 404."""
    try:
        row = await conn.fetchrow(
            """
            SELECT id, name, description, simulator_type, objective_function,
                   config::text, status, created_at, started_at, completed_at
            FROM experiments
            WHERE id = $1
            """,
            experiment_id,
        )
    except asyncpg.PostgresError as exc:
        logger.error("db_error_fetch_experiment", experiment_id=experiment_id, error=str(exc))
        raise HTTPException(status_code=500, detail="Database error fetching experiment")

    if not row:
        raise HTTPException(status_code=404, detail=f"Experiment '{experiment_id}' not found")
    return row


def _row_to_experiment(row) -> ExperimentResponse:
    import json

    return ExperimentResponse(
        id=str(row["id"]),
        name=row["name"],
        description=row["description"],
        simulator_type=row["simulator_type"],
        objective_function=row["objective_function"],
        config=json.loads(row["config"]) if row["config"] else None,
        status=row["status"],
        created_at=row["created_at"],
        started_at=row["started_at"],
        completed_at=row["completed_at"],
    )


def _row_to_generation(row) -> GenerationSummary:
    return GenerationSummary(
        id=str(row["id"]),
        experiment_id=str(row["experiment_id"]),
        generation_number=row["generation_number"],
        best_fitness=row["best_fitness"],
        avg_fitness=row["avg_fitness"],
        worst_fitness=row["worst_fitness"],
        status=row["status"],
        created_at=row["created_at"],
        completed_at=row["completed_at"],
    )

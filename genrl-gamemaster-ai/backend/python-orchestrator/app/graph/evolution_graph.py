# GenRL GameMaster AI - Evolution Graph (LangGraph)

from typing import TypedDict, Annotated, List, Optional, Dict, Any
from langgraph.graph import StateGraph, END
from langgraph.checkpoint.memory import MemorySaver
from langchain_core.messages import BaseMessage, HumanMessage, AIMessage, SystemMessage
from langchain_openai import ChatOpenAI
import json
import uuid
from datetime import datetime

from app.core.config import settings
from app.services.llm_cascade import LLMCascadeService
from app.services.sandbox import SandboxService
from app.core.database import get_db_session


class EvolutionState(TypedDict):
    experiment_id: str
    generation_number: int
    population: List[Dict[str, Any]]
    best_fitness: float
    avg_fitness: float
    stagnation_count: int
    current_code: str
    mutation_history: List[Dict[str, Any]]
    reasoning_trace: List[str]
    status: str
    error: Optional[str]


async def initialize_population(state: EvolutionState) -> EvolutionState:
    """Initialize the first generation population with random genomes."""
    from app.services.genome import GenomeService
    
    genome_service = GenomeService()
    population = await genome_service.create_initial_population(
        size=settings.DEFAULT_POPULATION_SIZE,
        game_type=state.get("game_type", "pacman")
    )
    
    return {
        **state,
        "population": population,
        "generation_number": 1,
        "stagnation_count": 0,
        "status": "evaluating"
    }


async def evaluate_population(state: EvolutionState) -> EvolutionState:
    """Evaluate all agents in the current population using the Java simulator."""
    from app.services.evaluation import EvaluationService
    
    eval_service = EvaluationService()
    results = await eval_service.evaluate_generation(
        experiment_id=state["experiment_id"],
        generation_number=state["generation_number"],
        population=state["population"],
        game_type=state.get("game_type", "pacman")
    )
    
    fitness_scores = [r["fitness_score"] for r in results if r["fitness_score"] is not None]
    best_fitness = max(fitness_scores) if fitness_scores else 0.0
    avg_fitness = sum(fitness_scores) / len(fitness_scores) if fitness_scores else 0.0
    
    # Check for stagnation
    stagnation_count = state.get("stagnation_count", 0)
    if best_fitness <= state.get("best_fitness", 0):
        stagnation_count += 1
    else:
        stagnation_count = 0
    
    return {
        **state,
        "population": results,
        "best_fitness": best_fitness,
        "avg_fitness": avg_fitness,
        "stagnation_count": stagnation_count,
        "status": "evaluated"
    }


async def check_stagnation(state: EvolutionState) -> str:
    """Determine if we need to trigger mutation or continue evolution."""
    if state["stagnation_count"] >= settings.STAGNATION_THRESHOLD:
        return "mutate"
    if state["generation_number"] >= settings.MAX_GENERATIONS:
        return "complete"
    return "evolve"


async def evolve_population(state: EvolutionState) -> EvolutionState:
    """Apply genetic operators to create next generation."""
    from app.services.genome import GenomeService
    
    genome_service = GenomeService()
    next_population = await genome_service.evolve(
        population=state["population"],
        mutation_rate=settings.MUTATION_RATE,
        crossover_rate=settings.CROSSOVER_RATE
    )
    
    return {
        **state,
        "population": next_population,
        "generation_number": state["generation_number"] + 1,
        "status": "evaluating"
    }


async def mutate_algorithm(state: EvolutionState) -> EvolutionState:
    """Use LRM to mutate the algorithm code when stagnation detected."""
    llm_service = LLMCascadeService()
    
    # Prepare context for LLM
    context = {
        "experiment_id": state["experiment_id"],
        "generation_number": state["generation_number"],
        "best_fitness": state["best_fitness"],
        "avg_fitness": state["avg_fitness"],
        "stagnation_count": state["stagnation_count"],
        "current_code": state["current_code"],
        "mutation_history": state["mutation_history"],
        "recent_fitness": [p.get("fitness_score") for p in state["population"][-10:]]
    }
    
    # Get mutation from LLM with reasoning
    mutation_result = await llm_service.generate_mutation(context)
    
    # Validate and test mutation in sandbox
    sandbox_service = SandboxService()
    validation_result = await sandbox_service.validate_code(
        code=mutation_result["mutated_code"],
        language="python"
    )
    
    if not validation_result["valid"]:
        # Retry with error feedback
        context["validation_error"] = validation_result["error"]
        mutation_result = await llm_service.generate_mutation(context)
        validation_result = await sandbox_service.validate_code(
            code=mutation_result["mutated_code"],
            language="python"
        )
    
    # Record mutation
    new_mutation = {
        "generation": state["generation_number"],
        "mutation_type": mutation_result["mutation_type"],
        "reasoning": mutation_result["reasoning"],
        "code_diff": mutation_result.get("diff", ""),
        "validation_status": "valid" if validation_result["valid"] else "failed",
        "timestamp": datetime.utcnow().isoformat()
    }
    
    return {
        **state,
        "current_code": mutation_result["mutated_code"],
        "mutation_history": state["mutation_history"] + [new_mutation],
        "reasoning_trace": state["reasoning_trace"] + [mutation_result["reasoning"]],
        "stagnation_count": 0,  # Reset stagnation after mutation
        "status": "mutated"
    }


async def persist_generation(state: EvolutionState) -> EvolutionState:
    """Persist generation results to database."""
    async with get_db_session() as session:
        from sqlalchemy import text
        
        # Update generation record
        await session.execute(
            text("""
                UPDATE generations 
                SET best_fitness = :best, avg_fitness = :avg, worst_fitness = :worst,
                    mutation_code = :mut_code, mutation_reasoning = :mut_reason,
                    status = 'completed', completed_at = NOW()
                WHERE experiment_id = :exp_id AND generation_number = :gen_num
            """),
            {
                "best": state["best_fitness"],
                "avg": state["avg_fitness"],
                "worst": min(p.get("fitness_score", 0) for p in state["population"]),
                "mut_code": state.get("current_code", ""),
                "mut_reason": state["reasoning_trace"][-1] if state["reasoning_trace"] else "",
                "exp_id": state["experiment_id"],
                "gen_num": state["generation_number"]
            }
        )
        
        # Bulk insert agents
        for i, agent in enumerate(state["population"]):
            await session.execute(
                text("""
                    INSERT INTO agents (generation_id, agent_index, genome, neural_weights, q_table,
                                      fitness_score, survival_time, score, win_status,
                                      execution_log, error_message, evaluation_duration_ms, evaluated_at)
                    SELECT id, :idx, :genome, :weights, :qtable, :fitness, :survival, :score, :win,
                           :log, :error, :duration, NOW()
                    FROM generations WHERE experiment_id = :exp_id AND generation_number = :gen_num
                """),
                {
                    "idx": i,
                    "genome": json.dumps(agent.get("genome", {})),
                    "weights": json.dumps(agent.get("neural_weights", {})),
                    "qtable": json.dumps(agent.get("q_table", {})),
                    "fitness": agent.get("fitness_score"),
                    "survival": agent.get("survival_time"),
                    "score": agent.get("score"),
                    "win": agent.get("win_status", False),
                    "log": agent.get("execution_log", ""),
                    "error": agent.get("error_message", ""),
                    "duration": agent.get("evaluation_duration_ms"),
                    "exp_id": state["experiment_id"],
                    "gen_num": state["generation_number"]
                }
            )
        
        await session.commit()
    
    return {**state, "status": "persisted"}


async def complete_experiment(state: EvolutionState) -> EvolutionState:
    """Mark experiment as complete."""
    async with get_db_session() as session:
        from sqlalchemy import text
        await session.execute(
            text("UPDATE experiments SET status = 'completed', completed_at = NOW() WHERE id = :id"),
            {"id": state["experiment_id"]}
        )
        await session.commit()
    
    return {**state, "status": "completed"}


def create_evolution_graph() -> StateGraph:
    """Create the LangGraph evolution workflow."""
    
    workflow = StateGraph(EvolutionState)
    
    # Add nodes
    workflow.add_node("initialize", initialize_population)
    workflow.add_node("evaluate", evaluate_population)
    workflow.add_node("persist", persist_generation)
    workflow.add_node("evolve", evolve_population)
    workflow.add_node("mutate", mutate_algorithm)
    workflow.add_node("complete", complete_experiment)
    
    # Set entry point
    workflow.set_entry_point("initialize")
    
    # Add edges
    workflow.add_edge("initialize", "evaluate")
    workflow.add_edge("evaluate", "persist")
    
    # Conditional routing after evaluation
    workflow.add_conditional_edges(
        "persist",
        check_stagnation,
        {
            "mutate": "mutate",
            "evolve": "evolve",
            "complete": "complete"
        }
    )
    
    workflow.add_edge("mutate", "evolve")
    workflow.add_edge("evolve", "evaluate")
    workflow.add_edge("complete", END)
    
    # Compile with checkpointing
    memory = MemorySaver()
    return workflow.compile(checkpointer=memory)
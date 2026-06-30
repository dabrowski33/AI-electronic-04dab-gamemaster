# GenRL GameMaster AI - Python Orchestrator Service
# Main application entry point

import os
import asyncio
from contextlib import asynccontextmanager
from typing import AsyncGenerator

import structlog
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic_settings import BaseSettings

from app.core.config import settings
from app.core.database import init_db, close_db
from app.api.routes import router as api_router
from app.graph.evolution_graph import create_evolution_graph
from app.services.sandbox import SandboxService
from app.services.llm_cascade import LLMCascadeService

structlog.configure(
    processors=[
        structlog.stdlib.filter_by_level,
        structlog.stdlib.add_logger_name,
        structlog.stdlib.add_log_level,
        structlog.stdlib.PositionalArgumentsFormatter(),
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
        structlog.processors.UnicodeDecoder(),
        structlog.processors.JSONRenderer()
    ],
    wrapper_class=structlog.stdlib.BoundLogger,
    logger_factory=structlog.stdlib.LoggerFactory(),
    cache_logger_on_first_use=True,
)

logger = structlog.get_logger()


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    logger.info("Starting Python Orchestrator Service")
    await init_db()
    app.state.evolution_graph = create_evolution_graph()
    app.state.sandbox_service = SandboxService()
    app.state.llm_cascade = LLMCascadeService()
    logger.info("Python Orchestrator Service started successfully")
    yield
    await close_db()
    logger.info("Python Orchestrator Service shut down")


def create_app() -> FastAPI:
    app = FastAPI(
        title="GenRL GameMaster AI - Orchestrator",
        description="AI Core Orchestration Service using LangGraph for evolutionary RL",
        version="1.0.0",
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.CORS_ORIGINS,
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(api_router, prefix="/api/v1")

    @app.get("/health")
    async def health_check():
        return {"status": "healthy", "service": "python-orchestrator"}

    @app.get("/health/ready")
    async def readiness_check():
        return {"status": "ready", "service": "python-orchestrator"}

    return app


app = create_app()

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host="0.0.0.0",
        port=8000,
        reload=settings.DEBUG,
        log_config=None,
    )
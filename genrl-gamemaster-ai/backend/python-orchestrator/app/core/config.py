# GenRL GameMaster AI - Configuration Settings

from functools import lru_cache
from typing import List
from pydantic_settings import BaseSettings
from pydantic import Field


class Settings(BaseSettings):
    # Application
    APP_NAME: str = "GenRL GameMaster AI - Orchestrator"
    DEBUG: bool = Field(default=False, env="DEBUG")
    ENVIRONMENT: str = Field(default="development", env="ENVIRONMENT")

    # Database
    DATABASE_URL: str = Field(
        default="postgresql://genrl:genrl_secure_pass@localhost:5432/genrl_gamemaster",
        env="DATABASE_URL"
    )
    DATABASE_POOL_SIZE: int = Field(default=20, env="DATABASE_POOL_SIZE")
    DATABASE_MAX_OVERFLOW: int = Field(default=10, env="DATABASE_MAX_OVERFLOW")

    # Redis
    REDIS_URL: str = Field(default="redis://localhost:6379/0", env="REDIS_URL")

    # External Services
    JAVA_SIMULATOR_URL: str = Field(default="http://localhost:8080", env="JAVA_SIMULATOR_URL")
    NODE_BFF_URL: str = Field(default="http://localhost:3000", env="NODE_BFF_URL")

    # LLM Providers
    OPENROUTER_API_KEY: str = Field(default="", env="OPENROUTER_API_KEY")
    OPENROUTER_BASE_URL: str = Field(default="https://openrouter.ai/api/v1", env="OPENROUTER_BASE_URL")
    OPENROUTER_MODEL: str = Field(default="nousresearch/hermes-3-70b", env="OPENROUTER_MODEL")

    OPENAI_API_KEY: str = Field(default="", env="OPENAI_API_KEY")
    OPENAI_BASE_URL: str = Field(default="https://api.openai.com/v1", env="OPENAI_BASE_URL")
    OPENAI_MODEL: str = Field(default="gpt-4o", env="OPENAI_MODEL")

    # LLM Cascade Settings
    LLM_TIMEOUT_SECONDS: int = Field(default=60, env="LLM_TIMEOUT_SECONDS")
    LLM_MAX_RETRIES: int = Field(default=2, env="LLM_MAX_RETRIES")
    LLM_FALLBACK_ENABLED: bool = Field(default=True, env="LLM_FALLBACK_ENABLED")

    # Evolution Settings
    DEFAULT_POPULATION_SIZE: int = Field(default=50, env="DEFAULT_POPULATION_SIZE")
    MAX_GENERATIONS: int = Field(default=200, env="MAX_GENERATIONS")
    STAGNATION_THRESHOLD: int = Field(default=50, env="STAGNATION_THRESHOLD")
    MUTATION_RATE: float = Field(default=0.1, env="MUTATION_RATE")
    CROSSOVER_RATE: float = Field(default=0.7, env="CROSSOVER_RATE")

    # Sandbox Settings
    SANDBOX_TIMEOUT_SECONDS: int = Field(default=60, env="SANDBOX_TIMEOUT_SECONDS")
    SANDBOX_MEMORY_LIMIT_MB: int = Field(default=512, env="SANDBOX_MEMORY_LIMIT_MB")
    SANDBOX_CPU_LIMIT: float = Field(default=1.0, env="SANDBOX_CPU_LIMIT")
    OPENCODE_IMAGE: str = Field(default="opencode/sandbox:latest", env="OPENCODE_IMAGE")

    # Game Settings
    SUPPORTED_GAMES: List[str] = Field(default=["pacman", "super_mario"], env="SUPPORTED_GAMES")
    FRAME_SKIP: int = Field(default=4, env="FRAME_SKIP")
    OBSERVATION_TYPE: str = Field(default="PIXELS", env="OBSERVATION_TYPE")

    # CORS
    CORS_ORIGINS: List[str] = Field(
        default=["http://localhost:5173", "http://localhost:3000"],
        env="CORS_ORIGINS"
    )

    # Logging
    LOG_LEVEL: str = Field(default="INFO", env="LOG_LEVEL")

    # Prometheus
    PROMETHEUS_PORT: int = Field(default=9090, env="PROMETHEUS_PORT")

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"
        case_sensitive = True


@lru_cache()
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
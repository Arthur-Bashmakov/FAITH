from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Runtime settings supplied through environment variables."""

    app_name: str = "FAITH API"
    database_url: str = "postgresql+psycopg://faith:faith_local_password@localhost:5432/faith"
    redis_url: str = "redis://localhost:6379/0"
    max_audio_size_mb: int = 20
    cache_ttl_seconds: int = 86_400
    rate_limit_requests: int = 30
    rate_limit_window_seconds: int = 60
    admin_username: str | None = None
    admin_password: str | None = None
    allowed_hosts: str = "*"
    access_token_ttl_seconds: int = 2_592_000
    auth_required: bool = False

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()

"""Settings from environment / .env. No secrets in code (CLAUDE.md)."""

from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_prefix="", extra="ignore")

    app_env: str = "development"
    database_url: str = "postgresql+asyncpg://postgres@127.0.0.1:5432/foodlens"
    public_base_url: str = "http://10.0.2.2:8000"
    upload_dir: Path = Path("var/uploads")
    upload_max_bytes: int = 10 * 1024 * 1024
    upload_url_ttl_seconds: int = 15 * 60
    guest_token_ttl_days: int = 90
    min_supported_app_version_code: int = 1
    latest_app_version_code: int = 1
    maintenance: bool = False
    cors_allow_origins: list[str] = []
    # Feature flags (SPEC §8.4); all default off except contributions.
    feature_contributions: bool = True
    feature_public_score: bool = False
    feature_sponsored: bool = False
    feature_off_lookup: bool = False
    feature_google_sign_in: bool = False


@lru_cache
def get_settings() -> Settings:
    return Settings()

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "hotel-ai-service"
    database_url: str = "postgresql://hotel_user:hotel_password@localhost:5432/hotel_db"
    analytics_database_url: str | None = None
    revenue_database_url: str | None = None
    customer_analytics_database_url: str | None = None
    backend_url: str = "http://localhost:8080/api/v1"
    ai_provider: str = "auto"
    gemini_api_key: str | None = None
    gemini_chat_model: str = "gemini-3.1-flash-lite"
    gemini_embedding_model: str = "gemini-embedding-2"
    openai_api_key: str | None = None
    openai_chat_model: str = "gpt-5.4-mini"
    openai_embedding_model: str = "text-embedding-3-small"
    request_timeout_seconds: float = 15
    jwt_jwk_set_uri: str = "http://identity-service:8101/.well-known/jwks.json"
    jwt_issuer: str = "http://identity-service:8101"
    jwt_audience: str = "hotel-api"

    @property
    def active_provider(self) -> str:
        requested = self.ai_provider.strip().lower()
        if requested == "gemini" and self.gemini_api_key:
            return "gemini"
        if requested == "openai" and self.openai_api_key:
            return "openai"
        if requested == "auto":
            if self.gemini_api_key:
                return "gemini"
            if self.openai_api_key:
                return "openai"
        return "fallback"

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()

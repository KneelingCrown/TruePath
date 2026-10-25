from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    app_name: str = "True Path API"
    database_url: str = "sqlite:///./truepath.db"
    uploads_dir: str = "./uploads"
    extra_search_max_queries: int = 3


settings = Settings()

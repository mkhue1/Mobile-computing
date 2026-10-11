from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    database_url: str
    jwt_secret: str
    jwt_algorithm: str = "HS256"
    jwt_expire_days: int = 7

    steam_api_key: str = ""
    public_base_url: str = "http://10.0.2.2:8000"
    steam_deep_link_success: str = "gamercalendar://steam/linked"
    steam_deep_link_error: str = "gamercalendar://steam/error"
    steam_link_state_expire_minutes: int = 10


settings = Settings()

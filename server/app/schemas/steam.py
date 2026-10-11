from pydantic import BaseModel


class SteamStatusResponse(BaseModel):
    linked: bool
    steam_id: str | None = None


class SteamLinkResponse(BaseModel):
    auth_url: str

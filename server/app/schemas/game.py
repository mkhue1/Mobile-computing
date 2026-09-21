from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class GameCreate(BaseModel):
    igdb_id: int = Field(gt=0)
    name: str = Field(min_length=1, max_length=255)
    cover_url: str | None = None


class GameResponse(BaseModel):
    id: UUID
    igdb_id: int
    name: str
    cover_url: str | None
    last_synced_at: datetime | None

    model_config = ConfigDict(from_attributes=True)

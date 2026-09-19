from datetime import datetime
from enum import Enum
from uuid import UUID
from pydantic import BaseModel, ConfigDict, EmailStr
from app.models.gaming_session import SessionType, SessionVisibility, SessionStatus

class SessionCreate(BaseModel):
    organiser_id: UUID
    game_id: UUID
    group_id: UUID | None

    title: str | None
    description: str | None

    start_at: datetime
    end_at: datetime

    session_type: SessionType 
    visibility: SessionVisibility
    status:   SessionStatus

    location_name: str | None
    player_limit: int | None


class SessionResponse(BaseModel):
    id: UUID
    organiser_id: UUID
    game_id: UUID
    group_id: UUID | None

    title: str | None
    description: str | None

    start_at: datetime
    end_at: datetime

    session_type: SessionType 
    visibility: SessionVisibility
    status:   SessionStatus

    location_name: str | None
    player_limit: int | None

    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)
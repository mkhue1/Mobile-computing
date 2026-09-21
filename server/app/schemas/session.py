from datetime import datetime
from enum import Enum
from uuid import UUID
from pydantic import BaseModel, ConfigDict, EmailStr
from app.models.gaming_session import InviteStatus, SessionType, SessionVisibility, SessionStatus

class SessionCreate(BaseModel):
    organiser_id: UUID
    game_id: UUID
    group_id: UUID | None = None

    title: str | None = None
    description: str | None = None

    start_at: datetime
    end_at: datetime

    session_type: SessionType 
    visibility: SessionVisibility
    status:   SessionStatus

    location_name: str | None = None
    player_limit: int | None = None

class SessionResponse(BaseModel):
    id: UUID
    organiser_id: UUID
    game_id: UUID
    group_id: UUID | None = None

    title: str | None = None
    description: str | None = None

    start_at: datetime
    end_at: datetime

    session_type: SessionType 
    visibility: SessionVisibility
    status:   SessionStatus

    location_name: str | None = None
    player_limit: int | None = None
    player_count: int

    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(from_attributes=True)

class InviteCreate(BaseModel):
    session_id: UUID
    sender_id: UUID
    receiver_id: UUID

class InviteCreateResponse(BaseModel):
    id: UUID
    session_id: UUID
    sender_id: UUID
    receiver_id: UUID
    status: InviteStatus
    created_at: datetime
    responded_at: datetime | None

    model_config = ConfigDict(from_attributes=True)


class InviteResponse(BaseModel):
    id: UUID
    session_id: UUID
    sender_id: UUID
    receiver_id: UUID
    status: InviteStatus
    created_at: datetime
    responded_at: datetime | None = None

    model_config = ConfigDict(from_attributes=True)

class InviteAccept(BaseModel):
    invite_id: UUID
    session_id: UUID
    receiver_id: UUID

class InviteDecline(BaseModel):
    invite_id: UUID
    receiver_id: UUID

class InviteAcitionResponse(BaseModel):
    status: bool
    message: str
    id: UUID

class ParticipantCreate(BaseModel):
    session_id: UUID
    user_id: UUID
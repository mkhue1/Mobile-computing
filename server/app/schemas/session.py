from datetime import datetime
from enum import Enum
from uuid import UUID
from pydantic import BaseModel, ConfigDict, EmailStr
from app.models.gaming_session import InviteStatus, SessionType, SessionVisibility, SessionStatus

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

class InviteCreate(BaseModel):
    id: UUID
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

class InviteAccept(BaseModel):
    invite_id: UUID
    session_id: UUID
    accepter_id: UUID

# Not super sure how to use pydantic for basic success messages
class InviteAcceptResponse(BaseModel):
    status: bool
    message: str
    id: UUID
    
class InviteDecline(BaseModel):
    invite_id: UUID
    accepter_id: UUID

class InviteDeclineResponse(BaseModel):
    status: bool
    message: str
    id: UUID

class ParticipantCreate(BaseModel):
    session_id: UUID
    user_id: UUID
from datetime import datetime
from enum import Enum
from uuid import UUID
from pydantic import AwareDatetime, BaseModel, ConfigDict, EmailStr, Field, model_validator
from app.models.gaming_session import InviteStatus, SessionType, SessionVisibility, SessionStatus
from app.schemas.user import UserResponse

class SessionCreate(BaseModel):
    game_id: UUID
    group_id: UUID | None = None

    title: str | None = None
    description: str | None = None

    start_at: datetime
    end_at: datetime

    session_type: SessionType 
    visibility: SessionVisibility

    location_name: str | None = None
    player_limit: int | None = None

class SessionUpdate(BaseModel):
    """
    Partial update: only fields present in the request are changed, and null clears an optional field.
    group_id is accepted so the full edit form can be sent, but it must match the current group.
    """

    game_id: UUID | None = None
    group_id: UUID | None = None

    title: str | None = None
    description: str | None = None

    start_at: AwareDatetime | None = None
    end_at: AwareDatetime | None = None

    session_type: SessionType | None = None
    visibility: SessionVisibility | None = None

    location_name: str | None = None
    player_limit: int | None = Field(default=None, gt=0)

    @model_validator(mode="after")
    def reject_null_required_fields(self):
        required = ("game_id", "start_at", "end_at", "session_type", "visibility")
        nulled = [name for name in required if name in self.model_fields_set and getattr(self, name) is None]
        if nulled:
            raise ValueError(f"{', '.join(nulled)} cannot be null")
        return self

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

class SessionCancel(BaseModel):
    session_id: UUID


class SessionCancelResponse(BaseModel):
    status: bool
    message: str
    id: UUID

class InviteCreate(BaseModel):
    session_id: UUID
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

class InviteDecline(BaseModel):
    invite_id: UUID

class InviteAcitionResponse(BaseModel):
    status: bool
    message: str
    id: UUID

class ParticipantResponse(BaseModel):
    user: UserResponse
    joined_at: datetime

class ParticipantCreate(BaseModel):
    session_id: UUID
    user_id: UUID
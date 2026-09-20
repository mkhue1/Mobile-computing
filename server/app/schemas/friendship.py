from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict


class FriendRequestCreate(BaseModel):
    receiver_id: UUID


class FriendRequestResponse(BaseModel):
    id: UUID
    sender_id: UUID
    receiver_id: UUID
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class FriendshipResponse(BaseModel):
    user_id: UUID
    friend_id: UUID
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)

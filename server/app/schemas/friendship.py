from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class FriendRequestCreate(BaseModel):
    receiver_id: int = Field(..., gt=0)


class FriendRequestResponse(BaseModel):
    id: int
    sender_id: int
    receiver_id: int
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class FriendshipResponse(BaseModel):
    user_id: int
    friend_id: int
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)

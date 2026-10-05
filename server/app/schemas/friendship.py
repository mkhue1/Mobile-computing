from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict

from app.schemas.user import UserResponse

"""
mutual: friend is on the your Steam friend list
not_friends: friend has Steam linked but is not a Steam friend
not_linked: friend has no Steam account linked
"""
SteamRelation = Literal["mutual", "not_friends", "not_linked"]


class FriendRequestCreate(BaseModel):
    receiver_id: UUID


class FriendRequestResponse(BaseModel):
    id: UUID
    sender_id: UUID
    receiver_id: UUID
    created_at: datetime
    sender: UserResponse | None = None
    receiver: UserResponse | None = None
    steam_relation: SteamRelation | None = None
    steam_persona_name: str | None = None

    model_config = ConfigDict(from_attributes=True)


class FriendshipResponse(BaseModel):
    user_id: UUID
    friend_id: UUID
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)

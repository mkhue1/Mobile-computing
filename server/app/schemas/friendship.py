from datetime import datetime

from pydantic import BaseModel, ConfigDict

from app.models.friendship import FriendshipStatus
from app.schemas.user import UserResponse


class FriendshipRecordResponse(BaseModel):
    """
    Basic representation of the friendship database record.
    Useful after creating a friend request.
    """

    id: int
    user_low_id: int
    user_high_id: int
    requested_by_id: int
    status: FriendshipStatus
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(
        from_attributes=True
    )


class IncomingFriendRequestResponse(BaseModel):
    """
    Friend request displayed to the receiving user.
    """

    id: int
    requester: UserResponse
    created_at: datetime
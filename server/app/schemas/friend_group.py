from datetime import datetime

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
)

from app.schemas.user import UserResponse


class FriendGroupCreate(BaseModel):
    name: str = Field(
        min_length=1,
        max_length=100,
    )


class FriendGroupUpdate(BaseModel):
    name: str = Field(
        min_length=1,
        max_length=100,
    )


class FriendGroupResponse(BaseModel):
    id: int
    owner_id: int
    name: str
    created_at: datetime
    updated_at: datetime

    model_config = ConfigDict(
        from_attributes=True
    )


class FriendGroupSummaryResponse(
    FriendGroupResponse
):
    member_count: int


class FriendGroupDetailResponse(
    FriendGroupResponse
):
    members: list[UserResponse] = Field(
        default_factory=list
    )
from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.models.user_group import GroupRole
from app.schemas.user import UserResponse


class GroupCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=120)


class GroupUpdate(BaseModel):
    name: str = Field(..., min_length=1, max_length=120)


class GroupMemberAdd(BaseModel):
    user_id: int = Field(..., gt=0)


class GroupOwnershipTransfer(BaseModel):
    new_owner_id: int = Field(..., gt=0)


class GroupMemberResponse(BaseModel):
    group_id: int
    user_id: int
    role: GroupRole
    joined_at: datetime
    user: UserResponse | None = None

    model_config = ConfigDict(from_attributes=True)


class GroupResponse(BaseModel):
    id: int
    name: str
    owner_id: int
    created_at: datetime

    model_config = ConfigDict(from_attributes=True)


class GroupDetailResponse(GroupResponse):
    members: list[GroupMemberResponse] = []

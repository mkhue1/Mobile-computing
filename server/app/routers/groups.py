from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.helpers.friendship import (
    are_friends,
    get_user_or_none,
    share_a_friend_in_group,
)
from app.models.user import User
from app.models.user_group import GroupRole, UserGroup, UserGroupMember
from app.schemas.user import UserResponse
from app.schemas.user_group import (
    GroupCreate,
    GroupDetailResponse,
    GroupMemberAdd,
    GroupMemberResponse,
    GroupOwnershipTransfer,
    GroupResponse,
    GroupUpdate,
)


router = APIRouter(
    prefix="/groups",
    tags=["groups"],
)


def _require_user(db: Session, user_id: int) -> User:
    user = get_user_or_none(db, user_id)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User {user_id} not found",
        )
    return user


def _get_group_or_404(db: Session, group_id: int) -> UserGroup:
    group = db.get(UserGroup, group_id)
    if group is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Group not found",
        )
    return group


def _get_membership(
    db: Session,
    group_id: int,
    user_id: int,
) -> UserGroupMember | None:
    return db.scalars(
        select(UserGroupMember).where(
            UserGroupMember.group_id == group_id,
            UserGroupMember.user_id == user_id,
        )
    ).first()


def _require_membership(
    db: Session,
    group_id: int,
    user_id: int,
) -> UserGroupMember:
    membership = _get_membership(db, group_id, user_id)
    if membership is None:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You are not a member of this group",
        )
    return membership


def _require_owner(db: Session, group: UserGroup, user_id: int) -> None:
    if group.owner_id != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the group owner can perform this action",
        )


def _group_member_ids(db: Session, group_id: int) -> set[int]:
    rows = db.scalars(
        select(UserGroupMember.user_id).where(
            UserGroupMember.group_id == group_id
        )
    ).all()
    return set(rows)


def _build_group_detail(db: Session, group: UserGroup) -> GroupDetailResponse:
    members = db.scalars(
        select(UserGroupMember).where(
            UserGroupMember.group_id == group.id
        )
    ).all()

    user_ids = [member.user_id for member in members]
    users_by_id: dict[int, User] = {}
    if user_ids:
        users = db.scalars(select(User).where(User.id.in_(user_ids))).all()
        users_by_id = {user.id: user for user in users}

    member_responses = [
        GroupMemberResponse(
            group_id=member.group_id,
            user_id=member.user_id,
            role=member.role,
            joined_at=member.joined_at,
            user=(
                UserResponse.model_validate(users_by_id[member.user_id])
                if member.user_id in users_by_id
                else None
            ),
        )
        for member in members
    ]

    return GroupDetailResponse(
        id=group.id,
        name=group.name,
        owner_id=group.owner_id,
        created_at=group.created_at,
        members=member_responses,
    )


@router.post(
    "/",
    response_model=GroupDetailResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_group(
    body: GroupCreate,
    user_id: int = Query(..., gt=0, description="Acting user id (owner)"),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)

    group = UserGroup(name=body.name.strip(), owner_id=user_id)
    db.add(group)
    db.flush()

    owner_membership = UserGroupMember(
        group_id=group.id,
        user_id=user_id,
        role=GroupRole.OWNER,
    )
    db.add(owner_membership)
    db.commit()
    db.refresh(group)

    return _build_group_detail(db, group)


@router.get(
    "/",
    response_model=list[GroupResponse],
)
def list_groups(
    user_id: int = Query(..., gt=0, description="Acting user id"),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)

    statement = (
        select(UserGroup)
        .join(
            UserGroupMember,
            UserGroupMember.group_id == UserGroup.id,
        )
        .where(UserGroupMember.user_id == user_id)
    )
    return db.scalars(statement).all()


@router.get(
    "/{group_id}",
    response_model=GroupDetailResponse,
)
def get_group(
    group_id: int,
    user_id: int = Query(..., gt=0, description="Acting user id"),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)
    group = _get_group_or_404(db, group_id)
    _require_membership(db, group_id, user_id)
    return _build_group_detail(db, group)


@router.patch(
    "/{group_id}",
    response_model=GroupResponse,
)
def update_group(
    group_id: int,
    body: GroupUpdate,
    user_id: int = Query(..., gt=0, description="Acting user id"),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)
    group = _get_group_or_404(db, group_id)
    _require_owner(db, group, user_id)

    group.name = body.name.strip()
    db.commit()
    db.refresh(group)
    return group


@router.delete(
    "/{group_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
def destroy_group(
    group_id: int,
    user_id: int = Query(..., gt=0, description="Acting user id"),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)
    group = _get_group_or_404(db, group_id)
    _require_owner(db, group, user_id)

    db.delete(group)
    db.commit()
    return None


@router.post(
    "/{group_id}/members",
    response_model=GroupMemberResponse,
    status_code=status.HTTP_201_CREATED,
)
def add_friend_to_group(
    group_id: int,
    body: GroupMemberAdd,
    user_id: int = Query(..., gt=0, description="Acting user id"),
    db: Session = Depends(get_db),
):
    """Add a friend to a group. Actor must be a member; target must be their friend."""
    _require_user(db, user_id)
    target = _require_user(db, body.user_id)
    group = _get_group_or_404(db, group_id)
    _require_membership(db, group_id, user_id)

    if body.user_id == user_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Use POST /groups/{group_id}/join to join a group yourself",
        )

    if not are_friends(db, user_id, body.user_id):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You can only add friends to a group",
        )

    if _get_membership(db, group_id, body.user_id) is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="User is already a member of this group",
        )

    membership = UserGroupMember(
        group_id=group.id,
        user_id=body.user_id,
        role=GroupRole.MEMBER,
    )
    db.add(membership)
    db.commit()
    db.refresh(membership)

    return GroupMemberResponse(
        group_id=membership.group_id,
        user_id=membership.user_id,
        role=membership.role,
        joined_at=membership.joined_at,
        user=UserResponse.model_validate(target),
    )


@router.post(
    "/{group_id}/join",
    response_model=GroupMemberResponse,
    status_code=status.HTTP_201_CREATED,
)
def join_group(
    group_id: int,
    user_id: int = Query(..., gt=0, description="Acting user id"),
    db: Session = Depends(get_db),
):
    """Join a group that at least one of your friends already belongs to."""
    actor = _require_user(db, user_id)
    group = _get_group_or_404(db, group_id)

    if _get_membership(db, group_id, user_id) is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="You are already a member of this group",
        )

    member_ids = _group_member_ids(db, group_id)
    if not share_a_friend_in_group(db, user_id, member_ids):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You can only join a group that a friend belongs to",
        )

    membership = UserGroupMember(
        group_id=group.id,
        user_id=user_id,
        role=GroupRole.MEMBER,
    )
    db.add(membership)
    db.commit()
    db.refresh(membership)

    return GroupMemberResponse(
        group_id=membership.group_id,
        user_id=membership.user_id,
        role=membership.role,
        joined_at=membership.joined_at,
        user=UserResponse.model_validate(actor),
    )


@router.delete(
    "/{group_id}/members/{member_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
def remove_or_leave_group(
    group_id: int,
    member_id: int,
    user_id: int = Query(..., gt=0, description="Acting user id"),
    db: Session = Depends(get_db),
):
    """
    Leave a group (member_id == user_id) or remove another member (owner only).
    Owners cannot leave; transfer ownership first.
    """
    _require_user(db, user_id)
    group = _get_group_or_404(db, group_id)
    _require_membership(db, group_id, user_id)

    target_membership = _get_membership(db, group_id, member_id)
    if target_membership is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Group member not found",
        )

    leaving = member_id == user_id

    if leaving:
        if group.owner_id == user_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Owner cannot leave the group; transfer ownership first",
            )
    else:
        _require_owner(db, group, user_id)
        if group.owner_id == member_id:
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Cannot remove the group owner; transfer ownership first",
            )

    db.delete(target_membership)
    db.commit()
    return None


@router.post(
    "/{group_id}/transfer",
    response_model=GroupDetailResponse,
)
def transfer_ownership(
    group_id: int,
    body: GroupOwnershipTransfer,
    user_id: int = Query(..., gt=0, description="Acting user id (current owner)"),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)
    _require_user(db, body.new_owner_id)
    group = _get_group_or_404(db, group_id)
    _require_owner(db, group, user_id)

    if body.new_owner_id == user_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="New owner must be a different member",
        )

    new_owner_membership = _get_membership(db, group_id, body.new_owner_id)
    if new_owner_membership is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="New owner must already be a group member",
        )

    current_owner_membership = _require_membership(db, group_id, user_id)

    group.owner_id = body.new_owner_id
    current_owner_membership.role = GroupRole.MEMBER
    new_owner_membership.role = GroupRole.OWNER

    db.commit()
    db.refresh(group)
    return _build_group_detail(db, group)

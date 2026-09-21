from fastapi import HTTPException, status
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.models.friend_group import (
    FriendGroup,
    FriendGroupMember,
)
from app.models.user import User
from app.services.friends import (
    are_friends,
    get_accepted_friend_ids,
)


def _get_owned_group(
        db: Session,
        owner_id: int,
        group_id: int,
) -> FriendGroup:

    statement = select(FriendGroup).where(
        FriendGroup.id == group_id,
        FriendGroup.owner_id == owner_id,
        )

    group = db.scalar(statement)

    if group is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friend group not found.",
        )

    return group


def get_groups(
        db: Session,
        owner_id: int,
) -> list[dict]:
    """
    Returns group summaries including member count.
    """

    statement = (
        select(
            FriendGroup,
            func.count(
                FriendGroupMember.user_id
            ).label("member_count"),
        )
        .outerjoin(
            FriendGroupMember,
            FriendGroupMember.group_id
            == FriendGroup.id,
            )
        .where(
            FriendGroup.owner_id == owner_id
        )
        .group_by(FriendGroup.id)
        .order_by(FriendGroup.name)
    )

    rows = db.execute(statement).all()

    return [
        {
            "id": group.id,
            "owner_id": group.owner_id,
            "name": group.name,
            "created_at": group.created_at,
            "updated_at": group.updated_at,
            "member_count": member_count,
        }
        for group, member_count in rows
    ]


def get_group_detail(
        db: Session,
        owner_id: int,
        group_id: int,
) -> dict:

    group = _get_owned_group(
        db,
        owner_id,
        group_id,
    )

    statement = (
        select(User)
        .join(
            FriendGroupMember,
            FriendGroupMember.user_id
            == User.id,
            )
        .where(
            FriendGroupMember.group_id
            == group.id
        )
        .order_by(User.name)
    )

    members = list(
        db.scalars(statement).all()
    )

    return {
        "id": group.id,
        "owner_id": group.owner_id,
        "name": group.name,
        "created_at": group.created_at,
        "updated_at": group.updated_at,
        "members": members,
    }


def create_group(
        db: Session,
        owner_id: int,
        name: str,
) -> FriendGroup:

    cleaned_name = name.strip()

    if not cleaned_name:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Group name cannot be empty.",
        )

    existing_statement = select(
        FriendGroup
    ).where(
        FriendGroup.owner_id == owner_id,
        FriendGroup.name == cleaned_name,
        )

    if db.scalar(existing_statement):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A group with this name already exists.",
        )

    group = FriendGroup(
        owner_id=owner_id,
        name=cleaned_name,
    )

    db.add(group)
    db.commit()
    db.refresh(group)

    return group


def update_group(
        db: Session,
        owner_id: int,
        group_id: int,
        name: str,
) -> FriendGroup:

    group = _get_owned_group(
        db,
        owner_id,
        group_id,
    )

    cleaned_name = name.strip()

    if not cleaned_name:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Group name cannot be empty.",
        )

    duplicate_statement = select(
        FriendGroup
    ).where(
        FriendGroup.owner_id == owner_id,
        FriendGroup.name == cleaned_name,
        FriendGroup.id != group_id,
        )

    if db.scalar(duplicate_statement):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A group with this name already exists.",
        )

    group.name = cleaned_name

    db.commit()
    db.refresh(group)

    return group


def delete_group(
        db: Session,
        owner_id: int,
        group_id: int,
) -> None:

    group = _get_owned_group(
        db,
        owner_id,
        group_id,
    )

    db.delete(group)
    db.commit()


def add_group_member(
        db: Session,
        owner_id: int,
        group_id: int,
        user_id: int,
) -> User:

    group = _get_owned_group(
        db,
        owner_id,
        group_id,
    )

    if owner_id == user_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You cannot add yourself to a friend group.",
        )

    user = db.get(
        User,
        user_id,
    )

    if user is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found.",
        )

    # Only accepted friends may be placed in
    # the user's friend groups.
    if not are_friends(
            db,
            owner_id,
            user_id,
    ):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Only friends can be added to a friend group.",
        )

    existing_statement = select(
        FriendGroupMember
    ).where(
        FriendGroupMember.group_id == group.id,
        FriendGroupMember.user_id == user_id,
        )

    if db.scalar(existing_statement):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="User is already in this group.",
        )

    membership = FriendGroupMember(
        group_id=group.id,
        user_id=user_id,
    )

    db.add(membership)
    db.commit()

    return user


def remove_group_member(
        db: Session,
        owner_id: int,
        group_id: int,
        user_id: int,
) -> None:

    group = _get_owned_group(
        db,
        owner_id,
        group_id,
    )

    statement = select(
        FriendGroupMember
    ).where(
        FriendGroupMember.group_id == group.id,
        FriendGroupMember.user_id == user_id,
        )

    membership = db.scalar(statement)

    if membership is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User is not a member of this group.",
        )

    db.delete(membership)
    db.commit()


def resolve_invitee_ids(
        db: Session,
        owner_id: int,
        friend_ids: list[int],
        group_ids: list[int],
) -> set[int]:
    """
    Designed for reuse by the session feature.

    Combines individually selected friends with
    members of selected groups.

    A set prevents duplicate invitations.
    """

    accepted_friend_ids = get_accepted_friend_ids(
        db,
        owner_id,
    )

    requested_friend_ids = set(
        friend_ids
    )

    invalid_friend_ids = (
            requested_friend_ids
            - accepted_friend_ids
    )

    if invalid_friend_ids:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=(
                "One or more selected users "
                "are not your friends."
            ),
        )

    invitee_ids = set(
        requested_friend_ids
    )

    if not group_ids:
        return invitee_ids

    # Make sure all groups belong to this user.
    groups_statement = select(
        FriendGroup.id
    ).where(
        FriendGroup.owner_id == owner_id,
        FriendGroup.id.in_(group_ids),
        )

    owned_group_ids = set(
        db.scalars(
            groups_statement
        ).all()
    )

    if owned_group_ids != set(group_ids):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="One or more groups do not belong to you.",
        )

    members_statement = select(
        FriendGroupMember.user_id
    ).where(
        FriendGroupMember.group_id.in_(
            group_ids
        )
    )

    group_member_ids = set(
        db.scalars(
            members_statement
        ).all()
    )

    invitee_ids.update(
        group_member_ids
    )

    return invitee_ids
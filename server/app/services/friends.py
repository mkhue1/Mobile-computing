from fastapi import HTTPException, status
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.models.friendship import (
    Friendship,
    FriendshipStatus,
)
from app.models.user import User


def _ordered_user_pair(
        user_a_id: int,
        user_b_id: int,
) -> tuple[int, int]:
    """
    Friendship pairs are always stored in ascending ID order.

    Example:
        users 8 and 3 -> (3, 8)

    This prevents:
        user 3 -> user 8
    and:
        user 8 -> user 3

    becoming two different friendship rows.
    """

    return (
        min(user_a_id, user_b_id),
        max(user_a_id, user_b_id),
    )


def get_friendship_between(
        db: Session,
        user_a_id: int,
        user_b_id: int,
) -> Friendship | None:

    low_id, high_id = _ordered_user_pair(
        user_a_id,
        user_b_id,
    )

    statement = select(Friendship).where(
        Friendship.user_low_id == low_id,
        Friendship.user_high_id == high_id,
        )

    return db.scalar(statement)


def are_friends(
        db: Session,
        user_a_id: int,
        user_b_id: int,
) -> bool:
    """
    Reusable by groups, sessions, invitations, NFC, etc.
    """

    friendship = get_friendship_between(
        db,
        user_a_id,
        user_b_id,
    )

    return (
            friendship is not None
            and friendship.status == FriendshipStatus.ACCEPTED
    )


def get_accepted_friend_ids(
        db: Session,
        user_id: int,
) -> set[int]:
    """
    Returns all accepted friend IDs.

    Useful for:
    - groups
    - session invitations
    - visibility rules
    """

    statement = select(Friendship).where(
        Friendship.status == FriendshipStatus.ACCEPTED,
        or_(
            Friendship.user_low_id == user_id,
            Friendship.user_high_id == user_id,
            ),
        )

    friendships = db.scalars(statement).all()

    friend_ids: set[int] = set()

    for friendship in friendships:

        if friendship.user_low_id == user_id:
            friend_ids.add(
                friendship.user_high_id
            )
        else:
            friend_ids.add(
                friendship.user_low_id
            )

    return friend_ids


def get_friends(
        db: Session,
        user_id: int,
) -> list[User]:

    friend_ids = get_accepted_friend_ids(
        db,
        user_id,
    )

    if not friend_ids:
        return []

    statement = (
        select(User)
        .where(User.id.in_(friend_ids))
        .order_by(User.name)
    )

    return list(
        db.scalars(statement).all()
    )


def get_incoming_friend_requests(
        db: Session,
        user_id: int,
) -> list[dict]:
    """
    Returns friend requests that the current user
    is allowed to accept or decline.
    """

    statement = (
        select(
            Friendship,
            User,
        )
        .join(
            User,
            User.id == Friendship.requested_by_id,
            )
        .where(
            Friendship.status == FriendshipStatus.PENDING,
            Friendship.requested_by_id != user_id,
            or_(
                Friendship.user_low_id == user_id,
                Friendship.user_high_id == user_id,
                ),
            )
        .order_by(Friendship.created_at.desc())
    )

    results = db.execute(statement).all()

    return [
        {
            "id": friendship.id,
            "requester": requester,
            "created_at": friendship.created_at,
        }
        for friendship, requester in results
    ]


def send_friend_request(
        db: Session,
        requester_id: int,
        target_user_id: int,
) -> Friendship:

    if requester_id == target_user_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You cannot send a friend request to yourself.",
        )

    target_user = db.get(
        User,
        target_user_id,
    )

    if target_user is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found.",
        )

    existing = get_friendship_between(
        db,
        requester_id,
        target_user_id,
    )

    if existing is not None:

        if existing.status == FriendshipStatus.ACCEPTED:
            message = "You are already friends."

        elif existing.requested_by_id == requester_id:
            message = "Friend request already sent."

        else:
            message = (
                "This user has already sent you a friend request."
            )

        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=message,
        )

    low_id, high_id = _ordered_user_pair(
        requester_id,
        target_user_id,
    )

    friendship = Friendship(
        user_low_id=low_id,
        user_high_id=high_id,
        requested_by_id=requester_id,
        status=FriendshipStatus.PENDING,
    )

    db.add(friendship)
    db.commit()
    db.refresh(friendship)

    return friendship


def accept_friend_request(
        db: Session,
        user_id: int,
        request_id: int,
) -> User:

    friendship = db.get(
        Friendship,
        request_id,
    )

    if friendship is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friend request not found.",
        )

    if friendship.status != FriendshipStatus.PENDING:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="This friend request is no longer pending.",
        )

    user_is_in_friendship = (
            friendship.user_low_id == user_id
            or friendship.user_high_id == user_id
    )

    if not user_is_in_friendship:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You cannot accept this friend request.",
        )

    # Sender cannot accept their own request.
    if friendship.requested_by_id == user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the recipient can accept this request.",
        )

    friendship.status = FriendshipStatus.ACCEPTED

    db.commit()
    db.refresh(friendship)

    requester = db.get(
        User,
        friendship.requested_by_id,
    )

    return requester


def decline_friend_request(
        db: Session,
        user_id: int,
        request_id: int,
) -> None:

    friendship = db.get(
        Friendship,
        request_id,
    )

    if friendship is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friend request not found.",
        )

    if friendship.status != FriendshipStatus.PENDING:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="This request is no longer pending.",
        )

    user_is_recipient = (
            (
                    friendship.user_low_id == user_id
                    or friendship.user_high_id == user_id
            )
            and friendship.requested_by_id != user_id
    )

    if not user_is_recipient:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You cannot decline this friend request.",
        )

    db.delete(friendship)
    db.commit()


def remove_friend(
        db: Session,
        user_id: int,
        friend_id: int,
) -> None:

    friendship = get_friendship_between(
        db,
        user_id,
        friend_id,
    )

    if (
            friendship is None
            or friendship.status != FriendshipStatus.ACCEPTED
    ):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friendship not found.",
        )

    db.delete(friendship)
    db.commit()
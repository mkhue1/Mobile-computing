from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.models.friendship import Friendship
from app.models.user import User


def ordered_friend_pair(user_a: int, user_b: int) -> tuple[int, int]:
    """Canonical undirected friendship edge (user_id < friend_id)."""
    return (user_a, user_b) if user_a < user_b else (user_b, user_a)


def get_user_or_none(db: Session, user_id: int) -> User | None:
    return db.get(User, user_id)


def are_friends(db: Session, user_a: int, user_b: int) -> bool:
    if user_a == user_b:
        return False

    low, high = ordered_friend_pair(user_a, user_b)
    statement = select(Friendship).where(
        Friendship.user_id == low,
        Friendship.friend_id == high,
    )
    return db.scalars(statement).first() is not None


def get_friendship(db: Session, user_a: int, user_b: int) -> Friendship | None:
    low, high = ordered_friend_pair(user_a, user_b)
    statement = select(Friendship).where(
        Friendship.user_id == low,
        Friendship.friend_id == high,
    )
    return db.scalars(statement).first()


def list_friend_ids(db: Session, user_id: int) -> list[int]:
    statement = select(Friendship).where(
        or_(
            Friendship.user_id == user_id,
            Friendship.friend_id == user_id,
        )
    )
    friendships = db.scalars(statement).all()
    return [
        friendship.friend_id if friendship.user_id == user_id else friendship.user_id
        for friendship in friendships
    ]


def share_a_friend_in_group(
    db: Session,
    user_id: int,
    group_member_ids: set[int],
) -> bool:
    """True if user_id is friends with at least one member of the group."""
    if not group_member_ids:
        return False

    friend_ids = set(list_friend_ids(db, user_id))
    return bool(friend_ids & group_member_ids)

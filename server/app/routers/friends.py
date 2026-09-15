from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import or_, select
from sqlalchemy.orm import Session

from app.database import get_db
from app.helpers.friendship import (
    are_friends,
    get_friendship,
    get_user_or_none,
    list_friend_ids,
    ordered_friend_pair,
)
from app.models.friendship import FriendRequest, Friendship
from app.models.user import User
from app.schemas.friendship import (
    FriendRequestCreate,
    FriendRequestResponse,
    FriendshipResponse,
)
from app.schemas.user import UserResponse


router = APIRouter(
    prefix="/friends",
    tags=["friends"],
)


def _require_user(db: Session, user_id: int) -> User:
    user = get_user_or_none(db, user_id)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User {user_id} not found",
        )
    return user


@router.get(
    "/",
    response_model=list[UserResponse],
)
def list_friends(
    user_id: int = Query(..., gt=0, description="Acting user id"),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)
    friend_ids = list_friend_ids(db, user_id)
    if not friend_ids:
        return []

    statement = select(User).where(User.id.in_(friend_ids))
    return db.scalars(statement).all()


@router.post(
    "/requests",
    response_model=FriendRequestResponse,
    status_code=status.HTTP_201_CREATED,
)
def send_friend_request(
    body: FriendRequestCreate,
    user_id: int = Query(..., gt=0, description="Acting user id (sender)"),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)
    _require_user(db, body.receiver_id)

    if user_id == body.receiver_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot send a friend request to yourself",
        )

    if are_friends(db, user_id, body.receiver_id):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Users are already friends",
        )

    existing = db.scalars(
        select(FriendRequest).where(
            FriendRequest.sender_id == user_id,
            FriendRequest.receiver_id == body.receiver_id,
        )
    ).first()
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Friend request already pending",
        )

    reverse = db.scalars(
        select(FriendRequest).where(
            FriendRequest.sender_id == body.receiver_id,
            FriendRequest.receiver_id == user_id,
        )
    ).first()
    if reverse is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A pending request from this user already exists; accept it instead",
        )

    request = FriendRequest(
        sender_id=user_id,
        receiver_id=body.receiver_id,
    )
    db.add(request)
    db.commit()
    db.refresh(request)
    return request


@router.get(
    "/requests",
    response_model=list[FriendRequestResponse],
)
def list_friend_requests(
    user_id: int = Query(..., gt=0, description="Acting user id"),
    direction: str = Query(
        "incoming",
        pattern="^(incoming|outgoing|all)$",
        description="incoming (default), outgoing, or all",
    ),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)

    if direction == "incoming":
        statement = select(FriendRequest).where(
            FriendRequest.receiver_id == user_id
        )
    elif direction == "outgoing":
        statement = select(FriendRequest).where(
            FriendRequest.sender_id == user_id
        )
    else:
        statement = select(FriendRequest).where(
            or_(
                FriendRequest.receiver_id == user_id,
                FriendRequest.sender_id == user_id,
            )
        )

    return db.scalars(statement).all()


@router.post(
    "/requests/{request_id}/accept",
    response_model=FriendshipResponse,
)
def accept_friend_request(
    request_id: int,
    user_id: int = Query(..., gt=0, description="Acting user id (receiver)"),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)

    request = db.get(FriendRequest, request_id)
    if request is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friend request not found",
        )

    if request.receiver_id != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the receiver can accept this friend request",
        )

    if are_friends(db, request.sender_id, request.receiver_id):
        db.delete(request)
        db.commit()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Users are already friends",
        )

    sender_id = request.sender_id
    receiver_id = request.receiver_id
    low, high = ordered_friend_pair(sender_id, receiver_id)
    friendship = Friendship(user_id=low, friend_id=high)
    db.add(friendship)

    # Clear this request and any reverse pending request.
    db.delete(request)
    reverse = db.scalars(
        select(FriendRequest).where(
            FriendRequest.sender_id == receiver_id,
            FriendRequest.receiver_id == sender_id,
        )
    ).first()
    if reverse is not None:
        db.delete(reverse)

    db.commit()
    db.refresh(friendship)
    return friendship


@router.post(
    "/requests/{request_id}/decline",
    status_code=status.HTTP_204_NO_CONTENT,
)
def decline_friend_request(
    request_id: int,
    user_id: int = Query(..., gt=0, description="Acting user id (receiver)"),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)

    request = db.get(FriendRequest, request_id)
    if request is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friend request not found",
        )

    if request.receiver_id != user_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the receiver can decline this friend request",
        )

    db.delete(request)
    db.commit()
    return None


@router.delete(
    "/{friend_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
def remove_friend(
    friend_id: int,
    user_id: int = Query(..., gt=0, description="Acting user id"),
    db: Session = Depends(get_db),
):
    _require_user(db, user_id)
    _require_user(db, friend_id)

    friendship = get_friendship(db, user_id, friend_id)
    if friendship is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friendship not found",
        )

    db.delete(friendship)
    db.commit()
    return None

from uuid import UUID

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
    SteamRelation,
)
from app.schemas.user import UserResponse
from app.security import get_current_user
from app.services.steam import SteamError, get_friend_steam_ids, get_steam_persona_names


router = APIRouter(
    prefix="/friends",
    tags=["friends"],
)


def _require_user(db: Session, user_id: UUID) -> User:
    user = get_user_or_none(db, user_id)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User {user_id} not found",
        )
    return user


def _peer_for_request(request: FriendRequest, viewer_id: UUID) -> UUID:
    if request.sender_id == viewer_id:
        return request.receiver_id
    return request.sender_id


async def _steam_relation_by_peer(
    viewer: User,
    peers_by_id: dict[UUID, User],
) -> dict[UUID, tuple[SteamRelation, str | None]]:
    """
    Map peer user id -> (relation, optional Steam persona name)
    Empty when the viewer has not linked Steam.
    """
    if viewer.steam_id is None:
        return {}

    relation_by_peer: dict[UUID, tuple[SteamRelation, str | None]] = {}
    linked_peers: list[User] = []

    for peer in peers_by_id.values():
        if peer.steam_id is None:
            relation_by_peer[peer.id] = ("not_linked", None)
        else:
            linked_peers.append(peer)

    if not linked_peers:
        return relation_by_peer

    try:
        steam_friend_ids = set(await get_friend_steam_ids(viewer.steam_id))
    except SteamError:
        return relation_by_peer

    mutual_peers = [
        peer for peer in linked_peers if peer.steam_id in steam_friend_ids
    ]
    persona_names: dict[str, str] = {}
    if mutual_peers:
        try:
            persona_names = await get_steam_persona_names(
                [peer.steam_id for peer in mutual_peers if peer.steam_id]
            )
        except SteamError:
            persona_names = {}

    for peer in linked_peers:
        if peer.steam_id in steam_friend_ids:
            relation_by_peer[peer.id] = (
                "mutual",
                persona_names.get(peer.steam_id),
            )
        else:
            relation_by_peer[peer.id] = ("not_friends", None)

    return relation_by_peer


async def _build_request_responses(
    db: Session,
    requests: list[FriendRequest],
    viewer: User,
) -> list[FriendRequestResponse]:
    user_ids = {request.sender_id for request in requests} | {
        request.receiver_id for request in requests
    }
    users_by_id: dict[UUID, User] = {}
    if user_ids:
        users = db.scalars(select(User).where(User.id.in_(user_ids))).all()
        users_by_id = {user.id: user for user in users}

    peers_by_id = {
        peer_id: users_by_id[peer_id]
        for request in requests
        if (peer_id := _peer_for_request(request, viewer.id)) in users_by_id
    }
    steam_by_peer = await _steam_relation_by_peer(viewer, peers_by_id)

    def _user_response(user_id: UUID) -> UserResponse | None:
        user = users_by_id.get(user_id)
        return UserResponse.model_validate(user) if user is not None else None

    responses: list[FriendRequestResponse] = []
    for request in requests:
        peer_id = _peer_for_request(request, viewer.id)
        steam_relation, steam_persona_name = steam_by_peer.get(
            peer_id, (None, None)
        )
        responses.append(
            FriendRequestResponse(
                id=request.id,
                sender_id=request.sender_id,
                receiver_id=request.receiver_id,
                created_at=request.created_at,
                sender=_user_response(request.sender_id),
                receiver=_user_response(request.receiver_id),
                steam_relation=steam_relation,
                steam_persona_name=steam_persona_name,
            )
        )
    return responses


@router.get(
    "/",
    response_model=list[UserResponse],
)
def list_friends(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    friend_ids = list_friend_ids(db, current_user.id)
    if not friend_ids:
        return []

    statement = select(User).where(User.id.in_(friend_ids))
    return db.scalars(statement).all()


@router.get(
    "/suggestions/steam",
    response_model=list[UserResponse],
)
async def steam_friend_suggestions(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if current_user.steam_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Connect your Steam account to see friend suggestions",
        )

    try:
        steam_friend_ids = await get_friend_steam_ids(current_user.steam_id)
    except SteamError as exc:
        status_code = (
            status.HTTP_400_BAD_REQUEST
            if exc.private_friends
            else status.HTTP_502_BAD_GATEWAY
        )
        raise HTTPException(status_code=status_code, detail=str(exc)) from exc

    if not steam_friend_ids:
        return []

    friend_ids = set(list_friend_ids(db, current_user.id))

    pending_rows = db.scalars(
        select(FriendRequest).where(
            or_(
                FriendRequest.sender_id == current_user.id,
                FriendRequest.receiver_id == current_user.id,
            )
        )
    ).all()
    pending_peer_ids = {
        row.receiver_id if row.sender_id == current_user.id else row.sender_id
        for row in pending_rows
    }

    candidates = db.scalars(
        select(User).where(
            User.steam_id.in_(steam_friend_ids),
            User.id != current_user.id,
        )
    ).all()

    return [
        user
        for user in candidates
        if user.id not in friend_ids and user.id not in pending_peer_ids
    ]


@router.post(
    "/requests",
    response_model=FriendRequestResponse,
    status_code=status.HTTP_201_CREATED,
)
def send_friend_request(
    body: FriendRequestCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    receiver = _require_user(db, body.receiver_id)

    if current_user.id == body.receiver_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Cannot send a friend request to yourself",
        )

    if are_friends(db, current_user.id, body.receiver_id):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Users are already friends",
        )

    existing = db.scalars(
        select(FriendRequest).where(
            FriendRequest.sender_id == current_user.id,
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
            FriendRequest.receiver_id == current_user.id,
        )
    ).first()
    if reverse is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A pending request from this user already exists; accept it instead",
        )

    request = FriendRequest(
        sender_id=current_user.id,
        receiver_id=body.receiver_id,
    )
    db.add(request)
    db.commit()
    db.refresh(request)
    return FriendRequestResponse(
        id=request.id,
        sender_id=request.sender_id,
        receiver_id=request.receiver_id,
        created_at=request.created_at,
        sender=UserResponse.model_validate(current_user),
        receiver=UserResponse.model_validate(receiver),
    )


@router.get(
    "/requests",
    response_model=list[FriendRequestResponse],
)
async def list_friend_requests(
    direction: str = Query(
        "incoming",
        pattern="^(incoming|outgoing|all)$",
        description="incoming (default), outgoing, or all",
    ),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if direction == "incoming":
        statement = select(FriendRequest).where(
            FriendRequest.receiver_id == current_user.id
        )
    elif direction == "outgoing":
        statement = select(FriendRequest).where(
            FriendRequest.sender_id == current_user.id
        )
    else:
        statement = select(FriendRequest).where(
            or_(
                FriendRequest.receiver_id == current_user.id,
                FriendRequest.sender_id == current_user.id,
            )
        )

    requests = db.scalars(statement).all()
    return await _build_request_responses(db, list(requests), current_user)


@router.post(
    "/requests/{request_id}/accept",
    response_model=FriendshipResponse,
)
def accept_friend_request(
    request_id: UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    request = db.get(FriendRequest, request_id)
    if request is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friend request not found",
        )

    if request.receiver_id != current_user.id:
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
    friendship = Friendship(
        user_id=low,
        friend_id=high,
    )
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
    request_id: UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    request = db.get(FriendRequest, request_id)
    if request is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friend request not found",
        )

    if request.receiver_id != current_user.id:
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
    friend_id: UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    _require_user(db, friend_id)

    friendship = get_friendship(db, current_user.id, friend_id)
    if friendship is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Friendship not found",
        )

    db.delete(friendship)
    db.commit()
    return None

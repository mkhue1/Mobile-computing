from fastapi import (
    APIRouter,
    Depends,
    Response,
    status,
)
from sqlalchemy.orm import Session

from app.database import get_db
from app.dependencies.current_user import (
    get_current_user_id,
)
from app.schemas.friendship import (
    FriendshipRecordResponse,
    IncomingFriendRequestResponse,
)
from app.schemas.user import UserResponse
from app.services import friends as friend_service


router = APIRouter(
    prefix="/friends",
    tags=["friends"],
)


@router.get(
    "/",
    response_model=list[UserResponse],
)
def get_friends(
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    return friend_service.get_friends(
        db,
        current_user_id,
    )


@router.get(
    "/requests",
    response_model=list[IncomingFriendRequestResponse],
)
def get_friend_requests(
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    return friend_service.get_incoming_friend_requests(
        db,
        current_user_id,
    )


@router.post(
    "/requests/{target_user_id}",
    response_model=FriendshipRecordResponse,
    status_code=status.HTTP_201_CREATED,
)
def send_friend_request(
        target_user_id: int,
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    return friend_service.send_friend_request(
        db,
        requester_id=current_user_id,
        target_user_id=target_user_id,
    )


@router.post(
    "/requests/{request_id}/accept",
    response_model=UserResponse,
)
def accept_friend_request(
        request_id: int,
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    return friend_service.accept_friend_request(
        db,
        user_id=current_user_id,
        request_id=request_id,
    )


@router.delete(
    "/requests/{request_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
def decline_friend_request(
        request_id: int,
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    friend_service.decline_friend_request(
        db,
        user_id=current_user_id,
        request_id=request_id,
    )

    return Response(
        status_code=status.HTTP_204_NO_CONTENT
    )


@router.delete(
    "/{friend_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
def remove_friend(
        friend_id: int,
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    friend_service.remove_friend(
        db,
        user_id=current_user_id,
        friend_id=friend_id,
    )

    return Response(
        status_code=status.HTTP_204_NO_CONTENT
    )
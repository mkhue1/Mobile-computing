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
from app.schemas.friend_group import (
    FriendGroupCreate,
    FriendGroupDetailResponse,
    FriendGroupSummaryResponse,
    FriendGroupUpdate,
)
from app.schemas.user import UserResponse
from app.services import (
    friend_groups as group_service,
)


router = APIRouter(
    prefix="/friend-groups",
    tags=["friend groups"],
)


@router.get(
    "/",
    response_model=list[FriendGroupSummaryResponse],
)
def get_groups(
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    return group_service.get_groups(
        db,
        current_user_id,
    )


@router.post(
    "/",
    response_model=FriendGroupDetailResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_group(
        group_data: FriendGroupCreate,
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    group = group_service.create_group(
        db,
        owner_id=current_user_id,
        name=group_data.name,
    )

    return group_service.get_group_detail(
        db,
        owner_id=current_user_id,
        group_id=group.id,
    )


@router.get(
    "/{group_id}",
    response_model=FriendGroupDetailResponse,
)
def get_group(
        group_id: int,
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    return group_service.get_group_detail(
        db,
        owner_id=current_user_id,
        group_id=group_id,
    )


@router.patch(
    "/{group_id}",
    response_model=FriendGroupDetailResponse,
)
def update_group(
        group_id: int,
        group_data: FriendGroupUpdate,
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    group_service.update_group(
        db,
        owner_id=current_user_id,
        group_id=group_id,
        name=group_data.name,
    )

    return group_service.get_group_detail(
        db,
        owner_id=current_user_id,
        group_id=group_id,
    )


@router.delete(
    "/{group_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
def delete_group(
        group_id: int,
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    group_service.delete_group(
        db,
        owner_id=current_user_id,
        group_id=group_id,
    )

    return Response(
        status_code=status.HTTP_204_NO_CONTENT
    )


@router.post(
    "/{group_id}/members/{user_id}",
    response_model=UserResponse,
)
def add_group_member(
        group_id: int,
        user_id: int,
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    return group_service.add_group_member(
        db,
        owner_id=current_user_id,
        group_id=group_id,
        user_id=user_id,
    )


@router.delete(
    "/{group_id}/members/{user_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
def remove_group_member(
        group_id: int,
        user_id: int,
        db: Session = Depends(get_db),
        current_user_id: int = Depends(
            get_current_user_id
        ),
):
    group_service.remove_group_member(
        db,
        owner_id=current_user_id,
        group_id=group_id,
        user_id=user_id,
    )

    return Response(
        status_code=status.HTTP_204_NO_CONTENT
    )
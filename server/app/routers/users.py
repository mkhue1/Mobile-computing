from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import User
from app.schemas.user import UserCreate, UserResponse
from app.security import get_current_user, hash_password


router = APIRouter(
    prefix="/users",
    tags=["users"],
)

SEARCH_MIN_LENGTH = 2


def _escape_like(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
    )


@router.get(
    "/",
    response_model=list[UserResponse],
)
def get_users(
    search: str | None = Query(
        None,
        description="Case-insensitive username match; ignored if under 2 characters",
    ),
    limit: int = Query(20, ge=1, le=50),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    statement = select(User)

    term = search.strip() if search else ""
    if len(term) >= SEARCH_MIN_LENGTH:
        statement = (
            statement.where(
                User.username.ilike(f"%{_escape_like(term)}%", escape="\\"),
                User.id != current_user.id,
            )
            .order_by(User.username)
            .limit(limit)
        )

    return db.scalars(statement).all()


@router.get(
    "/{user_id}",
    response_model=UserResponse,
)
def get_user(
    user_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    user = db.get(User, user_id)
    if user is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="User not found",
        )
    return user


@router.post(
    "/",
    response_model=UserResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_user(
    user: UserCreate,
    db: Session = Depends(get_db),
):
    new_user = User(
        email=user.email.lower(),
        username=user.username,
        password=hash_password(user.password),
    )

    db.add(new_user)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Email or username already registered",
        )

    db.refresh(new_user)
    return new_user

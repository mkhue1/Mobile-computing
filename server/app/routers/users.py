from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, File, HTTPException, Query, Response, UploadFile, status
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import User
from app.models.user_avatar import UserAvatar
from app.schemas.user import UserCreate, UserResponse
from app.security import get_current_user, hash_password


router = APIRouter(
    prefix="/users",
    tags=["users"],
)

SEARCH_MIN_LENGTH = 2
AVATAR_MAX_BYTES = 2 * 1024 * 1024


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


def _detect_image_type(data: bytes) -> str | None:
    # Trust the bytes, not the client-supplied Content-Type.
    if data.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "image/webp"
    return None


@router.put(
    "/me/avatar",
    response_model=UserResponse,
)
def upload_avatar(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    data = file.file.read(AVATAR_MAX_BYTES + 1)
    if len(data) > AVATAR_MAX_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_CONTENT_TOO_LARGE,
            detail="Profile picture must be 2 MB or smaller",
        )

    content_type = _detect_image_type(data)
    if content_type is None:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="Profile picture must be a JPEG, PNG, or WebP image",
        )

    now = datetime.now(timezone.utc)
    if current_user.avatar is None:
        current_user.avatar = UserAvatar(data=data, content_type=content_type, updated_at=now)
    else:
        current_user.avatar.data = data
        current_user.avatar.content_type = content_type
        current_user.avatar.updated_at = now

    db.commit()
    return current_user


@router.delete(
    "/me/avatar",
    status_code=status.HTTP_204_NO_CONTENT,
)
def delete_avatar(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if current_user.avatar is not None:
        current_user.avatar = None
        db.commit()


@router.get(
    "/{user_id}/avatar",
    response_class=Response,
)
def get_avatar(
    user_id: UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    avatar = db.get(UserAvatar, user_id)
    if avatar is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="No profile picture",
        )

    # Clients add ?v=<avatar_updated_at> to the URL, so a cached copy never goes stale.
    return Response(
        content=avatar.data,
        media_type=avatar.content_type,
        headers={"Cache-Control": "private, max-age=31536000, immutable"},
    )

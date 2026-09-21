from fastapi import (
    Depends,
    Header,
    HTTPException,
    status,
)
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.user import User


def get_current_user_id(
        x_user_id: int | None = Header(
            default=None,
            alias="X-User-Id",
        ),
        db: Session = Depends(get_db),
) -> int:
    """
    Temporary development authentication.

    Android sends:
        X-User-Id: 1

    Later this function can be replaced with
    JWT / Google OAuth authentication without
    changing the Friends or Groups routers.
    """

    if x_user_id is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="X-User-Id header is required.",
        )

    user = db.get(
        User,
        x_user_id,
    )

    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid user.",
        )

    return x_user_id
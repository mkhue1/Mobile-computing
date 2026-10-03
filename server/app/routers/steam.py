from datetime import datetime, timezone
from urllib.parse import urlencode

from fastapi import APIRouter, Depends, HTTPException, Request, status
from fastapi.responses import RedirectResponse
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.config import settings
from app.database import get_db
from app.models.user import User
from app.schemas.steam import SteamLinkResponse, SteamStatusResponse
from app.security import (
    create_steam_link_state,
    decode_steam_link_state,
    get_current_user,
)
from app.services.steam import SteamError, build_openid_login_url, verify_openid_assertion

router = APIRouter(
    prefix="/steam",
    tags=["steam"],
)


def _public_base_url() -> str:
    return settings.public_base_url.rstrip("/")


def _error_redirect(reason: str) -> RedirectResponse:
    query = urlencode({"reason": reason})
    return RedirectResponse(
        url=f"{settings.steam_deep_link_error}?{query}",
        status_code=status.HTTP_302_FOUND,
    )


@router.get(
    "/status",
    response_model=SteamStatusResponse,
)
def steam_status(
    current_user: User = Depends(get_current_user),
):
    return SteamStatusResponse(
        linked=current_user.steam_id is not None,
        steam_id=current_user.steam_id,
    )


@router.get(
    "/link",
    response_model=SteamLinkResponse,
)
def start_steam_link(
    current_user: User = Depends(get_current_user),
):
    state = create_steam_link_state(current_user.id)
    base = _public_base_url()
    return_to = f"{base}/steam/callback?{urlencode({'state': state})}"
    auth_url = build_openid_login_url(return_to=return_to, realm=base)
    return SteamLinkResponse(auth_url=auth_url)


@router.get("/callback")
async def steam_callback(
    request: Request,
    db: Session = Depends(get_db),
):
    params = {key: value for key, value in request.query_params.multi_items()}
    state = params.get("state")
    if not state:
        return _error_redirect("missing_state")

    try:
        user_id = decode_steam_link_state(state)
    except HTTPException:
        return _error_redirect("invalid_state")

    if params.get("openid.mode") == "cancel":
        return _error_redirect("cancelled")

    try:
        steam_id = await verify_openid_assertion(params)
    except SteamError:
        return _error_redirect("verification_failed")

    user = db.scalar(select(User).where(User.id == user_id))
    if user is None:
        return _error_redirect("user_not_found")

    existing = db.scalar(
        select(User).where(
            User.steam_id == steam_id,
            User.id != user_id,
        )
    )
    if existing is not None:
        return _error_redirect("steam_already_linked")

    user.steam_id = steam_id
    user.steam_linked_at = datetime.now(timezone.utc)
    db.commit()

    return RedirectResponse(
        url=settings.steam_deep_link_success,
        status_code=status.HTTP_302_FOUND,
    )


@router.delete(
    "/link",
    status_code=status.HTTP_204_NO_CONTENT,
)
def unlink_steam(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    if current_user.steam_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Steam account is not linked",
        )

    current_user.steam_id = None
    current_user.steam_linked_at = None
    db.commit()
    return None

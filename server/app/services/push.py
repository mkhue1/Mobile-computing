"""Push notifications to a user's phones through Firebase Cloud Messaging."""
import logging
from uuid import UUID

import firebase_admin
from firebase_admin import credentials, messaging
from sqlalchemy import delete, select

from app.config import settings
from app.database import SessionLocal
from app.models.device_token import DeviceToken

logger = logging.getLogger(__name__)

_firebase_app: firebase_admin.App | None = None


def _get_firebase_app() -> firebase_admin.App | None:
    """Sets up firebase on first use. Returns None when no key is configured, meaing no notifications will be sent."""
    global _firebase_app
    if _firebase_app is None and settings.firebase_credentials_path:
        _firebase_app = firebase_admin.initialize_app(
            credentials.Certificate(settings.firebase_credentials_path)
        )
    return _firebase_app


def send_to_user(user_id: UUID, data: dict[str, str]) -> None:
    """
    Sends a data message to every phone the user is signed in on. Meant to run as a background
    task, after the response has been sent, so it opens its own database session. It never raises exceptions
    as failed notification shouldn't affect the processing of the invite or request that triggers it.
    """
    try:
        firebase_app = _get_firebase_app()
    except Exception:
        logger.exception("Couldn't set up Firebase; push notifications are off")
        return
    if firebase_app is None:
        return

    with SessionLocal() as db:
        tokens = list(db.scalars(select(DeviceToken.token).where(DeviceToken.user_id == user_id)))
        if not tokens:
            return

        message = messaging.MulticastMessage(
            # Using FCM registration tokens, which is deprecated but still works
            # maybe update app to use newer FIDs (firebase ids) instead?
            tokens=tokens,
            data=data,
            # High priority - this is gaming!!
            android=messaging.AndroidConfig(priority="high"),
        )
        try:
            response = messaging.send_each_for_multicast(message, app=firebase_app)
        except Exception:
            logger.exception("Couldn't send a push notification to user %s", user_id)
            return

        # remove tokens firebase no longer recognises
        stale = [
            token
            for token, result in zip(tokens, response.responses)
            if isinstance(result.exception, messaging.UnregisteredError)
        ]
        if stale:
            db.execute(delete(DeviceToken).where(DeviceToken.token.in_(stale)))
            db.commit()


# The messages in the apps receiving format. All Firebase data values must be strings.

def session_invite_message(invite_id: UUID, session_id: UUID, sender_name: str, session_title: str) -> dict[str, str]:
    return {
        "type": "session_invite",
        "id": str(invite_id),
        "session_id": str(session_id),
        "title": "Session invite",
        "body": f"{sender_name} invited you to {session_title}",
    }


def friend_request_message(request_id: UUID, sender_name: str) -> dict[str, str]:
    return {
        "type": "friend_request",
        "id": str(request_id),
        "title": "Friend request",
        "body": f"{sender_name} sent you a friend request",
    }

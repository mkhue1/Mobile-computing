from uuid import UUID

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.helpers.friendship import are_friends
from app.models.gaming_session import (
    GamingSession,
    SessionInvite,
    SessionParticipant,
    SessionVisibility,
)
from app.models.user_group import UserGroupMember


def can_view_session(db: Session, session: GamingSession, user_id: UUID) -> bool:
    """
    Organisers, participants and invitees can always view a session.
    Otherwise it depends on visibility: public is open to everyone, friends to
    the organiser's friends, and group to members of the session's group.
    """
    if session.organiser_id == user_id:
        return True

    if db.get(SessionParticipant, (session.id, user_id)) is not None:
        return True

    invite = db.scalars(
        select(SessionInvite).where(
            SessionInvite.session_id == session.id,
            SessionInvite.receiver_id == user_id,
        )
    ).first()
    if invite is not None:
        return True

    if session.visibility == SessionVisibility.PUBLIC:
        return True

    if session.visibility == SessionVisibility.FRIENDS:
        return are_friends(db, session.organiser_id, user_id)

    if session.visibility == SessionVisibility.GROUP and session.group_id is not None:
        return db.get(UserGroupMember, (session.group_id, user_id)) is not None

    return False

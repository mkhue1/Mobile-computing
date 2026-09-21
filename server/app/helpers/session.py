
from uuid import UUID
from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.gaming_session import GamingSession, SessionInvite, SessionParticipant, SessionStatus, SessionType, SessionVisibility, InviteStatus
from app.schemas.session import InviteAccept, InviteAcceptResponse, InviteCreate, InviteCreateResponse, SessionResponse, SessionCreate

def isFull(db: Session, session_id: UUID, invite_id: UUID, accepter_id: UUID) -> bool:
    player_count = db.scalar(
        select(func.count())
        .select_from(SessionParticipant)
        .where(SessionParticipant.session_id == session_id)
    )
    max_size = db.get(GamingSession, session_id).player_limit
    return player_count >= max_size
    
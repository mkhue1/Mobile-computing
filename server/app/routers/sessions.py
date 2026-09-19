from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.gaming_session import GamingSession, SessionInvite, SessionParticipant, SessionStatus, SessionType, SessionVisibility, InviteStatus
from app.schemas.session import SessionResponse, SessionCreate



router = APIRouter(
    prefix="/sessions",
    tags=["sessions"],
)


@router.get(
    "/",
    response_model=list[SessionResponse],
)
def get_sessions(
    db: Session = Depends(get_db),
):
    statement = select(SessionResponse)

    return db.scalars(statement).all()


@router.post(
    "/",
    response_model=SessionResponse,
)
def create_session(
    session: SessionCreate,
    db: Session = Depends(get_db),
):
    new_session = GamingSession(
        organiser_id=session.organiser_id,
        game_id=session.game_id,
        group_id=session.group_id,
        title=session.title,
        description=session.description,
        start_at=session.start_at,
        end_at=session.end_at,
        session_type=session.session_type,
        visibility=session.visibility,
        status=session.status,
        location_name=session.location_name,
        player_limit=session.player_limit,
    )

    db.add(new_session)
    db.commit()
    db.refresh(new_session)

    return new_session
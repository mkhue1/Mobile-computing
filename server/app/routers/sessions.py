from fastapi import APIRouter, Depends
from server.app.helpers.session import isFull
from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.gaming_session import GamingSession, SessionInvite, SessionParticipant, SessionStatus, SessionType, SessionVisibility, InviteStatus
from app.schemas.session import InviteAccept, InviteAcceptResponse, InviteCreate, InviteCreateResponse, InviteDecline, InviteDeclineResponse, InviteResponse, SessionResponse, SessionCreate



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
    "/create",
    response_model=SessionResponse,
)
def create_session(
    session: SessionCreate,
    db: Session = Depends(get_db),
):
    new_session = GamingSession(
        organiser_id=session.organiser_id, # when authentication is added, should be updated to get the current users ID so this is not provided by the user
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

@router.post(
    "/{session_id}/invite",
    response_model=InviteResponse,
)
def create_invite(
    invite: InviteCreate,
    db: Session = Depends(get_db),
):
    new_invite = SessionInvite(
        session_id=invite.session_id,
        sender_id=invite.sender_id, 
        receiver_id=invite.receiver_id, # when authentication is added, should be updated to get the current users ID so this is not provided by the user
    )

    db.add(new_invite)
    db.commit()
    db.refresh(new_invite)

    return InviteCreateResponse(**new_invite)

@router.post(
    "/{session_id}/accept",
    response_model=InviteAcceptResponse,
)
def accept_invite(
    invite: InviteAccept,
    db: Session = Depends(get_db),
):
    session_participant = SessionParticipant(session_id = invite.session_id, user_id = invite.receiver_id)
    if (not isFull(db,invite.session_id, invite.invite_id, invite.accepter_id)): #TODO: concurrency controls
        session_invite = db.get(SessionInvite, invite.invite_id)
        session_invite.status = InviteStatus("accepted")
        db.add(session_participant)
        db.update(session_invite)
        db.commit()
        return InviteAcceptResponse(status=True, message="invited accepted", id=invite.session_id)
    else:
        return InviteAcceptResponse(status=False, message="failed to accept invite", id=invite.session_id)

@router.post(
    "/{session_id}/decline",
    response_model=InviteAcceptResponse,
)
def decline_invite(
    invite: InviteDecline,
    db: Session = Depends(get_db),
):
    session_invite = db.get(SessionInvite, invite.invite_id)
    session_invite.status = InviteStatus("declined")
    db.update(session_invite)
    db.commit()
    return InviteDeclineResponse(status=True, message="invited declined", id=invite.session_id)

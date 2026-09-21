from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.gaming_session import GamingSession, SessionInvite, SessionParticipant, SessionStatus, SessionType, SessionVisibility, InviteStatus
from app.schemas.session import InviteAccept, InviteAcitionResponse, InviteCreate, InviteCreateResponse, InviteDecline, InviteResponse, SessionResponse, SessionCreate



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
    statement = select(GamingSession)

    return db.scalars(statement).all()


@router.post(
    "/create",
    response_model=SessionResponse,
    status_code=status.HTTP_201_CREATED,

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
        player_count=1,
        player_limit=session.player_limit,
    )

    db.add(new_session)
    db.flush()
    db.add(SessionParticipant(session_id = new_session.id, user_id = session.organiser_id))
    db.commit()
    db.refresh(new_session)

    return new_session

@router.post(
    "/{session_id}/invite", # session id is sent in InviteCreate anyway so doesnt need to be in route - is there a better route name to use?
    response_model=InviteCreateResponse,
    status_code=status.HTTP_201_CREATED,

)
def create_invite(
    invite: InviteCreate,
    db: Session = Depends(get_db),
):

    existing = db.scalars(
            select(SessionInvite).where(SessionInvite.session_id == invite.session_id, SessionInvite.receiver_id == invite.receiver_id)
        ).first()
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Invitee {invite.receiver_id} already invited to session",
        )
    
    
    new_invite = SessionInvite(
        session_id=invite.session_id,
        sender_id=invite.sender_id, 
        receiver_id=invite.receiver_id, # when authentication is added, should be updated to get the current users ID so this is not provided by the user
    )

    db.add(new_invite)
    db.commit()
    db.refresh(new_invite)

    return new_invite


#TODO use authentication to confirm the requester is the user whose invites are being fetched
@router.get(
    "/invites/{user_id}",
    response_model=list[InviteResponse],
)
def get_invites(
    user_id: UUID,
    db: Session = Depends(get_db),
):
    statement = (
        select(SessionInvite)
        .where(SessionInvite.receiver_id == user_id)
        .order_by(SessionInvite.created_at.desc())
    )

    return db.scalars(statement).all()


#TODO use authentication to confirm making the request is the one invited
@router.post(
    "/{session_id}/accept",
    response_model=InviteAcitionResponse,
)
def accept_invite(
    invite: InviteAccept,
    db: Session = Depends(get_db),
):  
    session_invite = db.get(SessionInvite, invite.invite_id)
    if (
        session_invite is None
        or session_invite.session_id != invite.session_id
        or session_invite.receiver_id != invite.receiver_id
    ):
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Invite not found")
    if session_invite.status != InviteStatus.PENDING:
        raise HTTPException(status.HTTP_409_CONFLICT, detail="Invite has already been responded to")
    
    gaming_session = db.scalars(
            select(GamingSession).where(GamingSession.id == invite.session_id).with_for_update()
        ).one_or_none()
    
    if gaming_session.player_limit is not None and gaming_session.player_count >= gaming_session.player_limit:
        raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"Session {invite.session_id} is full",
                )
    
    session_participant = SessionParticipant(session_id = invite.session_id, user_id = invite.receiver_id)
    session_invite.status = InviteStatus.ACCEPTED
    gaming_session.player_count += 1
    db.add(session_participant)
    db.commit()
    return InviteAcitionResponse(status=True, message="invited accepted", id=invite.session_id)

@router.post(
        "/{session_id}/decline", 
        response_model=InviteAcitionResponse
    )
def decline_invite(
    invite: InviteDecline, 
    db: Session = Depends(get_db)
):
    session_invite = db.get(SessionInvite, invite.invite_id)
    if session_invite is None or session_invite.receiver_id != invite.receiver_id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Invite not found")
    if session_invite.status != InviteStatus.PENDING:
        raise HTTPException(status.HTTP_409_CONFLICT, detail="Invite has already been responded to")

    session_invite.status = InviteStatus.DECLINED
    session_invite.responded_at = func.now()
    db.commit()

    return InviteAcitionResponse(status=True, message="invite declined", id=session_invite.session_id)

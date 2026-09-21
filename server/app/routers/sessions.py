from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select, func
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.gaming_session import GamingSession, SessionInvite, SessionParticipant, SessionStatus, SessionType, SessionVisibility, InviteStatus
from app.models.user import User
from app.schemas.session import InviteAccept, InviteAcitionResponse, InviteCreate, InviteCreateResponse, InviteDecline, InviteResponse, SessionResponse, SessionCreate
from app.security import get_current_user




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
    current_user: User = Depends(get_current_user),

):
    statement = (
        select(GamingSession)
        .join(
            SessionParticipant,
            SessionParticipant.session_id == GamingSession.id,
        )
        .where(SessionParticipant.user_id == current_user.id)
    )

    return db.scalars(statement).all()


@router.post(
    "/create",
    response_model=SessionResponse,
    status_code=status.HTTP_201_CREATED,

)
def create_session(
    session: SessionCreate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    
    new_session = GamingSession(
        organiser_id=current_user.id,
        game_id=session.game_id,
        group_id=session.group_id,
        title=session.title,
        description=session.description,
        start_at=session.start_at,
        end_at=session.end_at,
        session_type=session.session_type,
        visibility=session.visibility,
        status=SessionStatus.OPEN,
        location_name=session.location_name,
        player_count=1,
        player_limit=session.player_limit,
    )

    db.add(new_session)
    db.flush()
    db.add(SessionParticipant(session_id = new_session.id, user_id = current_user.id))
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
    current_user: User = Depends(get_current_user),

):

    session = db.scalars(
            select(GamingSession).where(GamingSession.id == invite.session_id)
        ).first()
    if session is None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Session {invite.session_id} does not exist",
        )

    if current_user.id != session.organiser_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"User is trying to invite to a session they are not the organiser of",
        )

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
        sender_id=current_user.id, 
        receiver_id=invite.receiver_id, 
    )

    db.add(new_invite)
    db.commit()
    db.refresh(new_invite)

    return new_invite


@router.get(
    "/invites",
    response_model=list[InviteResponse],
)
def get_invites(
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    
    statement = (
        select(SessionInvite)
        .where(SessionInvite.receiver_id == current_user.id)
        .order_by(SessionInvite.created_at.desc())
    )

    return db.scalars(statement).all()


@router.post(
    "/{session_id}/accept",
    response_model=InviteAcitionResponse,
)
def accept_invite(
    invite: InviteAccept,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):  
    session_invite = db.get(SessionInvite, invite.invite_id)
    if session_invite is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Invite {invite.invite_id} not found")

    if current_user.id != session_invite.receiver_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"User is not the receipient of the invite",
        )
    
    if session_invite.status != InviteStatus.PENDING:
        raise HTTPException(status.HTTP_409_CONFLICT, detail=f"Invite {invite.invite_id} has already been responded to")
    
    gaming_session = db.scalars(
            select(GamingSession).where(GamingSession.id == session_invite.session_id).with_for_update()
        ).one_or_none()

    if gaming_session is None:
        raise HTTPException(
                    status_code=status.HTTP_404_NOT_FOUND,
                    detail=f"Session {session_invite.session_id} does not exist",
        )

    if gaming_session.status is SessionStatus.CANCELLED:
        raise HTTPException(
                    status_code=status.HTTP_409_NOT_FOUND,
                    detail=f"Session {session_invite.session_id} cannot be accepted as it is cancelled",
        )

    
    if gaming_session.player_limit is not None and gaming_session.player_count >= gaming_session.player_limit:
        raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"Session {session_invite.session_id} is full",
        )
    
    session_participant = SessionParticipant(session_id = invite.session_id, user_id = invite.receiver_id)
    session_invite.status = InviteStatus.ACCEPTED
    session_invite.responded_at = func.now()
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
    current_user: User = Depends(get_current_user), 
    db: Session = Depends(get_db)
):
    session_invite = db.get(SessionInvite, invite.invite_id)
    if session_invite is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Invite not found")
    if current_user.id != session_invite.receiver_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"User is not the receipient of the invite",
        )
    if session_invite.status != InviteStatus.PENDING:
        raise HTTPException(status.HTTP_409_CONFLICT, detail="Invite has already been responded to")

    session_invite.status = InviteStatus.DECLINED
    session_invite.responded_at = func.now()
    db.commit()

    return InviteAcitionResponse(status=True, message="invite declined", id=session_invite.session_id)

from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status, Query
from sqlalchemy import delete, func, or_, select, update
from sqlalchemy.orm import Session

from app.database import get_db
from app.helpers.session import can_view_session
from app.models.gaming_session import GamingSession, SessionInvite, SessionParticipant, SessionStatus, SessionType, SessionVisibility, InviteStatus
from app.models.user import User
from app.models.game import Game
from app.models.user_group import UserGroup, UserGroupMember
from app.schemas.session import LOCATION_FIELDS, InviteAccept, InviteAcitionResponse, InviteCreate, InviteCreateResponse, InviteDecline, InviteResponse, ParticipantResponse, SentInviteResponse, SessionCancelResponse, SessionResponse, SessionCreate, SessionUpdate
from app.schemas.user import UserResponse
from app.security import get_current_user




router = APIRouter(
    prefix="/sessions",
    tags=["sessions"],
)


def _require_group_membership(db: Session, group_id: UUID, user_id: UUID) -> None:
    if db.get(UserGroup, group_id) is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Group {group_id} does not exist",
        )

    if db.get(UserGroupMember, (group_id, user_id)) is None:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="You can only create sessions for groups you are a member of",
        )


def _require_in_person_location(session_type: SessionType, location_name: str | None) -> None:
    if session_type == SessionType.IN_PERSON and not (location_name and location_name.strip()):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="An in-person session needs a location",
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

@router.get(
    "/public",
    response_model=list[SessionResponse],
)
def search_public_sessions(
    q: str | None = Query(
        default=None,
        max_length=100,
        description="Matches against the game name or the session title",
    ),
    limit: int = Query(default=20, ge=1, le=50),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    # Sessions the user is already in are left out, since there is nothing left to join.
    already_in = select(SessionParticipant.session_id).where(
        SessionParticipant.user_id == current_user.id
    )

    statement = (
        select(GamingSession)
        .join(Game, Game.id == GamingSession.game_id)
        .where(
            # Only public, open sessions that haven't finished are ever returned, so private,
            # friends and group sessions can't leak through search.
            GamingSession.visibility == SessionVisibility.PUBLIC,
            GamingSession.status == SessionStatus.OPEN,
            GamingSession.end_at > func.now(),
            GamingSession.id.not_in(already_in),
        )
        .order_by(GamingSession.start_at)
        .limit(limit)
    )

    term = (q or "").strip()
    if term:
        # autoescape stops a typed % or _ from acting as a wildcard.
        statement = statement.where(
            or_(
                Game.name.icontains(term, autoescape=True),
                GamingSession.title.icontains(term, autoescape=True),
            )
        )

    return db.scalars(statement).all()


@router.post(
    "/{session_id}/join",
    response_model=SessionResponse,
)
def join_public_session(
    session_id: UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    # Locked so two people taking the last place at the same moment can't both get it.
    session = db.scalars(
        select(GamingSession).where(GamingSession.id == session_id).with_for_update()
    ).one_or_none()

    # 404 rather than 403 so a private session isn't revealed to exist.
    if session is None or session.visibility != SessionVisibility.PUBLIC:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Session {session_id} does not exist")
    if session.status != SessionStatus.OPEN:
        raise HTTPException(status.HTTP_409_CONFLICT, detail=f"Session {session_id} is {session.status.value} and can't be joined")
    if db.get(SessionParticipant, (session_id, current_user.id)) is not None:
        raise HTTPException(status.HTTP_409_CONFLICT, detail="You're already in this session")
    if session.player_limit is not None and session.player_count >= session.player_limit:
        raise HTTPException(status.HTTP_409_CONFLICT, detail=f"Session {session_id} is full")


    db.add(SessionParticipant(session_id=session_id, user_id=current_user.id))
    session.player_count += 1

    # If the user had been invited as well, that invite is now settled.
    db.execute(
        update(SessionInvite)
        .where(
            SessionInvite.session_id == session_id,
            SessionInvite.receiver_id == current_user.id,
            SessionInvite.status == InviteStatus.PENDING,
        )
        .values(status=InviteStatus.ACCEPTED, responded_at=func.now())
    )

    db.commit()
    db.refresh(session)
    return session


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
    if session.visibility == SessionVisibility.GROUP and session.group_id is None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="A group must be chosen for group visibility",
        )

    if session.group_id is not None:
        _require_group_membership(db, session.group_id, current_user.id)

    _require_in_person_location(session.session_type, session.location_name)

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
        location_address=session.location_address,
        location_place_id=session.location_place_id,
        location_lat=session.location_lat,
        location_lng=session.location_lng,
        player_count=1,
        player_limit=session.player_limit,
    )

    db.add(new_session)
    db.flush()
    db.add(SessionParticipant(session_id = new_session.id, user_id = current_user.id))
    
    if session.group_id is not None:
        member_ids = db.scalars(
            select(UserGroupMember.user_id).where(
                UserGroupMember.group_id == session.group_id,
                UserGroupMember.user_id != current_user.id,
            )
        ).all()
        db.add_all(
            SessionInvite(
                session_id=new_session.id,
                sender_id=current_user.id,
                receiver_id=member_id,
            )
            for member_id in member_ids
        )
    db.commit()
    db.refresh(new_session)

    return new_session

@router.post(
    "/{session_id}/cancel",
    response_model=SessionCancelResponse,
)
def cancel_session(
    session_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    session = db.scalars(
        select(GamingSession).where(GamingSession.id == session_id).with_for_update()
    ).one_or_none()
    if session is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Session {session_id} does not exist")
    if current_user.id != session.organiser_id:
        raise HTTPException(status.HTTP_403_FORBIDDEN, detail="User attempting to delete session they are not the organiser of")
    if session.status != SessionStatus.OPEN:
        raise HTTPException(status.HTTP_409_CONFLICT, detail=f"Session {session_id} is already {session.status.value}")

    session.status = SessionStatus.CANCELLED

    db.execute(
        update(SessionInvite)
        .where(
            SessionInvite.session_id == session_id,
            SessionInvite.status == InviteStatus.PENDING,
        )
        .values(status=InviteStatus.CANCELLED, responded_at=func.now())
    )

    db.commit()
    return SessionCancelResponse(status=True, message="session cancelled", id=session_id)


@router.post(
    "/{session_id}/leave",
    status_code=status.HTTP_204_NO_CONTENT,
)
def leave_session(
    session_id: UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    session = db.scalars(
        select(GamingSession).where(GamingSession.id == session_id).with_for_update()
    ).one_or_none()
    participant = db.get(SessionParticipant, (session_id, current_user.id)) if session else None
    if participant is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"You aren't in session {session_id}")
    if session.organiser_id == current_user.id:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Organisers can't leave their own session; cancel it instead")
    if session.status != SessionStatus.OPEN:
        raise HTTPException(status.HTTP_409_CONFLICT, detail=f"Session {session_id} is {session.status.value} and can't be left")

    _remove_participant(db, session, participant)
    db.commit()


@router.delete(
    "/{session_id}/participants/{user_id}",
    status_code=status.HTTP_204_NO_CONTENT,
)
def remove_participant(
    session_id: UUID,
    user_id: UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    session = db.scalars(
        select(GamingSession).where(GamingSession.id == session_id).with_for_update()
    ).one_or_none()
    if session is None or not can_view_session(db, session, current_user.id):
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Session {session_id} does not exist")
    if current_user.id != session.organiser_id:
        raise HTTPException(status.HTTP_403_FORBIDDEN, detail="Only the organiser can remove players")
    if user_id == session.organiser_id:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="The organiser can't be removed; cancel the session instead")
    if session.status != SessionStatus.OPEN:
        raise HTTPException(status.HTTP_409_CONFLICT, detail=f"Session {session_id} is {session.status.value} and players can't be removed")

    participant = db.get(SessionParticipant, (session_id, user_id))
    if participant is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"User {user_id} isn't in session {session_id}")

    _remove_participant(db, session, participant)
    db.commit()


def _remove_participant(db: Session, session: GamingSession, participant: SessionParticipant) -> None:
    db.delete(participant)
    session.player_count -= 1
    # Removing the invite lets the organiser invite them again and stops it granting access to the session.
    db.execute(
        delete(SessionInvite).where(
            SessionInvite.session_id == session.id,
            SessionInvite.receiver_id == participant.user_id,
        )
    )


@router.post(
    "/{session_id}/invite", # session id is sent in InviteCreate anyway so doesnt need to be in route - is there a better route name to use?
    response_model=InviteCreateResponse,
    status_code=status.HTTP_201_CREATED,

)
def create_invite(
    session_id: UUID,
    invite: InviteCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),

):
    if invite.session_id != session_id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Session id in the body doesn't match the URL",
        )

    session = db.scalars(
            select(GamingSession).where(GamingSession.id == invite.session_id)
        ).first()
    if session is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Session {invite.session_id} does not exist",
        )

    if current_user.id != session.organiser_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=f"User is trying to invite to a session they are not the organiser of",
        )

    if session.status != SessionStatus.OPEN:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Session {invite.session_id} is {session.status.value} and can't take invites",
        )

    if invite.receiver_id == current_user.id:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="You can't invite yourself",
        )

    if db.get(User, invite.receiver_id) is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"User {invite.receiver_id} does not exist",
        )

    if db.get(SessionParticipant, (invite.session_id, invite.receiver_id)) is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"User {invite.receiver_id} is already in the session",
        )

    existing = db.scalars(
            select(SessionInvite).where(SessionInvite.session_id == invite.session_id, SessionInvite.receiver_id == invite.receiver_id)
        ).first()
    if existing is not None:
        if existing.status == InviteStatus.PENDING:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail=f"Invitee {invite.receiver_id} already invited to session",
            )

        # Only one invite per user per session is allowed, so a past invite is reopened.
        existing.status = InviteStatus.PENDING
        existing.sender_id = current_user.id
        existing.created_at = func.now()
        existing.responded_at = None
        db.commit()
        db.refresh(existing)
        return existing

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
        .where(SessionInvite.receiver_id == current_user.id, SessionInvite.status == InviteStatus.PENDING)
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

    if gaming_session.status != SessionStatus.OPEN:
        raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"Session {session_invite.session_id} cannot be accepted as it is {gaming_session.status.value}",
        )

    
    if gaming_session.player_limit is not None and gaming_session.player_count >= gaming_session.player_limit:
        raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail=f"Session {session_invite.session_id} is full",
        )
    
    session_participant = SessionParticipant(session_id = session_invite.session_id, user_id = session_invite.receiver_id)
    session_invite.status = InviteStatus.ACCEPTED
    session_invite.responded_at = func.now()
    gaming_session.player_count += 1
    db.add(session_participant)
    db.commit()
    return InviteAcitionResponse(status=True, message="invited accepted", id=session_invite.session_id)

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


# Declared after the fixed /sessions/... paths so they aren't captured as a session id.
@router.get(
    "/{session_id}",
    response_model=SessionResponse,
)
def get_session(
    session_id: UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    session = db.get(GamingSession, session_id)

    # 404 rather than 403 so sessions a user can't see aren't revealed to exist.
    if session is None or not can_view_session(db, session, current_user.id):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Session {session_id} does not exist",
        )

    return session


@router.get(
    "/{session_id}/participants",
    response_model=list[ParticipantResponse],
)
def get_participants(
    session_id: UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    session = db.get(GamingSession, session_id)
    if session is None or not can_view_session(db, session, current_user.id):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Session {session_id} does not exist",
        )

    rows = db.execute(
        select(SessionParticipant, User)
        .join(User, User.id == SessionParticipant.user_id)
        .where(SessionParticipant.session_id == session_id)
        .order_by(SessionParticipant.joined_at)
    ).all()

    return [
        ParticipantResponse(user=UserResponse.model_validate(user), joined_at=participant.joined_at)
        for participant, user in rows
    ]


@router.get(
    "/{session_id}/invites",
    response_model=list[SentInviteResponse],
)
def get_sent_invites(
    session_id: UUID,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    session = db.get(GamingSession, session_id)
    if session is None or not can_view_session(db, session, current_user.id):
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Session {session_id} does not exist",
        )
    if current_user.id != session.organiser_id:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Only the organiser can see a session's invites",
        )

    rows = db.execute(
        select(SessionInvite, User)
        .join(User, User.id == SessionInvite.receiver_id)
        .where(
            SessionInvite.session_id == session_id,
            SessionInvite.status == InviteStatus.PENDING,
        )
        .order_by(SessionInvite.created_at)
    ).all()

    return [
        SentInviteResponse(
            **InviteResponse.model_validate(invite).model_dump(),
            receiver=UserResponse.model_validate(user),
        )
        for invite, user in rows
    ]


@router.patch(
    "/{session_id}",
    response_model=SessionResponse,
)
def update_session(
    session_id: UUID,
    changes: SessionUpdate,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    session = db.scalars(
        select(GamingSession).where(GamingSession.id == session_id).with_for_update()
    ).one_or_none()
    if session is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail=f"Session {session_id} does not exist")
    if current_user.id != session.organiser_id:
        raise HTTPException(status.HTTP_403_FORBIDDEN, detail="Only the organiser can edit a session")
    if session.status != SessionStatus.OPEN:
        raise HTTPException(status.HTTP_409_CONFLICT, detail=f"Session {session_id} is {session.status.value} and can't be edited")

    updates = changes.model_dump(exclude_unset=True)

    # Stops a new location name keeping the previous place's address and coordinates.
    if updates.keys() & set(LOCATION_FIELDS):
        for field in LOCATION_FIELDS:
            updates.setdefault(field, None)

    _require_in_person_location(
        updates.get("session_type", session.session_type),
        updates.get("location_name", session.location_name),
    )

    start_at = updates.get("start_at", session.start_at)
    end_at = updates.get("end_at", session.end_at)
    if end_at <= start_at:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="End time must be after start time")

    # Group members are invited when the session is created, so moving it to, from or between
    # groups would leave those invites out of sync with the group.
    if updates.get("group_id", session.group_id) != session.group_id:
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="A session's group can't be changed")
    visibility = updates.get("visibility", session.visibility)
    if visibility != session.visibility and SessionVisibility.GROUP in (visibility, session.visibility):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="A session can't be moved into or out of a group")

    player_limit = updates.get("player_limit", session.player_limit)
    if player_limit is not None and player_limit < session.player_count:
        raise HTTPException(
            status.HTTP_409_CONFLICT,
            detail=f"Player limit can't be below the current {session.player_count} players",
        )

    for field, value in updates.items():
        setattr(session, field, value)

    db.commit()
    db.refresh(session)
    return session

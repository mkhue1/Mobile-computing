from datetime import datetime
from enum import Enum

from sqlalchemy import (
    BigInteger,
    CheckConstraint,
    DateTime,
    Enum as SQLEnum,
    ForeignKey,
    Integer,
    String,
    Text,
    UniqueConstraint,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class SessionType(str, Enum):
    ONLINE = "online"
    IN_PERSON = "in_person"


class SessionVisibility(str, Enum):
    PRIVATE = "private"
    FRIENDS = "friends"
    GROUP = "group"
    PUBLIC = "public"


class SessionStatus(str, Enum):
    OPEN = "open"
    CANCELLED = "cancelled"
    COMPLETED = "completed"


class InviteStatus(str, Enum):
    PENDING = "pending"
    ACCEPTED = "accepted"
    DECLINED = "declined"


class GamingSession(Base):
    __tablename__ = "sessions"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
    )

    organiser_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
        ),
        nullable=False,
    )

    game_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey("games.id"),
        nullable=False,
    )

    group_id: Mapped[int | None] = mapped_column(
        BigInteger,
        ForeignKey(
            "user_groups.id",
            ondelete="SET NULL",
        ),
        nullable=True,
    )

    title: Mapped[str | None] = mapped_column(
        String(150),
        nullable=True,
    )

    description: Mapped[str | None] = mapped_column(
        Text,
        nullable=True,
    )

    start_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )

    end_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
    )

    session_type: Mapped[SessionType] = mapped_column(
        SQLEnum(
            SessionType,
            name="session_type",
            values_callable=lambda enum: [
                member.value for member in enum
            ],
        ),
        nullable=False,
    )

    visibility: Mapped[SessionVisibility] = mapped_column(
        SQLEnum(
            SessionVisibility,
            name="session_visibility",
            values_callable=lambda enum: [
                member.value for member in enum
            ],
        ),
        nullable=False,
        server_default=SessionVisibility.PRIVATE.value,
    )

    status: Mapped[SessionStatus] = mapped_column(
        SQLEnum(
            SessionStatus,
            name="session_status",
            values_callable=lambda enum: [
                member.value for member in enum
            ],
        ),
        nullable=False,
        server_default=SessionStatus.OPEN.value,
    )

    location_name: Mapped[str | None] = mapped_column(
        String(255),
        nullable=True,
    )

    player_limit: Mapped[int | None] = mapped_column(
        Integer,
        nullable=True,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
        onupdate=func.now(),
    )

    __table_args__ = (
        CheckConstraint(
            "end_at > start_at",
            name="ck_sessions_end_after_start",
        ),
        CheckConstraint(
            "player_limit IS NULL OR player_limit > 0",
            name="ck_sessions_player_limit_positive",
        ),
        CheckConstraint(
            "visibility <> 'group' OR group_id IS NOT NULL",
            name="ck_sessions_group_visibility",
        ),
    )


class SessionParticipant(Base):
    __tablename__ = "session_participants"

    session_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey(
            "sessions.id",
            ondelete="CASCADE",
        ),
        primary_key=True,
    )

    user_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
        ),
        primary_key=True,
    )

    joined_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )


class SessionInvite(Base):
    __tablename__ = "session_invites"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
    )

    session_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey(
            "sessions.id",
            ondelete="CASCADE",
        ),
        nullable=False,
    )

    sender_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
        ),
        nullable=False,
    )

    receiver_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
        ),
        nullable=False,
    )

    status: Mapped[InviteStatus] = mapped_column(
        SQLEnum(
            InviteStatus,
            name="invite_status",
            values_callable=lambda enum: [
                member.value for member in enum
            ],
        ),
        nullable=False,
        server_default=InviteStatus.PENDING.value,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

    responded_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True),
        nullable=True,
    )

    __table_args__ = (
        CheckConstraint(
            "sender_id <> receiver_id",
            name="ck_session_invites_not_self",
        ),
        UniqueConstraint(
            "session_id",
            "receiver_id",
            name="uq_session_invites_session_receiver",
        ),
    )
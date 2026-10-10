from datetime import datetime
from typing import TYPE_CHECKING, Optional
import uuid

from sqlalchemy import DateTime, String, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.database import Base

if TYPE_CHECKING:
    from app.models.user_avatar import UserAvatar


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        primary_key=True,
        default=uuid.uuid4,
    )

    email: Mapped[str] = mapped_column(
        String(255),
        unique=True,
        nullable=False,
    )

    username: Mapped[str] = mapped_column(
        String(120),
        unique=True,
        nullable=False,
    )

    password: Mapped[str] = mapped_column(
        String(255),
        nullable=False,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

    # selectin loads every user's avatar row in one extra query, so lists stay free of N+1.
    avatar: Mapped[Optional["UserAvatar"]] = relationship(
        lazy="selectin",
        cascade="all, delete-orphan",
        passive_deletes=True,
    )

    @property
    def avatar_updated_at(self) -> datetime | None:
        return self.avatar.updated_at if self.avatar is not None else None

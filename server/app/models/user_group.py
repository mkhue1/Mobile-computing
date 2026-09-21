from datetime import datetime
from enum import Enum
import uuid

from sqlalchemy import (
    DateTime,
    Enum as SQLEnum,
    ForeignKey,
    String,
    Uuid,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class GroupRole(str, Enum):
    OWNER = "owner"
    MEMBER = "member"


class UserGroup(Base):
    __tablename__ = "user_groups"

    id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        primary_key=True,
        default=uuid.uuid4,
    )

    name: Mapped[str] = mapped_column(
        String(120),
        nullable=False,
    )

    owner_id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
        ),
        nullable=False,
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )


class UserGroupMember(Base):
    __tablename__ = "user_group_members"

    group_id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        ForeignKey(
            "user_groups.id",
            ondelete="CASCADE",
        ),
        primary_key=True,
    )

    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
        ),
        primary_key=True,
    )

    role: Mapped[GroupRole] = mapped_column(
        SQLEnum(
            GroupRole,
            name="group_role",
            values_callable=lambda enum: [
                member.value for member in enum
            ],
        ),
        nullable=False,
        server_default=GroupRole.MEMBER.value,
    )

    joined_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )
from datetime import datetime
from enum import Enum

from sqlalchemy import (
    BigInteger,
    DateTime,
    Enum as SQLEnum,
    ForeignKey,
    String,
    func,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class GroupRole(str, Enum):
    OWNER = "owner"
    MEMBER = "member"


class UserGroup(Base):
    __tablename__ = "user_groups"

    id: Mapped[int] = mapped_column(
        BigInteger,
        primary_key=True,
        autoincrement=True,
    )

    name: Mapped[str] = mapped_column(
        String(120),
        nullable=False,
    )

    owner_id: Mapped[int] = mapped_column(
        BigInteger,
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

    group_id: Mapped[int] = mapped_column(
        BigInteger,
        ForeignKey(
            "user_groups.id",
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
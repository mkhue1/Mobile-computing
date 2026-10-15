from datetime import datetime
import uuid

from sqlalchemy import DateTime, ForeignKey, LargeBinary, String, Uuid, func
from sqlalchemy.orm import Mapped, mapped_column

from app.database import Base


class UserAvatar(Base):
    __tablename__ = "user_avatars"

    user_id: Mapped[uuid.UUID] = mapped_column(
        Uuid,
        ForeignKey(
            "users.id",
            ondelete="CASCADE",
        ),
        primary_key=True,
    )

    # Deferred so loading a user (and its avatar_updated_at) never pulls the image bytes.
    data: Mapped[bytes] = mapped_column(
        LargeBinary,
        nullable=False,
        deferred=True,
    )

    content_type: Mapped[str] = mapped_column(
        String(50),
        nullable=False,
    )

    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        nullable=False,
        server_default=func.now(),
    )

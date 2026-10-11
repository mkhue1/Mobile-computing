"""add user steam_id

Revision ID: a1b2c3d4e5f6
Revises: ef370a4673ea
Create Date: 2026-10-03 00:00:00.000000

"""
from typing import Sequence, Union

import sqlalchemy as sa
from alembic import op


revision: str = "a1b2c3d4e5f6"
down_revision: Union[str, Sequence[str], None] = "ef370a4673ea"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column("users", sa.Column("steam_id", sa.String(length=17), nullable=True))
    op.add_column(
        "users",
        sa.Column("steam_linked_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_unique_constraint("uq_users_steam_id", "users", ["steam_id"])


def downgrade() -> None:
    op.drop_constraint("uq_users_steam_id", "users", type_="unique")
    op.drop_column("users", "steam_linked_at")
    op.drop_column("users", "steam_id")

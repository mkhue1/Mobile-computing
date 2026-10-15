"""index session coordinates

Revision ID: b7e2a1c94d10
Revises: 948228cf07d0
Create Date: 2026-10-12 01:40:00.000000

"""
from typing import Sequence, Union

from alembic import op


revision: str = "b7e2a1c94d10"
down_revision: Union[str, Sequence[str], None] = "948228cf07d0"
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.create_index("ix_sessions_location", "sessions", ["location_lat", "location_lng"])


def downgrade() -> None:
    op.drop_index("ix_sessions_location", table_name="sessions")

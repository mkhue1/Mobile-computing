"""add cancelled invite status

Revision ID: ef370a4673ea
Revises: 493cefab6fd8
Create Date: 2026-10-01 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op


# revision identifiers, used by Alembic.
revision: str = 'ef370a4673ea'
down_revision: Union[str, Sequence[str], None] = '493cefab6fd8'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.execute("ALTER TYPE invite_status ADD VALUE IF NOT EXISTS 'cancelled'")


def downgrade() -> None:
    """Downgrade schema."""
    # Postgres has no DROP VALUE for enums, so can't downgrade
    # upgrade().
    raise NotImplementedError(
        "Cannot downgrade: Postgres cannot drop a single enum value. "
        "If no rows use invite_status = 'cancelled', rebuild the type "
        "manually (rename invite_status -> invite_status_old, create the "
        "3-value invite_status, ALTER COLUMN ... USING status::text::invite_status, "
        "drop invite_status_old); otherwise those rows must be migrated first."
    )

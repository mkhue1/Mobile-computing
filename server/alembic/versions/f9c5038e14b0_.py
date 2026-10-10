"""empty message

Revision ID: f9c5038e14b0
Revises: 6e757450f954, a4d9c2e7f1b3
Create Date: 2026-10-09 13:27:24.140471

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'f9c5038e14b0'
down_revision: Union[str, Sequence[str], None] = ('6e757450f954', 'a4d9c2e7f1b3')
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    pass


def downgrade() -> None:
    """Downgrade schema."""
    pass

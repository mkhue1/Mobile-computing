"""merge steam and location heads

Revision ID: e6c3faddc3bd
Revises: 7c1e4b9a2d3f, a1b2c3d4e5f6
Create Date: 2026-10-10 14:55:44.919835

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'e6c3faddc3bd'
down_revision: Union[str, Sequence[str], None] = ('7c1e4b9a2d3f', 'a1b2c3d4e5f6')
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    pass


def downgrade() -> None:
    """Downgrade schema."""
    pass

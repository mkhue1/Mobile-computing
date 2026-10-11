"""merge avatar and steam heads

Revision ID: 948228cf07d0
Revises: a4d9c2e7f1b3, e6c3faddc3bd
Create Date: 2026-10-11 17:18:57.875553

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '948228cf07d0'
down_revision: Union[str, Sequence[str], None] = ('a4d9c2e7f1b3', 'e6c3faddc3bd')
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    pass


def downgrade() -> None:
    """Downgrade schema."""
    pass

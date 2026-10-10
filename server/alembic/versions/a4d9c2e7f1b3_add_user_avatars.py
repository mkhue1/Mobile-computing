"""add user avatars

Revision ID: a4d9c2e7f1b3
Revises: 7c1e4b9a2d3f
Create Date: 2026-10-07 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'a4d9c2e7f1b3'
down_revision: Union[str, Sequence[str], None] = '7c1e4b9a2d3f'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.create_table('user_avatars',
    sa.Column('user_id', sa.Uuid(), nullable=False),
    sa.Column('data', sa.LargeBinary(), nullable=False),
    sa.Column('content_type', sa.String(length=50), nullable=False),
    sa.Column('updated_at', sa.DateTime(timezone=True), server_default=sa.text('now()'), nullable=False),
    sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
    sa.PrimaryKeyConstraint('user_id')
    )


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_table('user_avatars')

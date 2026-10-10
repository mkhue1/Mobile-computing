"""add google maps location details to sessions

Revision ID: 7c1e4b9a2d3f
Revises: ef370a4673ea
Create Date: 2026-10-04 00:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = '7c1e4b9a2d3f'
down_revision: Union[str, Sequence[str], None] = 'ef370a4673ea'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema."""
    op.add_column('sessions', sa.Column('location_address', sa.Text(), nullable=True))
    op.add_column('sessions', sa.Column('location_place_id', sa.Text(), nullable=True))
    op.add_column('sessions', sa.Column('location_lat', sa.Double(), nullable=True))
    op.add_column('sessions', sa.Column('location_lng', sa.Double(), nullable=True))
    op.create_check_constraint(
        'ck_sessions_location_coordinates_paired',
        'sessions',
        '(location_lat IS NULL) = (location_lng IS NULL)',
    )
    op.create_check_constraint(
        'ck_sessions_location_lat_range',
        'sessions',
        'location_lat IS NULL OR location_lat BETWEEN -90 AND 90',
    )
    op.create_check_constraint(
        'ck_sessions_location_lng_range',
        'sessions',
        'location_lng IS NULL OR location_lng BETWEEN -180 AND 180',
    )


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_constraint('ck_sessions_location_lng_range', 'sessions', type_='check')
    op.drop_constraint('ck_sessions_location_lat_range', 'sessions', type_='check')
    op.drop_constraint('ck_sessions_location_coordinates_paired', 'sessions', type_='check')
    op.drop_column('sessions', 'location_lng')
    op.drop_column('sessions', 'location_lat')
    op.drop_column('sessions', 'location_place_id')
    op.drop_column('sessions', 'location_address')

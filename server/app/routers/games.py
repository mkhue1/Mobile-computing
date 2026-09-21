from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.game import Game
from app.schemas.game import GameCreate, GameResponse


router = APIRouter(
    prefix="/games",
    tags=["games"],
)


@router.post(
    "/",
    response_model=GameResponse,
    status_code=status.HTTP_201_CREATED,
)
def create_game(
    game_create: GameCreate,
    db: Session = Depends(get_db),
):
    existing = db.scalars(
        select(Game).where(Game.igdb_id == game_create.igdb_id)
    ).first()
    if existing is not None:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail=f"Game with IGDB id {game_create.igdb_id} already exists",
        )

    new_game = Game(
        igdb_id=game_create.igdb_id,
        name=game_create.name,
        cover_url=game_create.cover_url,
    )

    db.add(new_game)
    db.commit()
    db.refresh(new_game)

    return new_game

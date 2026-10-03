import requests
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.helpers.igdb import register_game
from app.igdb import get_game as fetch_igdb_game
from app.models.game import Game
from app.schemas.game import GameCreate, GameResponse


router = APIRouter(
    prefix="/games",
    tags=["games"],
)


@router.get(
    "/",
    response_model=list[GameResponse],
)
def get_games(
    db: Session = Depends(get_db),
):
    statement = select(Game).order_by(Game.name)

    return db.scalars(statement).all()


@router.get(
    "/igdb/{igdb_id}",
    response_model=GameResponse,
)
def get_game(
    igdb_id: int,
    db: Session = Depends(get_db),
):
    existing = db.scalars(
        select(Game).where(Game.igdb_id == igdb_id)
    ).first()
    if existing is not None:
        return existing

    try:
        igdb_game = fetch_igdb_game(igdb_id)
    except requests.RequestException:
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail="Could not reach IGDB",
        )

    if igdb_game is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=f"Game with IGDB id {igdb_id} not found",
        )

    return register_game(db, igdb_game.igdb_id, igdb_game.name, igdb_game.cover_url)


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

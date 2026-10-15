from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.game import Game

def register_game(db: Session, igdb_id: int, name: str, cover_url: str) -> Game:
    existing = db.scalars(
        select(Game).where(Game.igdb_id == igdb_id)
    ).first()
    if existing is not None:
        return existing

    new_game = Game(
        igdb_id=igdb_id,
        name=name,
        cover_url=cover_url,
    )

    db.add(new_game)
    db.commit()
    db.refresh(new_game)

    return new_game
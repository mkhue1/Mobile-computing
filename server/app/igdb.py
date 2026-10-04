from datetime import date
import os
from typing import Tuple
import requests
from cachetools import cached, TTLCache
from threading import Lock
from app.models.game import Game
from app.schemas.game import GameSearchResponse

game_cache = TTLCache(maxsize=1000, ttl=600)
search_cache = TTLCache(maxsize=100, ttl=600)

IGDB_IMAGE_URL = "https://images.igdb.com/igdb/image/upload/t_cover_big/{image_id}.jpg"

def igbd_headers():
    return {"Client-ID": os.getenv("TWITCH_CLIENT_ID"), "Authorization": "Bearer " + os.getenv("IGDB_ACCESS_TOKEN", "")}

def get_igdb_credentials():
    url = "https://id.twitch.tv/oauth2/token"
    query_params = {"client_id": os.getenv("TWITCH_CLIENT_ID"), "client_secret": os.getenv("TWITCH_CLIENT_SECRET"), "grant_type": "client_credentials"}
    response = requests.post(url, query_params)
    print(response.json())
    return

# IGDB uses apicalypse query syntax, see https://apicalypse.io/syntax/
@cached(search_cache, lock=Lock())  
def search_igdb_game(query: str, limit: int = 10) -> list[GameSearchResponse]:
    url = "https://api.igdb.com/v4/games"
    # Escape so quotes in the provided qeury can't break search string
    escaped_query = query.replace("\\", "\\\\").replace('"', '\\"')
    db_query = f'where name ~ *"{escaped_query}"*; fields name, cover.image_id; sort total_rating_count desc; limit {limit};'
    response = requests.post(url, data=db_query, headers=igbd_headers(), timeout=5)
    response.raise_for_status()
    data = response.json()
    return [GameSearchResponse(
        igdb_id=game["id"],
        name=game["name"],
        cover_url=IGDB_IMAGE_URL.format(image_id=game["cover"]["image_id"]) if "cover" in game else None,
    ) for game in data]

@cached(game_cache,lock=Lock())
def fetch_igdb_game(igdb_id: int) -> GameSearchResponse | None:
    url = "https://api.igdb.com/v4/games"
    query = f"fields name, cover.image_id; where id = {igdb_id};"
    response = requests.post(url, data=query, headers=igbd_headers(), timeout=5)
    response.raise_for_status()

    results = response.json()
    if not results:
        return None

    data = results[0]
    cover = data.get("cover")
    return GameSearchResponse(
        igdb_id=data["id"],
        name=data["name"],
        cover_url=IGDB_IMAGE_URL.format(image_id=cover["image_id"]) if cover else None,
    )



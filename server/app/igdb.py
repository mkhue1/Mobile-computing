import os
from typing import Tuple
import requests

from app.models.game import Game

IGDB_IMAGE_URL = "https://images.igdb.com/igdb/image/upload/t_cover_big/{image_id}.jpg"


class GameSearchResult:
    def __init__(self, id: str, name: str):
        self.id = id
        self.name = name


def igbd_headers():
    return {"Client-ID": os.getenv("TWITCH_CLIENT_ID"), "Authorization": "Bearer " + os.getenv("IGDB_ACCESS_TOKEN", "")}

def get_igdb_credentials():
    url = "https://id.twitch.tv/oauth2/token"
    query_params = {"client_id": os.getenv("TWITCH_CLIENT_ID"), "client_secret": os.getenv("TWITCH_CLIENT_SECRET"), "grant_type": "client_credentials"}
    response = requests.post(url, query_params)
    print(response.json())
    return

# IGDB uses apicalypse query syntax, see https://apicalypse.io/syntax/  
def search_game(query: str) -> list[GameSearchResult]:
    url = "https://api.igdb.com/v4/games"
    db_query = f"search \"{query}\"; fields name;"
    response = requests.post(url, data=db_query, headers=igbd_headers())
    return response.json()

def get_game(igdb_id: int) -> Game | None:
    url = "https://api.igdb.com/v4/games"
    query = f"fields name, cover.image_id; where id = {igdb_id};"
    response = requests.post(url, data=query, headers=igbd_headers())
    response.raise_for_status()

    results = response.json()
    if not results:
        return None

    data = results[0]
    cover = data.get("cover")
    return Game(
        igdb_id=data["id"],
        name=data["name"],
        cover_url=IGDB_IMAGE_URL.format(image_id=cover["image_id"]) if cover else None,
    )


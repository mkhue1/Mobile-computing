from uuid import UUID

import pytest
import requests
from sqlalchemy import select

from app import igdb
from app.helpers.igdb import register_game
from app.models.game import Game


class FakeIGDBResponse:
    def __init__(self, payload, status_code=200):
        self._payload = payload
        self.status_code = status_code

    def json(self):
        return self._payload

    def raise_for_status(self):
        if self.status_code >= 400:
            raise requests.HTTPError(f"{self.status_code} error", response=self)


class FakeIGDB:
    """Stands in for requests.post in app.igdb and records every request made."""

    def __init__(self):
        self.calls = []
        self.payload = []
        self.status_code = 200
        self.error = None

    def post(self, url, data=None, headers=None, timeout=None, **kwargs):
        self.calls.append({"url": url, "data": data, "timeout": timeout})
        if self.error is not None:
            raise self.error
        return FakeIGDBResponse(self.payload, self.status_code)


@pytest.fixture(autouse=True)
def fake_igdb(monkeypatch):
    # Every test gets a fake IGDB, so nothing here can reach the real API.
    fake = FakeIGDB()
    monkeypatch.setattr(igdb.requests, "post", fake.post)
    return fake


@pytest.fixture(autouse=True)
def _clear_igdb_caches():
    # The caches live at module level, so results would otherwise leak between tests.
    igdb.search_cache.clear()
    igdb.game_cache.clear()
    yield
    igdb.search_cache.clear()
    igdb.game_cache.clear()


HALO = {"id": 740, "name": "Halo: Combat Evolved", "cover": {"id": 1, "image_id": "co1abc"}}
NO_COVER = {"id": 741, "name": "Coverless Game"}
HALO_COVER_URL = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1abc.jpg"


# --- search_igdb_game ----------------------------------------------------------


def test_search_maps_results_and_cover_urls(fake_igdb):
    fake_igdb.payload = [HALO, NO_COVER]

    results = igdb.search_igdb_game("halo")

    assert [(r.igdb_id, r.name, r.cover_url) for r in results] == [
        (740, "Halo: Combat Evolved", HALO_COVER_URL),
        (741, "Coverless Game", None),
    ]


def test_search_sends_limit_and_timeout(fake_igdb):
    igdb.search_igdb_game("halo", limit=5)

    call = fake_igdb.calls[0]
    assert 'where name ~ *"halo"*;' in call["data"]
    assert "sort total_rating_count desc;" in call["data"]
    assert "limit 5;" in call["data"]
    assert call["timeout"] == 5


def test_search_escapes_quotes_and_backslashes(fake_igdb):
    igdb.search_igdb_game('Halo "Reach\\')

    assert 'where name ~ *"Halo \\"Reach\\\\"*;' in fake_igdb.calls[0]["data"]


def test_search_raises_on_igdb_error(fake_igdb):
    fake_igdb.status_code = 429

    with pytest.raises(requests.HTTPError):
        igdb.search_igdb_game("halo")


def test_search_results_are_cached(fake_igdb):
    fake_igdb.payload = [HALO]

    first = igdb.search_igdb_game("halo")
    second = igdb.search_igdb_game("halo")

    assert first == second
    assert len(fake_igdb.calls) == 1


def test_search_with_no_matches_returns_empty_list(fake_igdb):
    results = igdb.search_igdb_game("zelda breath")

    assert results == []
    assert len(fake_igdb.calls) == 1


def test_different_searches_are_cached_separately(fake_igdb):
    igdb.search_igdb_game("halo")
    igdb.search_igdb_game("zelda")

    assert len(fake_igdb.calls) == 2


def test_failed_search_is_not_cached(fake_igdb):
    fake_igdb.error = requests.ConnectionError("down")
    with pytest.raises(requests.ConnectionError):
        igdb.search_igdb_game("halo")

    fake_igdb.error = None
    fake_igdb.payload = [HALO]
    results = igdb.search_igdb_game("halo")

    assert [r.igdb_id for r in results] == [740]
    assert len(fake_igdb.calls) == 2


# --- fetch_igdb_game -----------------------------------------------------------


def test_fetch_returns_game(fake_igdb):
    fake_igdb.payload = [HALO]

    game = igdb.fetch_igdb_game(740)

    assert (game.igdb_id, game.name, game.cover_url) == (740, "Halo: Combat Evolved", HALO_COVER_URL)
    assert "where id = 740;" in fake_igdb.calls[0]["data"]


def test_fetch_returns_none_for_unknown_game(fake_igdb):
    fake_igdb.payload = []

    assert igdb.fetch_igdb_game(999) is None


def test_fetch_results_are_cached(fake_igdb):
    fake_igdb.payload = [HALO]

    igdb.fetch_igdb_game(740)
    igdb.fetch_igdb_game(740)

    assert len(fake_igdb.calls) == 1


# --- register_game -------------------------------------------------------------


def test_register_game_saves_new_game(db):
    game = register_game(db, 740, "Halo: Combat Evolved", HALO_COVER_URL)

    assert isinstance(game.id, UUID)
    assert db.scalars(select(Game).where(Game.igdb_id == 740)).one() is not None


def test_register_game_returns_existing_game(db, game):
    registered = register_game(db, game.igdb_id, "Different Name", None)

    assert registered.id == game.id
    assert len(db.scalars(select(Game)).all()) == 1


# --- GET /games/search ---------------------------------------------------------


def test_search_endpoint(client, fake_igdb):
    fake_igdb.payload = [HALO, NO_COVER]

    response = client.get("/games/search", params={"q": "halo"})

    assert response.status_code == 200
    assert response.json() == [
        {"igdb_id": 740, "name": "Halo: Combat Evolved", "cover_url": HALO_COVER_URL},
        {"igdb_id": 741, "name": "Coverless Game", "cover_url": None},
    ]


def test_search_endpoint_requires_query(client):
    response = client.get("/games/search")

    assert response.status_code == 422


@pytest.mark.parametrize(
    "error",
    [requests.ConnectionError("down"), requests.Timeout("slow")],
)
def test_search_endpoint_when_igdb_unreachable(client, fake_igdb, error):
    fake_igdb.error = error

    response = client.get("/games/search", params={"q": "halo"})

    assert response.status_code == 502


def test_search_endpoint_when_igdb_returns_error(client, fake_igdb):
    fake_igdb.status_code = 500

    response = client.get("/games/search", params={"q": "halo"})

    assert response.status_code == 502


# --- GET /games/igdb/{igdb_id} -------------------------------------------------


def test_get_game_fetches_and_saves_new_game(client, db, fake_igdb):
    fake_igdb.payload = [HALO]

    response = client.get("/games/igdb/740")

    assert response.status_code == 200
    body = response.json()
    assert body["igdb_id"] == 740
    assert body["name"] == "Halo: Combat Evolved"
    assert body["cover_url"] == HALO_COVER_URL

    saved = db.scalars(select(Game).where(Game.igdb_id == 740)).one()
    assert body["id"] == str(saved.id)


def test_get_game_returns_saved_game_without_calling_igdb(client, fake_igdb, game):
    response = client.get(f"/games/igdb/{game.igdb_id}")

    assert response.status_code == 200
    assert response.json()["id"] == str(game.id)
    assert fake_igdb.calls == []


def test_get_game_twice_returns_same_saved_game(client, fake_igdb):
    fake_igdb.payload = [HALO]

    first = client.get("/games/igdb/740").json()
    second = client.get("/games/igdb/740").json()

    assert first["id"] == second["id"]
    assert len(fake_igdb.calls) == 1


def test_get_unknown_game(client, db, fake_igdb):
    fake_igdb.payload = []

    response = client.get("/games/igdb/999")

    assert response.status_code == 404
    assert db.scalars(select(Game)).all() == []


def test_get_game_when_igdb_unreachable(client, db, fake_igdb):
    fake_igdb.error = requests.ConnectionError("down")

    response = client.get("/games/igdb/740")

    assert response.status_code == 502
    assert db.scalars(select(Game)).all() == []


# --- GET /games/ and POST /games/ ----------------------------------------------


def test_list_games_sorted_by_name(client, db):
    db.add_all([Game(igdb_id=2, name="Zelda"), Game(igdb_id=3, name="Animal Crossing")])
    db.commit()

    response = client.get("/games/")

    assert [g["name"] for g in response.json()] == ["Animal Crossing", "Zelda"]


def test_create_game(client):
    response = client.post(
        "/games/",
        json={"igdb_id": 740, "name": "Halo: Combat Evolved", "cover_url": HALO_COVER_URL},
    )

    assert response.status_code == 201
    assert response.json()["igdb_id"] == 740


def test_create_duplicate_game(client, game):
    response = client.post("/games/", json={"igdb_id": game.igdb_id, "name": "Duplicate"})

    assert response.status_code == 409


@pytest.mark.parametrize(
    "payload",
    [{"igdb_id": 0, "name": "Bad id"}, {"igdb_id": 740, "name": ""}],
)
def test_create_game_validation(client, payload):
    response = client.post("/games/", json=payload)

    assert response.status_code == 422

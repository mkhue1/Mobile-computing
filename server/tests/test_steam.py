from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch

from app.helpers.friendship import ordered_friend_pair
from app.models.friendship import FriendRequest, Friendship
from app.models.user import User
from app.security import create_steam_link_state
from app.services.steam import SteamError


STEAM_ID_A = "76561198000000001"
STEAM_ID_B = "76561198000000002"
STEAM_ID_C = "76561198000000003"


def _openid_params(steam_id: str, state: str) -> dict[str, str]:
    return {
        "state": state,
        "openid.ns": "http://specs.openid.net/auth/2.0",
        "openid.mode": "id_res",
        "openid.op_endpoint": "https://steamcommunity.com/openid/login",
        "openid.claimed_id": f"https://steamcommunity.com/openid/id/{steam_id}",
        "openid.identity": f"https://steamcommunity.com/openid/id/{steam_id}",
        "openid.return_to": f"http://testserver/steam/callback?state={state}",
        "openid.response_nonce": "2026-01-01T00:00:00Z",
        "openid.assoc_handle": "test",
        "openid.signed": "signed,claimed_id",
        "openid.sig": "test-sig",
    }


def test_steam_status_unlinked(client, make_user, auth_headers):
    user = make_user("alice")

    response = client.get("/steam/status", headers=auth_headers(user))

    assert response.status_code == 200
    assert response.json() == {"linked": False, "steam_id": None}


def test_steam_link_returns_auth_url(client, make_user, auth_headers):
    user = make_user("alice")

    response = client.get("/steam/link", headers=auth_headers(user))

    assert response.status_code == 200
    auth_url = response.json()["auth_url"]
    assert auth_url.startswith("https://steamcommunity.com/openid/login?")
    assert "openid.mode=checkid_setup" in auth_url
    assert "steam%2Fcallback" in auth_url
    assert "state%3D" in auth_url


def test_steam_callback_links_account(client, make_user, db):
    user = make_user("alice")
    state = create_steam_link_state(user.id)
    params = _openid_params(STEAM_ID_A, state)

    with patch(
        "app.routers.steam.verify_openid_assertion",
        new=AsyncMock(return_value=STEAM_ID_A),
    ):
        response = client.get(
            "/steam/callback",
            params=params,
            follow_redirects=False,
        )

    assert response.status_code == 302
    assert response.headers["location"] == "gamercalendar://steam/linked"

    db.refresh(user)
    assert user.steam_id == STEAM_ID_A
    assert user.steam_linked_at is not None


def test_steam_callback_rejects_duplicate_steam_id(client, make_user, db):
    owner = make_user("owner")
    owner.steam_id = STEAM_ID_A
    owner.steam_linked_at = datetime.now(timezone.utc)
    db.commit()

    other = make_user("other")
    state = create_steam_link_state(other.id)

    with patch(
        "app.routers.steam.verify_openid_assertion",
        new=AsyncMock(return_value=STEAM_ID_A),
    ):
        response = client.get(
            "/steam/callback",
            params=_openid_params(STEAM_ID_A, state),
            follow_redirects=False,
        )

    assert response.status_code == 302
    assert "steam_already_linked" in response.headers["location"]

    db.refresh(other)
    assert other.steam_id is None


def test_steam_unlink(client, make_user, auth_headers, db):
    user = make_user("alice")
    user.steam_id = STEAM_ID_A
    user.steam_linked_at = datetime.now(timezone.utc)
    db.commit()

    response = client.delete("/steam/link", headers=auth_headers(user))

    assert response.status_code == 204
    db.refresh(user)
    assert user.steam_id is None
    assert user.steam_linked_at is None


def test_steam_suggestions_requires_link(client, make_user, auth_headers):
    user = make_user("alice")

    response = client.get(
        "/friends/suggestions/steam",
        headers=auth_headers(user),
    )

    assert response.status_code == 400
    assert "Connect your Steam account" in response.json()["detail"]


def test_steam_suggestions_matches_linked_friends(client, make_user, auth_headers, db):
    alice = make_user("alice")
    bob = make_user("bob")
    carol = make_user("carol")
    dave = make_user("dave")

    alice.steam_id = STEAM_ID_A
    bob.steam_id = STEAM_ID_B
    carol.steam_id = STEAM_ID_C
    dave.steam_id = "76561198000000004"
    db.commit()

    # Bob is already a friend; Carol has a pending request; Dave is eligible but not on Steam friends.
    low, high = ordered_friend_pair(alice.id, bob.id)
    db.add(Friendship(user_id=low, friend_id=high))
    db.add(FriendRequest(sender_id=alice.id, receiver_id=carol.id))
    db.commit()

    with patch(
        "app.routers.friends.get_friend_steam_ids",
        new=AsyncMock(return_value=[STEAM_ID_B, STEAM_ID_C, "76561198000000999"]),
    ):
        response = client.get(
            "/friends/suggestions/steam",
            headers=auth_headers(alice),
        )

    assert response.status_code == 200
    # Steam friends B/C are already friend / pending; unmatched Steam ID has no app user.
    assert response.json() == []


def test_steam_suggestions_returns_eligible_user(client, make_user, auth_headers, db):
    alice = make_user("alice")
    bob = make_user("bob")
    alice.steam_id = STEAM_ID_A
    bob.steam_id = STEAM_ID_B
    db.commit()

    with patch(
        "app.routers.friends.get_friend_steam_ids",
        new=AsyncMock(return_value=[STEAM_ID_B]),
    ):
        response = client.get(
            "/friends/suggestions/steam",
            headers=auth_headers(alice),
        )

    assert response.status_code == 200
    body = response.json()
    assert len(body) == 1
    assert body[0]["username"] == "bob"
    assert body[0]["steam_linked"] is True
    assert "steam_id" not in body[0]


def test_steam_suggestions_private_friends_list(client, make_user, auth_headers, db):
    alice = make_user("alice")
    alice.steam_id = STEAM_ID_A
    db.commit()

    with patch(
        "app.routers.friends.get_friend_steam_ids",
        new=AsyncMock(
            side_effect=SteamError(
                "Steam friend list is private. Make it public to see suggestions.",
                private_friends=True,
            )
        ),
    ):
        response = client.get(
            "/friends/suggestions/steam",
            headers=auth_headers(alice),
        )

    assert response.status_code == 400
    assert "private" in response.json()["detail"].lower()


def test_user_response_includes_steam_linked(client, make_user, auth_headers, db):
    alice = make_user("alice")
    bob = make_user("bob")
    bob.steam_id = STEAM_ID_B
    db.commit()

    response = client.get(
        "/users/",
        params={"search": "bo"},
        headers=auth_headers(alice),
    )

    assert response.status_code == 200
    body = response.json()
    assert len(body) == 1
    assert body[0]["steam_linked"] is True
    assert "steam_id" not in body[0]


def test_steam_callback_invalid_state(client):
    response = client.get(
        "/steam/callback",
        params={"state": "not-a-jwt", "openid.mode": "id_res"},
        follow_redirects=False,
    )

    assert response.status_code == 302
    assert "invalid_state" in response.headers["location"]

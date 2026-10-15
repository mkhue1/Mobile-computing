"""Steam OpenID verification and Web API helpers."""

from __future__ import annotations

import re
from urllib.parse import parse_qs, urlencode, urlsplit

import httpx

from app.config import settings

STEAM_OPENID_LOGIN = "https://steamcommunity.com/openid/login"
STEAM_OPENID_NS = "http://specs.openid.net/auth/2.0"
STEAM_ID_SELECT = "http://specs.openid.net/auth/2.0/identifier_select"
STEAM_CLAIMED_ID_RE = re.compile(
    r"^https://steamcommunity\.com/openid/id/(\d{17})$"
)
STEAM_CALLBACK_PATH = "/steam/callback"
FRIEND_LIST_URL = "https://api.steampowered.com/ISteamUser/GetFriendList/v1/"
PLAYER_SUMMARIES_URL = (
    "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/"
)


class SteamError(Exception):
    """Raised when a Steam API or OpenID call fails."""

    def __init__(self, message: str, *, private_friends: bool = False):
        super().__init__(message)
        self.private_friends = private_friends


def build_openid_login_url(*, return_to: str, realm: str) -> str:
    params = {
        "openid.ns": STEAM_OPENID_NS,
        "openid.mode": "checkid_setup",
        "openid.return_to": return_to,
        "openid.realm": realm,
        "openid.identity": STEAM_ID_SELECT,
        "openid.claimed_id": STEAM_ID_SELECT,
    }
    return f"{STEAM_OPENID_LOGIN}?{urlencode(params)}"


def parse_steam_id_from_claimed_id(claimed_id: str | None) -> str:
    if not claimed_id:
        raise SteamError("Missing Steam claimed_id")
    match = STEAM_CLAIMED_ID_RE.fullmatch(claimed_id.strip())
    if match is None:
        raise SteamError("Invalid Steam claimed_id")
    return match.group(1)


def _signed_openid_fields(params: dict[str, str]) -> set[str]:
    signed = params.get("openid.signed", "")
    return {field.strip() for field in signed.split(",") if field.strip()}


def _state_from_return_to(return_to: str | None) -> str:
    """Return the link state from a return_to URL on the public callback."""
    if not return_to:
        raise SteamError("Missing OpenID return_to")

    expected = urlsplit(settings.public_base_url.rstrip("/"))
    actual = urlsplit(return_to.strip())
    expected_path = f"{expected.path.rstrip('/')}{STEAM_CALLBACK_PATH}"
    if (
        actual.scheme.lower() != expected.scheme.lower()
        or actual.netloc.lower() != expected.netloc.lower()
        or actual.path.rstrip("/") != expected_path.rstrip("/")
    ):
        raise SteamError("OpenID return_to does not match the public base URL")

    states = parse_qs(actual.query).get("state", [])
    if len(states) != 1 or not states[0]:
        raise SteamError("Missing state in OpenID return_to")
    return states[0]


async def verify_openid_assertion(params: dict[str, str]) -> tuple[str, str]:
    """Validate a Steam OpenID assertion.

    Returns (steam_id, state), validating that return_to and claimed_id are
    signed in the process.
    """
    signed = _signed_openid_fields(params)
    if "return_to" not in signed or "claimed_id" not in signed:
        raise SteamError("OpenID return_to and claimed_id must be signed")

    check_params = {
        key: value
        for key, value in params.items()
        if key.startswith("openid.")
    }
    check_params["openid.mode"] = "check_authentication"

    async with httpx.AsyncClient(timeout=15.0) as client:
        response = await client.post(STEAM_OPENID_LOGIN, data=check_params)

    if response.status_code != 200:
        raise SteamError("Steam OpenID verification request failed")

    body = response.text
    valid = any(line.strip().lower() == "is_valid:true" for line in body.splitlines())
    if not valid:
        raise SteamError("Steam OpenID assertion is not valid")

    steam_id = parse_steam_id_from_claimed_id(params.get("openid.claimed_id"))
    state = _state_from_return_to(params.get("openid.return_to"))
    return steam_id, state


async def get_friend_steam_ids(steam_id: str) -> list[str]:
    if not settings.steam_api_key:
        raise SteamError("STEAM_API_KEY is not configured")

    async with httpx.AsyncClient(timeout=15.0) as client:
        response = await client.get(
            FRIEND_LIST_URL,
            params={
                "key": settings.steam_api_key,
                "steamid": steam_id,
                "relationship": "friend",
            },
        )

    if response.status_code == 401:
        raise SteamError(
            "Steam friend list is private. Make it public to see suggestions.",
            private_friends=True,
        )

    if response.status_code != 200:
        raise SteamError("Failed to fetch Steam friend list")

    data = response.json()
    friends = data.get("friendslist", {}).get("friends", [])
    return [str(friend["steamid"]) for friend in friends if "steamid" in friend]


async def get_steam_persona_names(steam_ids: list[str]) -> dict[str, str]:
    """Return a map of SteamID64 -> persona name for the given IDs."""
    if not settings.steam_api_key:
        raise SteamError("STEAM_API_KEY is not configured")
    if not steam_ids:
        return {}

    async with httpx.AsyncClient(timeout=15.0) as client:
        response = await client.get(
            PLAYER_SUMMARIES_URL,
            params={
                "key": settings.steam_api_key,
                "steamids": ",".join(steam_ids),
            },
        )

    if response.status_code != 200:
        raise SteamError("Failed to fetch Steam player summaries")

    players = response.json().get("response", {}).get("players", [])
    return {
        str(player["steamid"]): str(player["personaname"])
        for player in players
        if "steamid" in player and "personaname" in player
    }

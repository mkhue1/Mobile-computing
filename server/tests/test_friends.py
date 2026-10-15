from datetime import datetime, timezone
from unittest.mock import AsyncMock, patch
from uuid import UUID, uuid4

import pytest

from app.helpers.friendship import ordered_friend_pair
from app.models.friendship import FriendRequest, Friendship


@pytest.fixture
def send_request(client, auth_headers):
    def _send_request(sender, receiver):
        return client.post(
            "/friends/requests",
            json={"receiver_id": str(receiver.id)},
            headers=auth_headers(sender),
        )

    return _send_request


@pytest.fixture
def requested(make_user, send_request):
    """A pending friend request from sender to receiver."""
    sender = make_user("sender")
    receiver = make_user("receiver")
    response = send_request(sender, receiver)
    assert response.status_code == 201, response.text
    return sender, receiver, response.json()


# --- Authentication -----------------------------------------------------------


def test_requires_authentication(client):
    response = client.get("/friends/")

    assert response.status_code in (401, 403)


# --- GET /friends/ ------------------------------------------------------------


def test_list_friends_is_empty(client, make_user, auth_headers):
    response = client.get("/friends/", headers=auth_headers(make_user()))

    assert response.status_code == 200
    assert response.json() == []


def test_pending_request_is_not_a_friendship(client, auth_headers, requested):
    sender, _, _ = requested

    response = client.get("/friends/", headers=auth_headers(sender))

    assert response.json() == []


# --- POST /friends/requests ---------------------------------------------------


def test_send_friend_request(requested):
    sender, receiver, body = requested

    assert body["sender_id"] == str(sender.id)
    assert body["receiver_id"] == str(receiver.id)
    assert body["sender"]["username"] == "sender"
    assert body["receiver"]["username"] == "receiver"
    assert body["created_at"] is not None


def test_cannot_friend_yourself(make_user, send_request):
    user = make_user()

    response = send_request(user, user)

    assert response.status_code == 400


def test_cannot_friend_unknown_user(client, make_user, auth_headers):
    response = client.post(
        "/friends/requests",
        json={"receiver_id": str(uuid4())},
        headers=auth_headers(make_user()),
    )

    assert response.status_code == 404


def test_cannot_send_duplicate_request(send_request, requested):
    sender, receiver, _ = requested

    response = send_request(sender, receiver)

    assert response.status_code == 409


def test_cannot_send_request_when_reverse_is_pending(send_request, requested):
    sender, receiver, _ = requested

    response = send_request(receiver, sender)

    assert response.status_code == 409


def test_cannot_send_request_to_existing_friend(make_user, make_friends, send_request):
    user_a = make_user("a")
    user_b = make_user("b")
    make_friends(user_a, user_b)

    response = send_request(user_a, user_b)

    assert response.status_code == 409


# --- GET /friends/requests ----------------------------------------------------


def test_list_incoming_requests(client, auth_headers, requested):
    sender, receiver, request = requested

    incoming = client.get("/friends/requests", headers=auth_headers(receiver)).json()
    sender_incoming = client.get("/friends/requests", headers=auth_headers(sender)).json()

    assert [item["id"] for item in incoming] == [request["id"]]
    assert incoming[0]["sender"]["username"] == "sender"
    assert sender_incoming == []


def test_list_outgoing_requests(client, auth_headers, requested):
    sender, receiver, request = requested

    outgoing = client.get(
        "/friends/requests",
        params={"direction": "outgoing"},
        headers=auth_headers(sender),
    ).json()
    receiver_outgoing = client.get(
        "/friends/requests",
        params={"direction": "outgoing"},
        headers=auth_headers(receiver),
    ).json()

    assert [item["id"] for item in outgoing] == [request["id"]]
    assert receiver_outgoing == []


def test_list_all_requests(client, make_user, auth_headers, send_request):
    user = make_user("user")
    inbound_from = make_user("inbound")
    outbound_to = make_user("outbound")
    inbound = send_request(inbound_from, user).json()
    outbound = send_request(user, outbound_to).json()

    response = client.get(
        "/friends/requests",
        params={"direction": "all"},
        headers=auth_headers(user),
    )

    assert {item["id"] for item in response.json()} == {inbound["id"], outbound["id"]}


def test_list_requests_rejects_unknown_direction(client, make_user, auth_headers):
    response = client.get(
        "/friends/requests",
        params={"direction": "sideways"},
        headers=auth_headers(make_user()),
    )

    assert response.status_code == 422


def test_incoming_request_has_no_steam_relation_when_viewer_unlinked(
    client, auth_headers, requested
):
    _, receiver, _ = requested

    incoming = client.get("/friends/requests", headers=auth_headers(receiver)).json()

    assert incoming[0]["steam_relation"] is None
    assert incoming[0]["steam_persona_name"] is None


def test_incoming_request_marks_requester_without_steam(
    client, make_user, auth_headers, send_request, db
):
    receiver = make_user("receiver")
    sender = make_user("sender")
    receiver.steam_id = "76561198000000001"
    receiver.steam_linked_at = datetime.now(timezone.utc)
    db.commit()
    send_request(sender, receiver)

    incoming = client.get("/friends/requests", headers=auth_headers(receiver)).json()

    assert incoming[0]["steam_relation"] == "not_linked"
    assert incoming[0]["steam_persona_name"] is None


def test_incoming_request_marks_steam_mutual_and_persona(
    client, make_user, auth_headers, send_request, db
):
    receiver = make_user("receiver")
    sender = make_user("sender")
    receiver.steam_id = "76561198000000001"
    sender.steam_id = "76561198000000002"
    receiver.steam_linked_at = datetime.now(timezone.utc)
    sender.steam_linked_at = datetime.now(timezone.utc)
    db.commit()
    send_request(sender, receiver)

    with (
        patch(
            "app.routers.friends.get_friend_steam_ids",
            new=AsyncMock(return_value=["76561198000000002"]),
        ),
        patch(
            "app.routers.friends.get_steam_persona_names",
            new=AsyncMock(return_value={"76561198000000002": "SteamSender"}),
        ),
    ):
        incoming = client.get(
            "/friends/requests", headers=auth_headers(receiver)
        ).json()

    assert incoming[0]["steam_relation"] == "mutual"
    assert incoming[0]["steam_persona_name"] == "SteamSender"


def test_incoming_request_marks_linked_but_not_steam_friends(
    client, make_user, auth_headers, send_request, db
):
    receiver = make_user("receiver")
    sender = make_user("sender")
    receiver.steam_id = "76561198000000001"
    sender.steam_id = "76561198000000002"
    receiver.steam_linked_at = datetime.now(timezone.utc)
    sender.steam_linked_at = datetime.now(timezone.utc)
    db.commit()
    send_request(sender, receiver)

    with patch(
        "app.routers.friends.get_friend_steam_ids",
        new=AsyncMock(return_value=["76561198000000999"]),
    ):
        incoming = client.get(
            "/friends/requests", headers=auth_headers(receiver)
        ).json()

    assert incoming[0]["steam_relation"] == "not_friends"
    assert incoming[0]["steam_persona_name"] is None


# --- POST /friends/requests/{request_id}/accept -------------------------------


def test_accept_friend_request(client, db, auth_headers, requested):
    sender, receiver, request = requested

    response = client.post(
        f"/friends/requests/{request['id']}/accept",
        headers=auth_headers(receiver),
    )

    assert response.status_code == 200
    low, high = ordered_friend_pair(sender.id, receiver.id)
    body = response.json()
    assert body["user_id"] == str(low)
    assert body["friend_id"] == str(high)

    db.expire_all()
    assert db.get(FriendRequest, UUID(request["id"])) is None
    assert db.get(Friendship, (low, high)) is not None

    sender_friends = client.get("/friends/", headers=auth_headers(sender)).json()
    receiver_friends = client.get("/friends/", headers=auth_headers(receiver)).json()
    assert [user["id"] for user in sender_friends] == [str(receiver.id)]
    assert [user["id"] for user in receiver_friends] == [str(sender.id)]
    assert client.get("/friends/requests", headers=auth_headers(receiver)).json() == []


def test_only_receiver_can_accept(client, auth_headers, requested):
    sender, _, request = requested

    response = client.post(
        f"/friends/requests/{request['id']}/accept",
        headers=auth_headers(sender),
    )

    assert response.status_code == 403


def test_accept_unknown_request(client, make_user, auth_headers):
    response = client.post(
        f"/friends/requests/{uuid4()}/accept",
        headers=auth_headers(make_user()),
    )

    assert response.status_code == 404


def test_accept_when_already_friends_clears_the_request(
    client, db, make_user, auth_headers, make_friends
):
    sender = make_user("sender")
    receiver = make_user("receiver")
    make_friends(sender, receiver)
    request = FriendRequest(sender_id=sender.id, receiver_id=receiver.id)
    db.add(request)
    db.commit()
    request_id = request.id

    response = client.post(
        f"/friends/requests/{request_id}/accept",
        headers=auth_headers(receiver),
    )

    assert response.status_code == 409
    db.expunge_all()
    assert db.get(FriendRequest, request_id) is None


def test_accept_also_clears_a_reverse_request(client, db, auth_headers, requested):
    sender, receiver, request = requested
    reverse = FriendRequest(sender_id=receiver.id, receiver_id=sender.id)
    db.add(reverse)
    db.commit()
    reverse_id = reverse.id

    response = client.post(
        f"/friends/requests/{request['id']}/accept",
        headers=auth_headers(receiver),
    )

    assert response.status_code == 200, response.text
    db.expunge_all()
    assert db.get(FriendRequest, reverse_id) is None


# --- POST /friends/requests/{request_id}/decline ------------------------------


def test_decline_friend_request(client, db, auth_headers, requested):
    sender, receiver, request = requested

    response = client.post(
        f"/friends/requests/{request['id']}/decline",
        headers=auth_headers(receiver),
    )

    assert response.status_code == 204
    db.expire_all()
    assert db.get(FriendRequest, UUID(request["id"])) is None
    assert client.get("/friends/", headers=auth_headers(sender)).json() == []


def test_only_receiver_can_decline(client, db, auth_headers, requested):
    sender, _, request = requested

    response = client.post(
        f"/friends/requests/{request['id']}/decline",
        headers=auth_headers(sender),
    )

    assert response.status_code == 403
    db.expire_all()
    assert db.get(FriendRequest, UUID(request["id"])) is not None


def test_decline_unknown_request(client, make_user, auth_headers):
    response = client.post(
        f"/friends/requests/{uuid4()}/decline",
        headers=auth_headers(make_user()),
    )

    assert response.status_code == 404


# --- DELETE /friends/{friend_id} ----------------------------------------------


def test_remove_friend(client, db, make_user, auth_headers, make_friends):
    user_a = make_user("a")
    user_b = make_user("b")
    friendship = make_friends(user_a, user_b)
    friendship_key = (friendship.user_id, friendship.friend_id)

    response = client.delete(f"/friends/{user_b.id}", headers=auth_headers(user_a))

    assert response.status_code == 204
    db.expunge_all()
    assert db.get(Friendship, friendship_key) is None
    assert client.get("/friends/", headers=auth_headers(user_a)).json() == []
    assert client.get("/friends/", headers=auth_headers(user_b)).json() == []


def test_remove_unknown_user(client, make_user, auth_headers):
    response = client.delete(f"/friends/{uuid4()}", headers=auth_headers(make_user()))

    assert response.status_code == 404


def test_remove_user_who_is_not_a_friend(client, make_user, auth_headers):
    user = make_user("user")
    stranger = make_user("stranger")

    response = client.delete(f"/friends/{stranger.id}", headers=auth_headers(user))

    assert response.status_code == 404

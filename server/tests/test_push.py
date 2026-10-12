from datetime import datetime, timedelta, timezone
from types import SimpleNamespace

import pytest
from firebase_admin import messaging
from sqlalchemy import select

from app.models.device_token import DeviceToken
from app.services import push


@pytest.fixture
def sent(monkeypatch):
    """Records push notifications instead of sending them."""
    calls = []
    monkeypatch.setattr(push, "send_to_user", lambda user_id, data: calls.append((user_id, data)))
    return calls


def session_payload(game, **overrides):
    start_at = datetime.now(timezone.utc) + timedelta(days=1)
    payload = {
        "game_id": str(game.id),
        "title": "Friday night",
        "start_at": start_at.isoformat(),
        "end_at": (start_at + timedelta(hours=2)).isoformat(),
        "session_type": "online",
        "visibility": "private",
    }
    payload.update(overrides)
    return payload


def tokens_of(db, user):
    db.expire_all()
    return set(db.scalars(select(DeviceToken.token).where(DeviceToken.user_id == user.id)))


# --- PUT /users/me/device-tokens ----------------------------------------------


def test_register_device_token(client, db, make_user, auth_headers):
    user = make_user()

    response = client.put("/users/me/device-tokens", json={"token": "phone-1"}, headers=auth_headers(user))

    assert response.status_code == 204
    assert tokens_of(db, user) == {"phone-1"}


def test_register_same_token_twice_keeps_one(client, db, make_user, auth_headers):
    user = make_user()

    for _ in range(2):
        response = client.put("/users/me/device-tokens", json={"token": "phone-1"}, headers=auth_headers(user))
        assert response.status_code == 204

    assert tokens_of(db, user) == {"phone-1"}


def test_register_token_moves_it_to_the_new_user(client, db, make_user, auth_headers):
    first = make_user("first")
    second = make_user("second")
    client.put("/users/me/device-tokens", json={"token": "shared-phone"}, headers=auth_headers(first))

    client.put("/users/me/device-tokens", json={"token": "shared-phone"}, headers=auth_headers(second))

    assert tokens_of(db, first) == set()
    assert tokens_of(db, second) == {"shared-phone"}


def test_register_rejects_empty_token(client, make_user, auth_headers):
    response = client.put("/users/me/device-tokens", json={"token": ""}, headers=auth_headers(make_user()))

    assert response.status_code == 422


def test_register_requires_authentication(client):
    response = client.put("/users/me/device-tokens", json={"token": "phone-1"})

    assert response.status_code in (401, 403)


# --- DELETE /users/me/device-tokens/{token} -----------------------------------


def test_remove_device_token(client, db, make_user, auth_headers):
    user = make_user()
    client.put("/users/me/device-tokens", json={"token": "phone-1"}, headers=auth_headers(user))

    response = client.delete("/users/me/device-tokens/phone-1", headers=auth_headers(user))

    assert response.status_code == 204
    assert tokens_of(db, user) == set()


def test_remove_only_affects_own_tokens(client, db, make_user, auth_headers):
    owner = make_user("owner")
    other = make_user("other")
    client.put("/users/me/device-tokens", json={"token": "owners-phone"}, headers=auth_headers(owner))

    response = client.delete("/users/me/device-tokens/owners-phone", headers=auth_headers(other))

    assert response.status_code == 204
    assert tokens_of(db, owner) == {"owners-phone"}


def test_remove_unknown_token_succeeds(client, make_user, auth_headers):
    response = client.delete("/users/me/device-tokens/never-registered", headers=auth_headers(make_user()))

    assert response.status_code == 204


# --- Notifications sent by other endpoints ------------------------------------


def test_friend_request_notifies_receiver(client, make_user, auth_headers, sent):
    sender = make_user("sender")
    receiver = make_user("receiver")

    response = client.post(
        "/friends/requests",
        json={"receiver_id": str(receiver.id)},
        headers=auth_headers(sender),
    )

    assert response.status_code == 201
    assert len(sent) == 1
    user_id, data = sent[0]
    assert user_id == receiver.id
    assert data["type"] == "friend_request"
    assert data["id"] == response.json()["id"]
    assert "sender" in data["body"]


def test_invite_notifies_invitee(client, make_user, auth_headers, game, sent):
    organiser = make_user("organiser")
    invitee = make_user("invitee")
    session = client.post("/sessions/create", json=session_payload(game), headers=auth_headers(organiser)).json()

    response = client.post(
        f"/sessions/{session['id']}/invite",
        json={"session_id": session["id"], "receiver_id": str(invitee.id)},
        headers=auth_headers(organiser),
    )

    assert response.status_code == 201
    assert len(sent) == 1
    user_id, data = sent[0]
    assert user_id == invitee.id
    assert data["type"] == "session_invite"
    assert data["session_id"] == session["id"]
    assert data["id"] == response.json()["id"]
    assert "Friday night" in data["body"]


def test_reopened_invite_notifies_again(client, make_user, auth_headers, game, sent):
    organiser = make_user("organiser")
    invitee = make_user("invitee")
    session = client.post("/sessions/create", json=session_payload(game), headers=auth_headers(organiser)).json()
    invite = client.post(
        f"/sessions/{session['id']}/invite",
        json={"session_id": session["id"], "receiver_id": str(invitee.id)},
        headers=auth_headers(organiser),
    ).json()
    client.post(
        f"/sessions/{session['id']}/decline",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    response = client.post(
        f"/sessions/{session['id']}/invite",
        json={"session_id": session["id"], "receiver_id": str(invitee.id)},
        headers=auth_headers(organiser),
    )

    assert response.status_code == 201
    assert [user_id for user_id, _ in sent] == [invitee.id, invitee.id]


def test_group_session_notifies_other_members(client, make_user, make_group, auth_headers, game, sent):
    organiser = make_user("organiser")
    member_a = make_user("member_a")
    member_b = make_user("member_b")
    group = make_group(organiser, members=(member_a, member_b))

    response = client.post(
        "/sessions/create",
        json=session_payload(game, visibility="group", group_id=str(group.id)),
        headers=auth_headers(organiser),
    )

    assert response.status_code == 201
    assert {user_id for user_id, _ in sent} == {member_a.id, member_b.id}
    assert all(data["type"] == "session_invite" for _, data in sent)


def test_untitled_session_invite_uses_game_name(client, make_user, auth_headers, game, sent):
    organiser = make_user("organiser")
    invitee = make_user("invitee")
    session = client.post(
        "/sessions/create", json=session_payload(game, title=None), headers=auth_headers(organiser)
    ).json()

    client.post(
        f"/sessions/{session['id']}/invite",
        json={"session_id": session["id"], "receiver_id": str(invitee.id)},
        headers=auth_headers(organiser),
    )

    assert game.name in sent[0][1]["body"]


def test_private_session_without_group_notifies_nobody(client, make_user, auth_headers, game, sent):
    client.post("/sessions/create", json=session_payload(game), headers=auth_headers(make_user()))

    assert sent == []


# --- push.send_to_user --------------------------------------------------------


def test_send_to_user_does_nothing_without_credentials(db, make_user, monkeypatch):
    user = make_user()
    db.add(DeviceToken(token="phone-1", user_id=user.id))
    db.commit()
    monkeypatch.setattr(
        push.messaging,
        "send_each_for_multicast",
        lambda *args, **kwargs: pytest.fail("nothing should be sent without credentials"),
    )

    push.send_to_user(user.id, {"type": "friend_request"})


def test_send_to_user_removes_unregistered_tokens(db, make_user, monkeypatch):
    user = make_user()
    db.add_all([
        DeviceToken(token="live-token", user_id=user.id),
        DeviceToken(token="stale-token", user_id=user.id),
    ])
    db.commit()
    sent_messages = []

    def fake_send(message, app):
        sent_messages.append(message)
        return SimpleNamespace(responses=[
            SimpleNamespace(
                exception=messaging.UnregisteredError("gone") if token == "stale-token" else None
            )
            for token in message.tokens
        ])

    monkeypatch.setattr(push, "_get_firebase_app", lambda: object())
    monkeypatch.setattr(push.messaging, "send_each_for_multicast", fake_send)

    push.send_to_user(user.id, {"type": "friend_request", "id": "1"})

    assert set(sent_messages[0].tokens) == {"live-token", "stale-token"}
    assert sent_messages[0].data == {"type": "friend_request", "id": "1"}
    assert tokens_of(db, user) == {"live-token"}


def test_send_to_user_swallows_firebase_errors(db, make_user, monkeypatch):
    user = make_user()
    db.add(DeviceToken(token="phone-1", user_id=user.id))
    db.commit()

    def failing_send(message, app):
        raise RuntimeError("Firebase is down")

    monkeypatch.setattr(push, "_get_firebase_app", lambda: object())
    monkeypatch.setattr(push.messaging, "send_each_for_multicast", failing_send)

    push.send_to_user(user.id, {"type": "friend_request"})  # must not raise

    assert tokens_of(db, user) == {"phone-1"}

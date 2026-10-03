from datetime import datetime, timedelta, timezone
from uuid import UUID, uuid4

import pytest
from sqlalchemy import select

from app.models.gaming_session import (
    GamingSession,
    InviteStatus,
    SessionInvite,
    SessionParticipant,
    SessionStatus,
)


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


@pytest.fixture
def create_session(client, auth_headers, game):
    def _create_session(organiser, **overrides):
        response = client.post(
            "/sessions/create",
            json=session_payload(game, **overrides),
            headers=auth_headers(organiser),
        )
        assert response.status_code == 201, response.text
        return response.json()

    return _create_session


@pytest.fixture
def send_invite(client, auth_headers):
    def _send_invite(sender, session_id, receiver):
        return client.post(
            f"/sessions/{session_id}/invite",
            json={"session_id": session_id, "receiver_id": str(receiver.id)},
            headers=auth_headers(sender),
        )

    return _send_invite


@pytest.fixture
def invited(make_user, create_session, send_invite):
    """An organiser, a session they created, and a pending invite to a second user."""
    organiser = make_user("organiser")
    invitee = make_user("invitee")
    session = create_session(organiser)

    response = send_invite(organiser, session["id"], invitee)
    assert response.status_code == 201, response.text

    return organiser, invitee, session, response.json()


# --- Authentication -----------------------------------------------------------


def test_requires_authentication(client):
    response = client.get("/sessions/")

    # HTTPBearer rejects a missing header; the exact code differs between FastAPI versions.
    assert response.status_code in (401, 403)


def test_rejects_invalid_token(client):
    response = client.get("/sessions/", headers={"Authorization": "Bearer not-a-jwt"})

    assert response.status_code == 401


# --- POST /sessions/create ----------------------------------------------------


def test_create_session(client, db, make_user, auth_headers, game):
    organiser = make_user()

    response = client.post(
        "/sessions/create",
        json=session_payload(game),
        headers=auth_headers(organiser),
    )

    assert response.status_code == 201
    body = response.json()
    assert body["organiser_id"] == str(organiser.id)
    assert body["status"] == "open"
    assert body["player_count"] == 1

    participant = db.get(SessionParticipant, (UUID(body["id"]), organiser.id))
    assert participant is not None


def test_create_session_ignores_client_supplied_status(make_user, create_session):
    session = create_session(make_user(), status="cancelled")

    assert session["status"] == "open"


def test_create_group_session_requires_group_id(client, make_user, auth_headers, game):
    response = client.post(
        "/sessions/create",
        json=session_payload(game, visibility="group"),
        headers=auth_headers(make_user()),
    )

    assert response.status_code == 400


def test_create_session_with_unknown_group(client, make_user, auth_headers, game):
    response = client.post(
        "/sessions/create",
        json=session_payload(game, visibility="group", group_id=str(uuid4())),
        headers=auth_headers(make_user()),
    )

    assert response.status_code == 404


def test_create_session_for_group_user_is_not_in(
    client, make_user, make_group, auth_headers, game
):
    group = make_group(make_user("owner"))
    outsider = make_user("outsider")

    response = client.post(
        "/sessions/create",
        json=session_payload(game, visibility="group", group_id=str(group.id)),
        headers=auth_headers(outsider),
    )

    assert response.status_code == 403


def test_create_group_session_invites_other_members(
    db, make_user, make_group, create_session
):
    organiser = make_user("organiser")
    member_a = make_user("member_a")
    member_b = make_user("member_b")
    group = make_group(organiser, members=(member_a, member_b))

    session = create_session(organiser, visibility="group", group_id=str(group.id))

    invites = db.scalars(
        select(SessionInvite).where(SessionInvite.session_id == UUID(session["id"]))
    ).all()
    assert {invite.receiver_id for invite in invites} == {member_a.id, member_b.id}
    assert all(invite.sender_id == organiser.id for invite in invites)
    assert all(invite.status == InviteStatus.PENDING for invite in invites)


# --- GET /sessions/ -----------------------------------------------------------


def test_get_sessions_only_returns_own_sessions(
    client, make_user, auth_headers, create_session
):
    organiser = make_user("organiser")
    stranger = make_user("stranger")
    session = create_session(organiser)

    organiser_sessions = client.get("/sessions/", headers=auth_headers(organiser)).json()
    stranger_sessions = client.get("/sessions/", headers=auth_headers(stranger)).json()

    assert [s["id"] for s in organiser_sessions] == [session["id"]]
    assert stranger_sessions == []


# --- GET /sessions/{session_id} -----------------------------------------------


def test_organiser_can_view_session(client, make_user, auth_headers, create_session):
    organiser = make_user()
    session = create_session(organiser)

    response = client.get(f"/sessions/{session['id']}", headers=auth_headers(organiser))

    assert response.status_code == 200
    assert response.json()["id"] == session["id"]


def test_private_session_is_hidden_from_strangers(
    client, make_user, auth_headers, create_session
):
    session = create_session(make_user("organiser"), visibility="private")

    response = client.get(
        f"/sessions/{session['id']}", headers=auth_headers(make_user("stranger"))
    )

    assert response.status_code == 404


def test_public_session_is_visible_to_strangers(
    client, make_user, auth_headers, create_session
):
    session = create_session(make_user("organiser"), visibility="public")

    response = client.get(
        f"/sessions/{session['id']}", headers=auth_headers(make_user("stranger"))
    )

    assert response.status_code == 200


def test_invitee_can_view_private_session(client, auth_headers, invited):
    _, invitee, session, _ = invited

    response = client.get(f"/sessions/{session['id']}", headers=auth_headers(invitee))

    assert response.status_code == 200


def test_get_unknown_session(client, make_user, auth_headers):
    response = client.get(f"/sessions/{uuid4()}", headers=auth_headers(make_user()))

    assert response.status_code == 404


# --- GET /sessions/{session_id}/participants ----------------------------------


def test_get_participants(client, auth_headers, invited):
    organiser, invitee, session, invite = invited
    client.post(
        f"/sessions/{session['id']}/accept",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    response = client.get(
        f"/sessions/{session['id']}/participants", headers=auth_headers(organiser)
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert [p["user"]["id"] for p in body] == [str(organiser.id), str(invitee.id)]
    assert body[0]["user"]["username"] == "organiser"
    assert body[0]["joined_at"] is not None


def test_participants_exclude_pending_invitees(client, auth_headers, invited):
    organiser, _, session, _ = invited

    response = client.get(
        f"/sessions/{session['id']}/participants", headers=auth_headers(organiser)
    )

    assert [p["user"]["id"] for p in response.json()] == [str(organiser.id)]


def test_invitee_can_view_participants(client, auth_headers, invited):
    _, invitee, session, _ = invited

    response = client.get(
        f"/sessions/{session['id']}/participants", headers=auth_headers(invitee)
    )

    assert response.status_code == 200


def test_participants_of_private_session_hidden_from_strangers(
    client, make_user, auth_headers, create_session
):
    session = create_session(make_user("organiser"), visibility="private")

    response = client.get(
        f"/sessions/{session['id']}/participants", headers=auth_headers(make_user("stranger"))
    )

    assert response.status_code == 404


def test_participants_of_unknown_session(client, make_user, auth_headers):
    response = client.get(f"/sessions/{uuid4()}/participants", headers=auth_headers(make_user()))

    assert response.status_code == 404


# --- PATCH /sessions/{session_id} ---------------------------------------------


def test_update_session(client, db, make_user, auth_headers, create_session):
    organiser = make_user()
    session = create_session(organiser, description="Bring snacks")
    new_start = datetime.now(timezone.utc) + timedelta(days=3)

    response = client.patch(
        f"/sessions/{session['id']}",
        json={
            "title": "Saturday night",
            "start_at": new_start.isoformat(),
            "end_at": (new_start + timedelta(hours=4)).isoformat(),
            "player_limit": 6,
        },
        headers=auth_headers(organiser),
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["title"] == "Saturday night"
    assert datetime.fromisoformat(body["start_at"]) == new_start
    assert body["player_limit"] == 6
    # Fields left out of the request are unchanged.
    assert body["description"] == "Bring snacks"
    assert body["visibility"] == "private"

    stored = db.get(GamingSession, UUID(session["id"]))
    assert stored.title == "Saturday night"


def test_update_session_null_clears_optional_field(
    client, make_user, auth_headers, create_session
):
    organiser = make_user()
    session = create_session(organiser, description="Bring snacks", player_limit=4)

    response = client.patch(
        f"/sessions/{session['id']}",
        json={"description": None, "player_limit": None},
        headers=auth_headers(organiser),
    )

    assert response.status_code == 200, response.text
    assert response.json()["description"] is None
    assert response.json()["player_limit"] is None


def test_update_session_with_full_form(client, make_user, auth_headers, game, create_session):
    """The app's edit screen sends every field, including nulls, so a create payload must be accepted."""
    organiser = make_user()
    session = create_session(organiser)
    payload = session_payload(
        game,
        title=None,
        description=None,
        group_id=None,
        location_name="Alex's place",
        session_type="in_person",
        player_limit=None,
    )

    response = client.patch(
        f"/sessions/{session['id']}", json=payload, headers=auth_headers(organiser)
    )

    assert response.status_code == 200, response.text
    assert response.json()["title"] is None
    assert response.json()["session_type"] == "in_person"
    assert response.json()["location_name"] == "Alex's place"


def test_only_organiser_can_update(client, auth_headers, invited):
    _, invitee, session, _ = invited

    response = client.patch(
        f"/sessions/{session['id']}", json={"title": "Hijacked"}, headers=auth_headers(invitee)
    )

    assert response.status_code == 403


def test_update_unknown_session(client, make_user, auth_headers):
    response = client.patch(
        f"/sessions/{uuid4()}", json={"title": "Nope"}, headers=auth_headers(make_user())
    )

    assert response.status_code == 404


def test_cannot_update_cancelled_session(client, make_user, auth_headers, create_session):
    organiser = make_user()
    session = create_session(organiser)
    client.post(f"/sessions/{session['id']}/cancel", headers=auth_headers(organiser))

    response = client.patch(
        f"/sessions/{session['id']}", json={"title": "Back on"}, headers=auth_headers(organiser)
    )

    assert response.status_code == 409


def test_update_rejects_end_before_start(client, make_user, auth_headers, create_session):
    organiser = make_user()
    session = create_session(organiser)
    # Only end_at is sent, so it's checked against the stored start time.
    earlier = datetime.fromisoformat(session["start_at"]) - timedelta(hours=1)

    response = client.patch(
        f"/sessions/{session['id']}",
        json={"end_at": earlier.isoformat()},
        headers=auth_headers(organiser),
    )

    assert response.status_code == 400


def test_update_rejects_timezone_naive_times(client, make_user, auth_headers, create_session):
    organiser = make_user()
    session = create_session(organiser)

    response = client.patch(
        f"/sessions/{session['id']}",
        json={"start_at": "2030-01-01T18:00:00"},
        headers=auth_headers(organiser),
    )

    assert response.status_code == 422


@pytest.mark.parametrize("field", ["game_id", "start_at", "end_at", "session_type", "visibility"])
def test_update_rejects_null_required_field(
    client, make_user, auth_headers, create_session, field
):
    organiser = make_user()
    session = create_session(organiser)

    response = client.patch(
        f"/sessions/{session['id']}", json={field: None}, headers=auth_headers(organiser)
    )

    assert response.status_code == 422


def test_update_rejects_non_positive_player_limit(
    client, make_user, auth_headers, create_session
):
    organiser = make_user()
    session = create_session(organiser)

    response = client.patch(
        f"/sessions/{session['id']}", json={"player_limit": 0}, headers=auth_headers(organiser)
    )

    assert response.status_code == 422


def test_update_player_limit_below_player_count(client, auth_headers, invited):
    organiser, invitee, session, invite = invited
    client.post(
        f"/sessions/{session['id']}/accept",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    response = client.patch(
        f"/sessions/{session['id']}", json={"player_limit": 1}, headers=auth_headers(organiser)
    )

    assert response.status_code == 409


def test_update_between_non_group_visibilities(
    client, make_user, auth_headers, create_session
):
    organiser = make_user()
    session = create_session(organiser, visibility="private")

    response = client.patch(
        f"/sessions/{session['id']}", json={"visibility": "public"}, headers=auth_headers(organiser)
    )

    assert response.status_code == 200, response.text
    assert response.json()["visibility"] == "public"


def test_cannot_move_session_into_group(
    client, db, make_user, make_group, auth_headers, create_session
):
    organiser = make_user("organiser")
    group = make_group(organiser)
    session = create_session(organiser)

    response = client.patch(
        f"/sessions/{session['id']}",
        json={"visibility": "group", "group_id": str(group.id)},
        headers=auth_headers(organiser),
    )

    assert response.status_code == 400
    assert db.get(GamingSession, UUID(session["id"])).group_id is None


@pytest.mark.parametrize("changes", [{"visibility": "public"}, {"visibility": "public", "group_id": None}])
def test_cannot_move_session_out_of_group(
    client, make_user, make_group, auth_headers, create_session, changes
):
    organiser = make_user("organiser")
    group = make_group(organiser)
    session = create_session(organiser, visibility="group", group_id=str(group.id))

    response = client.patch(
        f"/sessions/{session['id']}", json=changes, headers=auth_headers(organiser)
    )

    assert response.status_code == 400


def test_cannot_change_session_group(
    client, make_user, make_group, auth_headers, create_session
):
    organiser = make_user("organiser")
    group = make_group(organiser)
    other_group = make_group(organiser)
    session = create_session(organiser, visibility="group", group_id=str(group.id))

    response = client.patch(
        f"/sessions/{session['id']}",
        json={"group_id": str(other_group.id)},
        headers=auth_headers(organiser),
    )

    assert response.status_code == 400


def test_update_group_session_resending_same_group(
    client, make_user, make_group, auth_headers, game, create_session
):
    """The edit form sends the group back unchanged, which must be accepted."""
    organiser = make_user("organiser")
    group = make_group(organiser)
    session = create_session(organiser, visibility="group", group_id=str(group.id))

    response = client.patch(
        f"/sessions/{session['id']}",
        json=session_payload(game, title="Renamed", visibility="group", group_id=str(group.id)),
        headers=auth_headers(organiser),
    )

    assert response.status_code == 200, response.text
    assert response.json()["title"] == "Renamed"
    assert response.json()["group_id"] == str(group.id)


# --- POST /sessions/{session_id}/cancel ---------------------------------------


def test_cancel_session(client, db, auth_headers, invited):
    organiser, _, session, invite = invited

    response = client.post(f"/sessions/{session['id']}/cancel", headers=auth_headers(organiser))

    assert response.status_code == 200
    assert response.json()["status"] is True

    assert db.get(GamingSession, UUID(session["id"])).status == SessionStatus.CANCELLED
    cancelled_invite = db.get(SessionInvite, UUID(invite["id"]))
    assert cancelled_invite.status == InviteStatus.CANCELLED
    assert cancelled_invite.responded_at is not None


def test_only_organiser_can_cancel(client, auth_headers, invited):
    _, invitee, session, _ = invited

    response = client.post(f"/sessions/{session['id']}/cancel", headers=auth_headers(invitee))

    assert response.status_code == 403


def test_cannot_cancel_twice(client, make_user, auth_headers, create_session):
    organiser = make_user()
    session = create_session(organiser)

    client.post(f"/sessions/{session['id']}/cancel", headers=auth_headers(organiser))
    response = client.post(f"/sessions/{session['id']}/cancel", headers=auth_headers(organiser))

    assert response.status_code == 409


def test_cancel_unknown_session(client, make_user, auth_headers):
    response = client.post(f"/sessions/{uuid4()}/cancel", headers=auth_headers(make_user()))

    assert response.status_code == 404


# --- POST /sessions/{session_id}/leave ----------------------------------------


@pytest.fixture
def joined(client, auth_headers, invited):
    """Like `invited`, but the invitee has accepted and is a participant."""
    organiser, invitee, session, invite = invited
    response = client.post(
        f"/sessions/{session['id']}/accept",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )
    assert response.status_code == 200, response.text
    return organiser, invitee, session


def test_leave_session(client, db, auth_headers, joined):
    _, player, session = joined

    response = client.post(f"/sessions/{session['id']}/leave", headers=auth_headers(player))

    assert response.status_code == 204
    assert db.get(SessionParticipant, (UUID(session["id"]), player.id)) is None
    assert db.get(GamingSession, UUID(session["id"])).player_count == 1
    assert client.get("/sessions/", headers=auth_headers(player)).json() == []


def test_leaving_private_session_removes_access(client, auth_headers, joined):
    _, player, session = joined

    client.post(f"/sessions/{session['id']}/leave", headers=auth_headers(player))

    response = client.get(f"/sessions/{session['id']}", headers=auth_headers(player))
    assert response.status_code == 404


def test_organiser_can_reinvite_after_leaving(auth_headers, client, send_invite, joined):
    organiser, player, session = joined
    client.post(f"/sessions/{session['id']}/leave", headers=auth_headers(player))

    response = send_invite(organiser, session["id"], player)

    assert response.status_code == 201, response.text


def test_organiser_cannot_leave(client, db, auth_headers, joined):
    organiser, _, session = joined

    response = client.post(f"/sessions/{session['id']}/leave", headers=auth_headers(organiser))

    assert response.status_code == 400
    assert db.get(SessionParticipant, (UUID(session["id"]), organiser.id)) is not None


def test_cannot_leave_session_not_in(client, db, auth_headers, invited):
    _, invitee, session, invite = invited

    response = client.post(f"/sessions/{session['id']}/leave", headers=auth_headers(invitee))

    assert response.status_code == 404
    # A pending invite isn't touched by a failed leave.
    assert db.get(SessionInvite, UUID(invite["id"])).status == InviteStatus.PENDING


def test_cannot_leave_cancelled_session(client, auth_headers, joined):
    organiser, player, session = joined
    client.post(f"/sessions/{session['id']}/cancel", headers=auth_headers(organiser))

    response = client.post(f"/sessions/{session['id']}/leave", headers=auth_headers(player))

    assert response.status_code == 409


def test_leave_unknown_session(client, make_user, auth_headers):
    response = client.post(f"/sessions/{uuid4()}/leave", headers=auth_headers(make_user()))

    assert response.status_code == 404


# --- DELETE /sessions/{session_id}/participants/{user_id} ---------------------


def test_remove_participant(client, db, auth_headers, joined):
    organiser, player, session = joined

    response = client.delete(
        f"/sessions/{session['id']}/participants/{player.id}", headers=auth_headers(organiser)
    )

    assert response.status_code == 204
    assert db.get(SessionParticipant, (UUID(session["id"]), player.id)) is None
    assert db.get(GamingSession, UUID(session["id"])).player_count == 1
    # The removed player loses access to the private session.
    assert client.get(f"/sessions/{session['id']}", headers=auth_headers(player)).status_code == 404


def test_removed_player_can_be_reinvited(client, auth_headers, send_invite, joined):
    organiser, player, session = joined
    client.delete(
        f"/sessions/{session['id']}/participants/{player.id}", headers=auth_headers(organiser)
    )

    response = send_invite(organiser, session["id"], player)

    assert response.status_code == 201, response.text


def test_only_organiser_can_remove_participants(client, db, make_user, auth_headers, create_session, send_invite):
    organiser = make_user("organiser")
    player_a = make_user("player_a")
    player_b = make_user("player_b")
    session = create_session(organiser)
    for player in (player_a, player_b):
        invite = send_invite(organiser, session["id"], player).json()
        client.post(
            f"/sessions/{session['id']}/accept",
            json={"invite_id": invite["id"]},
            headers=auth_headers(player),
        )

    response = client.delete(
        f"/sessions/{session['id']}/participants/{player_b.id}", headers=auth_headers(player_a)
    )

    assert response.status_code == 403
    assert db.get(SessionParticipant, (UUID(session["id"]), player_b.id)) is not None


def test_organiser_cannot_remove_themselves(client, auth_headers, joined):
    organiser, _, session = joined

    response = client.delete(
        f"/sessions/{session['id']}/participants/{organiser.id}", headers=auth_headers(organiser)
    )

    assert response.status_code == 400


def test_cannot_remove_from_cancelled_session(client, auth_headers, joined):
    organiser, player, session = joined
    client.post(f"/sessions/{session['id']}/cancel", headers=auth_headers(organiser))

    response = client.delete(
        f"/sessions/{session['id']}/participants/{player.id}", headers=auth_headers(organiser)
    )

    assert response.status_code == 409


def test_remove_user_not_in_session(client, make_user, auth_headers, joined):
    organiser, _, session = joined

    response = client.delete(
        f"/sessions/{session['id']}/participants/{make_user('stranger').id}",
        headers=auth_headers(organiser),
    )

    assert response.status_code == 404


def test_remove_from_unknown_session(client, make_user, auth_headers):
    user = make_user()

    response = client.delete(
        f"/sessions/{uuid4()}/participants/{uuid4()}", headers=auth_headers(user)
    )

    assert response.status_code == 404


# --- POST /sessions/{session_id}/invite ---------------------------------------


def test_create_invite(invited):
    organiser, invitee, session, invite = invited

    assert invite["session_id"] == session["id"]
    assert invite["sender_id"] == str(organiser.id)
    assert invite["receiver_id"] == str(invitee.id)
    assert invite["status"] == "pending"


def test_only_organiser_can_invite(make_user, create_session, send_invite):
    session = create_session(make_user("organiser"))

    response = send_invite(make_user("stranger"), session["id"], make_user("target"))

    assert response.status_code == 403


def test_cannot_invite_same_user_twice(send_invite, invited):
    organiser, invitee, session, _ = invited

    response = send_invite(organiser, session["id"], invitee)

    assert response.status_code == 409


def test_invite_to_unknown_session(make_user, send_invite):
    response = send_invite(make_user("organiser"), str(uuid4()), make_user("target"))

    assert response.status_code == 404


def test_reinvite_after_decline(client, auth_headers, send_invite, invited):
    organiser, invitee, session, invite = invited
    client.post(
        f"/sessions/{session['id']}/decline",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    response = send_invite(organiser, session["id"], invitee)

    assert response.status_code == 201, response.text
    body = response.json()
    assert body["id"] == invite["id"]
    assert body["status"] == "pending"
    assert body["responded_at"] is None

    pending = client.get("/sessions/invites", headers=auth_headers(invitee)).json()
    assert [i["id"] for i in pending] == [invite["id"]]


def test_reopened_invite_can_be_accepted(client, db, auth_headers, send_invite, invited):
    organiser, invitee, session, invite = invited
    request = dict(json={"invite_id": invite["id"]}, headers=auth_headers(invitee))
    client.post(f"/sessions/{session['id']}/decline", **request)
    send_invite(organiser, session["id"], invitee)

    response = client.post(f"/sessions/{session['id']}/accept", **request)

    assert response.status_code == 200, response.text
    assert db.get(SessionParticipant, (UUID(session["id"]), invitee.id)) is not None


def test_cannot_invite_to_cancelled_session(
    client, make_user, auth_headers, create_session, send_invite
):
    organiser = make_user("organiser")
    session = create_session(organiser)
    client.post(f"/sessions/{session['id']}/cancel", headers=auth_headers(organiser))

    response = send_invite(organiser, session["id"], make_user("target"))

    assert response.status_code == 409


def test_cannot_invite_self(make_user, create_session, send_invite):
    organiser = make_user("organiser")
    session = create_session(organiser)

    response = send_invite(organiser, session["id"], organiser)

    assert response.status_code == 400


def test_cannot_invite_unknown_user(client, make_user, auth_headers, create_session):
    organiser = make_user("organiser")
    session = create_session(organiser)

    response = client.post(
        f"/sessions/{session['id']}/invite",
        json={"session_id": session["id"], "receiver_id": str(uuid4())},
        headers=auth_headers(organiser),
    )

    assert response.status_code == 404


def test_cannot_invite_existing_player(send_invite, joined):
    organiser, player, session = joined

    response = send_invite(organiser, session["id"], player)

    assert response.status_code == 409


def test_invite_rejects_mismatched_session_ids(
    client, make_user, auth_headers, create_session
):
    organiser = make_user("organiser")
    session = create_session(organiser)
    other_session = create_session(organiser)

    response = client.post(
        f"/sessions/{session['id']}/invite",
        json={"session_id": other_session["id"], "receiver_id": str(make_user("target").id)},
        headers=auth_headers(organiser),
    )

    assert response.status_code == 400


# --- GET /sessions/{session_id}/invites ---------------------------------------


def test_organiser_sees_pending_invites(client, auth_headers, invited):
    organiser, invitee, session, invite = invited

    response = client.get(f"/sessions/{session['id']}/invites", headers=auth_headers(organiser))

    assert response.status_code == 200, response.text
    body = response.json()
    assert [i["id"] for i in body] == [invite["id"]]
    assert body[0]["receiver"]["id"] == str(invitee.id)
    assert body[0]["receiver"]["username"] == "invitee"


@pytest.mark.parametrize("response_route", ["accept", "decline"])
def test_sent_invites_exclude_answered(client, auth_headers, invited, response_route):
    organiser, invitee, session, invite = invited
    client.post(
        f"/sessions/{session['id']}/{response_route}",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    response = client.get(f"/sessions/{session['id']}/invites", headers=auth_headers(organiser))

    assert response.json() == []


def test_only_organiser_sees_sent_invites(client, auth_headers, invited):
    _, invitee, session, _ = invited

    response = client.get(f"/sessions/{session['id']}/invites", headers=auth_headers(invitee))

    assert response.status_code == 403


def test_sent_invites_of_hidden_session(client, make_user, auth_headers, create_session):
    session = create_session(make_user("organiser"), visibility="private")

    response = client.get(
        f"/sessions/{session['id']}/invites", headers=auth_headers(make_user("stranger"))
    )

    assert response.status_code == 404


def test_sent_invites_of_unknown_session(client, make_user, auth_headers):
    response = client.get(f"/sessions/{uuid4()}/invites", headers=auth_headers(make_user()))

    assert response.status_code == 404


# --- GET /sessions/invites ----------------------------------------------------


def test_get_invites_returns_own_invites(client, make_user, auth_headers, invited):
    _, invitee, _, invite = invited

    invitee_invites = client.get("/sessions/invites", headers=auth_headers(invitee)).json()
    stranger_invites = client.get(
        "/sessions/invites", headers=auth_headers(make_user("stranger"))
    ).json()

    assert [i["id"] for i in invitee_invites] == [invite["id"]]
    assert stranger_invites == []


def test_get_invites_only_returns_pending(client, auth_headers, invited):
    _, invitee, session, invite = invited
    client.post(
        f"/sessions/{session['id']}/decline",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    response = client.get("/sessions/invites", headers=auth_headers(invitee))

    assert response.json() == []


# --- POST /sessions/{session_id}/accept ---------------------------------------


def test_accept_invite(client, db, auth_headers, invited):
    _, invitee, session, invite = invited

    response = client.post(
        f"/sessions/{session['id']}/accept",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    assert response.status_code == 200
    assert db.get(SessionInvite, UUID(invite["id"])).status == InviteStatus.ACCEPTED
    assert db.get(SessionParticipant, (UUID(session["id"]), invitee.id)) is not None
    assert db.get(GamingSession, UUID(session["id"])).player_count == 2

    invitee_sessions = client.get("/sessions/", headers=auth_headers(invitee)).json()
    assert [s["id"] for s in invitee_sessions] == [session["id"]]


def test_only_invitee_can_accept(client, auth_headers, invited):
    organiser, _, session, invite = invited

    response = client.post(
        f"/sessions/{session['id']}/accept",
        json={"invite_id": invite["id"]},
        headers=auth_headers(organiser),
    )

    assert response.status_code == 403


def test_cannot_accept_twice(client, auth_headers, invited):
    _, invitee, session, invite = invited
    request = dict(
        url=f"/sessions/{session['id']}/accept",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    client.post(**request)
    response = client.post(**request)

    assert response.status_code == 409


def test_cannot_accept_into_full_session(
    client, db, make_user, auth_headers, create_session, send_invite
):
    organiser = make_user("organiser")
    invitee = make_user("invitee")
    # The organiser takes the only place.
    session = create_session(organiser, player_limit=1)
    invite = send_invite(organiser, session["id"], invitee).json()

    response = client.post(
        f"/sessions/{session['id']}/accept",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    assert response.status_code == 409
    assert db.get(SessionParticipant, (UUID(session["id"]), invitee.id)) is None


def test_cannot_accept_invite_to_cancelled_session(client, auth_headers, invited):
    organiser, invitee, session, invite = invited
    client.post(f"/sessions/{session['id']}/cancel", headers=auth_headers(organiser))

    response = client.post(
        f"/sessions/{session['id']}/accept",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    assert response.status_code == 409


def test_accept_unknown_invite(client, make_user, auth_headers):
    response = client.post(
        f"/sessions/{uuid4()}/accept",
        json={"invite_id": str(uuid4())},
        headers=auth_headers(make_user()),
    )

    assert response.status_code == 404


# --- POST /sessions/{session_id}/decline --------------------------------------


def test_decline_invite(client, db, auth_headers, invited):
    _, invitee, session, invite = invited

    response = client.post(
        f"/sessions/{session['id']}/decline",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    assert response.status_code == 200
    declined_invite = db.get(SessionInvite, UUID(invite["id"]))
    assert declined_invite.status == InviteStatus.DECLINED
    assert declined_invite.responded_at is not None
    assert db.get(SessionParticipant, (UUID(session["id"]), invitee.id)) is None


def test_only_invitee_can_decline(client, auth_headers, invited):
    organiser, _, session, invite = invited

    response = client.post(
        f"/sessions/{session['id']}/decline",
        json={"invite_id": invite["id"]},
        headers=auth_headers(organiser),
    )

    assert response.status_code == 403


def test_cannot_decline_after_accepting(client, auth_headers, invited):
    _, invitee, session, invite = invited
    client.post(
        f"/sessions/{session['id']}/accept",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    response = client.post(
        f"/sessions/{session['id']}/decline",
        json={"invite_id": invite["id"]},
        headers=auth_headers(invitee),
    )

    assert response.status_code == 409

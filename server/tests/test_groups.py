from uuid import UUID, uuid4

import pytest
from sqlalchemy import select

from app.models.user_group import UserGroup, UserGroupMember


@pytest.fixture
def create_group(client, auth_headers):
    def _create_group(owner, name="Friday crew"):
        response = client.post(
            "/groups/",
            json={"name": name},
            headers=auth_headers(owner),
        )
        assert response.status_code == 201, response.text
        return response.json()

    return _create_group


# --- Authentication -----------------------------------------------------------


def test_requires_authentication(client):
    response = client.get("/groups/")

    assert response.status_code in (401, 403)


# --- POST /groups/ ------------------------------------------------------------


def test_create_group(client, db, make_user, auth_headers):
    owner = make_user("owner")

    response = client.post(
        "/groups/",
        json={"name": "  Friday crew  "},
        headers=auth_headers(owner),
    )

    assert response.status_code == 201
    body = response.json()
    assert body["name"] == "Friday crew"
    assert body["owner_id"] == str(owner.id)
    assert [member["user_id"] for member in body["members"]] == [str(owner.id)]
    assert body["members"][0]["role"] == "owner"
    assert body["members"][0]["user"]["username"] == "owner"

    membership = db.get(UserGroupMember, (UUID(body["id"]), owner.id))
    assert membership is not None


def test_create_group_rejects_blank_name(client, make_user, auth_headers):
    response = client.post(
        "/groups/",
        json={"name": ""},
        headers=auth_headers(make_user()),
    )

    assert response.status_code == 422


# --- GET /groups/ -------------------------------------------------------------


def test_list_groups_only_returns_memberships(client, make_user, auth_headers, create_group):
    owner = make_user("owner")
    stranger = make_user("stranger")
    group = create_group(owner)
    create_group(stranger, name="Other crew")

    response = client.get("/groups/", headers=auth_headers(owner))

    assert [item["id"] for item in response.json()] == [group["id"]]


# --- GET /groups/{group_id} ---------------------------------------------------


def test_member_can_view_group(client, make_user, make_friends, auth_headers, create_group):
    owner = make_user("owner")
    member = make_user("member")
    make_friends(owner, member)
    group = create_group(owner)
    client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(member.id)},
        headers=auth_headers(owner),
    )

    response = client.get(f"/groups/{group['id']}", headers=auth_headers(member))

    assert response.status_code == 200
    assert {item["user"]["username"] for item in response.json()["members"]} == {
        "owner",
        "member",
    }


def test_non_member_cannot_view_group(client, make_user, auth_headers, create_group):
    group = create_group(make_user("owner"))

    response = client.get(
        f"/groups/{group['id']}", headers=auth_headers(make_user("stranger"))
    )

    assert response.status_code == 403


def test_get_unknown_group(client, make_user, auth_headers):
    response = client.get(f"/groups/{uuid4()}", headers=auth_headers(make_user()))

    assert response.status_code == 404


# --- PATCH /groups/{group_id} -------------------------------------------------


def test_owner_can_rename_group(client, db, make_user, auth_headers, create_group):
    owner = make_user("owner")
    group = create_group(owner)

    response = client.patch(
        f"/groups/{group['id']}",
        json={"name": "  Saturday crew  "},
        headers=auth_headers(owner),
    )

    assert response.status_code == 200
    assert response.json()["name"] == "Saturday crew"
    assert db.get(UserGroup, UUID(group["id"])).name == "Saturday crew"


def test_member_cannot_rename_group(
    client, make_user, make_friends, auth_headers, create_group
):
    owner = make_user("owner")
    member = make_user("member")
    make_friends(owner, member)
    group = create_group(owner)
    client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(member.id)},
        headers=auth_headers(owner),
    )

    response = client.patch(
        f"/groups/{group['id']}",
        json={"name": "Hijacked"},
        headers=auth_headers(member),
    )

    assert response.status_code == 403


def test_rename_unknown_group(client, make_user, auth_headers):
    response = client.patch(
        f"/groups/{uuid4()}",
        json={"name": "Nope"},
        headers=auth_headers(make_user()),
    )

    assert response.status_code == 404


# --- DELETE /groups/{group_id} ------------------------------------------------


def test_owner_can_delete_group(client, db, make_user, auth_headers, create_group):
    owner = make_user("owner")
    group = create_group(owner)

    response = client.delete(f"/groups/{group['id']}", headers=auth_headers(owner))

    assert response.status_code == 204
    db.expire_all()
    assert db.get(UserGroup, UUID(group["id"])) is None
    assert (
        db.scalars(
            select(UserGroupMember).where(UserGroupMember.group_id == UUID(group["id"]))
        ).all()
        == []
    )
    assert client.get(f"/groups/{group['id']}", headers=auth_headers(owner)).status_code == 404


def test_member_cannot_delete_group(
    client, db, make_user, make_friends, auth_headers, create_group
):
    owner = make_user("owner")
    member = make_user("member")
    make_friends(owner, member)
    group = create_group(owner)
    client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(member.id)},
        headers=auth_headers(owner),
    )

    response = client.delete(f"/groups/{group['id']}", headers=auth_headers(member))

    assert response.status_code == 403
    assert db.get(UserGroup, UUID(group["id"])) is not None


def test_delete_unknown_group(client, make_user, auth_headers):
    response = client.delete(f"/groups/{uuid4()}", headers=auth_headers(make_user()))

    assert response.status_code == 404


# --- POST /groups/{group_id}/members ------------------------------------------


def test_member_can_add_a_friend(client, make_user, make_friends, auth_headers, create_group):
    owner = make_user("owner")
    member = make_user("member")
    friend = make_user("friend")
    make_friends(owner, member)
    make_friends(member, friend)
    group = create_group(owner)
    client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(member.id)},
        headers=auth_headers(owner),
    )

    response = client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(friend.id)},
        headers=auth_headers(member),
    )

    assert response.status_code == 201, response.text
    body = response.json()
    assert body["user_id"] == str(friend.id)
    assert body["role"] == "member"
    assert body["user"]["username"] == "friend"


def test_cannot_add_someone_who_is_not_your_friend(
    client, make_user, make_friends, auth_headers, create_group
):
    owner = make_user("owner")
    member = make_user("member")
    outsider = make_user("outsider")
    make_friends(owner, member)
    make_friends(member, outsider)
    group = create_group(owner)
    client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(member.id)},
        headers=auth_headers(owner),
    )

    response = client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(outsider.id)},
        headers=auth_headers(owner),
    )

    assert response.status_code == 403


def test_cannot_add_yourself(client, make_user, auth_headers, create_group):
    owner = make_user("owner")
    group = create_group(owner)

    response = client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(owner.id)},
        headers=auth_headers(owner),
    )

    assert response.status_code == 400


def test_cannot_add_existing_member(client, make_user, make_friends, auth_headers, create_group):
    owner = make_user("owner")
    member = make_user("member")
    make_friends(owner, member)
    group = create_group(owner)
    client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(member.id)},
        headers=auth_headers(owner),
    )

    response = client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(member.id)},
        headers=auth_headers(owner),
    )

    assert response.status_code == 409


def test_cannot_add_unknown_user(client, make_user, auth_headers, create_group):
    owner = make_user("owner")
    group = create_group(owner)

    response = client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(uuid4())},
        headers=auth_headers(owner),
    )

    assert response.status_code == 404


def test_non_member_cannot_add_anyone(
    client, make_user, make_friends, auth_headers, create_group
):
    owner = make_user("owner")
    stranger = make_user("stranger")
    friend = make_user("friend")
    make_friends(stranger, friend)
    group = create_group(owner)

    response = client.post(
        f"/groups/{group['id']}/members",
        json={"user_id": str(friend.id)},
        headers=auth_headers(stranger),
    )

    assert response.status_code == 403


# --- POST /groups/{group_id}/join ---------------------------------------------


def test_join_group_a_friend_belongs_to(
    client, make_user, make_friends, auth_headers, create_group
):
    owner = make_user("owner")
    friend = make_user("friend")
    make_friends(owner, friend)
    group = create_group(owner)

    response = client.post(f"/groups/{group['id']}/join", headers=auth_headers(friend))

    assert response.status_code == 201, response.text
    assert response.json()["role"] == "member"
    assert response.json()["user_id"] == str(friend.id)
    listed = client.get("/groups/", headers=auth_headers(friend)).json()
    assert [item["id"] for item in listed] == [group["id"]]


def test_cannot_join_without_a_friend_in_the_group(
    client, make_user, make_friends, auth_headers, create_group
):
    owner = make_user("owner")
    stranger = make_user("stranger")
    outsider = make_user("outsider")
    make_friends(stranger, outsider)
    group = create_group(owner)

    response = client.post(f"/groups/{group['id']}/join", headers=auth_headers(stranger))

    assert response.status_code == 403


def test_cannot_join_twice(client, make_user, auth_headers, create_group):
    owner = make_user("owner")
    group = create_group(owner)

    response = client.post(f"/groups/{group['id']}/join", headers=auth_headers(owner))

    assert response.status_code == 409


def test_join_unknown_group(client, make_user, auth_headers):
    response = client.post(f"/groups/{uuid4()}/join", headers=auth_headers(make_user()))

    assert response.status_code == 404


# --- DELETE /groups/{group_id}/members/{member_id} ----------------------------


def test_member_can_leave(client, db, make_user, make_friends, auth_headers, create_group):
    owner = make_user("owner")
    member = make_user("member")
    make_friends(owner, member)
    group = create_group(owner)
    client.post(f"/groups/{group['id']}/join", headers=auth_headers(member))

    response = client.delete(
        f"/groups/{group['id']}/members/{member.id}",
        headers=auth_headers(member),
    )

    assert response.status_code == 204
    db.expire_all()
    assert db.get(UserGroupMember, (UUID(group["id"]), member.id)) is None
    assert client.get("/groups/", headers=auth_headers(member)).json() == []


def test_owner_cannot_leave(client, db, make_user, auth_headers, create_group):
    owner = make_user("owner")
    group = create_group(owner)

    response = client.delete(
        f"/groups/{group['id']}/members/{owner.id}",
        headers=auth_headers(owner),
    )

    assert response.status_code == 400
    assert db.get(UserGroupMember, (UUID(group["id"]), owner.id)) is not None


def test_owner_can_remove_a_member(
    client, db, make_user, make_friends, auth_headers, create_group
):
    owner = make_user("owner")
    member = make_user("member")
    make_friends(owner, member)
    group = create_group(owner)
    client.post(f"/groups/{group['id']}/join", headers=auth_headers(member))

    response = client.delete(
        f"/groups/{group['id']}/members/{member.id}",
        headers=auth_headers(owner),
    )

    assert response.status_code == 204
    db.expire_all()
    assert db.get(UserGroupMember, (UUID(group["id"]), member.id)) is None


def test_member_cannot_remove_another_member(
    client, db, make_user, make_friends, auth_headers, create_group
):
    owner = make_user("owner")
    member_a = make_user("member_a")
    member_b = make_user("member_b")
    make_friends(owner, member_a)
    make_friends(owner, member_b)
    group = create_group(owner)
    for member in (member_a, member_b):
        client.post(f"/groups/{group['id']}/join", headers=auth_headers(member))

    response = client.delete(
        f"/groups/{group['id']}/members/{member_b.id}",
        headers=auth_headers(member_a),
    )

    assert response.status_code == 403
    assert db.get(UserGroupMember, (UUID(group["id"]), member_b.id)) is not None


def test_remove_unknown_member(client, make_user, auth_headers, create_group):
    owner = make_user("owner")
    group = create_group(owner)

    response = client.delete(
        f"/groups/{group['id']}/members/{uuid4()}",
        headers=auth_headers(owner),
    )

    assert response.status_code == 404


def test_non_member_cannot_remove_anyone(client, make_user, auth_headers, create_group):
    owner = make_user("owner")
    group = create_group(owner)

    response = client.delete(
        f"/groups/{group['id']}/members/{owner.id}",
        headers=auth_headers(make_user("stranger")),
    )

    assert response.status_code == 403


# --- POST /groups/{group_id}/transfer ----------------------------------------


def test_transfer_ownership(client, db, make_user, make_friends, auth_headers, create_group):
    owner = make_user("owner")
    member = make_user("member")
    make_friends(owner, member)
    group = create_group(owner)
    client.post(f"/groups/{group['id']}/join", headers=auth_headers(member))

    response = client.post(
        f"/groups/{group['id']}/transfer",
        json={"new_owner_id": str(member.id)},
        headers=auth_headers(owner),
    )

    assert response.status_code == 200, response.text
    body = response.json()
    assert body["owner_id"] == str(member.id)
    roles = {item["user_id"]: item["role"] for item in body["members"]}
    assert roles[str(member.id)] == "owner"
    assert roles[str(owner.id)] == "member"

    db.expire_all()
    stored = db.get(UserGroup, UUID(group["id"]))
    assert stored.owner_id == member.id

    leave = client.delete(
        f"/groups/{group['id']}/members/{owner.id}",
        headers=auth_headers(owner),
    )
    assert leave.status_code == 204
    still_owner = client.delete(
        f"/groups/{group['id']}/members/{member.id}",
        headers=auth_headers(member),
    )
    assert still_owner.status_code == 400


def test_cannot_transfer_to_yourself(client, make_user, auth_headers, create_group):
    owner = make_user("owner")
    group = create_group(owner)

    response = client.post(
        f"/groups/{group['id']}/transfer",
        json={"new_owner_id": str(owner.id)},
        headers=auth_headers(owner),
    )

    assert response.status_code == 400


def test_cannot_transfer_to_a_non_member(
    client, make_user, make_friends, auth_headers, create_group
):
    owner = make_user("owner")
    friend = make_user("friend")
    make_friends(owner, friend)
    group = create_group(owner)

    response = client.post(
        f"/groups/{group['id']}/transfer",
        json={"new_owner_id": str(friend.id)},
        headers=auth_headers(owner),
    )

    assert response.status_code == 400


def test_member_cannot_transfer(
    client, make_user, make_friends, auth_headers, create_group
):
    owner = make_user("owner")
    member = make_user("member")
    other = make_user("other")
    make_friends(owner, member)
    make_friends(owner, other)
    group = create_group(owner)
    for user in (member, other):
        client.post(f"/groups/{group['id']}/join", headers=auth_headers(user))

    response = client.post(
        f"/groups/{group['id']}/transfer",
        json={"new_owner_id": str(other.id)},
        headers=auth_headers(member),
    )

    assert response.status_code == 403


def test_transfer_unknown_user(client, make_user, auth_headers, create_group):
    owner = make_user("owner")
    group = create_group(owner)

    response = client.post(
        f"/groups/{group['id']}/transfer",
        json={"new_owner_id": str(uuid4())},
        headers=auth_headers(owner),
    )

    assert response.status_code == 404


def test_transfer_unknown_group(client, make_user, auth_headers):
    owner = make_user("owner")

    response = client.post(
        f"/groups/{uuid4()}/transfer",
        json={"new_owner_id": str(owner.id)},
        headers=auth_headers(owner),
    )

    assert response.status_code == 404

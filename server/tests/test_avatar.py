from datetime import datetime

import pytest

from app.models.user_avatar import UserAvatar

PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 64
JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 64
WEBP = b"RIFF\x00\x00\x00\x00WEBP" + b"\x00" * 64
MAX_BYTES = 2 * 1024 * 1024


def _upload(client, headers, data, content_type="image/png"):
    return client.put(
        "/users/me/avatar",
        files={"file": ("avatar", data, content_type)},
        headers=headers,
    )


def _parse(value: str) -> datetime:
    return datetime.fromisoformat(value)


@pytest.mark.parametrize(
    ("data", "expected_type"),
    [(JPEG, "image/jpeg"), (PNG, "image/png"), (WEBP, "image/webp")],
)
def test_upload_accepts_supported_types(client, make_user, auth_headers, data, expected_type):
    user = make_user()

    response = _upload(client, auth_headers(user), data)
    assert response.status_code == 200
    assert response.json()["avatar_updated_at"] is not None

    image = client.get(f"/users/{user.id}/avatar", headers=auth_headers(user))
    assert image.status_code == 200
    assert image.content == data
    assert image.headers["content-type"] == expected_type


def test_upload_shows_in_auth_me(client, make_user, auth_headers):
    user = make_user()
    uploaded = _upload(client, auth_headers(user), PNG).json()

    me = client.get("/auth/me", headers=auth_headers(user)).json()

    assert _parse(me["avatar_updated_at"]) == _parse(uploaded["avatar_updated_at"])


def test_reupload_replaces_avatar(client, make_user, auth_headers):
    user = make_user()
    first = _upload(client, auth_headers(user), PNG).json()["avatar_updated_at"]
    second = _upload(client, auth_headers(user), JPEG).json()["avatar_updated_at"]

    assert _parse(second) > _parse(first)
    image = client.get(f"/users/{user.id}/avatar", headers=auth_headers(user))
    assert image.content == JPEG


def test_upload_accepts_exactly_max_size(client, make_user, auth_headers):
    user = make_user()
    data = PNG[:8] + b"\x00" * (MAX_BYTES - 8)

    assert _upload(client, auth_headers(user), data).status_code == 200


def test_upload_rejects_over_max_size(client, make_user, auth_headers):
    user = make_user()
    data = PNG[:8] + b"\x00" * (MAX_BYTES - 7)

    response = _upload(client, auth_headers(user), data)

    assert response.status_code == 413
    assert client.get("/auth/me", headers=auth_headers(user)).json()["avatar_updated_at"] is None


@pytest.mark.parametrize("data", [b"", b"hello world", b"GIF89a" + b"\x00" * 64])
def test_upload_rejects_unsupported_bytes_even_with_image_header(client, make_user, auth_headers, data):
    user = make_user()

    response = _upload(client, auth_headers(user), data, content_type="image/png")

    assert response.status_code == 415


def test_get_avatar_404_when_none(client, make_user, auth_headers):
    user = make_user()

    response = client.get(f"/users/{user.id}/avatar", headers=auth_headers(user))

    assert response.status_code == 404


def test_other_users_can_fetch_avatar(client, make_user, auth_headers):
    owner = make_user("owner")
    viewer = make_user("viewer")
    _upload(client, auth_headers(owner), PNG)

    response = client.get(f"/users/{owner.id}/avatar", headers=auth_headers(viewer))

    assert response.status_code == 200
    assert response.content == PNG


def test_delete_avatar(client, make_user, auth_headers):
    user = make_user()
    _upload(client, auth_headers(user), PNG)

    response = client.delete("/users/me/avatar", headers=auth_headers(user))

    assert response.status_code == 204
    assert client.get(f"/users/{user.id}/avatar", headers=auth_headers(user)).status_code == 404
    assert client.get("/auth/me", headers=auth_headers(user)).json()["avatar_updated_at"] is None


def test_delete_avatar_when_none_is_noop(client, make_user, auth_headers):
    user = make_user()

    assert client.delete("/users/me/avatar", headers=auth_headers(user)).status_code == 204


def test_avatar_endpoints_require_auth(client, make_user):
    user = make_user()

    assert _upload(client, {}, PNG).status_code in (401, 403)
    assert client.delete("/users/me/avatar").status_code in (401, 403)
    assert client.get(f"/users/{user.id}/avatar").status_code in (401, 403)


def test_deleting_user_deletes_avatar(client, db, make_user, auth_headers):
    user = make_user()
    _upload(client, auth_headers(user), PNG)

    db.delete(user)
    db.commit()

    assert db.get(UserAvatar, user.id) is None


def test_friends_list_includes_avatar_updated_at(client, make_user, auth_headers, make_friends):
    me = make_user("me")
    friend = make_user("friend")
    make_friends(me, friend)
    _upload(client, auth_headers(friend), PNG)

    friends = client.get("/friends/", headers=auth_headers(me)).json()

    assert friends[0]["avatar_updated_at"] is not None

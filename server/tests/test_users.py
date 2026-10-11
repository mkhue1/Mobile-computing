from uuid import uuid4

import pytest
from sqlalchemy import select

from app.models.user import User


def user_payload(**overrides):
    payload = {
        "email": "Ada@Example.com",
        "username": "ada",
        "password": "password1",
    }
    payload.update(overrides)
    return payload


# --- POST /users/ -------------------------------------------------------------


def test_create_user(client, db):
    response = client.post("/users/", json=user_payload())

    assert response.status_code == 201
    body = response.json()
    assert body["email"] == "ada@example.com"
    assert body["username"] == "ada"
    assert "password" not in body

    stored = db.scalar(select(User).where(User.username == "ada"))
    assert stored is not None
    assert stored.password != "password1"
    assert stored.password.startswith("$2")


def test_create_user_rejects_duplicate_email(client):
    client.post("/users/", json=user_payload())

    response = client.post(
        "/users/",
        json=user_payload(email="ada@example.com", username="ada_other"),
    )

    assert response.status_code == 409


def test_create_user_rejects_duplicate_email_with_different_case(client):
    client.post("/users/", json=user_payload())

    response = client.post(
        "/users/",
        json=user_payload(email="ADA@example.com", username="ada_other"),
    )

    assert response.status_code == 409


def test_create_user_rejects_duplicate_username(client):
    client.post("/users/", json=user_payload())

    response = client.post(
        "/users/",
        json=user_payload(email="other@example.com", username="ada"),
    )

    assert response.status_code == 409


@pytest.mark.parametrize(
    "username",
    ["ab", "has space", "bad!name", ""],
)
def test_create_user_rejects_invalid_username(client, username):
    response = client.post("/users/", json=user_payload(username=username))

    assert response.status_code == 422


@pytest.mark.parametrize(
    "password",
    ["short1", "nodigits", "12345678", ""],
)
def test_create_user_rejects_weak_password(client, password):
    response = client.post("/users/", json=user_payload(password=password))

    assert response.status_code == 422


def test_create_user_rejects_invalid_email(client):
    response = client.post("/users/", json=user_payload(email="not-an-email"))

    assert response.status_code == 422


# --- GET /users/ --------------------------------------------------------------


def test_get_users_requires_authentication(client):
    response = client.get("/users/")

    assert response.status_code in (401, 403)


def test_search_matches_username_and_excludes_self(client, make_user, auth_headers):
    seeker = make_user("seeker")
    make_user("adalovelace")
    make_user("adal")
    make_user("grace")

    response = client.get(
        "/users/",
        params={"search": "  ADA  "},
        headers=auth_headers(seeker),
    )

    assert response.status_code == 200
    assert [user["username"] for user in response.json()] == ["adal", "adalovelace"]
    assert all("password" not in user for user in response.json())


def test_search_shorter_than_two_characters_is_ignored(client, make_user, auth_headers):
    seeker = make_user("seeker")
    make_user("ada")

    response = client.get("/users/", params={"search": "a"}, headers=auth_headers(seeker))

    assert {user["username"] for user in response.json()} == {"seeker", "ada"}


def test_search_without_a_term_returns_every_user(client, make_user, auth_headers):
    seeker = make_user("seeker")
    make_user("ada")

    response = client.get("/users/", headers=auth_headers(seeker))

    assert {user["username"] for user in response.json()} == {"seeker", "ada"}


def test_search_treats_wildcards_as_literals(client, make_user, auth_headers):
    seeker = make_user("seeker")
    make_user("a_b")
    make_user("axb")
    make_user("a%b")

    underscore = client.get(
        "/users/", params={"search": "a_b"}, headers=auth_headers(seeker)
    )
    percent = client.get(
        "/users/", params={"search": "a%b"}, headers=auth_headers(seeker)
    )

    assert [user["username"] for user in underscore.json()] == ["a_b"]
    assert [user["username"] for user in percent.json()] == ["a%b"]


def test_search_respects_limit(client, make_user, auth_headers):
    seeker = make_user("seeker")
    for name in ("cara", "aria", "mara"):
        make_user(name)

    response = client.get(
        "/users/",
        params={"search": "ar", "limit": 2},
        headers=auth_headers(seeker),
    )

    assert [user["username"] for user in response.json()] == ["aria", "cara"]


@pytest.mark.parametrize("limit", [0, 51])
def test_search_rejects_limit_out_of_range(client, make_user, auth_headers, limit):
    response = client.get(
        "/users/",
        params={"limit": limit},
        headers=auth_headers(make_user()),
    )

    assert response.status_code == 422


# --- GET /users/{user_id} -----------------------------------------------------


def test_get_user_by_id(client, make_user, auth_headers):
    viewer = make_user("viewer")
    target = make_user("target")

    response = client.get(f"/users/{target.id}", headers=auth_headers(viewer))

    assert response.status_code == 200
    body = response.json()
    assert body["id"] == str(target.id)
    assert body["username"] == "target"
    assert "password" not in body


def test_get_user_by_id_not_found(client, make_user, auth_headers):
    response = client.get(
        f"/users/{uuid4()}",
        headers=auth_headers(make_user()),
    )

    assert response.status_code == 404


def test_get_user_by_id_requires_authentication(client, make_user):
    target = make_user("target")

    response = client.get(f"/users/{target.id}")

    assert response.status_code in (401, 403)

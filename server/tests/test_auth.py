from uuid import uuid4

import pytest

from app.security import create_access_token


@pytest.fixture
def register(client):
    def _register(**overrides):
        payload = {
            "email": "Ada@Example.com",
            "username": "ada",
            "password": "password1",
        }
        payload.update(overrides)
        response = client.post("/users/", json=payload)
        assert response.status_code == 201, response.text
        return response.json()

    return _register


# --- POST /auth/login ---------------------------------------------------------


def test_login_with_email(client, register):
    user = register()

    response = client.post(
        "/auth/login",
        json={"identifier": "  ADA@example.com  ", "password": "password1"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["token_type"] == "bearer"
    assert body["access_token"]
    assert body["user"]["id"] == user["id"]
    assert body["user"]["email"] == "ada@example.com"
    assert "password" not in body["user"]


def test_login_with_username(client, register):
    user = register()

    response = client.post(
        "/auth/login",
        json={"identifier": "ada", "password": "password1"},
    )

    assert response.status_code == 200
    assert response.json()["user"]["id"] == user["id"]


def test_login_username_is_case_sensitive(client, register):
    register()

    response = client.post(
        "/auth/login",
        json={"identifier": "Ada", "password": "password1"},
    )

    assert response.status_code == 401


def test_login_rejects_wrong_password(client, register):
    register()

    response = client.post(
        "/auth/login",
        json={"identifier": "ada", "password": "password2"},
    )

    assert response.status_code == 401


def test_login_rejects_unknown_user(client):
    response = client.post(
        "/auth/login",
        json={"identifier": "nobody", "password": "password1"},
    )

    assert response.status_code == 401


@pytest.mark.parametrize(
    "payload",
    [{}, {"identifier": "", "password": "password1"}, {"identifier": "ada", "password": ""}],
)
def test_login_rejects_missing_credentials(client, payload):
    response = client.post("/auth/login", json=payload)

    assert response.status_code == 422


# --- GET /auth/me -------------------------------------------------------------


def test_me_returns_current_user(client, register):
    user = register()
    login = client.post(
        "/auth/login",
        json={"identifier": "ada", "password": "password1"},
    )

    response = client.get(
        "/auth/me",
        headers={"Authorization": f"Bearer {login.json()['access_token']}"},
    )

    assert response.status_code == 200
    assert response.json()["id"] == user["id"]
    assert response.json()["username"] == "ada"


def test_me_requires_authentication(client):
    response = client.get("/auth/me")

    assert response.status_code in (401, 403)


def test_me_rejects_invalid_token(client):
    response = client.get("/auth/me", headers={"Authorization": "Bearer not-a-jwt"})

    assert response.status_code == 401


def test_me_rejects_token_for_missing_user(client):
    token = create_access_token(uuid4())

    response = client.get("/auth/me", headers={"Authorization": f"Bearer {token}"})

    assert response.status_code == 401

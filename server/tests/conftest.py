import os
from pathlib import Path

from dotenv import load_dotenv
from sqlalchemy import create_engine, text
from sqlalchemy.engine import make_url

# Local runs read TEST_DATABASE_URL from .env.test. Docker only
# mounts ./server, so inside the container this file doesn't exist and the URL
# is derived from DATABASE_URL instead. Variables already set in the shell win.
load_dotenv(Path(__file__).resolve().parents[2] / ".env.test")

# Point the app at a separate test database before anything imports app.config,
# because Settings() and the engine are both created at import time.
_base_url = os.environ.get("TEST_DATABASE_URL") or os.environ.get("DATABASE_URL")
if _base_url is None:
    raise RuntimeError(
        "Set TEST_DATABASE_URL (or DATABASE_URL) to a Postgres URL to run the tests"
    )

TEST_DB_URL = make_url(_base_url)
if "TEST_DATABASE_URL" not in os.environ:
    TEST_DB_URL = TEST_DB_URL.set(database=f"{TEST_DB_URL.database}_test")

# Every test wipes all tables, so never let that run against a real database.
if not TEST_DB_URL.database.endswith("_test"):
    raise RuntimeError(
        f"Refusing to run tests against '{TEST_DB_URL.database}': "
        "the test database name must end in '_test'"
    )

os.environ["DATABASE_URL"] = TEST_DB_URL.render_as_string(hide_password=False)
os.environ.setdefault("JWT_SECRET", "test-secret")
os.environ.setdefault("STEAM_API_KEY", "test-steam-key")
os.environ.setdefault("PUBLIC_BASE_URL", "http://testserver")
os.environ.setdefault("STEAM_DEEP_LINK_SUCCESS", "gamercalendar://steam/linked")
os.environ.setdefault("STEAM_DEEP_LINK_ERROR", "gamercalendar://steam/error")

import pytest
from fastapi.testclient import TestClient

from app import models
from app.database import Base, SessionLocal, engine
from app.main import app
from app.models.game import Game
from app.models.user import User
from app.models.user_group import GroupRole, UserGroup, UserGroupMember
from app.security import create_access_token


def _ensure_database_exists() -> None:
    admin_engine = create_engine(
        TEST_DB_URL.set(database="postgres"),
        isolation_level="AUTOCOMMIT",
    )
    with admin_engine.connect() as conn:
        exists = conn.scalar(
            text("SELECT 1 FROM pg_database WHERE datname = :name"),
            {"name": TEST_DB_URL.database},
        )
        if not exists:
            conn.execute(text(f'CREATE DATABASE "{TEST_DB_URL.database}"'))
    admin_engine.dispose()


@pytest.fixture(scope="session", autouse=True)
def _schema():
    _ensure_database_exists()
    Base.metadata.drop_all(engine)
    Base.metadata.create_all(engine)
    yield
    engine.dispose()


@pytest.fixture(autouse=True)
def _clean_tables():
    table_names = ", ".join(f'"{table.name}"' for table in Base.metadata.sorted_tables)
    with engine.begin() as conn:
        conn.execute(text(f"TRUNCATE {table_names} CASCADE"))


@pytest.fixture
def client():
    with TestClient(app) as test_client:
        yield test_client


@pytest.fixture
def db():
    with SessionLocal() as session:
        yield session


@pytest.fixture
def make_user(db):
    counter = 0

    def _make_user(username: str | None = None) -> User:
        nonlocal counter
        counter += 1
        username = username or f"user{counter}"
        user = User(
            email=f"{username}@example.com",
            username=username,
            password="not-a-real-hash",
        )
        db.add(user)
        db.commit()
        return user

    return _make_user


@pytest.fixture
def auth_headers():
    def _auth_headers(user: User) -> dict[str, str]:
        return {"Authorization": f"Bearer {create_access_token(user.id)}"}

    return _auth_headers


@pytest.fixture
def game(db):
    game = Game(igdb_id=1, name="Test Game")
    db.add(game)
    db.commit()
    return game


@pytest.fixture
def make_group(db):
    def _make_group(owner: User, members: tuple[User, ...] = ()) -> UserGroup:
        group = UserGroup(name="Test Group", owner_id=owner.id)
        db.add(group)
        db.flush()

        db.add(UserGroupMember(group_id=group.id, user_id=owner.id, role=GroupRole.OWNER))
        for member in members:
            db.add(UserGroupMember(group_id=group.id, user_id=member.id, role=GroupRole.MEMBER))

        db.commit()
        return group

    return _make_group

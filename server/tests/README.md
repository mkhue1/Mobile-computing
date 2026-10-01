# Tests

Integration tests for the API. They call the endpoints through FastAPI's `TestClient` and run against a test Postgres database (`gamercalendar_test`)

## Running the tests

You need the Postgres container running:

```
docker compose up -d db
```

The backend container doesn't need to be running.

### Locally

first time setup, from repo root:

```
cp .env.test.example .env.test
python -m venv .venv
.venv/Scripts/python -m pip install -r server/requirements-dev.txt
```

On macOS/Linux use `.venv/bin/python` instead of `.venv/Scripts/python`.

`.env.test` points at `localhost:5433`, the port that Docker exposes Postgres on, change it if your postgres setup is different.

Then, from `server/`:

```
../.venv/Scripts/python -m pytest
```

if you are already in your venv you can just run the pytest command.

### Inside Docker

From the repo root:

```
docker compose run --rm --no-deps backend sh -c "pip install -r requirements-dev.txt && pytest"
```

This connects over the compose network and works out the test database from `DATABASE_URL`, so `.env.test` isn't used.

## How the database is handled

- The test database is created on first run if it doesn't exist.
- At the start of every run, all tables are dropped and recreated from the SQLAlchemy models.
- Before every test, all tables data are cleared.

## Adding more tests

Put tests in `server/tests/test_<area>.py`, for example `test_groups.py` for the groups router. pytest will pick up any function whose name starts with `test_`.

Useful testing fixtures from `conftest.py` are available in test files. You can access them by naming them as test arguments:
If you make other helpful fixtures when making your own tests, add them to confgi and update the readme here.

| Fixture | What it gives you |
|---|---|
| `client` | A `TestClient` for making requests to the app |
| `db` | A SQLAlchemy session, for setting up data and checking results |
| `make_user()` | Creates and returns a `User`. Pass a username to choose one |
| `auth_headers(user)` | The `Authorization` header for that user, using a real JWT |
| `game` | A `Game` row, needed when creating sessions |
| `make_group(owner, members=(...))` | Creates a group with an owner and optional members |

Example:

```python
def test_only_organiser_can_cancel(client, make_user, auth_headers, game):
    organiser = make_user("organiser")
    stranger = make_user("stranger")
    session = client.post(
        "/sessions/create",
        json={...},
        headers=auth_headers(organiser),
    ).json()

    response = client.patch(f"/sessions/{session['id']}", headers=auth_headers(stranger))

    assert response.status_code == 403
```

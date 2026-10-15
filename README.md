# Roundtable

Android app (Kotlin, Jetpack Compose) with a FastAPI + PostgreSQL backend.

The backend is deployed on Google Cloud Run, so the quickest way to try the app is to point it at the deployed backend and only run the app locally. You can also run the backend yourself in Docker.

## Requirements

- [Android Studio](https://developer.android.com/studio), recent enough for **Android SDK Platform 37** (install it via *Tools → SDK Manager*)
- An Android emulator using a **Google Play** system image (API 37 recommended; minimum API 24). Google Play services are needed for maps, place search, geofencing and push notifications.
- [Docker Desktop](https://www.docker.com/products/docker-desktop/), only if you run the backend locally

## 1. App configuration files

These files are not in the repository and must be added.

### `android/app/google-services.json`

Required to build the app. Provided separately with the submission; place it at `android/app/google-services.json`.

### `android/local.properties`

Let Android Studio create this file when you first open the project, then add these two lines:

```properties
API_BASE_URL=https://gamercalendar-api-592534958966.australia-southeast1.run.app/
GOOGLE_MAPS_API_KEY=<provided separately>
```

This uses the deployed backend. To use a backend running locally in Docker instead (see [Running the backend locally](#running-the-backend-locally)), set:

```properties
API_BASE_URL=http://10.0.2.2:8000/
```

`10.0.2.2` is the emulator's address for your computer. On a physical phone, use `http://<your-computer's-LAN-IP>:8000/`. Keep the trailing `/` on every URL.

> Don't copy `local.properties.example` as-is: it contains one developer's SDK path and IP address.

## 2. Build and run the app

1. In Android Studio, **Open** the `android/` folder and wait for Gradle sync to finish (the first sync takes several minutes).
2. Start a Google Play emulator and press **Run ▶**.
3. Allow the permission prompts: **notifications**, **calendar** and, on API 37, **nearby devices** on launch, and **location** and **camera** later, when using Explore, the map or QR scanning. Nearby devices is only needed to reach a backend on your local network; it can be denied when using the deployed backend.
4. **Sign up** in the app. Passwords must be at least 8 characters, include a letter and a digit, and not be easy to guess. Create a second account to try friends, groups and invites.

The deployed backend scales to zero when idle, so the first request after a quiet period can take a few seconds.

## Running the backend locally

Only needed if you are not using the deployed backend.

### `.env` (repository root)

```bash
cp .env.example .env
```

**Database and login** (the defaults in `.env.example` work locally):

| Variable | Purpose |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Database container |
| `DATABASE_URL` | Backend connection to the database |
| `JWT_SECRET` | Signs login tokens. Any long random string |

**API keys** (required for all features to work). Our keys are provided separately with the submission; paste them into `.env`:

| Variable | Enables | How to obtain your own |
|---|---|---|
| `TWITCH_CLIENT_ID`, `IGDB_ACCESS_TOKEN` | Game search when creating a session | Create an app at https://dev.twitch.tv/console/apps/create (client type *Confidential*, redirect URL `https://localhost/`). Use its client ID and secret to generate an app access token (below). **IGDB tokens expire after about 60 days.** |
| `STEAM_API_KEY`, `PUBLIC_BASE_URL` | Linking a Steam account and Steam friend suggestions | Key: https://steamcommunity.com/dev/apikey. `PUBLIC_BASE_URL`: `http://10.0.2.2:8000` for the emulator |
| `FIREBASE_CREDENTIALS_PATH` | Push notifications | Place the provided service account key at `server/firebase-service-account.json`. The default value in `.env.example` already points to it |

To generate an IGDB access token from your Twitch client ID and secret:

```bash
curl -X POST "https://id.twitch.tv/oauth2/token?client_id=<CLIENT_ID>&client_secret=<CLIENT_SECRET>&grant_type=client_credentials"
```

Copy `access_token` from the response into `IGDB_ACCESS_TOKEN`. `TWITCH_CLIENT_SECRET` can also be kept in `.env`, but the server only uses the client ID and access token.

### Start the backend

```bash
docker compose up --build
```

Wait for `Application startup complete`, then in a second terminal create the database tables:

```bash
docker compose run --rm backend alembic upgrade head
```

Check that it's running at http://localhost:8000/docs, and set `API_BASE_URL=http://10.0.2.2:8000/` in `android/local.properties`.

To stop the backend, press `Ctrl+C` or run `docker compose down`. Data is kept; `docker compose down -v` deletes the database.

## Running backend tests

```bash
docker compose run --rm backend sh -c "pip install -r requirements-dev.txt && pytest"
```

See `server/tests/README.md` for running them outside Docker.

## Troubleshooting

| Problem | Fix |
|---|---|
| Build fails: `google-services.json is missing` | Place the provided file at `android/app/google-services.json` |
| Gradle can't find SDK Platform 37 | Install it in *Tools → SDK Manager* |
| App can't reach the server | Check `API_BASE_URL` in `android/local.properties` (including the trailing `/`), then rebuild. For a local backend, also check it's running and the nearby devices permission was allowed |
| Map is blank | Set `GOOGLE_MAPS_API_KEY` and use a Google Play emulator image |
| Game search fails (502) | Local backend: add or refresh the Twitch/IGDB values in `.env`, then `docker compose up --force-recreate` |
| No push notifications | Local backend: check `server/firebase-service-account.json` is in place, then `docker compose up --force-recreate` |
| Backend error `column ... does not exist` | `docker compose run --rm backend alembic upgrade head` |
| Backend error `ModuleNotFoundError` | `docker compose up --build` |
| "Different signature" when installing | Uninstall the app from the emulator and run again |

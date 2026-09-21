from fastapi import FastAPI

from app.routers.friends import router as friends_router
from app.routers.games import router as games_router
from app.routers.groups import router as groups_router
from app.routers.users import router as users_router
from app.routers.sessions import router as sessions_router



app = FastAPI()

app.include_router(users_router)
app.include_router(friends_router)
app.include_router(games_router)
app.include_router(groups_router)
app.include_router(sessions_router)


@app.get("/health")
def health():
    return {
        "status": "ok"
    }

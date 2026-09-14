# Mobile-computing
Gaming calendar mobile app

will explain stuff later (when dev actually gets started on this)

for now:

make a .env following .env.example
edit local.properties as per local.propeties.example

docker compose up --build

this is for migrations to make sure db is configured

docker compose run --rm backend alembic upgrade head 

open up android studio and select thd android folder as a project

and hopefully it builds!

current functionality is:

signup (on swagger docs (localhost:8000/docs)) + click the users button to get a list of users

db, backend and frontend are currently all connected


IGDB SETUP:

Needs a key for this from twitch, we need both a client id and a client secret for this

https://dev.twitch.tv/console/apps/create

Set client type to condidential and use https://localhost/ for the Redirect URL (not used)

Click manage then save client id and the secret to the .env file.
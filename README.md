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


BACKEND FOLDER STRUCTURE:

3 main folders within server/app

1: Models:

This is what we're using to manage the db, write changes in here, make migrations and then the db gets the changes.

2: Routers:

Hosts the APIs

3: Schemas:

Typing for the APIs both requests and responses.

FRONTEND FOLDER STRUCTURE:

1: ui

this is literally what the user sees.

2: viewmodel

manage screen state and logic

3: data/model

typing for kotlin data structures


4: data/api

where the backend API calls are

5: repository

manages access to data from within the app

THIS NEEDS TO BE EXPANDED FURTHER AS THE APP DEVELOPS MORE (more structured folders etc...)
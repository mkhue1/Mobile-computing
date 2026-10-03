import os
import requests

def get_igdb_credentials():
    url = "https://id.twitch.tv/oauth2/token"
    query_params = {"client_id": os.getenv("TWITCH_CLIENT_ID"), "client_secret": os.getenv("TWITCH_CLIENT_SECRET"), "grant_type": "client_credentials"}
    response = requests.post(url, query_params)
    print(response.json())
    return

# IGDB uses apicalypse query syntax, see https://apicalypse.io/syntax/  
def search_game(query: str):
    url = "https://api.igdb.com/v4/games"
    db_query = f"search \"{query}\"; fields name;"
    custom_headers  = {"Client-ID": "cgd7gdgj4jtqro9etusnomlhwtdb1j", "Authorization": "Bearer " + "47awsurdq1k0wxcxt0exyq44v3ak2d"}
    print(custom_headers)
    response = requests.post(url, data=db_query, headers=custom_headers)
    print("hello")
    print(response.json())

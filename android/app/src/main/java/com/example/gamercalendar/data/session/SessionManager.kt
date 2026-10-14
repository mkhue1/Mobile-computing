package com.example.gamercalendar.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "session"
)

data class StoredSession(
    val token: String,
    val username: String,
    val email: String,
)

class SessionManager(private val context: Context) {

    private val tokenKey = stringPreferencesKey("access_token")
    private val usernameKey = stringPreferencesKey("username")
    private val emailKey = stringPreferencesKey("email")

    @Volatile
    private var cachedToken: String? = null

    val accessToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[tokenKey].also { cachedToken = it }
    }

    val username: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[usernameKey]
    }

    val email: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[emailKey]
    }

    fun peekToken(): String? = cachedToken

    suspend fun getToken(): String? {
        val token = context.dataStore.data.first()[tokenKey]
        cachedToken = token
        return token
    }

    suspend fun readSession(): StoredSession? {
        val prefs = context.dataStore.data.first()
        val token = prefs[tokenKey] ?: return null
        val username = prefs[usernameKey] ?: return null
        val email = prefs[emailKey] ?: return null
        cachedToken = token
        return StoredSession(token, username, email)
    }

    suspend fun saveSession(token: String, username: String, email: String) {
        cachedToken = token
        context.dataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[usernameKey] = username
            prefs[emailKey] = email
        }
    }

    suspend fun clearSession() {
        cachedToken = null
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}

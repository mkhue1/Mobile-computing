package com.example.gamercalendar.util

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import retrofit2.HttpException

/**
 * Extracts the human-readable message from a FastAPI error response, which is
 * either `{"detail": "..."}` or a validation error list containing `"msg"` fields.
 */
fun apiErrorDetail(e: Exception): String? {
    if (e !is HttpException) return null
    val body = try {
        e.response()?.errorBody()?.string()
    } catch (_: Exception) {
        null
    }
    if (body.isNullOrBlank()) return null
    return apiErrorDetailFromBody(body)
}

internal fun apiErrorDetailFromBody(body: String): String? {
    val root = try {
        JSONTokener(body).nextValue()
    } catch (_: Exception) {
        return null
    }
    if (root !is JSONObject || !root.has("detail")) return null

    return when (val detail = root.get("detail")) {
        is String -> detail.takeIf { it.isNotBlank() }
        is JSONArray -> detail.keys().firstNotNullOfOrNull { index ->
            when (val item = detail.opt(index)) {
                is String -> item.takeIf { it.isNotBlank() }
                is JSONObject -> item.optString("msg").takeIf { it.isNotBlank() }
                else -> null
            }
        }
        else -> null
    }
}

private fun JSONArray.keys(): Iterable<Int> = 0 until length()

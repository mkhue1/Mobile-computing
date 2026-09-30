package com.example.gamercalendar.util

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

    val detail = Regex("\"detail\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    if (!detail.isNullOrBlank()) return detail

    val listDetail = Regex("\"msg\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
    if (!listDetail.isNullOrBlank()) return listDetail

    return null
}

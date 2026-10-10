package com.example.gamercalendar.data.repository

import com.example.gamercalendar.data.api.ApiClient
import com.example.gamercalendar.data.model.User
import com.example.gamercalendar.data.session.CurrentUserStore
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class UserRepository {

    suspend fun getUsers(search: String? = null, limit: Int? = null): List<User> {
        return ApiClient.api.getUsers(search = search, limit = limit)
    }

    suspend fun getCurrentUser(): User {
        return ApiClient.api.me().also { CurrentUserStore.set(it) }
    }

    suspend fun uploadAvatar(jpeg: ByteArray): User {
        val body = jpeg.toRequestBody("image/jpeg".toMediaType())
        val part = MultipartBody.Part.createFormData("file", "avatar.jpg", body)
        return ApiClient.api.uploadAvatar(part).also { CurrentUserStore.set(it) }
    }

    suspend fun deleteAvatar(): User {
        ApiClient.api.deleteAvatar()
        return getCurrentUser()
    }
}

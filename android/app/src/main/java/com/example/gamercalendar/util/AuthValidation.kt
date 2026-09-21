package com.example.gamercalendar.util

import android.util.Patterns

object AuthValidation {

    private val usernamePattern = Regex("^[a-zA-Z0-9_-]{3,120}$")
    private val letterPattern = Regex("[A-Za-z]")
    private val digitPattern = Regex("\\d")

    fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    }

    fun isValidUsername(username: String): Boolean {
        return usernamePattern.matches(username.trim())
    }

    fun isValidPassword(password: String): Boolean {
        return password.length >= 8 &&
            letterPattern.containsMatchIn(password) &&
            digitPattern.containsMatchIn(password)
    }

    fun passwordError(password: String): String? {
        if (password.isEmpty()) return null
        if (password.length < 8) {
            return "Password must be at least 8 characters"
        }
        if (!letterPattern.containsMatchIn(password) || !digitPattern.containsMatchIn(password)) {
            return "Password must contain at least one letter and one digit"
        }
        return null
    }
}

package com.example.picencrypt.utils

enum class PasswordStrength(val level: Int, val scoreRatio: Float) {
    EMPTY(0, 0f),
    VERY_WEAK(1, 0.25f),
    WEAK(2, 0.5f),
    GOOD(3, 0.75f),
    STRONG(4, 1.0f)
}

/**
 * Real-time password entropy and strength evaluator for AES-256 container encryption.
 */
object PasswordStrengthEvaluator {

    fun evaluate(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength.EMPTY
        if (password.length < 6) return PasswordStrength.VERY_WEAK

        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { it.isLowerCase() } && password.any { it.isUpperCase() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when {
            score >= 4 -> PasswordStrength.STRONG
            score >= 3 -> PasswordStrength.GOOD
            score >= 2 -> PasswordStrength.WEAK
            else -> PasswordStrength.VERY_WEAK
        }
    }
}

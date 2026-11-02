package com.example.picencrypt

import com.example.picencrypt.utils.PasswordStrength
import com.example.picencrypt.utils.PasswordStrengthEvaluator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests verifying password strength grading across different entropy levels.
 */
class PasswordStrengthEvaluatorTest {

    @Test
    fun testEmptyPassword() {
        assertEquals(PasswordStrength.EMPTY, PasswordStrengthEvaluator.evaluate(""))
    }

    @Test
    fun testVeryWeakPassword() {
        assertEquals(PasswordStrength.VERY_WEAK, PasswordStrengthEvaluator.evaluate("123"))
        assertEquals(PasswordStrength.VERY_WEAK, PasswordStrengthEvaluator.evaluate("abc"))
    }

    @Test
    fun testWeakPassword() {
        // 6-7 digits only
        assertEquals(PasswordStrength.VERY_WEAK, PasswordStrengthEvaluator.evaluate("123456"))
        // 8 characters, only lowercase and digits
        assertEquals(PasswordStrength.WEAK, PasswordStrengthEvaluator.evaluate("pass1234"))
    }

    @Test
    fun testGoodPassword() {
        // 8 chars, mixed case and digits
        assertEquals(PasswordStrength.GOOD, PasswordStrengthEvaluator.evaluate("Pass1234"))
    }

    @Test
    fun testStrongPassword() {
        // >= 12 chars, mixed case, digits and symbols
        assertEquals(PasswordStrength.STRONG, PasswordStrengthEvaluator.evaluate("P@ssw0rd!2026Strong"))
    }
}

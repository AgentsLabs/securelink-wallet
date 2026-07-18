package com.securelink.wallet

import com.securelink.wallet.security.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordGeneratorTest {
    @Test
    fun generatedPasswordHasRequestedLengthAndRequiredCharacterClasses() {
        val password = PasswordGenerator().generate(24)

        assertEquals(24, password.length)
        assertTrue(password.any { it.isLowerCase() })
        assertTrue(password.any { it.isUpperCase() })
        assertTrue(password.any { it.isDigit() })
        assertTrue(password.any { !it.isLetterOrDigit() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun generatedPasswordRejectsShortLength() {
        PasswordGenerator().generate(8)
    }
}

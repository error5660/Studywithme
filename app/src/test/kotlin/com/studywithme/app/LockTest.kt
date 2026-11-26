package com.studywithme.app

import com.studywithme.app.lock.LockPolicy
import com.studywithme.app.lock.PasswordHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LockTest {
    private val own = "com.studywithme.app"

    @Test fun passwordIsCaseInsensitiveAndRejectsWrongOnes() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash(PasswordHasher.normalizePassword("AbCd"), salt)
        assertTrue(PasswordHasher.matches(PasswordHasher.normalizePassword("abcd"), salt, hash))
        assertTrue(PasswordHasher.matches(PasswordHasher.normalizePassword(" ABCD "), salt, hash))
        assertFalse(PasswordHasher.matches(PasswordHasher.normalizePassword("abce"), salt, hash))
    }

    @Test fun hashIsSaltedAndNeverTheInput() {
        val a = PasswordHasher.hash("abcd", PasswordHasher.newSalt())
        val b = PasswordHasher.hash("abcd", PasswordHasher.newSalt())
        assertNotEquals(PasswordHasher.encode(a), PasswordHasher.encode(b))
        assertFalse(PasswordHasher.encode(a).contains("abcd"))
    }

    @Test fun onlyFourEnglishLettersAreValid() {
        assertTrue(PasswordHasher.isValidPassword("abcd"))
        assertFalse(PasswordHasher.isValidPassword("abc"))
        assertFalse(PasswordHasher.isValidPassword("abcde"))
        assertFalse(PasswordHasher.isValidPassword("ab1d"))
        assertFalse(PasswordHasher.isValidPassword("가나다라"))
    }

    @Test fun recoveryCodeHasTwelveCharsAndSurvivesRetyping() {
        val code = PasswordHasher.newRecoveryCode()
        assertTrue(Regex("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}").matches(code))
        assertEquals(PasswordHasher.normalizeRecovery(code), PasswordHasher.normalizeRecovery(code.lowercase().replace("-", " ")))
    }

    @Test fun everyAppIsBlockedExceptOwnPhoneAndTheAllowlist() {
        fun blocked(pkg: String?, user: Set<String> = emptySet(), dyn: Set<String> = emptySet()) = LockPolicy.isBlocked(pkg, own, user, dyn)
        assertTrue(blocked("com.kakao.talk"))
        assertTrue(blocked("com.sec.android.app.launcher"))      // home screen is blocked too
        assertFalse(blocked(own))
        assertFalse(blocked("com.samsung.android.dialer"))        // phone is always allowed
        assertFalse(blocked("com.samsung.android.incallui"))
        assertFalse(blocked("com.android.systemui"))
        assertFalse(blocked("com.kakao.talk", user = setOf("com.kakao.talk")))
        assertFalse(blocked("com.samsung.android.honeyboard", dyn = setOf("com.samsung.android.honeyboard")))
    }

    @Test fun settingsAndPlayStoreStayBlockedEvenIfTheUserAllowsThem() {
        val user = setOf("com.android.settings", "com.android.vending")
        assertTrue(LockPolicy.isBlocked("com.android.settings", own, user, emptySet()))
        assertTrue(LockPolicy.isBlocked("com.android.vending", own, user, emptySet()))
        assertTrue(LockPolicy.isBlocked("com.google.android.packageinstaller", own, user, emptySet()))
    }
}

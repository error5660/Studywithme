package com.studywithme.app.lock

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Salted PBKDF2 hashing. Plain passwords and recovery codes are never stored. */
object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I, easy to copy by hand

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(secret: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, 256)).encoded

    fun matches(secret: String, salt: ByteArray, expected: ByteArray): Boolean =
        MessageDigest.isEqual(hash(secret, salt), expected) // constant-time compare

    /** The password is 4 English letters and case does not matter. */
    fun normalizePassword(input: String): String = input.trim().lowercase()
    fun isValidPassword(normalized: String): Boolean = normalized.length == 4 && normalized.all { it in 'a'..'z' }

    /** 12 random characters shown as XXXX-XXXX-XXXX. */
    fun newRecoveryCode(): String {
        val rnd = SecureRandom()
        return (0 until 12).map { RECOVERY_ALPHABET[rnd.nextInt(RECOVERY_ALPHABET.length)] }.chunked(4).joinToString("-") { it.joinToString("") }
    }

    fun normalizeRecovery(input: String): String = input.uppercase().filter { it.isLetterOrDigit() }

    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun decode(text: String): ByteArray = Base64.getDecoder().decode(text)
}

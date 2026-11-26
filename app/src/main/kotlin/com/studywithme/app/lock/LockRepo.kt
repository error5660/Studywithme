package com.studywithme.app.lock

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class LockState(
    val endMs: Long = 0,
    val startMs: Long = 0,
    val subjectId: Long? = null,
    /** Apps the user ticked. The phone and emergency apps are allowed on top of this, always. */
    val allowlist: Set<String> = emptySet(),
    val hasPassword: Boolean = false,
) {
    fun active(now: Long = System.currentTimeMillis()) = endMs > now
}

class Credentials(val pwSalt: ByteArray, val pwHash: ByteArray, val recSalt: ByteArray?, val recHash: ByteArray?)

private val Context.lockStore by preferencesDataStore("lock")

private object K {
    val endMs = longPreferencesKey("end_ms")
    val startMs = longPreferencesKey("start_ms")
    val subject = longPreferencesKey("subject_id")
    val allowlist = stringSetPreferencesKey("allowlist")
    val pwSalt = stringPreferencesKey("pw_salt")
    val pwHash = stringPreferencesKey("pw_hash")
    val recSalt = stringPreferencesKey("rec_salt")
    val recHash = stringPreferencesKey("rec_hash")
}

private fun Preferences.toState() = LockState(
    endMs = this[K.endMs] ?: 0, startMs = this[K.startMs] ?: 0, subjectId = this[K.subject],
    allowlist = this[K.allowlist] ?: emptySet(), hasPassword = this[K.pwHash] != null,
)

class LockRepo(private val context: Context) {
    val flow: Flow<LockState> = context.lockStore.data.map { it.toState() }

    suspend fun credentials(): Credentials? {
        val p = context.lockStore.data.first()
        val salt = p[K.pwSalt] ?: return null
        val hash = p[K.pwHash] ?: return null
        return Credentials(PasswordHasher.decode(salt), PasswordHasher.decode(hash), p[K.recSalt]?.let(PasswordHasher::decode), p[K.recHash]?.let(PasswordHasher::decode))
    }

    suspend fun savePassword(salt: ByteArray, hash: ByteArray) {
        context.lockStore.edit { it[K.pwSalt] = PasswordHasher.encode(salt); it[K.pwHash] = PasswordHasher.encode(hash) }
    }

    suspend fun saveRecovery(salt: ByteArray, hash: ByteArray) {
        context.lockStore.edit { it[K.recSalt] = PasswordHasher.encode(salt); it[K.recHash] = PasswordHasher.encode(hash) }
    }

    suspend fun setLock(endMs: Long, startMs: Long, subjectId: Long?) {
        context.lockStore.edit {
            it[K.endMs] = endMs; it[K.startMs] = startMs
            if (subjectId == null) it.remove(K.subject) else it[K.subject] = subjectId
        }
    }

    suspend fun clearLock() {
        context.lockStore.edit { it.remove(K.endMs); it.remove(K.startMs); it.remove(K.subject) }
    }

    suspend fun setAllowlist(packages: Set<String>) {
        context.lockStore.edit { it[K.allowlist] = packages }
    }
}

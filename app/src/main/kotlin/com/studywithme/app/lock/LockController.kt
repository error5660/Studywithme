package com.studywithme.app.lock

import android.content.Context
import android.os.SystemClock
import com.studywithme.app.data.AppDatabase
import com.studywithme.app.data.EarlyUnlock
import com.studywithme.app.reminders.Notifier
import com.studywithme.app.reminders.ReminderScheduler
import com.studywithme.app.timer.Alerter
import com.studywithme.app.timer.TimerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Owns the lock's lifecycle (start, expire, early unlock), the password and the allowlist. */
class LockController(
    private val context: Context,
    private val repo: LockRepo,
    private val db: AppDatabase,
    private val engine: TimerEngine,
    private val scope: CoroutineScope,
    private val reminders: ReminderScheduler,
    private val notifier: Notifier,
    private val alerter: Alerter,
    private val quiet: () -> com.studywithme.app.data.QuietMode,
) {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Last known state, readable synchronously (the accessibility service needs it on every window change). */
    @Volatile
    var snapshot = LockState()
        private set

    val state = repo.flow

    private var failedAttempts = 0
    private var blockedUntilRt = 0L

    init {
        scope.launch { repo.flow.collect { snapshot = it } }
    }

    /** Called on every process start and after reboot: resume an unfinished lock. */
    fun restore() {
        scope.launch {
            val s = repo.flow.first()
            snapshot = s
            val now = System.currentTimeMillis()
            when {
                s.endMs > now -> {
                    engine.lockActive = true
                    _active.value = true
                    reminders.scheduleLockEnd(s.endMs)
                    LockService.start(context)
                }
                s.endMs != 0L -> finish(early = false, silent = true) // expired while the phone was off
                else -> _active.value = false
            }
        }
    }

    suspend fun start(durationMin: Int, subjectId: Long?) {
        val now = System.currentTimeMillis()
        val end = now + durationMin * 60_000L
        repo.setLock(end, now, subjectId)
        snapshot = snapshot.copy(endMs = end, startMs = now, subjectId = subjectId)
        engine.lockActive = true
        _active.value = true
        reminders.scheduleLockEnd(end)
        LockService.start(context)
    }

    /** Ends the lock: [early] means the password was used, otherwise the time ran out. Safe to call twice. */
    suspend fun finish(early: Boolean, silent: Boolean = false) {
        val s = snapshot
        if (s.endMs == 0L && !_active.value) return
        val now = System.currentTimeMillis()
        if (early) db.earlyUnlockDao().insert(EarlyUnlock(atMs = now, remainingMs = (s.endMs - now).coerceAtLeast(0), lockStartMs = s.startMs))
        repo.clearLock()
        snapshot = snapshot.copy(endMs = 0, startMs = 0, subjectId = null)
        engine.lockActive = false
        _active.value = false
        reminders.cancelLockEnd()
        LockService.stop(context)
        if (!early && !silent) {
            notifier.postLockEnded(quiet())
            alerter.alert(quiet())
        }
    }

    /** Called by the alarm and the service when the time is up. */
    fun endIfExpired() {
        scope.launch { if (snapshot.endMs != 0L && !snapshot.active()) finish(early = false) }
    }

    // ---- password -------------------------------------------------------------------------

    /** Remaining wait after too many wrong attempts, in seconds (0 = may try). */
    fun lockoutSeconds(): Int = ((blockedUntilRt - SystemClock.elapsedRealtime()) / 1000).toInt().coerceAtLeast(0).let { if (it == 0 && blockedUntilRt > SystemClock.elapsedRealtime()) 1 else it }

    /** True if [input] is the password or the recovery code. Five wrong tries in a row block input for a minute. */
    suspend fun verify(input: String): Boolean {
        if (lockoutSeconds() > 0) return false
        val ok = withContext(Dispatchers.Default) {
            val c = repo.credentials() ?: return@withContext false
            val pw = PasswordHasher.normalizePassword(input)
            (PasswordHasher.isValidPassword(pw) && PasswordHasher.matches(pw, c.pwSalt, c.pwHash)) ||
                (c.recSalt != null && c.recHash != null && PasswordHasher.matches(PasswordHasher.normalizeRecovery(input), c.recSalt, c.recHash))
        }
        if (ok) failedAttempts = 0 else if (++failedAttempts >= MAX_ATTEMPTS) { failedAttempts = 0; blockedUntilRt = SystemClock.elapsedRealtime() + LOCKOUT_MS }
        return ok
    }

    /** First-time setup. Returns the recovery code, which is shown once and never stored in plain text. */
    suspend fun createPassword(password: String): String = withContext(Dispatchers.Default) {
        val code = PasswordHasher.newRecoveryCode()
        changePassword(password)
        val salt = PasswordHasher.newSalt()
        repo.saveRecovery(salt, PasswordHasher.hash(PasswordHasher.normalizeRecovery(code), salt))
        code
    }

    suspend fun changePassword(password: String) = withContext(Dispatchers.Default) {
        val salt = PasswordHasher.newSalt()
        repo.savePassword(salt, PasswordHasher.hash(PasswordHasher.normalizePassword(password), salt))
        snapshot = snapshot.copy(hasPassword = true)
    }

    suspend fun setAllowlist(packages: Set<String>) = repo.setAllowlist(packages)

    companion object {
        const val MAX_ATTEMPTS = 5
        const val LOCKOUT_MS = 60_000L
    }
}

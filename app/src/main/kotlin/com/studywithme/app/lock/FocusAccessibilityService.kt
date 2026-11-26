package com.studywithme.app.lock

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.telecom.TelecomManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.studywithme.app.StudyApp

/**
 * Watches which app comes to the front. While the lock is on, any app that is not allowed
 * is answered immediately by bringing the lock screen back.
 */
class FocusAccessibilityService : AccessibilityService() {
    private var lastBounceRt = 0L
    private var dynamicAllowed: Set<String> = emptySet()
    private var dynamicAtRt = -DYNAMIC_REFRESH_MS

    override fun onServiceConnected() {
        (application as StudyApp).container.lock.restore()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val state = (application as StudyApp).container.lock.snapshot
        if (!state.active()) return
        if (!LockPolicy.isBlocked(event.packageName?.toString(), packageName, state.allowlist, allowedNow())) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastBounceRt < BOUNCE_GAP_MS) return
        lastBounceRt = now
        bounce()
    }

    private fun bounce() {
        try {
            startActivity(LockActivity.intent(this))
        } catch (e: Exception) {
            performGlobalAction(GLOBAL_ACTION_HOME) // fallback: at least leave the blocked app
        }
    }

    /** Keyboard apps and the default dialer change rarely, so they are looked up at most every 30 seconds. */
    private fun allowedNow(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (now - dynamicAtRt > DYNAMIC_REFRESH_MS) {
            dynamicAtRt = now
            val set = HashSet<String>()
            getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.forEach { set += it.packageName }
            getSystemService(TelecomManager::class.java)?.let { t -> t.defaultDialerPackage?.let(set::add); t.systemDialerPackage?.let(set::add) }
            dynamicAllowed = set
        }
        return dynamicAllowed
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val BOUNCE_GAP_MS = 400L
        const val DYNAMIC_REFRESH_MS = 30_000L
    }
}

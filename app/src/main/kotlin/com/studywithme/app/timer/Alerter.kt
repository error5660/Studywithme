package com.studywithme.app.timer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.VibratorManager
import com.studywithme.app.data.QuietMode
import com.studywithme.app.data.SettingsRepo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Plays an in-app alert that obeys the quiet-mode setting. The screen pulse is always shown. */
class Alerter(private val context: Context) {
    private val _pulses = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pulses: SharedFlow<Unit> = _pulses.asSharedFlow()
    private var ringtone: Ringtone? = null

    fun alert(mode: QuietMode) {
        _pulses.tryEmit(Unit)
        if (mode != QuietMode.Silent) vibrate()
        if (mode == QuietMode.Normal) playSound()
    }

    private fun vibrate() {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 220, 140, 220), -1))
    }

    private fun playSound() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone?.stop()
        // Alarm usage keeps the sound audible even if Do Not Disturb (priority) is on.
        ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            play()
        }
    }
}

/** Turns Do Not Disturb on while studying and puts the previous state back afterwards. */
class DndController(context: Context, private val repo: SettingsRepo) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    fun hasAccess(): Boolean = nm.isNotificationPolicyAccessGranted

    suspend fun enable() {
        if (!hasAccess() || repo.savedDndFilter() != null) return
        val previous = nm.currentInterruptionFilter
        if (previous == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) return
        repo.setSavedDndFilter(previous)
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
    }

    suspend fun restore() {
        val previous = repo.savedDndFilter() ?: return
        if (hasAccess()) nm.setInterruptionFilter(previous)
        repo.setSavedDndFilter(null)
    }
}

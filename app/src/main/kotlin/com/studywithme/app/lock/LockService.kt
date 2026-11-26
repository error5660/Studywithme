package com.studywithme.app.lock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.studywithme.app.R
import com.studywithme.app.StudyApp
import com.studywithme.app.reminders.Notifier
import com.studywithme.app.timer.TimerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Foreground service that keeps the app alive during the lock and shows the countdown notification. */
class LockService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val c = (application as StudyApp).container
        val s = c.lock.snapshot
        ServiceCompat.startForeground(this, NOTIFICATION_ID, build(s.endMs, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        job?.cancel()
        job = c.scope.launch {
            launch {
                // The alarm is the main way the lock ends; this is the backup while the process is alive.
                delay((c.lock.snapshot.endMs - System.currentTimeMillis()).coerceAtLeast(0) + 500)
                c.lock.endIfExpired()
            }
            combine(c.lock.state, c.engine.state, c.db.subjectDao().observeAll()) { lock, timer, subjects -> Triple(lock.endMs, timer, subjects) }
                .collect { (end, timer, subjects) ->
                    val studying = if (timer.running) subjects.firstOrNull { it.id == timer.subjectId }?.name else null
                    getSystemService(android.app.NotificationManager::class.java).notify(NOTIFICATION_ID, build(end, studying))
                }
        }
        return START_STICKY
    }

    private fun build(endMs: Long, studying: String?): Notification {
        val open = PendingIntent.getActivity(this, 1, LockActivity.intent(this), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, Notifier.CH_LOCK)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.lock_notif_title))
            .setContentText(studying?.let { getString(R.string.lock_notif_studying, it) } ?: getString(R.string.lock_notif_idle))
            .setUsesChronometer(true).setChronometerCountDown(true).setWhen(endMs)
            .setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 7001

        fun start(context: Context) {
            try { ContextCompat.startForegroundService(context, Intent(context, LockService::class.java)) } catch (e: Exception) { /* the accessibility service still enforces the lock */ }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LockService::class.java))
        }
    }
}

package com.studywithme.app.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.studywithme.app.MainActivity
import com.studywithme.app.R
import com.studywithme.app.StudyApp
import com.studywithme.app.data.CalendarEvent
import com.studywithme.app.data.QuietMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Events with no travel time are announced this many minutes before they start. */
const val DEFAULT_LEAD_MIN = 5

fun CalendarEvent.occursOn(day: LocalDate): Boolean {
    val first = LocalDate.ofEpochDay(dateEpochDay)
    return if (weekly) !day.isBefore(first) && day.dayOfWeek == first.dayOfWeek else day == first
}

fun CalendarEvent.leadMin() = if (travelMin > 0) travelMin else DEFAULT_LEAD_MIN

/** The moment the reminder for the occurrence on [day] fires: start time minus travel time. */
fun CalendarEvent.reminderMs(day: LocalDate, zone: ZoneId): Long =
    day.atStartOfDay(zone).toInstant().toEpochMilli() + (startMin - leadMin()) * 60_000L

/** Earliest reminder strictly after [nowMs] within the next two weeks, or null. */
fun nextReminderMs(events: List<CalendarEvent>, nowMs: Long, zone: ZoneId): Long? {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return (0L..15L).asSequence().map { today.plusDays(it) }.flatMap { day ->
        events.asSequence().filter { it.reminderOn && it.occursOn(day) }.map { it.reminderMs(day, zone) }
    }.filter { it > nowMs }.minOrNull()
}

/** Occurrences whose reminder time falls in (nowMs - windowMs, nowMs]; these are the ones to announce now. */
fun dueReminders(events: List<CalendarEvent>, nowMs: Long, windowMs: Long, zone: ZoneId): List<Pair<CalendarEvent, LocalDate>> {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return (0L..1L).map { today.plusDays(it) }.flatMap { day ->
        events.filter { it.reminderOn && it.occursOn(day) && it.reminderMs(day, zone) in (nowMs - windowMs + 1)..nowMs }.map { it to day }
    }
}

/** Keeps exactly one exact alarm pending: the next reminder across all events. */
class ReminderScheduler(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun pending() = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(events: List<CalendarEvent>) {
        val at = nextReminderMs(events, System.currentTimeMillis(), ZoneId.systemDefault())
        if (at == null) { alarms.cancel(pending()); return }
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending())
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending()) // exact alarms revoked: late but not lost
        }
    }

    companion object { const val ACTION_ALARM = "com.studywithme.app.REMINDER" }
}

/** Posts reminder notifications; the channel is chosen by the current quiet-mode setting. */
class Notifier(private val context: Context) {
    private val nm = context.getSystemService(NotificationManager::class.java)

    init {
        fun channel(id: String, name: Int, sound: Boolean, vibrate: Boolean) = NotificationChannel(id, context.getString(name), NotificationManager.IMPORTANCE_HIGH).apply {
            if (sound) setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build(),
            ) else setSound(null, null)
            enableVibration(vibrate)
        }
        nm.createNotificationChannels(listOf(
            channel(CH_NORMAL, R.string.channel_reminder_normal, sound = true, vibrate = true),
            channel(CH_VIBRATE, R.string.channel_reminder_vibrate, sound = false, vibrate = true),
            channel(CH_SILENT, R.string.channel_reminder_silent, sound = false, vibrate = false),
        ))
    }

    fun postReminder(event: CalendarEvent, quiet: QuietMode) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val start = "%02d:%02d".format(event.startMin / 60, event.startMin % 60)
        val text = if (event.travelMin > 0) context.getString(R.string.reminder_depart, start, event.travelMin)
        else context.getString(R.string.reminder_soon, DEFAULT_LEAD_MIN, start)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val channel = when (quiet) { QuietMode.Normal -> CH_NORMAL; QuietMode.Vibrate -> CH_VIBRATE; QuietMode.Silent -> CH_SILENT }
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title).setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setAutoCancel(true).setContentIntent(open)
            .build()
        nm.notify(event.id.toInt(), n)
    }

    companion object {
        const val CH_NORMAL = "reminder_normal"
        const val CH_VIBRATE = "reminder_vibrate"
        const val CH_SILENT = "reminder_silent"
    }
}

/** Fires on the alarm, after a reboot, app update, or clock/timezone change: announce what is due, then arm the next alarm. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = (context.applicationContext as StudyApp).container
        val pending = goAsync()
        c.scope.launch(Dispatchers.IO) {
            try {
                val events = c.db.eventDao().getAll()
                val quiet = c.settingsRepo.flow.first().quiet
                if (intent.action == ReminderScheduler.ACTION_ALARM) {
                    dueReminders(events, System.currentTimeMillis(), 2 * 60_000L, ZoneId.systemDefault())
                        .forEach { (e, _) -> c.notifier.postReminder(e, quiet) }
                }
                c.reminders.schedule(events)
            } finally {
                pending.finish()
            }
        }
    }
}


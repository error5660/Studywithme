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
import com.studywithme.app.data.nextDailyMs
import com.studywithme.app.data.nextWeeklyMs
import com.studywithme.app.data.studyDay
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

    private fun arm(pi: PendingIntent, at: Long) {
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) // exact alarms revoked: late but not lost
        }
    }

    private fun simplePending(code: Int, action: String) = PendingIntent.getBroadcast(
        context, code, Intent(context, ReminderReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(events: List<CalendarEvent>) {
        val at = nextReminderMs(events, System.currentTimeMillis(), ZoneId.systemDefault())
        if (at == null) alarms.cancel(pending()) else arm(pending(), at)
    }

    /** Daily "wrong answers to review" reminder and the Sunday 21:00 weekly report: each is re-armed after it fires. */
    fun scheduleDaily() = arm(simplePending(2, ACTION_REVIEW), nextDailyMs(System.currentTimeMillis(), REVIEW_HOUR))
    fun scheduleWeekly() = arm(simplePending(3, ACTION_WEEKLY), nextWeeklyMs(System.currentTimeMillis()))

    private fun lockEndPending() = PendingIntent.getBroadcast(
        context, 1, Intent(context, ReminderReceiver::class.java).setAction(ACTION_LOCK_END),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** A separate exact alarm that ends the focus lock on time even if the app process was killed. */
    fun scheduleLockEnd(endMs: Long) = arm(lockEndPending(), endMs)

    fun cancelLockEnd() = alarms.cancel(lockEndPending())

    companion object {
        const val ACTION_ALARM = "com.studywithme.app.REMINDER"
        const val ACTION_LOCK_END = "com.studywithme.app.LOCK_END"
        const val ACTION_REVIEW = "com.studywithme.app.REVIEW"
        const val ACTION_WEEKLY = "com.studywithme.app.WEEKLY"
        const val REVIEW_HOUR = 18
    }
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
        nm.createNotificationChannel(NotificationChannel(CH_LOCK, context.getString(R.string.channel_lock), NotificationManager.IMPORTANCE_LOW))
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

    fun postGoalReached(studied: String, quiet: QuietMode) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val channel = when (quiet) { QuietMode.Normal -> CH_NORMAL; QuietMode.Vibrate -> CH_VIBRATE; QuietMode.Silent -> CH_SILENT }
        nm.notify(GOAL_REACHED_ID, NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.goal_reached_title)).setContentText(context.getString(R.string.goal_reached_body, studied))
            .setAutoCancel(true).setContentIntent(open).build())
    }

    private fun post(id: Int, title: String, text: String, quiet: QuietMode) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val channel = when (quiet) { QuietMode.Normal -> CH_NORMAL; QuietMode.Vibrate -> CH_VIBRATE; QuietMode.Silent -> CH_SILENT }
        nm.notify(id, NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(open).build())
    }

    fun postReviewDue(count: Int, quiet: QuietMode) =
        post(REVIEW_DUE_ID, context.getString(R.string.review_notif_title), context.getString(R.string.review_notif_body, count), quiet)

    fun postWeeklyReport(totalMs: Long, prevMs: Long, quiet: QuietMode) {
        fun fmt(ms: Long) = (ms / 60_000).toInt().let { context.getString(R.string.duration_hm, it / 60, it % 60) }
        val diff = totalMs - prevMs
        val change = context.getString(if (diff >= 0) R.string.report_change_up else R.string.report_change_down, fmt(kotlin.math.abs(diff)))
        post(WEEKLY_REPORT_ID, context.getString(R.string.report_notif_title), context.getString(R.string.report_notif_body, fmt(totalMs), change), quiet)
    }

    fun postLockEnded(quiet: QuietMode) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val channel = when (quiet) { QuietMode.Normal -> CH_NORMAL; QuietMode.Vibrate -> CH_VIBRATE; QuietMode.Silent -> CH_SILENT }
        nm.notify(LOCK_ENDED_ID, NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.lock_ended_title)).setContentText(context.getString(R.string.lock_ended_body))
            .setAutoCancel(true).setContentIntent(open).build())
    }

    companion object {
        const val LOCK_ENDED_ID = 7002
        const val GOAL_REACHED_ID = 7003
        const val REVIEW_DUE_ID = 7004
        const val WEEKLY_REPORT_ID = 7005
        const val CH_LOCK = "lock_status"
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
                when (intent.action) {
                    ReminderScheduler.ACTION_LOCK_END -> c.lock.endIfExpired()
                    ReminderScheduler.ACTION_REVIEW -> c.db.noteDao().dueCount(studyDay(System.currentTimeMillis()).toEpochDay())
                        .let { if (it > 0) c.notifier.postReviewDue(it, quiet) }
                    ReminderScheduler.ACTION_WEEKLY -> {
                        val now = System.currentTimeMillis()
                        val r = c.reports.generate(com.studywithme.app.data.mondayOf(studyDay(now)), now)
                        c.notifier.postWeeklyReport(r.totalMs, r.prevTotalMs, quiet)
                    }
                    else -> { // alarm for an event, reboot, app update, clock change: also catch up a missed weekly report
                        c.lock.restore()
                        c.reports.ensureLatest()?.let { c.notifier.postWeeklyReport(it.totalMs, it.prevTotalMs, quiet) }
                    }
                }
                if (intent.action == ReminderScheduler.ACTION_ALARM) {
                    dueReminders(events, System.currentTimeMillis(), 2 * 60_000L, ZoneId.systemDefault())
                        .forEach { (e, _) -> c.notifier.postReminder(e, quiet) }
                }
                c.reminders.schedule(events)
                c.reminders.scheduleDaily()
                c.reminders.scheduleWeekly()
            } finally {
                pending.finish()
            }
        }
    }
}


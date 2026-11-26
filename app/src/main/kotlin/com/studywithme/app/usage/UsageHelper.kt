package com.studywithme.app.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process

/** Phone screen time from UsageStatsManager, minus this app and the home screen. Needs "usage access". */
class UsageHelper(private val context: Context) {
    fun hasAccess(): Boolean =
        context.getSystemService(AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED

    /** Packages whose foreground time is not "using the phone": this app, the launcher and system UI. */
    private fun ignored(): Set<String> {
        val pm = context.packageManager
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0).map { it.activityInfo.packageName }
        return (homes + context.packageName + "com.android.systemui" + "android").toSet()
    }

    /**
     * Adds up the time activities were on screen between [fromMs] and [toMs].
     * shortcut: Android keeps these events for only about a week, so older days read as 0.
     */
    fun phoneMs(fromMs: Long, toMs: Long): Long {
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(fromMs, toMs)
        val skip = ignored()
        val open = HashMap<String, Long>() // "package/class" -> time it came to the front
        val e = UsageEvents.Event()
        var total = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val key = "${e.packageName}/${e.className}"
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> open[key] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                    open.remove(key)?.let { if (e.packageName !in skip) total += e.timeStamp - it }
            }
        }
        for ((key, since) in open) if (key.substringBefore('/') !in skip) total += toMs - since // still on screen now
        return total
    }
}

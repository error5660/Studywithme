package com.studywithme.app.reminders

import com.studywithme.app.data.AppDatabase
import com.studywithme.app.data.DAY_MS
import com.studywithme.app.data.WeeklyReport
import com.studywithme.app.data.buildReport
import com.studywithme.app.data.lastWeeklyDueMs
import com.studywithme.app.data.mondayOf
import com.studywithme.app.data.studyDay
import com.studywithme.app.data.studyDayStartOf
import com.studywithme.app.usage.UsageHelper
import java.time.LocalDate

/** Collects the numbers for a weekly report from the database and phone usage, and stores it. */
class ReportService(private val db: AppDatabase, private val usage: UsageHelper) {
    suspend fun generate(weekStart: LocalDate, nowMs: Long = System.currentTimeMillis()): WeeklyReport {
        val from = studyDayStartOf(weekStart)
        val to = from + 7 * DAY_MS
        val report = buildReport(
            weekStart = weekStart,
            sessions = db.sessionDao().getBetween(from - 60 * DAY_MS, to),
            earlyUnlocks = db.earlyUnlockDao().countBetween(from, to),
            notesAdded = db.noteDao().addedBetween(weekStart.toEpochDay(), weekStart.toEpochDay() + 6),
            notesReviewed = db.noteDao().reviewsBetween(from, to),
            phoneMs = if (usage.hasAccess()) usage.phoneMs(from, minOf(to, nowMs)) else null,
            nowMs = nowMs,
        )
        db.reportDao().upsert(report)
        return report
    }

    /**
     * If the last Sunday-21:00 report is missing (phone was off, app not running), make it now.
     * Returns it only when it is still fresh (within 12 hours of Sunday 21:00), so it is worth a notification;
     * an older missing one is created silently.
     */
    suspend fun ensureLatest(nowMs: Long = System.currentTimeMillis()): WeeklyReport? {
        val due = lastWeeklyDueMs(nowMs)
        val weekStart = mondayOf(studyDay(due))
        if (db.reportDao().get(weekStart.toEpochDay()) != null) return null
        val report = generate(weekStart, nowMs)
        return if (nowMs - due < 12 * 3_600_000L) report else null
    }
}

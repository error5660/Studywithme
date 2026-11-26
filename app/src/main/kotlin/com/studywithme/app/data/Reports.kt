package com.studywithme.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Builds the weekly report for the Monday-to-Sunday week starting [weekStart]. [sessions] must cover the 60 days before the week's end. */
fun buildReport(
    weekStart: LocalDate,
    sessions: List<StudySession>,
    earlyUnlocks: Int,
    notesAdded: Int,
    notesReviewed: Int,
    phoneMs: Long?,
    nowMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): WeeklyReport {
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val totals = dailyTotals(sessions, days.map { studyDayStartOf(it, zone) })
    val prevTotal = dailyTotals(sessions, (1..7).map { studyDayStartOf(weekStart.minusDays(it.toLong()), zone) }).sumOf { it.totalMs }
    val bestIdx = totals.indices.maxByOrNull { totals[it].totalMs } ?: 0
    val bySubject = HashMap<Long?, Long>()
    totals.forEach { t -> t.bySubject.forEach { (id, v) -> bySubject[id] = (bySubject[id] ?: 0L) + v } }

    // streak counted up to the last day of the week that has happened already
    val lastDay = minOf(weekStart.plusDays(6), studyDay(nowMs, zone))
    val streakDaysList = (59 downTo 0).map { lastDay.minusDays(it.toLong()) }
    val streak = streakDays(dailyTotals(sessions, streakDaysList.map { studyDayStartOf(it, zone) }).map { it.totalMs })

    val weekStartMs = studyDayStartOf(weekStart, zone)
    val rated = sessions.filter { it.startMs >= weekStartMs && it.startMs < weekStartMs + 7 * DAY_MS && it.focusScore != null }
    return WeeklyReport(
        weekStartDay = weekStart.toEpochDay(), createdMs = nowMs,
        totalMs = totals.sumOf { it.totalMs }, prevTotalMs = prevTotal,
        bestDay = days[bestIdx].toEpochDay(), bestDayMs = totals[bestIdx].totalMs,
        streak = streak, avgFocus = if (rated.isEmpty()) null else rated.map { it.focusScore!! }.average().toFloat(),
        phoneMs = phoneMs, earlyUnlocks = earlyUnlocks, notesAdded = notesAdded, notesReviewed = notesReviewed,
        bySubject = encodeBySubject(bySubject),
    )
}

fun encodeBySubject(map: Map<Long?, Long>): String =
    map.entries.sortedByDescending { it.value }.joinToString(",") { "${it.key ?: "-"}:${it.value}" }

fun decodeBySubject(text: String): List<Pair<Long?, Long>> =
    if (text.isBlank()) emptyList() else text.split(",").map { it.substringBefore(':').let { id -> if (id == "-") null else id.toLong() } to it.substringAfter(':').toLong() }

fun mondayOf(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())

// ---- alarm times ----------------------------------------------------------------------------

/** The next Sunday 21:00 strictly after [nowMs]. */
fun nextWeeklyMs(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)
    var t = now.with(DayOfWeek.SUNDAY).withHour(21).withMinute(0).withSecond(0).withNano(0)
    if (t <= now) t = t.plusWeeks(1)
    return t.toInstant().toEpochMilli()
}

/** The most recent Sunday 21:00 at or before [nowMs]. */
fun lastWeeklyDueMs(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long = nextWeeklyMs(nowMs, zone).let {
    ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), zone).minusWeeks(1).toInstant().toEpochMilli()
}

/** The next [hour]:00 strictly after [nowMs]. */
fun nextDailyMs(nowMs: Long, hour: Int, zone: ZoneId = ZoneId.systemDefault()): Long {
    val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMs), zone)
    var t = now.withHour(hour).withMinute(0).withSecond(0).withNano(0)
    if (t <= now) t = t.plusDays(1)
    return t.toInstant().toEpochMilli()
}

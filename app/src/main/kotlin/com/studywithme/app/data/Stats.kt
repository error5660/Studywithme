package com.studywithme.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A day counts for the streak when at least this much was studied. */
const val STREAK_MIN_MS = 10 * 60_000L

/** Study time per day for [dayStarts] (each the start of a study day), clipped to that day. */
fun dailyTotals(sessions: List<StudySession>, dayStarts: List<Long>): List<TodayTotals> =
    dayStarts.map { computeTotals(sessions, it, null, 0, 0) }

/** [totals] is oldest to newest and the last item is today. Today may still be empty without breaking the streak. */
fun streakDays(totals: List<Long>, minMs: Long = STREAK_MIN_MS): Int {
    var i = totals.lastIndex
    if (i >= 0 && totals[i] < minMs) i--
    var n = 0
    while (i >= 0 && totals[i] >= minMs) { n++; i-- }
    return n
}

/** 0 = nothing, 1..4 = darker orange; levels are relative to the daily goal. */
fun heatLevel(ms: Long, goalMs: Long): Int = when {
    ms < 60_000 -> 0
    ms * 4 <= goalMs -> 1
    ms * 2 <= goalMs -> 2
    ms < goalMs -> 3
    else -> 4
}

enum class TimeOfDay { Morning, Afternoon, Evening, LateNight }

fun timeOfDay(hour: Int) = when (hour) {
    in 6..11 -> TimeOfDay.Morning
    in 12..17 -> TimeOfDay.Afternoon
    in 18..23 -> TimeOfDay.Evening
    else -> TimeOfDay.LateNight
}

class FocusAverages(
    val bySubject: Map<Long?, Float>,
    val byTimeOfDay: Map<TimeOfDay, Float>,
    val byDay: Map<LocalDate, Float>,
)

/** Average 1-5 focus score; sessions the student skipped rating are ignored. */
fun focusAverages(sessions: List<StudySession>, zone: ZoneId = ZoneId.systemDefault()): FocusAverages {
    val rated = sessions.filter { it.focusScore != null }
    fun <K> avg(key: (StudySession) -> K) = rated.groupBy(key).mapValues { (_, v) -> v.map { it.focusScore!! }.average().toFloat() }
    return FocusAverages(
        bySubject = avg { it.subjectId },
        byTimeOfDay = avg { timeOfDay(Instant.ofEpochMilli(it.startMs).atZone(zone).hour) },
        byDay = avg { studyDay(it.startMs, zone) },
    )
}

package com.studywithme.app.data

import java.time.Instant
import java.time.ZoneId

/** A "study day" starts at 04:00 so late-night study still counts for the day it belongs to. */
const val STUDY_DAY_START_HOUR = 4
const val DAY_MS = 24L * 60 * 60 * 1000 // shortcut: ignores DST, fine for Korea

fun studyDayStart(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val shifted = Instant.ofEpochMilli(nowMs).atZone(zone).minusHours(STUDY_DAY_START_HOUR.toLong())
    return shifted.toLocalDate().atStartOfDay(zone).plusHours(STUDY_DAY_START_HOUR.toLong()).toInstant().toEpochMilli()
}

class TodayTotals(val totalMs: Long, val bySubject: Map<Long?, Long>)

/** Sums sessions clipped to [dayStart, dayStart + 1 day), plus an optional not-yet-saved live segment. */
fun computeTotals(
    sessions: List<StudySession>,
    dayStart: Long,
    liveSubjectId: Long?,
    liveMs: Long,
    nowMs: Long,
): TodayTotals {
    val dayEnd = dayStart + DAY_MS
    val map = HashMap<Long?, Long>()
    for (s in sessions) {
        val ms = minOf(s.endMs, dayEnd) - maxOf(s.startMs, dayStart)
        if (ms > 0) map[s.subjectId] = (map[s.subjectId] ?: 0L) + ms
    }
    if (liveMs > 0) {
        val ms = minOf(nowMs, dayEnd) - maxOf(nowMs - liveMs, dayStart)
        if (ms > 0) map[liveSubjectId] = (map[liveSubjectId] ?: 0L) + ms
    }
    return TodayTotals(map.values.sum(), map)
}

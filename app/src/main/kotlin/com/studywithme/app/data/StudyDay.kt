package com.studywithme.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A "study day" starts at 04:00 so late-night study still counts for the day it belongs to. */
const val STUDY_DAY_START_HOUR = 4
const val DAY_MS = 24L * 60 * 60 * 1000 // shortcut: ignores DST, fine for Korea

/** The calendar date a moment belongs to, counting 00:00-03:59 as the previous day. */
fun studyDay(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(nowMs).atZone(zone).minusHours(STUDY_DAY_START_HOUR.toLong()).toLocalDate()

fun studyDayStart(nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    studyDay(nowMs, zone).atStartOfDay(zone).plusHours(STUDY_DAY_START_HOUR.toLong()).toInstant().toEpochMilli()

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

/** The moment a given study day begins (04:00 on that date). */
fun studyDayStartOf(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    date.atStartOfDay(zone).plusHours(STUDY_DAY_START_HOUR.toLong()).toInstant().toEpochMilli()

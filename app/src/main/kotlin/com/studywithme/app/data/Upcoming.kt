package com.studywithme.app.data

import com.studywithme.app.reminders.occursOn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Exams that are today or later, soonest first. */
fun upcomingExams(events: List<CalendarEvent>, today: LocalDate): List<Pair<CalendarEvent, LocalDate>> =
    events.filter { it.kind == CalendarEvent.KIND_EXAM }
        .map { it to LocalDate.ofEpochDay(it.dateEpochDay) }
        .filter { !it.second.isBefore(today) }
        .sortedWith(compareBy({ it.second }, { it.first.startMin }))

/** The next event that starts after [nowMs] within two weeks, with its start time. */
fun nextEventStart(events: List<CalendarEvent>, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<CalendarEvent, Long>? {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    return (0L..15L).asSequence().map { today.plusDays(it) }.flatMap { day ->
        events.asSequence().filter { it.occursOn(day) }.map { it to day.atStartOfDay(zone).toInstant().toEpochMilli() + it.startMin * 60_000L }
    }.filter { it.second > nowMs }.minByOrNull { it.second }
}

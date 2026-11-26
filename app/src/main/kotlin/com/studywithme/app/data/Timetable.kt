package com.studywithme.app.data

import java.time.LocalDate
import java.time.ZoneId

/** The grid runs from 06:00 to 02:00 the next day: 20 rows of 6 ten-minute cells. */
const val GRID_START_HOUR = 6
const val GRID_ROWS = 20
const val CELLS_PER_ROW = 6
const val CELL_COUNT = GRID_ROWS * CELLS_PER_ROW
const val CELL_MS = 10 * 60_000L

/** A cell is filled by the timer when at least this much of it was studied. */
const val MIN_FILL_MS = 5 * 60_000L

fun gridStartMs(day: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    day.atTime(GRID_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

/** For every cell, the subject studied longest in it, if that was at least 5 minutes. A null subject means a deleted one. */
fun autoCells(sessions: List<StudySession>, gridStart: Long): Map<Int, Long?> {
    val gridEnd = gridStart + CELL_COUNT * CELL_MS
    val ms = HashMap<Pair<Int, Long?>, Long>()
    for (s in sessions) {
        var t = maxOf(s.startMs, gridStart)
        val end = minOf(s.endMs, gridEnd)
        while (t < end) {
            val slot = ((t - gridStart) / CELL_MS).toInt()
            val cellEnd = gridStart + (slot + 1) * CELL_MS
            val part = minOf(end, cellEnd) - t
            ms.merge(slot to s.subjectId, part, Long::plus)
            t += part
        }
    }
    val result = HashMap<Int, Long?>()
    ms.entries.groupBy { it.key.first }.forEach { (slot, entries) ->
        val best = entries.maxBy { it.value }
        if (best.value >= MIN_FILL_MS) result[slot] = best.key.second
    }
    return result
}

/** Hand-painted cells win over the automatic fill; ERASED hides a cell. */
fun mergeCells(auto: Map<Int, Long?>, manual: List<ManualCell>): Map<Int, Long?> {
    val merged = HashMap(auto)
    for (m in manual) if (m.subjectId == ManualCell.ERASED) merged.remove(m.slot) else merged[m.slot] = m.subjectId
    return merged
}

package com.studywithme.app

import com.studywithme.app.data.CELL_MS
import com.studywithme.app.data.ManualCell
import com.studywithme.app.data.StudySession
import com.studywithme.app.data.TimeOfDay
import com.studywithme.app.data.autoCells
import com.studywithme.app.data.focusAverages
import com.studywithme.app.data.heatLevel
import com.studywithme.app.data.mergeCells
import com.studywithme.app.data.streakDays
import com.studywithme.app.data.timeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StatsTest {
    private val min = 60_000L
    private val h = 60 * min

    @Test fun streakCountsBackFromTodayAndTodayMayStillBeEmpty() {
        val ok = 30 * min
        assertEquals(3, streakDays(listOf(0, ok, ok, ok)))          // today done
        assertEquals(2, streakDays(listOf(0, 0, ok, ok, 0)))        // today empty so far: still 2
        assertEquals(0, streakDays(listOf(ok, ok, 0, 0)))           // yesterday missed: broken
        assertEquals(1, streakDays(listOf(0, 9 * min, ok)))         // under 10 minutes does not count
    }

    @Test fun heatLevelsAreRelativeToTheGoal() {
        val goal = 4 * h
        assertEquals(0, heatLevel(30_000, goal))
        assertEquals(1, heatLevel(h, goal))
        assertEquals(2, heatLevel(2 * h, goal))
        assertEquals(3, heatLevel(3 * h, goal))
        assertEquals(4, heatLevel(4 * h, goal))
    }

    @Test fun timeOfDayBuckets() {
        assertEquals(TimeOfDay.Morning, timeOfDay(6)); assertEquals(TimeOfDay.Afternoon, timeOfDay(12))
        assertEquals(TimeOfDay.Evening, timeOfDay(23)); assertEquals(TimeOfDay.LateNight, timeOfDay(0)); assertEquals(TimeOfDay.LateNight, timeOfDay(5))
    }

    @Test fun focusAveragesIgnoreUnratedSessions() {
        val seoul = ZoneId.of("Asia/Seoul")
        fun at(hour: Int) = ZonedDateTime.of(2026, 11, 26, hour, 0, 0, 0, seoul).toInstant().toEpochMilli()
        val list = listOf(
            StudySession(subjectId = 1, startMs = at(9), endMs = at(10), focusScore = 5),
            StudySession(subjectId = 1, startMs = at(20), endMs = at(21), focusScore = 3),
            StudySession(subjectId = 2, startMs = at(21), endMs = at(22), focusScore = null),
        )
        val a = focusAverages(list, seoul)
        assertEquals(4f, a.bySubject[1L]!!, 0.001f)
        assertNull(a.bySubject[2L])
        assertEquals(5f, a.byTimeOfDay[TimeOfDay.Morning]!!, 0.001f)
        assertEquals(3f, a.byTimeOfDay[TimeOfDay.Evening]!!, 0.001f)
    }

    // ---- timetable -----------------------------------------------------------------------

    private val grid = 1_000_000_000L

    @Test fun cellFillsOnlyWhenStudiedAtLeastFiveMinutes() {
        // 06:05-06:15 -> first cell has 5 min (filled), second cell has 5 min (filled)
        val s = StudySession(subjectId = 1, startMs = grid + 5 * min, endMs = grid + 15 * min)
        assertEquals(mapOf<Int, Long?>(0 to 1L, 1 to 1L), autoCells(listOf(s), grid))
        // 4 minutes is not enough
        assertTrue(autoCells(listOf(StudySession(subjectId = 1, startMs = grid, endMs = grid + 4 * min)), grid).isEmpty())
    }

    @Test fun theSubjectStudiedLongestWinsACell() {
        val a = StudySession(subjectId = 1, startMs = grid, endMs = grid + 4 * min + 30_000) // 4.5 min
        val b = StudySession(subjectId = 2, startMs = grid + 5 * min, endMs = grid + CELL_MS)  // 5 min
        assertEquals(2L, autoCells(listOf(a, b), grid)[0])
    }

    @Test fun sessionsOutsideTheGridAreClippedAndLongOnesSpanManyCells() {
        val long = StudySession(subjectId = 1, startMs = grid - 30 * min, endMs = grid + 25 * min)
        assertEquals(setOf(0, 1, 2), autoCells(listOf(long), grid).keys)  // 25 min in the grid: cells 0, 1 full, 2 half
    }

    @Test fun handPaintedCellsOverrideAndErasedHidesTheAutoFill() {
        val auto: Map<Int, Long?> = mapOf(0 to 1L, 1 to 1L)
        val merged = mergeCells(auto, listOf(ManualCell(1, 0, ManualCell.ERASED), ManualCell(1, 5, 3L), ManualCell(1, 1, 2L)))
        assertEquals(mapOf<Int, Long?>(1 to 2L, 5 to 3L), merged)
    }
}

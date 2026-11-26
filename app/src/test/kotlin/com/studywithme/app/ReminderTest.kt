package com.studywithme.app

import com.studywithme.app.data.CalendarEvent
import com.studywithme.app.reminders.dueReminders
import com.studywithme.app.reminders.nextReminderMs
import com.studywithme.app.reminders.occursOn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int) = ZonedDateTime.of(y, m, d, h, min, 0, 0, seoul).toInstant().toEpochMilli()

    // Mondays 21:00-23:00 math academy, 10 min travel, repeating from 2026-03-02 (a Monday).
    private val math = CalendarEvent(id = 1, title = "math", dateEpochDay = LocalDate.of(2026, 3, 2).toEpochDay(), startMin = 21 * 60, endMin = 23 * 60, weekly = true, travelMin = 10, color = 0)

    @Test fun weeklyEventOccursOnlyOnItsWeekdayAfterTheStart() {
        assertTrue(math.occursOn(LocalDate.of(2026, 11, 30)))   // a Monday
        assertFalse(math.occursOn(LocalDate.of(2026, 12, 1)))   // Tuesday
        assertFalse(math.occursOn(LocalDate.of(2026, 2, 23)))   // Monday before the series began
    }

    @Test fun reminderFiresTravelTimeBeforeStart() {
        // Thursday 2026-11-26 -> next Monday is 11-30; 21:00 minus 10 min = 20:50.
        assertEquals(ms(2026, 11, 30, 20, 50), nextReminderMs(listOf(math), ms(2026, 11, 26, 12, 0), seoul))
    }

    @Test fun eventWithoutTravelTimeIsAnnouncedFiveMinutesBefore() {
        val e = math.copy(travelMin = 0)
        assertEquals(ms(2026, 11, 30, 20, 55), nextReminderMs(listOf(e), ms(2026, 11, 26, 12, 0), seoul))
    }

    @Test fun reminderAlreadyPassedTodayMovesToNextWeek() {
        // Monday 20:51 is after the 20:50 reminder, so the next one is the following Monday.
        assertEquals(ms(2026, 12, 7, 20, 50), nextReminderMs(listOf(math), ms(2026, 11, 30, 20, 51), seoul))
    }

    @Test fun disabledOrEmptyGivesNoAlarm() {
        assertNull(nextReminderMs(listOf(math.copy(reminderOn = false)), 0, seoul))
        assertNull(nextReminderMs(emptyList(), 0, seoul))
    }

    @Test fun dueRemindersPicksOnlyThoseInsideTheWindow() {
        val at = ms(2026, 11, 30, 20, 50)
        assertEquals(1, dueReminders(listOf(math), at + 500, 120_000, seoul).size)
        assertEquals(0, dueReminders(listOf(math), at - 1, 120_000, seoul).size)
        assertEquals(0, dueReminders(listOf(math), at + 5 * 60_000, 120_000, seoul).size)
    }

    @Test fun oneOffEventOccursOnlyOnItsDate() {
        val exam = math.copy(weekly = false, dateEpochDay = LocalDate.of(2026, 12, 8).toEpochDay())
        assertTrue(exam.occursOn(LocalDate.of(2026, 12, 8)))
        assertFalse(exam.occursOn(LocalDate.of(2026, 12, 15)))
    }
}

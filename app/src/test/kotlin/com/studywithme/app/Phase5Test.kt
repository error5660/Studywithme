package com.studywithme.app

import com.studywithme.app.backup.BackupData
import com.studywithme.app.backup.decodeBackup
import com.studywithme.app.backup.encodeBackup
import com.studywithme.app.data.CalendarEvent
import com.studywithme.app.data.Exam
import com.studywithme.app.data.ExamScore
import com.studywithme.app.data.ManualCell
import com.studywithme.app.data.Settings
import com.studywithme.app.data.StudySession
import com.studywithme.app.data.Subject
import com.studywithme.app.data.WrongNote
import com.studywithme.app.data.afterReview
import com.studywithme.app.data.buildReport
import com.studywithme.app.data.decodeBySubject
import com.studywithme.app.data.encodeBySubject
import com.studywithme.app.data.firstReviewDay
import com.studywithme.app.data.lastWeeklyDueMs
import com.studywithme.app.data.nextDailyMs
import com.studywithme.app.data.nextEventStart
import com.studywithme.app.data.nextWeeklyMs
import com.studywithme.app.data.upcomingExams
import com.studywithme.app.noise.NoiseSynth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class Phase5Test {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun ms(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, seoul).toInstant().toEpochMilli()
    private val hour = 3_600_000L

    // ---- spaced review ------------------------------------------------------------------

    @Test fun reviewsComeBackAfterOneThreeAndSevenDays() {
        assertEquals(101L, firstReviewDay(100))
        assertEquals(1 to 104L, afterReview(0, true, 101))     // passed #1 -> next in 3 days
        assertEquals(2 to 111L, afterReview(1, true, 104))     // passed #2 -> next in 7 days
        assertEquals(3 to null, afterReview(2, true, 111))     // passed #3 -> mastered
    }

    @Test fun aWrongAnswerRestartsTheCycleFromTheBeginning() {
        assertEquals(0 to 105L, afterReview(2, false, 104))
        assertEquals(0 to 105L, afterReview(0, false, 104))
    }

    // ---- weekly report ------------------------------------------------------------------

    private fun session(day: LocalDate, hourOfDay: Int, minutes: Int, subject: Long, focus: Int? = null): StudySession {
        val start = ZonedDateTime.of(day.atTime(hourOfDay, 0), seoul).toInstant().toEpochMilli()
        return StudySession(subjectId = subject, startMs = start, endMs = start + minutes * 60_000L, focusScore = focus)
    }

    @Test fun reportTotalsCompareWithLastWeekAndPickTheBestDay() {
        val monday = LocalDate.of(2026, 11, 23)
        val sessions = listOf(
            session(monday, 10, 60, 1, 4), session(monday.plusDays(2), 10, 120, 2, 2), session(monday.plusDays(2), 15, 30, 1, null),
            session(monday.minusDays(3), 10, 90, 1),                               // last week
        )
        val r = buildReport(monday, sessions, earlyUnlocks = 2, notesAdded = 5, notesReviewed = 3, phoneMs = 4 * hour, nowMs = ms(2026, 11, 29, 21), zone = seoul)
        assertEquals(210 * 60_000L, r.totalMs)
        assertEquals(90 * 60_000L, r.prevTotalMs)
        assertEquals(monday.plusDays(2).toEpochDay(), r.bestDay)
        assertEquals(150 * 60_000L, r.bestDayMs)
        assertEquals(3f, r.avgFocus!!, 0.001f)                                     // (4 + 2) / 2, unrated ignored
        assertEquals(2, r.earlyUnlocks); assertEquals(5, r.notesAdded); assertEquals(3, r.notesReviewed)
        assertEquals(4 * hour, r.phoneMs)
        assertEquals(listOf<Pair<Long?, Long>>(2L to 120 * 60_000L, 1L to 90 * 60_000L), decodeBySubject(r.bySubject))
    }

    @Test fun reportWithNoStudyIsAllZeroAndKeepsNullFocusAndPhone() {
        val r = buildReport(LocalDate.of(2026, 11, 23), emptyList(), 0, 0, 0, null, ms(2026, 11, 29, 21), seoul)
        assertEquals(0L, r.totalMs); assertNull(r.avgFocus); assertNull(r.phoneMs); assertEquals(0, r.streak)
    }

    @Test fun bySubjectSurvivesEncodingIncludingDeletedSubjects() {
        val m = linkedMapOf<Long?, Long>(1L to 5, null to 9)
        assertEquals(listOf<Pair<Long?, Long>>(null to 9L, 1L to 5L), decodeBySubject(encodeBySubject(m)))
        assertTrue(decodeBySubject("").isEmpty())
    }

    // ---- alarm times --------------------------------------------------------------------

    @Test fun weeklyReportIsDueEverySundayAtNinePm() {
        // Thursday 2026-11-26 -> Sunday 11-29 21:00
        assertEquals(ms(2026, 11, 29, 21), nextWeeklyMs(ms(2026, 11, 26, 12), seoul))
        // Sunday 20:59 still today, 21:00 sharp already moves a week on
        assertEquals(ms(2026, 11, 29, 21), nextWeeklyMs(ms(2026, 11, 29, 20, 59), seoul))
        assertEquals(ms(2026, 12, 6, 21), nextWeeklyMs(ms(2026, 11, 29, 21), seoul))
        // last due moment is the previous Sunday 21:00
        assertEquals(ms(2026, 11, 22, 21), lastWeeklyDueMs(ms(2026, 11, 26, 12), seoul))
    }

    @Test fun dailyReviewReminderFiresAtTheNextOccurrenceOfTheHour() {
        assertEquals(ms(2026, 11, 26, 18), nextDailyMs(ms(2026, 11, 26, 9), 18, seoul))
        assertEquals(ms(2026, 11, 27, 18), nextDailyMs(ms(2026, 11, 26, 18), 18, seoul))
    }

    // ---- widgets: exams and next event --------------------------------------------------

    private fun event(id: Long, day: LocalDate, startMin: Int, kind: Int = 0, weekly: Boolean = false) =
        CalendarEvent(id = id, title = "e$id", kind = kind, dateEpochDay = day.toEpochDay(), startMin = startMin, endMin = startMin + 50, weekly = weekly, color = 0)

    @Test fun upcomingExamsSkipPastOnesAndSortBySoonest() {
        val today = LocalDate.of(2026, 11, 26)
        val list = listOf(
            event(1, today.plusDays(12), 700, kind = 1), event(2, today.minusDays(1), 600, kind = 1),
            event(3, today.plusDays(12), 620, kind = 1), event(4, today.plusDays(1), 600),
        )
        assertEquals(listOf(3L, 1L), upcomingExams(list, today).map { it.first.id })
    }

    @Test fun nextEventIsTheEarliestOneAfterNowIncludingWeeklyRepeats() {
        val monday = LocalDate.of(2026, 3, 2)
        val weekly = event(1, monday, 21 * 60, weekly = true)              // Mondays 21:00
        val oneOff = event(2, LocalDate.of(2026, 11, 28), 9 * 60)          // Saturday 09:00
        val next = nextEventStart(listOf(weekly, oneOff), ms(2026, 11, 26, 12), seoul)!!
        assertEquals(2L, next.first.id)
        assertEquals(ms(2026, 11, 28, 9), next.second)
        assertEquals(1L, nextEventStart(listOf(weekly), ms(2026, 11, 26, 12), seoul)!!.first.id)
        assertNull(nextEventStart(emptyList(), 0, seoul))
    }

    // ---- white noise --------------------------------------------------------------------

    @Test fun everyNoiseLoopHasSoundAndLoopsWithoutAClick() {
        for (kind in listOf(NoiseSynth.RAIN, NoiseSynth.CAFE, NoiseSynth.FAN)) {
            val pcm = NoiseSynth.generate(kind, seconds = 2)
            assertEquals(NoiseSynth.RATE * 2, pcm.size)
            assertTrue("kind $kind is silent", pcm.maxOf { abs(it.toInt()) } > 5000)
            assertTrue("kind $kind clips", pcm.maxOf { abs(it.toInt()) } < Short.MAX_VALUE)
            val biggestStep = (1 until pcm.size).maxOf { abs(pcm[it] - pcm[it - 1]) }
            val seam = abs(pcm[0] - pcm.last())
            assertTrue("kind $kind seam $seam vs $biggestStep", seam <= biggestStep)
        }
    }

    // ---- backup -------------------------------------------------------------------------

    private fun sample() = BackupData(
        exportedMs = 123, settings = Settings(dailyGoalMin = 400, night = true),
        subjects = listOf(Subject(1, "미적분", 0xFFE8743B.toInt(), 0, targetGrade = 2)),
        sessions = listOf(StudySession(1, 1, 1000, 2000, 4)), events = listOf(event(1, LocalDate.of(2026, 12, 8), 620, kind = 1)),
        tasks = emptyList(), progress = emptyList(), earlyUnlocks = emptyList(), manualCells = listOf(ManualCell(5, 3, -1)),
        notes = listOf(WrongNote(1, 1, "메모", "a,b", "p1.jpg", "p2.jpg", 10, 1, 13)),
        noteReviews = emptyList(), exams = listOf(Exam(1, "1차", 5)), examScores = listOf(ExamScore(1, 1, 88, 2)), reports = emptyList(),
    )

    @Test fun backupSurvivesAnEncodeDecodeRoundTrip() {
        val back = decodeBackup(encodeBackup(sample()))
        assertEquals(sample(), back)
        assertEquals(setOf("p1.jpg", "p2.jpg"), back.photoNames())
    }

    @Test fun backupFromANewerAppOrGarbageIsRejected() {
        val newer = encodeBackup(sample()).replace("\"version\":1", "\"version\":99")
        val rejected = runCatching { decodeBackup(newer) }.isFailure
        assertTrue(rejected)
        assertTrue(runCatching { decodeBackup("not json") }.isFailure)
    }

    @Test fun unknownFieldsFromFutureBackupsAreIgnored() {
        val extended = encodeBackup(sample()).replaceFirst("{", "{\"someNewField\":42,")
        assertNotNull(decodeBackup(extended))
    }
}

class NowPlayingTest {
    private data class S(val pkg: String, val playing: Boolean)
    private fun pick(vararg s: S) = com.studywithme.app.music.pickSession(s.toList(), { it.pkg }, { it.playing })

    @Test fun aPlayingAppWinsOverAPausedSpotify() {
        assertEquals("youtube", pick(S("com.spotify.music", false), S("youtube", true))?.pkg)
    }

    @Test fun spotifyWinsWhenBothArePlayingOrBothPaused() {
        assertEquals("com.spotify.music", pick(S("melon", true), S("com.spotify.music", true))?.pkg)
        assertEquals("com.spotify.music", pick(S("melon", false), S("com.spotify.music", false))?.pkg)
    }

    @Test fun nothingToShowWithoutSessions() {
        assertNull(pick())
    }
}

class TrackPositionTest {
    private fun track(playing: Boolean) = com.studywithme.app.music.Track("t", "a", null, null, playing, durationMs = 153_000, positionMs = 100_000, positionAtRt = 1_000)

    @Test fun playingTrackMovesOnFromTheLastReportedPosition() {
        assertEquals(105_000L, track(true).positionAt(6_000))
    }

    @Test fun pausedTrackStaysPutAndNothingRunsPastTheEnd() {
        assertEquals(100_000L, track(false).positionAt(60_000))
        assertEquals(153_000L, track(true).positionAt(999_000))
    }
}

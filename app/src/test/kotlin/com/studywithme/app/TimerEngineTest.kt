package com.studywithme.app

import com.studywithme.app.data.Settings
import com.studywithme.app.data.StudySession
import com.studywithme.app.data.computeTotals
import com.studywithme.app.data.studyDayStart
import com.studywithme.app.timer.Phase
import com.studywithme.app.timer.TimerEngine
import com.studywithme.app.timer.TimerStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimerEngineTest {
    private val min = 60_000L
    private var clock = 1_000_000L
    private var settings = Settings()
    private val saved = mutableListOf<StudySession>()
    private val engine = TimerEngine({ settings }, { saved += it }, { clock }, { clock })

    private fun advance(ms: Long) { clock += ms }

    @Test fun stopwatchSavesOneSessionAndCountsStudyTime() {
        engine.start(1)
        advance(10 * min)
        assertEquals(10 * min, engine.state.value.studyMs(clock))
        engine.stop()
        assertEquals(1, saved.size)
        assertEquals(10 * min, saved[0].endMs - saved[0].startMs)
        assertEquals(TimerStatus.Idle, engine.state.value.status)
    }

    @Test fun pauseStopsTheClockAndResumeStartsANewSession() {
        engine.start(1)
        advance(5 * min); engine.pause()
        advance(30 * min) // paused: must not count
        engine.resume()
        advance(5 * min); engine.stop()
        assertEquals(2, saved.size)
        assertEquals(10 * min, saved.sumOf { it.endMs - it.startMs })
    }

    @Test fun tapsShorterThanThreeSecondsAreIgnored() {
        engine.start(1); advance(2_000); engine.stop()
        assertTrue(saved.isEmpty())
    }

    @Test fun leavingTheAppPausesUnlessLocked() {
        engine.start(1); advance(min)
        engine.onBackgrounded()
        assertEquals(TimerStatus.Paused, engine.state.value.status)
        assertTrue(engine.state.value.autoPaused)

        engine.resume(); engine.lockActive = true
        engine.onBackgrounded()
        assertEquals(TimerStatus.Running, engine.state.value.status)
    }

    @Test fun pomodoroCyclesFocusBreakFocusAndBreaksAreNotSaved() {
        settings = Settings(pomodoroOn = true, focusMin = 25, breakMin = 5)
        engine.start(1)
        advance(25 * min); engine.tick()
        assertEquals(Phase.ShortBreak, engine.state.value.phase)
        assertEquals(1, saved.size)

        advance(5 * min); engine.tick()
        assertEquals(Phase.Focus, engine.state.value.phase)
        assertEquals(2, engine.state.value.cycle)
        assertEquals(1, saved.size) // break time never becomes a session
        assertEquals(25 * min, engine.state.value.studyMs(clock))
    }

    @Test fun longBreakComesAfterEveryNthFocusBlock() {
        settings = Settings(pomodoroOn = true, focusMin = 10, breakMin = 2, longBreakOn = true, longBreakEvery = 2, longBreakMin = 20)
        engine.start(1)
        advance(10 * min); engine.tick()            // focus 1 -> short
        assertEquals(Phase.ShortBreak, engine.state.value.phase)
        advance(2 * min); engine.tick()             // -> focus 2
        advance(10 * min); engine.tick()            // focus 2 -> long
        assertEquals(Phase.LongBreak, engine.state.value.phase)
    }

    @Test fun pausedPomodoroDoesNotAdvance() {
        settings = Settings(pomodoroOn = true, focusMin = 25, breakMin = 5)
        engine.start(1); advance(20 * min); engine.pause()
        advance(60 * min); engine.tick()
        assertEquals(Phase.Focus, engine.state.value.phase)
        engine.resume(); advance(5 * min); engine.tick()
        assertEquals(Phase.ShortBreak, engine.state.value.phase)
    }

    @Test fun switchingSubjectEndsThePreviousSession() {
        engine.start(1); advance(3 * min)
        engine.start(2); advance(4 * min); engine.stop()
        assertEquals(listOf<Long?>(1L, 2L), saved.map { it.subjectId })
    }

    @Test fun studyDayStartsAtFourInTheMorning() {
        val seoul = ZoneId.of("Asia/Seoul")
        fun ms(h: Int, m: Int, day: Int) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, seoul).toInstant().toEpochMilli()
        val expected = ms(4, 0, 10)
        assertEquals(expected, studyDayStart(ms(4, 0, 10), seoul))
        assertEquals(expected, studyDayStart(ms(23, 59, 10), seoul))
        assertEquals(expected, studyDayStart(ms(1, 30, 11), seoul)) // 01:30 after midnight still belongs to day 10
        assertEquals(ms(4, 0, 9), studyDayStart(ms(3, 59, 10), seoul))
    }

    @Test fun totalsClipSessionsToTheStudyDay() {
        val day = 100 * 60 * min
        val s = StudySession(subjectId = 1, startMs = day - 10 * min, endMs = day + 20 * min) // straddles the day start
        val t = computeTotals(listOf(s), day, liveSubjectId = 2, liveMs = 5 * min, nowMs = day + 60 * min)
        assertEquals(25 * min, t.totalMs)
        assertEquals(20 * min, t.bySubject[1L])
        assertEquals(5 * min, t.bySubject[2L])
    }
}

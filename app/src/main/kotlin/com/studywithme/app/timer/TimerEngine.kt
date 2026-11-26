package com.studywithme.app.timer

import com.studywithme.app.data.Settings
import com.studywithme.app.data.StudySession
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TimerStatus { Idle, Running, Paused }
enum class Phase { Focus, ShortBreak, LongBreak }

/**
 * Everything is stored as "base + current run" so the UI can compute exact values from the
 * monotonic clock on every frame without the engine having to tick the state.
 */
data class TimerState(
    val status: TimerStatus = TimerStatus.Idle,
    val subjectId: Long? = null,
    val phase: Phase = Phase.Focus,
    val cycle: Int = 1,
    val studyBaseMs: Long = 0,
    val phaseBaseMs: Long = 0,
    val runStartRt: Long = 0,
    val autoPaused: Boolean = false,
) {
    val running get() = status == TimerStatus.Running
    private fun runMs(nowRt: Long) = if (running) (nowRt - runStartRt).coerceAtLeast(0) else 0
    fun phaseElapsedMs(nowRt: Long) = phaseBaseMs + runMs(nowRt)
    fun studyMs(nowRt: Long) = studyBaseMs + liveFocusMs(nowRt)

    /** Focus time of the current run that has not been written to the database yet. */
    fun liveFocusMs(nowRt: Long) = if (phase == Phase.Focus) runMs(nowRt) else 0
}

fun Settings.phaseLengthMs(phase: Phase): Long = 60_000L * when (phase) {
    Phase.Focus -> focusMin
    Phase.ShortBreak -> breakMin
    Phase.LongBreak -> longBreakMin
}

/** A stretch of study that just ended; the UI asks "how was your focus?" and rates sessions inside it. */
data class FocusWindow(val subjectId: Long?, val fromMs: Long, val toMs: Long)

class TimerEngine(
    private val settings: () -> Settings,
    private val saveSession: (StudySession) -> Unit,
    private val rt: () -> Long,
    private val wall: () -> Long,
) {
    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _phaseChanges = MutableSharedFlow<Phase>(extraBufferCapacity = 4)
    /** Emits the new phase whenever Pomodoro switches; used for alerts. */
    val phaseChanges: SharedFlow<Phase> = _phaseChanges.asSharedFlow()

    private val _focusEnded = MutableSharedFlow<FocusWindow>(extraBufferCapacity = 4)
    /** Fires when a study session stops or a Pomodoro focus block ends (and was at least a minute long). */
    val focusEnded: SharedFlow<FocusWindow> = _focusEnded.asSharedFlow()
    private var windowFromWall = 0L

    private fun endWindow(subjectId: Long?) {
        val from = windowFromWall
        windowFromWall = 0
        val to = wall()
        if (from != 0L && to - from >= MIN_RATING_MS) _focusEnded.tryEmit(FocusWindow(subjectId, from, to))
    }

    /** Set by the focus lock (Phase 3): while true, leaving the app does not pause the timer. */
    var lockActive = false

    fun start(subjectId: Long) {
        val s = _state.value
        if (s.status != TimerStatus.Idle) {
            if (s.subjectId == subjectId) {
                if (s.status == TimerStatus.Paused) resume()
                return
            }
            stop()
        }
        _state.value = TimerState(status = TimerStatus.Running, subjectId = subjectId, runStartRt = rt())
        windowFromWall = wall()
    }

    fun pause(auto: Boolean = false) {
        val s = _state.value
        if (!s.running) return
        _state.value = settle(s).copy(status = TimerStatus.Paused, autoPaused = auto)
    }

    fun resume() {
        val s = _state.value
        if (s.status != TimerStatus.Paused) return
        _state.value = s.copy(status = TimerStatus.Running, runStartRt = rt(), autoPaused = false)
    }

    fun stop() {
        val s = _state.value
        if (s.running) settle(s)
        if (s.status != TimerStatus.Idle) endWindow(s.subjectId)
        _state.value = TimerState()
    }

    fun onBackgrounded() {
        if (_state.value.running && !lockActive) pause(auto = true)
    }

    /** Called when the Pomodoro switch changes: the phase clock starts over from a fresh focus block. */
    fun restartPomodoro() {
        val s = _state.value
        if (s.status == TimerStatus.Idle) return
        val settled = if (s.running) settle(s) else s
        _state.value = settled.copy(
            phase = Phase.Focus, cycle = 1, phaseBaseMs = 0,
            runStartRt = if (s.running) rt() else 0,
        )
    }

    /** Called about twice a second; switches Pomodoro phases when time is up. */
    fun tick() {
        val s = _state.value
        val cfg = settings()
        if (!s.running || !cfg.pomodoroOn) return
        if (s.phaseElapsedMs(rt()) < cfg.phaseLengthMs(s.phase)) return
        val next = when {
            s.phase != Phase.Focus -> Phase.Focus
            cfg.longBreakOn && s.cycle % cfg.longBreakEvery == 0 -> Phase.LongBreak
            else -> Phase.ShortBreak
        }
        _state.value = settle(s).copy(
            phase = next,
            cycle = if (next == Phase.Focus) s.cycle + 1 else s.cycle,
            phaseBaseMs = 0,
            runStartRt = rt(),
        )
        if (next == Phase.Focus) windowFromWall = wall() else endWindow(s.subjectId)
        _phaseChanges.tryEmit(next)
    }

    /** Closes the current run: stores a session row for focus time and folds the run into the base values. */
    private fun settle(s: TimerState): TimerState {
        val dur = (rt() - s.runStartRt).coerceAtLeast(0)
        val focus = s.phase == Phase.Focus
        if (focus && dur >= MIN_SEGMENT_MS) {
            val end = wall()
            saveSession(StudySession(subjectId = s.subjectId, startMs = end - dur, endMs = end))
        }
        return s.copy(
            studyBaseMs = s.studyBaseMs + if (focus) dur else 0,
            phaseBaseMs = s.phaseBaseMs + dur,
            runStartRt = 0,
        )
    }

    companion object {
        const val MIN_SEGMENT_MS = 3_000L // ignore accidental taps
        const val MIN_RATING_MS = 60_000L
    }
}

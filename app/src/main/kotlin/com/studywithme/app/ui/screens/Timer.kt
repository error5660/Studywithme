package com.studywithme.app.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.data.Settings
import com.studywithme.app.data.Subject
import com.studywithme.app.timer.Phase
import com.studywithme.app.timer.TimerState
import com.studywithme.app.timer.TimerStatus
import com.studywithme.app.timer.phaseLengthMs
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.LocalSettings
import com.studywithme.app.ui.components.ColorDot
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.NowPlaying
import com.studywithme.app.ui.components.PrimaryButton
import com.studywithme.app.ui.components.SoftButton
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.components.WarmSwitch
import com.studywithme.app.ui.findActivity
import com.studywithme.app.ui.formatHms
import com.studywithme.app.ui.formatMs
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the big number shows, derived from the engine state at one instant. */
private class Display(
    val big: String,
    /** 0..1 progress of the current Pomodoro phase, or null when Pomodoro is off. */
    val progress: Float?,
    val phaseLabel: String?,
    val studyMs: Long,
)

@Composable
private fun rememberDisplay(timer: TimerState, settings: Settings): Display {
    // Recomposed every second because the screens also read the wall-clock ticker.
    val rt = SystemClock.elapsedRealtime()
    val studyMs = timer.studyMs(rt)
    if (!settings.pomodoroOn) return Display(formatHms(studyMs), null, null, studyMs)
    val length = settings.phaseLengthMs(timer.phase)
    val elapsed = timer.phaseElapsedMs(rt)
    val label = stringResource(
        when (timer.phase) { Phase.Focus -> R.string.phase_focus; Phase.ShortBreak -> R.string.phase_break; Phase.LongBreak -> R.string.phase_long_break },
    )
    return Display(
        big = formatMs(length - elapsed),
        progress = (elapsed.toFloat() / length).coerceIn(0f, 1f),
        phaseLabel = stringResource(R.string.phase_cycle, label, timer.cycle),
        studyMs = studyMs,
    )
}

@Composable
fun TimerScreen(onClose: () -> Unit) {
    val c = LocalContainer.current
    val settings = LocalSettings.current
    val layout = rememberLayout()
    val timer by c.engine.state.collectAsStateWithLifecycle()
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val now by c.tick.collectAsStateWithLifecycle(System.currentTimeMillis())
    val subject = subjects.firstOrNull { it.id == timer.subjectId }
    var deskForced by rememberSaveable { mutableStateOf(false) }
    // Sideways = desk clock; the button forces it in portrait too.
    val desk = deskForced || layout.landscape
    val display = rememberDisplay(timer, settings)
    now.hashCode() // read so this screen recomposes every second

    ImmersiveWhile(desk)
    // follow the music player only while the timer is on screen
    DisposableEffect(Unit) { c.nowPlaying.start(); onDispose { c.nowPlaying.stop() } }
    BackHandler(deskForced && !layout.landscape) { deskForced = false }

    val onToggle = { if (timer.running) c.engine.pause() else c.engine.resume() }
    Box(Modifier.fillMaxSize()) {
        if (desk) {
            DeskClock(subject, timer, display, now, onToggle, onStop = { c.engine.stop() }, onClose = {
                if (deskForced && !layout.landscape) deskForced = false else onClose()
            })
        } else {
            FullTimer(subject, timer, display, settings, onToggle, onStop = { c.engine.stop() }, onClose, onDesk = { deskForced = true })
        }
    }
}

@Composable
private fun FullTimer(
    subject: Subject?, timer: TimerState, display: Display, settings: Settings,
    onToggle: () -> Unit, onStop: () -> Unit, onClose: () -> Unit, onDesk: () -> Unit,
) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            TextAction(stringResource(R.string.timer_home), onClose)
            Spacer(Modifier.weight(1f))
            TextAction(stringResource(R.string.timer_desk_mode), onDesk)
        }
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            SubjectLabel(subject)
            Spacer(Modifier.height(20.dp))
            Text(display.big, style = AppText.timer, maxLines = 1)
            if (display.progress != null) {
                Spacer(Modifier.height(12.dp))
                ProgressLine(display.progress, Modifier.width(240.dp))
                Spacer(Modifier.height(14.dp))
                Text(display.phaseLabel.orEmpty(), style = AppText.bodyMedium, color = w.accent)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.timer_session_elapsed, formatHms(display.studyMs)), style = AppText.caption, color = w.inkSoft)
            }
            Spacer(Modifier.height(10.dp))
            StatusLine(timer, Modifier)
        }
        NowPlaying(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 480.dp).padding(bottom = 20.dp))
        Column(Modifier.align(Alignment.CenterHorizontally).widthIn(max = 480.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton(stringResource(if (timer.running) R.string.timer_pause else R.string.timer_resume), onToggle, Modifier.weight(2f))
                SoftButton(stringResource(R.string.timer_stop), onStop, Modifier.weight(1f))
            }
            GlassCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.pomo_title), style = AppText.bodyMedium)
                        Text(stringResource(R.string.pomo_summary, settings.focusMin, settings.breakMin), style = AppText.caption, color = w.inkSoft)
                    }
                    WarmSwitch(settings.pomodoroOn) { on -> c.updateSettings { it.copy(pomodoroOn = on) } }
                }
            }
        }
    }
}

@Composable
private fun DeskClock(
    subject: Subject?, timer: TimerState, display: Display, nowMs: Long,
    onToggle: () -> Unit, onStop: () -> Unit, onClose: () -> Unit,
) {
    val w = LocalWarm.current
    var controls by remember { mutableStateOf(false) }
    LaunchedEffect(controls) { if (controls) { delay(4000); controls = false } }
    val pattern = stringResource(R.string.clock_pattern)
    val clock = remember(pattern, nowMs / 1000) { DateTimeFormatter.ofPattern(pattern, Locale.KOREAN).format(Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault())) }

    BoxWithConstraints(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { controls = !controls },
    ) {
        // Digits are about 0.55 em wide; leave margins on both sides and never let the height dominate.
        val numberSize = with(LocalDensity.current) { minOf(maxWidth * 0.19f, maxHeight * 0.42f).toSp() }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            SubjectLabel(subject)
            display.phaseLabel?.let { Text(it, style = AppText.caption, color = w.accent, modifier = Modifier.padding(top = 4.dp)) }
            Text(display.big, style = AppText.timer.copy(fontSize = numberSize, lineHeight = numberSize), maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
            Text(clock, style = AppText.number.copy(fontSize = androidx.compose.ui.unit.TextUnit(28f, androidx.compose.ui.unit.TextUnitType.Sp)), color = w.inkSoft)
            StatusLine(timer, Modifier.padding(top = 8.dp))
        }
        NowPlaying(Modifier.align(Alignment.TopStart).padding(8.dp).widthIn(max = 320.dp), artSize = 44.dp)
        AnimatedVisibility(controls, Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp), enter = fadeIn(), exit = fadeOut()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton(stringResource(if (timer.running) R.string.timer_pause else R.string.timer_resume), onToggle)
                SoftButton(stringResource(R.string.timer_stop), onStop)
                TextAction(stringResource(R.string.common_close), onClose)
            }
        }
    }
}

@Composable
private fun SubjectLabel(subject: Subject?) {
    val w = LocalWarm.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(subject?.let { Color(it.color) } ?: w.inkFaint, 10.dp)
        Spacer(Modifier.width(10.dp))
        Text(subject?.name ?: stringResource(R.string.timer_no_subject), style = AppText.bodyMedium, color = w.inkSoft)
    }
}

@Composable
private fun StatusLine(timer: TimerState, modifier: Modifier) {
    if (timer.status != TimerStatus.Paused) return
    val w = LocalWarm.current
    Text(
        stringResource(if (timer.autoPaused) R.string.timer_auto_paused else R.string.timer_paused_label),
        style = AppText.caption, color = w.inkSoft, modifier = modifier,
    )
}

@Composable
private fun ProgressLine(progress: Float, modifier: Modifier) {
    val w = LocalWarm.current
    Box(modifier.height(2.dp).background(w.inkFaint.copy(alpha = 0.5f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(w.accent))
    }
}

/** Hides the system bars for the desk clock and brings them back afterwards. */
@Composable
private fun ImmersiveWhile(enabled: Boolean) {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        if (enabled && ctl != null) {
            ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

package com.studywithme.app.ui.screens

import android.content.Intent
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.timer.TimerStatus
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.components.Chip
import com.studywithme.app.ui.components.PrimaryButton
import com.studywithme.app.ui.components.SoftButton
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.components.WarmDialog
import com.studywithme.app.ui.formatHms
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val EARLY_WAIT_SECONDS = 60

/** What the student sees while the lock is on: time left, today's study time, and the study timer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LockScreen(onDone: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val layout = rememberLayout()
    val lock by c.lock.state.collectAsStateWithLifecycle(null)
    val timer by c.engine.state.collectAsStateWithLifecycle()
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val now by c.tick.collectAsStateWithLifecycle(System.currentTimeMillis())
    val totals = rememberTodayTotals(timer)
    val context = LocalContext.current
    var picked by remember { mutableStateOf<Long?>(null) }
    var early by remember { mutableIntStateOf(0) } // 0 none, 1 password, 2 waiting
    var showTimetable by remember { mutableStateOf(false) }

    BackHandler(true) {} // the lock screen cannot be left with the back button
    BackHandler(showTimetable) { showTimetable = false }
    val state = lock ?: return
    // Null above means "still loading"; once loaded, a finished lock closes this screen.
    LaunchedEffect(state.endMs, now / 1000) { if (!state.active(now)) onDone() }

    val remaining = (state.endMs - now).coerceAtLeast(0)
    val current = picked ?: timer.subjectId ?: state.subjectId ?: subjects.firstOrNull()?.id
    val sideBySide = layout.landscape && layout.widthDp >= 600

    @Composable
    fun Info(modifier: Modifier) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.lock_active_title), style = AppText.label, color = w.inkSoft)
        Spacer(Modifier.height(12.dp))
        Text(formatHms(remaining), style = if (layout.shortHeight) AppText.hero.copy(fontSize = androidx.compose.ui.unit.TextUnit(64f, androidx.compose.ui.unit.TextUnitType.Sp)) else AppText.timer, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.lock_today, formatHms(totals.totalMs)), style = AppText.body, color = w.inkSoft)
    }

    @Composable
    fun Controls(modifier: Modifier) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)) {
        Text(stringResource(R.string.lock_subject_pick), style = AppText.caption, color = w.inkSoft)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { s ->
                Chip(s.name, current == s.id, {
                    picked = s.id
                    if (timer.status != TimerStatus.Idle) c.engine.start(s.id) // switching subject mid-study starts a new session
                })
            }
        }
        Row(Modifier.widthIn(max = 420.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val label = when (timer.status) {
                TimerStatus.Idle -> R.string.lock_timer_start
                TimerStatus.Running -> R.string.lock_timer_pause
                TimerStatus.Paused -> R.string.lock_timer_resume
            }
            PrimaryButton(stringResource(label), {
                when (timer.status) {
                    TimerStatus.Idle -> current?.let { c.engine.start(it) }
                    TimerStatus.Running -> c.engine.pause()
                    TimerStatus.Paused -> c.engine.resume()
                }
            }, Modifier.weight(2f))
            if (timer.status != TimerStatus.Idle) SoftButton(stringResource(R.string.lock_timer_stop), { c.engine.stop() }, Modifier.weight(1.4f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextAction(stringResource(R.string.lock_timetable), { showTimetable = true }, color = w.inkSoft)
            TextAction(stringResource(R.string.lock_call), { context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, color = w.inkSoft)
            TextAction(stringResource(R.string.lock_early), { early = 1 }, color = w.inkSoft)
        }
    }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp)) {
        if (sideBySide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically) {
                Info(Modifier.weight(1f).fillMaxSize())
                Controls(Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()))
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Info(Modifier.fillMaxWidth().padding(vertical = 24.dp))
                Controls(Modifier.fillMaxWidth().padding(bottom = 24.dp))
            }
        }
    }

    if (showTimetable) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp)) {
                TimetableScreen(embedded = true, onClose = { showTimetable = false })
            }
        }
    }
    when (early) {
        1 -> PasswordGateDialog(onDismiss = { early = 0 }, onVerified = { early = 2 })
        2 -> EarlyUnlockWait(onKeep = { early = 0 }, onConfirm = { early = 0 })
    }
}

/** After the password: a forced 60-second pause with a "really quit?" message before the lock opens. */
@Composable
private fun EarlyUnlockWait(onKeep: () -> Unit, onConfirm: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val scope = rememberCoroutineScope()
    val startRt = remember { SystemClock.elapsedRealtime() }
    var left by remember { mutableIntStateOf(EARLY_WAIT_SECONDS) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(250)
            left = (EARLY_WAIT_SECONDS - (SystemClock.elapsedRealtime() - startRt) / 1000).toInt().coerceAtLeast(0)
        }
    }
    WarmDialog(
        title = stringResource(R.string.early_title),
        onDismiss = onKeep,
        confirmText = stringResource(R.string.early_confirm),
        confirmEnabled = left == 0,
        onConfirm = { scope.launch { c.lock.finish(early = true); onConfirm() } },
        dismissText = stringResource(R.string.early_keep),
    ) {
        Text(if (left > 0) stringResource(R.string.early_body, left) else stringResource(R.string.early_ready), style = AppText.body, color = w.inkSoft)
    }
}

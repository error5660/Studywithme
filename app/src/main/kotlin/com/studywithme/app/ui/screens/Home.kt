package com.studywithme.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.data.Subject
import com.studywithme.app.data.TodayTotals
import com.studywithme.app.data.computeTotals
import com.studywithme.app.data.studyDayStart
import com.studywithme.app.timer.TimerState
import com.studywithme.app.timer.TimerStatus
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.components.ColorDot
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.Hairline
import com.studywithme.app.ui.components.SectionTitle
import com.studywithme.app.ui.formatHms
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Today's totals, including the part of the running timer that is not in the database yet. */
@Composable
fun rememberTodayTotals(timer: TimerState): TodayTotals {
    val c = LocalContainer.current
    val now by c.tick.collectAsStateWithLifecycle(System.currentTimeMillis())
    val dayStart = studyDayStart(now)
    val sessions by remember(dayStart) { c.db.sessionDao().observeBetween(dayStart, dayStart + com.studywithme.app.data.DAY_MS) }
        .collectAsStateWithLifecycle(emptyList())
    return computeTotals(sessions, dayStart, timer.subjectId, timer.liveFocusMs(SystemClock.elapsedRealtime()), now)
}

@Composable
fun formatDuration(ms: Long): String {
    val minutes = (ms / 60_000).toInt()
    return if (minutes >= 60) stringResource(R.string.duration_hm, minutes / 60, minutes % 60) else stringResource(R.string.duration_m, minutes)
}

@Composable
fun HomeScreen(padding: PaddingValues, onOpenTimer: () -> Unit) {
    val c = LocalContainer.current
    val layout = rememberLayout()
    val timer by c.engine.state.collectAsStateWithLifecycle()
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val totals = rememberTodayTotals(timer)
    val heroStyle = if (layout.shortHeight) AppText.hero.copy(fontSize = androidx.compose.ui.unit.TextUnit(56f, androidx.compose.ui.unit.TextUnitType.Sp)) else AppText.hero

    if (layout.twoPane) {
        Row(Modifier.fillMaxSize().padding(padding), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Hero(totals.totalMs, heroStyle)
                ActiveCard(timer, subjects, totals, onOpenTimer)
                DdayCard()
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                TodoCard()
                SubjectList(subjects, timer, totals, onOpenTimer)
            }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Hero(totals.totalMs, heroStyle)
            ActiveCard(timer, subjects, totals, onOpenTimer)
            DdayCard()
            TodoCard()
            SubjectList(subjects, timer, totals, onOpenTimer)
        }
    }
}

@Composable
private fun Hero(totalMs: Long, numberStyle: androidx.compose.ui.text.TextStyle) {
    val w = LocalWarm.current
    val pattern = stringResource(R.string.date_pattern)
    val date = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.KOREAN).format(Instant.now().atZone(ZoneId.systemDefault())) }
    Column(Modifier.padding(top = 16.dp)) {
        Text(date, style = AppText.label, color = w.inkSoft)
        Spacer(Modifier.height(20.dp))
        Text(formatHms(totalMs), style = numberStyle, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.home_today_label), style = AppText.body, color = w.inkSoft)
    }
}

/** Shown only while a session exists: a quiet way back to the timer. */
@Composable
private fun ActiveCard(timer: TimerState, subjects: List<Subject>, totals: TodayTotals, onOpenTimer: () -> Unit) {
    if (timer.status == TimerStatus.Idle) return
    val w = LocalWarm.current
    val subject = subjects.firstOrNull { it.id == timer.subjectId }
    GlassCard(Modifier.fillMaxWidth(), onClick = onOpenTimer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(subject?.let { Color(it.color) } ?: w.inkFaint, 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(subject?.name ?: stringResource(R.string.subject_deleted), style = AppText.bodyMedium)
                Text(
                    stringResource(if (timer.running) R.string.home_studying_now else R.string.home_paused),
                    style = AppText.caption, color = if (timer.running) w.accent else w.inkSoft,
                )
            }
            Text(stringResource(R.string.home_open_timer), style = AppText.label, color = w.accent)
        }
    }
}

@Composable
private fun SubjectList(subjects: List<Subject>, timer: TimerState, totals: TodayTotals, onOpenTimer: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        SectionTitle(stringResource(R.string.home_subjects_title), Modifier.padding(bottom = 4.dp))
        if (subjects.isEmpty()) {
            Text(stringResource(R.string.home_no_subjects), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(vertical = 12.dp))
        }
        subjects.forEachIndexed { i, s ->
            if (i > 0) Hairline()
            val active = timer.status != TimerStatus.Idle && timer.subjectId == s.id
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp)
                    .clickable { c.engine.start(s.id); onOpenTimer() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorDot(Color(s.color), 10.dp)
                Spacer(Modifier.width(14.dp))
                Text(s.name, style = AppText.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    formatDuration(totals.bySubject[s.id] ?: 0L),
                    style = AppText.number, color = if (active) w.accent else w.inkSoft,
                )
            }
        }
    }
}

@Composable
fun PlaceholderScreen(titleRes: Int, padding: PaddingValues) {
    val w = LocalWarm.current
    Column(Modifier.fillMaxSize().padding(padding).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(stringResource(titleRes), style = AppText.title)
        GlassCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.placeholder_title), style = AppText.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.placeholder_body), style = AppText.caption, color = w.inkSoft)
        }
    }
}

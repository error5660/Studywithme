package com.studywithme.app.ui.screens

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.data.DAY_MS
import com.studywithme.app.data.Subject
import com.studywithme.app.data.TimeOfDay
import com.studywithme.app.data.dailyTotals
import com.studywithme.app.data.focusAverages
import com.studywithme.app.data.heatLevel
import com.studywithme.app.data.streakDays
import com.studywithme.app.data.studyDay
import com.studywithme.app.data.studyDayStartOf
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.LocalSettings
import com.studywithme.app.ui.components.ColorDot
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.Heatmap
import com.studywithme.app.ui.components.Hairline
import com.studywithme.app.ui.components.SectionTitle
import com.studywithme.app.ui.components.SubjectDonut
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.components.ThinProgress
import com.studywithme.app.ui.components.WarmDialog
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HEAT_WEEKS_PHONE = 18
private const val HEAT_WEEKS_WIDE = 30

private fun mondayOf(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

@Composable
fun StatsScreen() {
    val c = LocalContainer.current
    val layout = rememberLayout()
    val goalMs = LocalSettings.current.dailyGoalMin * 60_000L
    val timer by c.engine.state.collectAsStateWithLifecycle()
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val now by c.tick.collectAsStateWithLifecycle(System.currentTimeMillis())
    val today = studyDay(now)
    val heatWeeks = if (layout.twoPane) HEAT_WEEKS_WIDE else HEAT_WEEKS_PHONE
    val thisMonday = mondayOf(today)
    val firstMonday = thisMonday.minusWeeks((heatWeeks - 1).toLong())

    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    val sessions by remember(today, heatWeeks) { c.db.sessionDao().observeBetween(studyDayStartOf(firstMonday), studyDayStartOf(thisMonday.plusWeeks(1))) }
        .collectAsStateWithLifecycle(emptyList())
    val totalsByDate = remember(sessions, today, heatWeeks) {
        val days = generateSequence(firstMonday) { it.plusDays(1) }.take(heatWeeks * 7).toList()
        days.zip(dailyTotals(sessions, days.map { studyDayStartOf(it) })).toMap()
    }
    val todayTotals = rememberTodayTotals(timer)

    val goalCard = @Composable { GoalCard(todayTotals.totalMs, goalMs, streakDays((0 until 60).map { i -> today.minusDays((59 - i).toLong()) }.map { d -> if (d == today) todayTotals.totalMs else totalsByDate[d]?.totalMs ?: 0L })) }
    val phoneCard = @Composable { PhoneTodayCard(todayTotals.totalMs, today, now) }
    val weekCard = @Composable { WeekCard(weekOffset, { weekOffset += it }, thisMonday, today, totalsByDate, subjects, todayTotals.totalMs) }
    val heatCard = @Composable { HeatCard(heatWeeks, firstMonday, today, totalsByDate, goalMs, selected, { selected = it }, subjects, sessions, todayTotals.totalMs) }
    val focusCard = @Composable { FocusCard(sessions, subjects, today) }

    if (layout.twoPane) {
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) { goalCard(); phoneCard(); weekCard() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) { heatCard(); focusCard() }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) { goalCard(); phoneCard(); weekCard(); heatCard(); focusCard() }
    }
}

@Composable
private fun GoalCard(studiedMs: Long, goalMs: Long, streak: Int) {
    val w = LocalWarm.current
    val pct = if (goalMs > 0) (studiedMs * 100 / goalMs).toInt() else 0
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.stats_goal_title))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.stats_goal_value, formatDuration(goalMs), pct), style = AppText.bodyMedium)
        Spacer(Modifier.height(10.dp))
        ThinProgress(if (goalMs > 0) studiedMs.toFloat() / goalMs else 0f)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(stringResource(R.string.stats_streak, streak), style = AppText.number.copy(fontSize = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp)), color = w.accent)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.stats_streak_hint), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

/** Today's study time next to today's phone screen time. */
@Composable
private fun PhoneTodayCard(studyMs: Long, today: LocalDate, now: Long) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val access = remember(resumes) { c.usage.hasAccess() }
    var guide by remember { mutableStateOf(false) }
    val phoneMs by produceState<Long?>(null, access, today, now / 60_000) {
        value = if (access) withContext(Dispatchers.IO) { c.usage.phoneMs(studyDayStartOf(today), now) } else null
    }
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.phone_title))
        Spacer(Modifier.height(10.dp))
        val phone = phoneMs
        if (!access || phone == null) {
            Text(stringResource(R.string.phone_need_access), style = AppText.caption, color = w.inkSoft)
            TextAction(stringResource(R.string.phone_guide_open), { guide = true })
        } else {
            val max = maxOf(studyMs, phone, 1L).toFloat()
            CompareRow(stringResource(R.string.phone_study), studyMs, studyMs / max, w.accent)
            Spacer(Modifier.height(10.dp))
            CompareRow(stringResource(R.string.phone_phone), phone, phone / max, w.inkFaint)
            Text(stringResource(R.string.phone_note), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(top = 10.dp))
        }
    }
    if (guide) UsageGuideDialog { guide = false }
}

@Composable
private fun CompareRow(label: String, ms: Long, fraction: Float, color: Color) {
    val w = LocalWarm.current
    Column {
        Row { Text(label, style = AppText.body, modifier = Modifier.weight(1f)); Text(formatDuration(ms), style = AppText.number, color = w.inkSoft) }
        Spacer(Modifier.height(6.dp))
        ThinProgress(fraction, color = color)
    }
}

@Composable
private fun UsageGuideDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    WarmDialog(
        title = stringResource(R.string.phone_guide_title), onDismiss = onDismiss,
        confirmText = stringResource(R.string.phone_open_settings),
        onConfirm = { context.startActivity(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS)); onDismiss() },
    ) { Text(stringResource(R.string.phone_guide_steps), style = AppText.body) }
}

@Composable
private fun WeekCard(
    offset: Int, onOffset: (Int) -> Unit, thisMonday: LocalDate, today: LocalDate,
    totals: Map<LocalDate, com.studywithme.app.data.TodayTotals>, subjects: List<Subject>, liveTodayMs: Long,
) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val monday = thisMonday.plusWeeks(offset.toLong())
    val days = (0..6).map { monday.plusDays(it.toLong()) }
    val names = stringArrayResource(R.array.weekday_short)
    val labels = days.map { names[it.dayOfWeek.value % 7] }
    fun ms(d: LocalDate) = if (d == today) liveTodayMs else totals[d]?.totalMs ?: 0L
    val rangeFmt = DateTimeFormatter.ofPattern(stringResource(R.string.cal_range_pattern), Locale.KOREAN)
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val access = remember(resumes) { c.usage.hasAccess() }
    val phone by produceState<List<Float>?>(null, access, monday) {
        value = if (access) withContext(Dispatchers.IO) {
            days.map { d ->
                val start = studyDayStartOf(d)
                if (start > System.currentTimeMillis()) 0f else c.usage.phoneMs(start, minOf(start + DAY_MS, System.currentTimeMillis())) / 3_600_000f
            }
        } else null
    }
    // subject share for the week
    val share = HashMap<Long?, Long>()
    days.forEach { d ->
        val t = totals[d]
        if (d == today) { /* live total has no per-subject split; the saved part is enough for the ring */ }
        t?.bySubject?.forEach { (id, v) -> share[id] = (share[id] ?: 0L) + v }
    }
    val weekTotal = share.values.sum()

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.stats_week_title), Modifier.weight(1f))
            TextAction(stringResource(R.string.cal_prev), { onOffset(-1) })
            Text(stringResource(R.string.dday_range, rangeFmt.format(monday), rangeFmt.format(monday.plusDays(6))), style = AppText.caption, color = w.inkSoft)
            TextAction(stringResource(R.string.cal_next), { if (offset < 0) onOffset(1) })
        }
        Spacer(Modifier.height(8.dp))
        WeeklyBarsHost(labels, days.map { ms(it) / 3_600_000f }, phone, days.indexOf(today))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(stringResource(R.string.stats_legend_study), w.accent)
            if (phone != null) Legend(stringResource(R.string.stats_legend_phone), w.inkFaint)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.stats_unit_hours), style = AppText.caption, color = w.inkSoft)
        }
        Hairline(Modifier.padding(vertical = 16.dp))
        SectionTitle(stringResource(R.string.stats_share_title))
        Spacer(Modifier.height(12.dp))
        if (weekTotal == 0L) {
            Text(stringResource(R.string.stats_no_data), style = AppText.caption, color = w.inkSoft)
        } else {
            val ordered = share.entries.sortedByDescending { it.value }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                SubjectDonut(ordered.map { (id, v) -> (subjects.firstOrNull { it.id == id }?.let { Color(it.color) } ?: w.inkFaint) to v }, formatDuration(weekTotal))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ordered.take(8).forEach { (id, v) ->
                        val s = subjects.firstOrNull { it.id == id }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(s?.let { Color(it.color) } ?: w.inkFaint, 8.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(s?.name ?: stringResource(R.string.subject_deleted), style = AppText.caption, modifier = Modifier.weight(1f))
                            Text("${v * 100 / weekTotal}%", style = AppText.caption, color = w.inkSoft)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyBarsHost(labels: List<String>, study: List<Float>, phone: List<Float>?, todayIndex: Int) =
    com.studywithme.app.ui.components.WeeklyBars(labels, study, phone, todayIndex)

@Composable
private fun Legend(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ColorDot(color, 8.dp); Spacer(Modifier.width(6.dp)); Text(text, style = AppText.caption, color = LocalWarm.current.inkSoft)
    }
}

@Composable
private fun HeatCard(
    weeks: Int, firstMonday: LocalDate, today: LocalDate, totals: Map<LocalDate, com.studywithme.app.data.TodayTotals>, goalMs: Long,
    selected: LocalDate?, onSelect: (LocalDate?) -> Unit, subjects: List<Subject>, sessions: List<com.studywithme.app.data.StudySession>, liveTodayMs: Long,
) {
    val w = LocalWarm.current
    fun dateAt(week: Int, row: Int) = firstMonday.plusWeeks(week.toLong()).plusDays(row.toLong())
    fun ms(d: LocalDate) = if (d == today) liveTodayMs else totals[d]?.totalMs ?: 0L
    val dayFmt = DateTimeFormatter.ofPattern(stringResource(R.string.cal_day_pattern), Locale.KOREAN)
    val selectedCell = selected?.let { d ->
        val diff = java.time.temporal.ChronoUnit.DAYS.between(firstMonday, d).toInt()
        if (diff in 0 until weeks * 7) diff / 7 to diff % 7 else null
    }
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.stats_heat_title))
        Spacer(Modifier.height(12.dp))
        Heatmap(
            weeks, { wk, row -> val d = dateAt(wk, row); if (d.isAfter(today)) -1 else heatLevel(ms(d), goalMs) },
            selectedCell, { wk, row -> val d = dateAt(wk, row); onSelect(if (d.isAfter(today)) null else d) },
        )
        Spacer(Modifier.height(12.dp))
        if (selected == null) {
            Text(stringResource(R.string.stats_heat_hint), style = AppText.caption, color = w.inkSoft)
        } else {
            Text(dayFmt.format(selected), style = AppText.bodyMedium)
            val total = ms(selected)
            if (total < 60_000) {
                Text(stringResource(R.string.stats_day_empty), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(top = 4.dp))
            } else {
                Text(stringResource(R.string.stats_day_total, formatDuration(total)), style = AppText.body, color = w.accent, modifier = Modifier.padding(vertical = 4.dp))
                totals[selected]?.bySubject?.entries?.sortedByDescending { it.value }?.forEach { (id, v) ->
                    val s = subjects.firstOrNull { it.id == id }
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(s?.let { Color(it.color) } ?: w.inkFaint, 8.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(s?.name ?: stringResource(R.string.subject_deleted), style = AppText.caption, modifier = Modifier.weight(1f))
                        Text(formatDuration(v), style = AppText.caption, color = w.inkSoft)
                    }
                }
                val avg = focusAverages(sessions.filter { studyDay(it.startMs) == selected }).byDay[selected]
                if (avg != null) Text(stringResource(R.string.stats_focus_title) + "  " + stringResource(R.string.stats_focus_avg, avg), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun FocusCard(sessions: List<com.studywithme.app.data.StudySession>, subjects: List<Subject>, today: LocalDate) {
    val w = LocalWarm.current
    val avg = remember(sessions) { focusAverages(sessions) }
    val dayFmt = DateTimeFormatter.ofPattern(stringResource(R.string.cal_range_pattern), Locale.KOREAN)
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.stats_focus_title))
        if (avg.bySubject.isEmpty()) {
            Text(stringResource(R.string.stats_focus_none), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(top = 8.dp))
            return@GlassCard
        }
        FocusGroup(stringResource(R.string.stats_focus_subject), avg.bySubject.entries.sortedByDescending { it.value }.map {
            (subjects.firstOrNull { s -> s.id == it.key }?.name ?: stringResource(R.string.subject_deleted)) to it.value
        })
        FocusGroup(stringResource(R.string.stats_focus_time), TimeOfDay.entries.mapNotNull { t ->
            avg.byTimeOfDay[t]?.let { stringResource(when (t) { TimeOfDay.Morning -> R.string.tod_morning; TimeOfDay.Afternoon -> R.string.tod_afternoon; TimeOfDay.Evening -> R.string.tod_evening; TimeOfDay.LateNight -> R.string.tod_night }) to it }
        })
        FocusGroup(stringResource(R.string.stats_focus_day), (6 downTo 0).mapNotNull { i -> today.minusDays(i.toLong()).let { d -> avg.byDay[d]?.let { dayFmt.format(d) to it } } })
    }
}

@Composable
private fun FocusGroup(title: String, rows: List<Pair<String, Float>>) {
    if (rows.isEmpty()) return
    val w = LocalWarm.current
    Spacer(Modifier.height(16.dp))
    Text(title, style = AppText.caption, color = w.inkSoft)
    rows.forEach { (label, v) ->
        Column(Modifier.padding(top = 8.dp)) {
            Row { Text(label, style = AppText.body, modifier = Modifier.weight(1f)); Text(stringResource(R.string.stats_focus_avg, v), style = AppText.number, color = w.inkSoft) }
            Spacer(Modifier.height(4.dp))
            ThinProgress(v / 5f)
        }
    }
}

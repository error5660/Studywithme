package com.studywithme.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.data.CalendarEvent
import com.studywithme.app.data.Subject
import com.studywithme.app.reminders.occursOn
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.components.Chip
import com.studywithme.app.ui.components.ColorPicker
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.Hairline
import com.studywithme.app.ui.components.LabeledTextField
import com.studywithme.app.ui.components.SettingRow
import com.studywithme.app.ui.components.Stepper
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.components.WarmDialog
import com.studywithme.app.ui.components.WarmSwitch
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import com.studywithme.app.ui.theme.SubjectPalette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class CalMode { Month, Week, Day }

private class EventDraft(val event: CalendarEvent?, val date: LocalDate)

@Composable
private fun rememberFmt(patternRes: Int): DateTimeFormatter {
    val pattern = stringResource(patternRes)
    return remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.KOREAN) }
}

private fun hm(min: Int) = "%02d:%02d".format(min / 60, min % 60)

@Composable
fun CalendarScreen(padding: PaddingValues) {
    val c = LocalContainer.current
    val layout = rememberLayout()
    val events by c.db.eventDao().observeAll().collectAsStateWithLifecycle(emptyList())
    var mode by rememberSaveable { mutableStateOf(CalMode.Month) }
    var selectedEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var draft by remember { mutableStateOf<EventDraft?>(null) }
    val day = LocalDate.ofEpochDay(selectedEpoch)

    val monthFmt = rememberFmt(R.string.cal_month_pattern)
    val dayFmt = rememberFmt(R.string.cal_day_pattern)
    val rangeFmt = rememberFmt(R.string.cal_range_pattern)
    val weekStart = day.minusDays((day.dayOfWeek.value % 7).toLong()) // weeks start on Sunday
    val title = when (mode) {
        CalMode.Month -> monthFmt.format(day)
        CalMode.Week -> stringResource(R.string.dday_range, rangeFmt.format(weekStart), rangeFmt.format(weekStart.plusDays(6)))
        CalMode.Day -> dayFmt.format(day)
    }
    fun move(dir: Long) {
        selectedEpoch = when (mode) {
            CalMode.Month -> day.plusMonths(dir)
            CalMode.Week -> day.plusWeeks(dir)
            CalMode.Day -> day.plusDays(dir)
        }.toEpochDay()
    }

    Column(Modifier.fillMaxSize().padding(padding).padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = AppText.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextAction(stringResource(R.string.cal_prev), { move(-1) })
            TextAction(stringResource(R.string.cal_today), { selectedEpoch = LocalDate.now().toEpochDay() })
            TextAction(stringResource(R.string.cal_next), { move(1) })
        }
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.cal_mode_month), mode == CalMode.Month, { mode = CalMode.Month })
            Chip(stringResource(R.string.cal_mode_week), mode == CalMode.Week, { mode = CalMode.Week })
            Chip(stringResource(R.string.cal_mode_day), mode == CalMode.Day, { mode = CalMode.Day })
            Spacer(Modifier.weight(1f))
            TextAction(stringResource(R.string.cal_add), { draft = EventDraft(null, day) })
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val onEdit = { e: CalendarEvent, d: LocalDate -> draft = EventDraft(e, d) }
            when (mode) {
                CalMode.Month ->
                    if (layout.twoPane) {
                        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { MonthGrid(day, events) { selectedEpoch = it.toEpochDay() } }
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Agenda(day, events, onEdit) }
                        }
                    } else {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            MonthGrid(day, events) { selectedEpoch = it.toEpochDay() }
                            Agenda(day, events, onEdit)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                CalMode.Week -> Timeline(List(7) { weekStart.plusDays(it.toLong()) }, events, detailed = false, onEdit)
                CalMode.Day -> Timeline(listOf(day), events, detailed = true, onEdit)
            }
        }
    }

    draft?.let { d -> EventDialog(d, onDismiss = { draft = null }) }
}

@Composable
private fun MonthGrid(selected: LocalDate, events: List<CalendarEvent>, onSelect: (LocalDate) -> Unit) {
    val w = LocalWarm.current
    val today = LocalDate.now()
    val first = YearMonth.from(selected).atDay(1)
    val lead = first.dayOfWeek.value % 7
    val length = first.lengthOfMonth()
    val rows = (lead + length + 6) / 7
    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            stringArrayResource(R.array.weekday_short).forEachIndexed { i, name ->
                Text(name, style = AppText.caption, color = if (i == 0) w.accent else w.inkSoft, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Spacer(Modifier.height(4.dp))
        repeat(rows) { r ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val n = r * 7 + col - lead + 1
                    if (n !in 1..length) { Spacer(Modifier.weight(1f)) } else {
                        val date = first.plusDays((n - 1).toLong())
                        val colors = events.filter { it.occursOn(date) }.map { it.color }.distinct().take(3)
                        val isSel = date == selected
                        Column(
                            Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(12.dp)).clickable { onSelect(date) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(if (isSel) w.accent else if (date == today) w.accentSoft else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) { Text(n.toString(), style = AppText.number, color = if (isSel) w.onAccent else if (date == today) w.accent else w.ink) }
                            Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                colors.forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(Color(it))) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Agenda(day: LocalDate, events: List<CalendarEvent>, onEdit: (CalendarEvent, LocalDate) -> Unit) {
    val w = LocalWarm.current
    val dayFmt = rememberFmt(R.string.cal_day_pattern)
    val today = events.filter { it.occursOn(day) }.sortedBy { it.startMin }
    GlassCard(Modifier.fillMaxWidth()) {
        Text(dayFmt.format(day), style = AppText.bodyMedium)
        Spacer(Modifier.height(8.dp))
        if (today.isEmpty()) Text(stringResource(R.string.cal_empty_day), style = AppText.caption, color = w.inkSoft)
        today.forEachIndexed { i, e ->
            if (i > 0) Hairline()
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 60.dp).clickable { onEdit(e, day) }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).height(36.dp).clip(RoundedCornerShape(2.dp)).background(Color(e.color)))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(e.title, style = AppText.bodyMedium)
                    Text(eventDetail(e), style = AppText.caption, color = w.inkSoft)
                }
            }
        }
    }
}

@Composable
private fun eventDetail(e: CalendarEvent): String {
    val parts = mutableListOf("${hm(e.startMin)}–${hm(e.endMin)}")
    if (e.travelMin > 0) parts += stringResource(R.string.cal_travel_badge, e.travelMin)
    if (e.weekly) parts += stringResource(R.string.cal_weekly_badge)
    return parts.joinToString(" · ")
}

private val HourHeight = 56.dp

/** Hour grid with event blocks. shortcut: overlapping events are drawn on top of each other, not side by side. */
@Composable
private fun Timeline(days: List<LocalDate>, events: List<CalendarEvent>, detailed: Boolean, onEdit: (CalendarEvent, LocalDate) -> Unit) {
    val w = LocalWarm.current
    val today = LocalDate.now()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val dowFmt = rememberFmt(R.string.cal_dow_pattern)
    LaunchedEffect(Unit) { scroll.scrollTo(with(density) { (HourHeight * 7).roundToPx() }) }
    val nowMin = remember(days) { LocalTime.now().let { it.hour * 60 + it.minute } }

    GlassCard(Modifier.fillMaxSize(), padding = PaddingValues(0.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Spacer(Modifier.width(40.dp))
            days.forEach { d ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(dowFmt.format(d), style = AppText.caption, color = w.inkSoft)
                    Text(d.dayOfMonth.toString(), style = AppText.bodyMedium, color = if (d == today) w.accent else w.ink)
                }
            }
        }
        Hairline()
        Row(Modifier.weight(1f).verticalScroll(scroll)) {
            Column(Modifier.width(40.dp)) {
                repeat(24) { h -> Box(Modifier.height(HourHeight), contentAlignment = Alignment.TopCenter) { Text(stringResource(R.string.cal_hour_label, h), style = AppText.caption, color = w.inkFaint) } }
            }
            days.forEach { d ->
                Box(
                    Modifier.weight(1f).height(HourHeight * 24).drawBehind {
                        repeat(24) { h -> drawLine(w.inkFaint.copy(alpha = 0.3f), Offset(0f, h * HourHeight.toPx()), Offset(size.width, h * HourHeight.toPx()), 1f) }
                    },
                ) {
                    events.filter { it.occursOn(d) }.forEach { e ->
                        Box(
                            Modifier.fillMaxWidth().offset(y = HourHeight * (e.startMin / 60f)).height(HourHeight * ((e.endMin - e.startMin) / 60f))
                                .padding(horizontal = 1.dp).clip(RoundedCornerShape(6.dp)).background(Color(e.color).copy(alpha = 0.3f))
                                .clickable { onEdit(e, d) },
                        ) {
                            Row(Modifier.fillMaxSize()) {
                                Box(Modifier.width(3.dp).fillMaxHeight().background(Color(e.color)))
                                Column(Modifier.padding(horizontal = 5.dp, vertical = 3.dp)) {
                                    Text(e.title, style = if (detailed) AppText.label else AppText.caption.copy(fontSize = 11.sp, lineHeight = 13.sp), maxLines = if (detailed) 2 else 3, overflow = TextOverflow.Ellipsis)
                                    if (detailed) Text(eventDetail(e), style = AppText.caption, color = w.inkSoft)
                                }
                            }
                        }
                    }
                    if (d == today) Box(Modifier.fillMaxWidth().offset(y = HourHeight * (nowMin / 60f)).height(2.dp).background(w.accent))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EventDialog(draft: EventDraft, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val dao = c.db.eventDao()
    val e = draft.event
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val titleState = rememberTextFieldState(e?.title.orEmpty())
    var kind by remember { mutableIntStateOf(e?.kind ?: CalendarEvent.KIND_EVENT) }
    var subjectId by remember { mutableStateOf(e?.subjectId) }
    var date by remember { mutableStateOf(e?.let { LocalDate.ofEpochDay(it.dateEpochDay) } ?: draft.date) }
    var startMin by remember { mutableIntStateOf(e?.startMin ?: 9 * 60) }
    var endMin by remember { mutableIntStateOf(e?.endMin ?: 10 * 60) }
    var weekly by remember { mutableStateOf(e?.weekly ?: false) }
    var travel by remember { mutableIntStateOf(e?.travelMin ?: 0) }
    var reminder by remember { mutableStateOf(e?.reminderOn ?: true) }
    var color by remember { mutableIntStateOf(e?.color ?: SubjectPalette[0].toArgb()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf<Boolean?>(null) } // true = start, false = end
    var confirmDelete by remember { mutableStateOf(false) }
    val dateFmt = rememberFmt(R.string.event_date_format)

    val timeOk = endMin > startMin
    val valid = titleState.text.isNotBlank() && timeOk

    WarmDialog(
        title = stringResource(if (e == null) R.string.event_add_title else R.string.event_edit_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.common_save),
        confirmEnabled = valid,
        onConfirm = {
            val saved = CalendarEvent(
                id = e?.id ?: 0, title = titleState.text.toString().trim(), kind = kind, dateEpochDay = date.toEpochDay(),
                startMin = startMin, endMin = endMin, weekly = weekly, travelMin = travel, reminderOn = reminder,
                subjectId = if (kind == CalendarEvent.KIND_EXAM) subjectId else null, color = color,
            )
            c.scope.launch { if (e == null) dao.insert(saved) else dao.update(saved) }
            onDismiss()
        },
    ) {
        LabeledTextField(stringResource(R.string.event_title_label), titleState)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.event_kind_event), kind == CalendarEvent.KIND_EVENT, { kind = CalendarEvent.KIND_EVENT })
            Chip(stringResource(R.string.event_kind_exam), kind == CalendarEvent.KIND_EXAM, { kind = CalendarEvent.KIND_EXAM })
        }
        if (kind == CalendarEvent.KIND_EXAM) {
            Text(stringResource(R.string.event_exam_subject), style = AppText.caption, color = w.inkSoft)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subjects.forEach { s: Subject -> Chip(s.name, subjectId == s.id, { subjectId = s.id; color = s.color }) }
            }
        }
        ValueRow(stringResource(R.string.event_date), dateFmt.format(date)) { pickDate = true }
        ValueRow(stringResource(R.string.event_start), hm(startMin)) { pickTime = true }
        ValueRow(stringResource(R.string.event_end), hm(endMin)) { pickTime = false }
        if (!timeOk) Text(stringResource(R.string.event_time_error), style = AppText.caption, color = w.accent)
        SettingRow(stringResource(R.string.event_weekly), description = stringResource(R.string.event_weekly_desc)) { WarmSwitch(weekly) { weekly = it } }
        Stepper(
            stringResource(R.string.event_travel), stringResource(R.string.duration_m, travel),
            { travel = (travel - 5).coerceAtLeast(0) }, { travel = (travel + 5).coerceAtMost(120) },
        )
        SettingRow(stringResource(R.string.event_reminder), description = stringResource(R.string.event_reminder_desc)) { WarmSwitch(reminder) { reminder = it } }
        ColorPicker(color) { color = it }
        if (e != null) TextAction(stringResource(R.string.common_delete), { confirmDelete = true }, color = w.inkSoft)
    }

    if (pickDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = { TextAction(stringResource(R.string.common_save), { st.selectedDateMillis?.let { date = LocalDate.ofEpochDay(it / 86_400_000L) }; pickDate = false }) },
            dismissButton = { TextAction(stringResource(R.string.common_cancel), { pickDate = false }, color = w.inkSoft) },
        ) { DatePicker(st) }
    }
    pickTime?.let { isStart ->
        val cur = if (isStart) startMin else endMin
        val st = rememberTimePickerState(cur / 60, cur % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = null },
            containerColor = w.dialog, shape = RoundedCornerShape(20.dp),
            title = { Text(stringResource(R.string.event_pick_time_title), style = AppText.bodyMedium) },
            text = { TimeInput(st) },
            confirmButton = {
                TextAction(stringResource(R.string.common_save), {
                    val m = st.hour * 60 + st.minute
                    if (isStart) { startMin = m; if (endMin <= m) endMin = (m + 60).coerceAtMost(24 * 60) } else endMin = m
                    pickTime = null
                })
            },
            dismissButton = { TextAction(stringResource(R.string.common_cancel), { pickTime = null }, color = w.inkSoft) },
        )
    }
    if (confirmDelete && e != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = w.dialog, titleContentColor = w.ink, textContentColor = w.inkSoft, shape = RoundedCornerShape(20.dp),
            title = { Text(stringResource(R.string.event_delete_title), style = AppText.bodyMedium) },
            text = { if (e.weekly) Text(stringResource(R.string.event_delete_body), style = AppText.body) },
            confirmButton = { TextAction(stringResource(R.string.common_delete), { c.scope.launch { dao.delete(e) }; confirmDelete = false; onDismiss() }) },
            dismissButton = { TextAction(stringResource(R.string.common_cancel), { confirmDelete = false }, color = w.inkSoft) },
        )
    }
}

@Composable
private fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    val w = LocalWarm.current
    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppText.body, modifier = Modifier.weight(1f))
        Text(value, style = AppText.number.copy(fontSize = AppText.body.fontSize), color = w.accent)
    }
}

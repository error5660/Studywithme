package com.studywithme.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.data.CalendarEvent
import com.studywithme.app.data.Subject
import com.studywithme.app.data.Task
import com.studywithme.app.data.studyDay
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.components.Chip
import com.studywithme.app.ui.components.ColorDot
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.Hairline
import com.studywithme.app.ui.components.LabeledTextField
import com.studywithme.app.ui.components.SectionTitle
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.components.WarmDialog
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Composable
private fun ddayText(today: LocalDate, date: LocalDate): String {
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    return if (days <= 0) stringResource(R.string.dday_today) else stringResource(R.string.dday_format, days)
}

/** Countdown to the next exam, plus the upcoming exams per subject. Hidden when no exam is ahead. */
@Composable
fun DdayCard() {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val events by c.db.eventDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val now by c.tick.collectAsStateWithLifecycle(System.currentTimeMillis())
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val exams = remember(events, today) {
        events.filter { it.kind == CalendarEvent.KIND_EXAM }
            .map { it to LocalDate.ofEpochDay(it.dateEpochDay) }
            .filter { !it.second.isBefore(today) }
            .sortedWith(compareBy({ it.second }, { it.first.startMin }))
    }
    if (exams.isEmpty()) return
    val first = exams.first().second
    val periodEnd = exams.filter { it.second <= first.plusDays(7) }.maxOf { it.second }
    val fmt = DateTimeFormatter.ofPattern(stringResource(R.string.cal_range_pattern), Locale.KOREAN)

    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.dday_title))
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(ddayText(today, first), style = AppText.hero.copy(fontSize = 44.sp, lineHeight = 52.sp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.dday_range, fmt.format(first), fmt.format(periodEnd)), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        exams.take(4).forEachIndexed { i, (e, date) ->
            if (i > 0) Hairline()
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(ddayText(today, date), style = AppText.number, color = w.accent, modifier = Modifier.width(64.dp))
                Text(e.title, style = AppText.body, modifier = Modifier.weight(1f))
                Text(fmt.format(date), style = AppText.caption, color = w.inkSoft)
            }
        }
        if (exams.size > 4) Text(stringResource(R.string.dday_more, exams.size - 4), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(top = 4.dp))
    }
}

private class TaskDraft(val task: Task?)

/** Today's checklist. Unfinished tasks from earlier days trigger a "move to today?" dialog. */
@Composable
fun TodoCard() {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val dao = c.db.taskDao()
    val now by c.tick.collectAsStateWithLifecycle(System.currentTimeMillis())
    val day = studyDay(now).toEpochDay()
    val tasks by remember(day) { dao.observeDay(day) }.collectAsStateWithLifecycle(emptyList())
    val stale by remember(day) { dao.observeStale(day) }.collectAsStateWithLifecycle(emptyList())
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    var draft by remember { mutableStateOf<TaskDraft?>(null) }
    var promptTick by remember { mutableIntStateOf(0) } // re-read c.carryPromptDismissed after "later"

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.todo_title))
            if (tasks.isNotEmpty()) Text(stringResource(R.string.todo_count, tasks.count { it.done }, tasks.size), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(start = 10.dp))
            Spacer(Modifier.weight(1f))
            TextAction(stringResource(R.string.todo_add), { draft = TaskDraft(null) })
        }
        if (tasks.isEmpty()) Text(stringResource(R.string.todo_empty), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 8.dp))
        tasks.forEach { t ->
            val subject = subjects.firstOrNull { it.id == t.subjectId }
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    t.done, { done -> c.scope.launch { dao.update(t.copy(done = done)) } },
                    colors = CheckboxDefaults.colors(checkedColor = w.accent, uncheckedColor = w.inkFaint, checkmarkColor = w.onAccent),
                )
                Column(Modifier.weight(1f).clickable { draft = TaskDraft(t) }.padding(vertical = 6.dp)) {
                    Text(t.text, style = AppText.body, color = if (t.done) w.inkSoft else w.ink, textDecoration = if (t.done) TextDecoration.LineThrough else null)
                }
                if (subject != null) { ColorDot(Color(subject.color), 8.dp); Spacer(Modifier.width(6.dp)); Text(subject.name, style = AppText.caption, color = w.inkSoft) }
            }
        }
    }

    draft?.let { d -> TaskDialog(d.task, day, subjects) { draft = null } }

    if (stale.isNotEmpty() && !(c.carryPromptDismissed && promptTick >= 0)) {
        val w2 = LocalWarm.current
        AlertDialog(
            onDismissRequest = { c.carryPromptDismissed = true; promptTick++ },
            containerColor = w2.dialog, titleContentColor = w2.ink, textContentColor = w2.inkSoft, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            title = { Text(stringResource(R.string.carry_title), style = AppText.bodyMedium) },
            text = { Text(stringResource(R.string.carry_body, stale.size), style = AppText.body) },
            confirmButton = { TextAction(stringResource(R.string.carry_move), { c.scope.launch { dao.moveStaleTo(day) } }) },
            dismissButton = {
                Row {
                    TextAction(stringResource(R.string.carry_delete), { c.scope.launch { dao.deleteStale(day) } }, color = w2.inkSoft)
                    TextAction(stringResource(R.string.carry_later), { c.carryPromptDismissed = true; promptTick++ }, color = w2.inkSoft)
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskDialog(task: Task?, day: Long, subjects: List<Subject>, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val dao = c.db.taskDao()
    val text = rememberTextFieldState(task?.text.orEmpty())
    var subjectId by remember { mutableStateOf(task?.subjectId) }
    WarmDialog(
        title = stringResource(if (task == null) R.string.todo_add_title else R.string.todo_edit_title),
        onDismiss = onDismiss,
        confirmText = stringResource(R.string.common_save),
        confirmEnabled = text.text.isNotBlank(),
        onConfirm = {
            val value = text.text.toString().trim()
            c.scope.launch { if (task == null) dao.insert(Task(text = value, subjectId = subjectId, dayEpoch = day)) else dao.update(task.copy(text = value, subjectId = subjectId)) }
            onDismiss()
        },
    ) {
        LabeledTextField(stringResource(R.string.todo_text_label), text, maxLength = 60)
        Text(stringResource(R.string.todo_subject_label), style = AppText.caption, color = w.inkSoft)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.todo_no_subject), subjectId == null, { subjectId = null })
            subjects.forEach { s -> Chip(s.name, subjectId == s.id, { subjectId = s.id }) }
        }
        if (task != null) TextAction(stringResource(R.string.common_delete), { c.scope.launch { dao.delete(task) }; onDismiss() }, color = w.inkSoft)
    }
}

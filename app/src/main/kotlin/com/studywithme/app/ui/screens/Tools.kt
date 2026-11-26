package com.studywithme.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLengthTrim
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.data.Subject
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.LocalSettings
import com.studywithme.app.ui.components.Chip
import com.studywithme.app.ui.components.ColorDot
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.Hairline
import com.studywithme.app.ui.components.SoftButton
import com.studywithme.app.ui.components.Stepper
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.components.WarmSwitch
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import com.studywithme.app.ui.theme.SubjectPalette
import kotlinx.coroutines.launch

@Composable
fun ToolsScreen(padding: PaddingValues) {
    val layout = rememberLayout()
    if (layout.twoPane) {
        Row(Modifier.fillMaxSize().padding(padding).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { ToolsTitle(); Spacer(Modifier.height(20.dp)); PomodoroCard() }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { Spacer(Modifier.height(44.dp)); SubjectsCard() }
        }
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ToolsTitle()
            PomodoroCard()
            SubjectsCard()
        }
    }
}

@Composable
private fun ToolsTitle() = Text(stringResource(R.string.tools_title), style = AppText.title)

@Composable
private fun PomodoroCard() {
    val c = LocalContainer.current
    val s = LocalSettings.current
    val w = LocalWarm.current
    fun edit(block: (com.studywithme.app.data.Settings) -> com.studywithme.app.data.Settings) = c.updateSettings(block)
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.pomo_title), style = AppText.bodyMedium)
                Text(stringResource(R.string.pomo_summary, s.focusMin, s.breakMin), style = AppText.caption, color = w.inkSoft)
            }
            WarmSwitch(s.pomodoroOn) { on -> edit { it.copy(pomodoroOn = on) } }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.pomo_preset_25), s.focusMin == 25 && s.breakMin == 5, { edit { it.copy(focusMin = 25, breakMin = 5) } })
            Chip(stringResource(R.string.pomo_preset_50), s.focusMin == 50 && s.breakMin == 10, { edit { it.copy(focusMin = 50, breakMin = 10) } })
        }
        Spacer(Modifier.height(8.dp))
        Stepper(
            stringResource(R.string.pomo_focus_minutes), stringResource(R.string.duration_m, s.focusMin),
            { edit { it.copy(focusMin = (it.focusMin - 5).coerceAtLeast(5)) } },
            { edit { it.copy(focusMin = (it.focusMin + 5).coerceAtMost(180)) } },
        )
        Stepper(
            stringResource(R.string.pomo_break_minutes), stringResource(R.string.duration_m, s.breakMin),
            { edit { it.copy(breakMin = (it.breakMin - 1).coerceAtLeast(1)) } },
            { edit { it.copy(breakMin = (it.breakMin + 1).coerceAtMost(60)) } },
        )
        Hairline(Modifier.padding(vertical = 4.dp))
        Row(Modifier.defaultMinSize(minHeight = 52.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pomo_long_break), style = AppText.body, modifier = Modifier.weight(1f))
            WarmSwitch(s.longBreakOn) { on -> edit { it.copy(longBreakOn = on) } }
        }
        if (s.longBreakOn) {
            Stepper(
                stringResource(R.string.pomo_long_break_every), stringResource(R.string.pomo_long_break_every_value, s.longBreakEvery),
                { edit { it.copy(longBreakEvery = (it.longBreakEvery - 1).coerceAtLeast(2)) } },
                { edit { it.copy(longBreakEvery = (it.longBreakEvery + 1).coerceAtMost(8)) } },
            )
            Stepper(
                stringResource(R.string.pomo_long_break_minutes), stringResource(R.string.duration_m, s.longBreakMin),
                { edit { it.copy(longBreakMin = (it.longBreakMin - 5).coerceAtLeast(5)) } },
                { edit { it.copy(longBreakMin = (it.longBreakMin + 5).coerceAtMost(90)) } },
            )
        }
    }
}

@Composable
private fun SubjectsCard() {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val dao = c.db.subjectDao()
    val subjects by dao.observeAll().collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<Subject?>(null) }
    var adding by remember { mutableStateOf(false) }

    GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        Text(stringResource(R.string.subjects_title), style = AppText.bodyMedium)
        Text(stringResource(R.string.subjects_hint), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 4.dp))
        subjects.forEachIndexed { i, s ->
            if (i > 0) Hairline()
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable { editing = s }.defaultMinSize(minHeight = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(Color(s.color), 10.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(s.name, style = AppText.body)
                }
                if (i > 0) TextAction(stringResource(R.string.subject_move_up), { c.scope.launch { dao.swapOrder(subjects[i - 1], s) } }, color = w.inkSoft)
                if (i < subjects.lastIndex) TextAction(stringResource(R.string.subject_move_down), { c.scope.launch { dao.swapOrder(s, subjects[i + 1]) } }, color = w.inkSoft)
            }
        }
        Spacer(Modifier.height(8.dp))
        SoftButton(stringResource(R.string.subjects_add), { adding = true }, Modifier.fillMaxWidth())
    }

    if (adding) {
        SubjectDialog(null, onDismiss = { adding = false }, onDelete = null) { name, color ->
            c.scope.launch { dao.insert(Subject(name = name, color = color, sortOrder = dao.maxOrder() + 1)) }
            adding = false
        }
    }
    editing?.let { s ->
        SubjectDialog(s, onDismiss = { editing = null }, onDelete = { c.scope.launch { dao.delete(s) }; editing = null }) { name, color ->
            c.scope.launch { dao.update(s.copy(name = name, color = color)) }
            editing = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectDialog(
    initial: Subject?,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: (String, Int) -> Unit,
) {
    val w = LocalWarm.current
    val nameState = rememberTextFieldState(initial?.name.orEmpty())
    val name = nameState.text.toString()
    var color by remember { mutableIntStateOf(initial?.color ?: SubjectPalette.first().toArgb()) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = w.dialog,
        titleContentColor = w.ink,
        textContentColor = w.ink,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
        title = { Text(stringResource(if (initial == null) R.string.subject_add_title else R.string.subject_edit_title), style = AppText.title.copy(fontSize = AppText.bodyMedium.fontSize)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text(stringResource(R.string.subject_name_label), style = AppText.caption, color = w.inkSoft)
                    BasicTextField(
                        state = nameState, lineLimits = TextFieldLineLimits.SingleLine, inputTransformation = InputTransformation.maxLengthTrim(20),
                        textStyle = AppText.body.copy(color = w.ink), cursorBrush = SolidColor(w.accent),
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                    Hairline(Modifier.padding(top = 6.dp))
                }
                Text(stringResource(R.string.subject_color_label), style = AppText.caption, color = w.inkSoft)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SubjectPalette.forEach { c ->
                        val selected = c.toArgb() == color
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(c)
                                .then(if (selected) Modifier.border(3.dp, w.dialog, CircleShape).border(5.dp, c, CircleShape) else Modifier)
                                .clickable { color = c.toArgb() },
                        )
                    }
                }
                if (onDelete != null) TextAction(stringResource(R.string.common_delete), { confirmDelete = true }, color = w.inkSoft)
            }
        },
        confirmButton = { TextAction(stringResource(R.string.common_save), { if (name.isNotBlank()) onSave(name.trim(), color) }) },
        dismissButton = { TextAction(stringResource(R.string.common_cancel), onDismiss, color = w.inkSoft) },
    )

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = w.dialog, titleContentColor = w.ink, textContentColor = w.inkSoft,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
            title = { Text(stringResource(R.string.subject_delete_title), style = AppText.bodyMedium) },
            text = { Text(stringResource(R.string.subject_delete_body), style = AppText.body) },
            confirmButton = { TextAction(stringResource(R.string.common_delete), onDelete) },
            dismissButton = { TextAction(stringResource(R.string.common_cancel), { confirmDelete = false }, color = w.inkSoft) },
        )
    }
}

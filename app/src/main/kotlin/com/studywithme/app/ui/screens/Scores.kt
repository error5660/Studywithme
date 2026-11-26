package com.studywithme.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.data.Exam
import com.studywithme.app.data.ExamScore
import com.studywithme.app.data.Subject
import com.studywithme.app.data.studyDay
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.LocalSettings
import com.studywithme.app.ui.components.Chip
import com.studywithme.app.ui.components.ColorDot
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.Hairline
import com.studywithme.app.ui.components.LabeledTextField
import com.studywithme.app.ui.components.PrimaryButton
import com.studywithme.app.ui.components.SectionTitle
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.components.WarmDialog
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import kotlinx.coroutines.launch

/** Grade 1 (best) to 5, shown as "1" or as "A" depending on the chosen scale. */
private fun gradeShort(grade: Int, scale: Int): String = if (scale == 1) ('A' + grade - 1).toString() else grade.toString()

@Composable
private fun gradeLong(grade: Int?, scale: Int): String = when {
    grade == null -> stringResource(R.string.grade_none)
    scale == 1 -> ('A' + grade - 1).toString()
    else -> stringResource(R.string.grade_5, grade)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScoresScreen(onClose: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val layout = rememberLayout()
    val scale = LocalSettings.current.gradeScale
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val exams by c.db.examDao().observeExams().collectAsStateWithLifecycle(emptyList())
    val scoresOrNull by c.db.examDao().observeScores().collectAsStateWithLifecycle(null)
    var examId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    BackHandler(onBack = onClose)
    val scores = scoresOrNull ?: return
    val selected = exams.firstOrNull { it.id == examId } ?: exams.lastOrNull()

    val entry = @Composable { selected?.let { ExamEntry(it, subjects, scores.filter { s -> s.examId == it.id }, scale) } }
    val target = @Composable { TargetCard(subjects, exams, scores, scale) }
    val trend = @Composable { TrendCard(subjects, exams, scores, scale) }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextAction(stringResource(R.string.common_close), onClose)
            Text(stringResource(R.string.scores_title), style = AppText.title, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextAction(stringResource(R.string.scores_add_exam), { adding = true })
        }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.scores_scale_title), style = AppText.caption, color = w.inkSoft, modifier = Modifier.weight(1f))
                Chip(stringResource(R.string.scale_5), scale == 0, { c.updateSettings { it.copy(gradeScale = 0) } })
                Chip(stringResource(R.string.scale_ae), scale == 1, { c.updateSettings { it.copy(gradeScale = 1) } })
            }
            if (exams.isEmpty()) {
                Text(stringResource(R.string.scores_empty), style = AppText.body, color = w.inkSoft)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    exams.forEach { e -> Chip(e.name, selected?.id == e.id, { examId = e.id }) }
                }
                if (layout.twoPane) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(Modifier.weight(1f)) { entry() }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { target(); trend() }
                    }
                } else {
                    entry(); target(); trend()
                }
            }
        }
    }

    if (adding) {
        val name = rememberTextFieldState()
        WarmDialog(
            title = stringResource(R.string.scores_add_exam), onDismiss = { adding = false },
            confirmText = stringResource(R.string.common_save), confirmEnabled = name.text.isNotBlank(),
            onConfirm = {
                c.scope.launch { examId = c.db.examDao().insertExam(Exam(name = name.text.toString().trim(), dateEpochDay = studyDay(System.currentTimeMillis()).toEpochDay())) }
                adding = false
            },
        ) { LabeledTextField(stringResource(R.string.scores_exam_name), name, maxLength = 30) }
    }
}

/** Score and grade for every subject of one exam, saved together. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExamEntry(exam: Exam, subjects: List<Subject>, saved: List<ExamScore>, scale: Int) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val texts: Map<Long, TextFieldState> = remember(exam.id, subjects.size) { subjects.associate { s -> s.id to TextFieldState(saved.firstOrNull { it.subjectId == s.id }?.score?.toString().orEmpty()) } }
    val grades = remember(exam.id, subjects.size) { mutableStateMapOf<Long, Int?>().also { m -> subjects.forEach { s -> m[s.id] = saved.firstOrNull { it.subjectId == s.id }?.grade } } }
    var confirmDelete by remember { mutableStateOf(false) }
    var savedNote by remember(exam.id) { mutableStateOf(false) }

    GlassCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.scores_entry_title, exam.name), style = AppText.bodyMedium)
        subjects.forEachIndexed { i, s ->
            if (i > 0) Hairline(Modifier.padding(top = 8.dp))
            Column(Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    ColorDot(Color(s.color), 10.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(s.name, style = AppText.body, modifier = Modifier.weight(1f))
                    LabeledTextField(stringResource(R.string.scores_score), texts.getValue(s.id), Modifier.width(72.dp), numeric = true)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { g -> Chip(gradeShort(g, scale), grades[s.id] == g, { grades[s.id] = if (grades[s.id] == g) null else g; savedNote = false }) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(stringResource(R.string.scores_save), {
            val rows = subjects.map { s -> ExamScore(exam.id, s.id, texts.getValue(s.id).text.toString().toIntOrNull()?.coerceIn(0, 100), grades[s.id]) }
            c.scope.launch { c.db.examDao().upsertScores(rows) }
            savedNote = true
        }, Modifier.fillMaxWidth())
        TextAction(stringResource(R.string.scores_delete_exam), { confirmDelete = true }, color = w.inkSoft)
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = w.dialog, titleContentColor = w.ink, textContentColor = w.inkSoft,
            title = { Text(stringResource(R.string.scores_delete_title), style = AppText.bodyMedium) },
            text = { Text(stringResource(R.string.scores_delete_body), style = AppText.body) },
            confirmButton = { TextAction(stringResource(R.string.common_delete), { c.scope.launch { c.db.examDao().deleteScoresOf(exam.id); c.db.examDao().deleteExam(exam) }; confirmDelete = false }) },
            dismissButton = { TextAction(stringResource(R.string.common_cancel), { confirmDelete = false }, color = w.inkSoft) },
        )
    }
}

/** Latest grade per subject next to the target, with the gap in plain words. */
@Composable
private fun TargetCard(subjects: List<Subject>, exams: List<Exam>, scores: List<ExamScore>, scale: Int) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val order = exams.withIndex().associate { it.value.id to it.index }
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.scores_target_title))
        subjects.forEachIndexed { i, s ->
            if (i > 0) Hairline(Modifier.padding(top = 8.dp))
            val current = scores.filter { it.subjectId == s.id && it.grade != null }.maxByOrNull { order[it.examId] ?: -1 }?.grade
            Column(Modifier.padding(top = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDot(Color(s.color), 10.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(s.name, style = AppText.body, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.scores_current) + "  " + gradeLong(current, scale), style = AppText.number, color = w.ink)
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.scores_target), style = AppText.caption, color = w.inkSoft, modifier = Modifier.width(36.dp))
                    (1..5).forEach { g -> Chip(gradeShort(g, scale), s.targetGrade == g, { c.scope.launch { c.db.subjectDao().update(s.copy(targetGrade = if (s.targetGrade == g) null else g)) } }) }
                }
                val target = s.targetGrade
                Text(
                    when {
                        target == null -> stringResource(R.string.scores_no_target)
                        current == null -> stringResource(R.string.scores_no_grade)
                        current <= target -> stringResource(R.string.scores_gap_reached)
                        else -> stringResource(R.string.scores_gap_more, current - target)
                    },
                    style = AppText.caption, color = if (target != null && current != null && current > target) w.accent else w.inkSoft, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrendCard(subjects: List<Subject>, exams: List<Exam>, scores: List<ExamScore>, scale: Int) {
    val w = LocalWarm.current
    var subjectId by rememberSaveable { mutableStateOf<Long?>(null) }
    val subject = subjects.firstOrNull { it.id == subjectId } ?: subjects.firstOrNull() ?: return
    val points = exams.mapNotNull { e -> scores.firstOrNull { it.examId == e.id && it.subjectId == subject.id }?.grade?.let { e.name to it } }
    GlassCard(Modifier.fillMaxWidth()) {
        SectionTitle(stringResource(R.string.scores_trend_title))
        Text(stringResource(R.string.scores_trend_hint), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { s -> Chip(s.name, s.id == subject.id, { subjectId = s.id }) }
        }
        Spacer(Modifier.height(16.dp))
        if (points.isEmpty()) {
            Text(stringResource(R.string.scores_no_grade), style = AppText.caption, color = w.inkSoft)
        } else {
            Row(Modifier.fillMaxWidth().height(150.dp)) {
                Column(Modifier.width(30.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    (1..5).forEach { g -> Text(gradeShort(g, scale), style = AppText.caption.copy(fontSize = 11.sp), color = w.inkSoft) }
                }
                Canvas(Modifier.weight(1f).fillMaxHeight()) {
                    val n = points.size
                    fun x(i: Int) = size.width * (i + 0.5f) / n
                    fun y(g: Int) = size.height * (g - 1) / 4f
                    for (g in 1..5) drawLine(w.inkFaint.copy(alpha = 0.3f), Offset(0f, y(g)), Offset(size.width, y(g)), 1f)
                    for (i in 1 until n) drawLine(Color(subject.color), Offset(x(i - 1), y(points[i - 1].second)), Offset(x(i), y(points[i].second)), 3.dp.toPx())
                    points.forEachIndexed { i, p -> drawCircle(Color(subject.color), 5.dp.toPx(), Offset(x(i), y(p.second))) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Spacer(Modifier.width(30.dp))
                points.forEach { Text(it.first, style = AppText.caption.copy(fontSize = 10.sp), color = w.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
        }
    }
}

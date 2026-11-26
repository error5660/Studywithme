package com.studywithme.app.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.data.NoteReview
import com.studywithme.app.data.Subject
import com.studywithme.app.data.WrongNote
import com.studywithme.app.data.afterReview
import com.studywithme.app.data.firstReviewDay
import com.studywithme.app.data.studyDay
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.components.Chip
import com.studywithme.app.ui.components.ColorDot
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.LabeledTextField
import com.studywithme.app.ui.components.PrimaryButton
import com.studywithme.app.ui.components.SoftButton
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.components.WarmDialog
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A photo from private storage, loaded off the main thread and shrunk to about [maxPx]. */
@Composable
fun PhotoImage(name: String, maxPx: Int, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop) {
    val c = LocalContainer.current
    val bmp by produceState<ImageBitmap?>(null, name, maxPx) { value = c.photos.load(name, maxPx)?.asImageBitmap() }
    Box(modifier.background(LocalWarm.current.inkFaint.copy(alpha = 0.15f))) {
        bmp?.let { Image(it, contentDescription = null, modifier = if (scale == ContentScale.FillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxSize(), contentScale = scale) }
    }
}

private fun WrongNote.isDue(today: Long) = nextReviewDay != null && nextReviewDay <= today

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotebookScreen(onClose: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val layout = rememberLayout()
    val now by c.tick.collectAsStateWithLifecycle(System.currentTimeMillis())
    val today = studyDay(now).toEpochDay()
    val notes by c.db.noteDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    var subjectFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var dueOnly by rememberSaveable { mutableStateOf(false) }
    var editor by remember { mutableStateOf<WrongNote?>(null) }
    var adding by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<WrongNote?>(null) }
    var reviewing by remember { mutableStateOf(false) }

    BackHandler { if (reviewing) reviewing = false else onClose() }
    val dueCount = notes.count { it.isDue(today) }
    val shown = notes.filter { (subjectFilter == null || it.subjectId == subjectFilter) && (!dueOnly || it.isDue(today)) }

    if (reviewing) { ReviewScreen(embedded = false, onClose = { reviewing = false }); return }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextAction(stringResource(R.string.common_close), onClose)
            Text(stringResource(R.string.note_title), style = AppText.title, modifier = Modifier.weight(1f).padding(start = 8.dp))
            TextAction(stringResource(R.string.note_add), { adding = true })
        }
        FlowRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(stringResource(R.string.note_filter_all), subjectFilter == null, { subjectFilter = null })
            subjects.forEach { s -> Chip(s.name, subjectFilter == s.id, { subjectFilter = if (subjectFilter == s.id) null else s.id }) }
            Chip(stringResource(R.string.note_filter_due), dueOnly, { dueOnly = !dueOnly })
        }
        if (dueCount > 0) PrimaryButton(stringResource(R.string.note_review_start, dueCount), { reviewing = true }, Modifier.fillMaxWidth().padding(bottom = 12.dp))
        else Text(stringResource(R.string.note_review_hint), style = AppText.caption, color = w.inkSoft, modifier = Modifier.padding(bottom = 12.dp))
        if (shown.isEmpty()) Text(stringResource(R.string.note_empty), style = AppText.body, color = w.inkSoft)
        LazyVerticalGrid(
            GridCells.Adaptive(if (layout.twoPane) 200.dp else 150.dp), Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(shown, key = { it.id }) { n -> NoteCard(n, subjects.firstOrNull { it.id == n.subjectId }, today) { viewing = n } }
        }
    }

    if (adding) NoteEditor(null, subjects) { adding = false }
    editor?.let { n -> NoteEditor(n, subjects) { editor = null } }
    viewing?.let { n ->
        // read the live copy so the dialog reflects edits and reviews
        val live = notes.firstOrNull { it.id == n.id } ?: run { viewing = null; return@let }
        NoteDetail(live, subjects.firstOrNull { it.id == live.subjectId }, today, onEdit = { editor = live; viewing = null }, onDismiss = { viewing = null })
    }
}

@Composable
private fun NoteCard(note: WrongNote, subject: Subject?, today: Long, onClick: () -> Unit) {
    val w = LocalWarm.current
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick, padding = PaddingValues(0.dp)) {
        PhotoImage(note.photo, 480, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ColorDot(subject?.let { Color(it.color) } ?: w.inkFaint, 8.dp)
                Spacer(Modifier.width(6.dp))
                Text(subject?.name ?: stringResource(R.string.subject_deleted), style = AppText.caption, color = w.inkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (note.memo.isNotBlank()) Text(note.memo, style = AppText.caption, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            val (label, due) = when {
                note.nextReviewDay == null -> stringResource(R.string.note_badge_done) to false
                note.isDue(today) -> stringResource(R.string.note_badge_due) to true
                else -> stringResource(R.string.note_badge_stage, note.stage) to false
            }
            Text(label, style = AppText.caption, color = if (due) w.accent else w.inkSoft, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun NoteDetail(note: WrongNote, subject: Subject?, today: Long, onEdit: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    var showAnswer by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern(stringResource(R.string.cal_range_pattern), Locale.KOREAN)
    WarmDialog(
        title = subject?.name ?: stringResource(R.string.subject_deleted), onDismiss = onDismiss,
        confirmText = stringResource(R.string.note_edit), onConfirm = onEdit, dismissText = stringResource(R.string.common_close), wide = true,
    ) {
        PhotoImage(note.photo, 1600, Modifier.fillMaxWidth(), ContentScale.FillWidth)
        if (note.tags.isNotBlank()) Text(note.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("  ") { "#$it" }, style = AppText.caption, color = w.accent)
        if (note.memo.isNotBlank()) Text(note.memo, style = AppText.body)
        if (note.answerPhoto != null) {
            TextAction(stringResource(if (showAnswer) R.string.note_hide_answer else R.string.note_show_answer), { showAnswer = !showAnswer })
            if (showAnswer) PhotoImage(note.answerPhoto, 1600, Modifier.fillMaxWidth(), ContentScale.FillWidth)
        }
        Text(
            if (note.nextReviewDay == null) stringResource(R.string.note_badge_done) else stringResource(R.string.note_next_review, fmt.format(LocalDate.ofEpochDay(note.nextReviewDay))),
            style = AppText.caption, color = w.inkSoft,
        )
        TextAction(stringResource(R.string.common_delete), { confirmDelete = true }, color = w.inkSoft)
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = w.dialog, titleContentColor = w.ink, textContentColor = w.inkSoft,
            title = { Text(stringResource(R.string.note_delete_title), style = AppText.bodyMedium) },
            text = { Text(stringResource(R.string.note_delete_body), style = AppText.body) },
            confirmButton = { TextAction(stringResource(R.string.common_delete), { c.scope.launch { c.db.noteDao().delete(note); c.photos.delete(note.photo); c.photos.delete(note.answerPhoto) }; confirmDelete = false; onDismiss() }) },
            dismissButton = { TextAction(stringResource(R.string.common_cancel), { confirmDelete = false }, color = w.inkSoft) },
        )
    }
}

/** Add (note == null) or edit a note: problem photo, optional answer photo, subject, memo and tags. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditor(note: WrongNote?, subjects: List<Subject>, onDismiss: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val scope = rememberCoroutineScope()
    val memo = rememberTextFieldState(note?.memo.orEmpty())
    val tags = rememberTextFieldState(note?.tags.orEmpty())
    var photo by remember { mutableStateOf(note?.photo) }
    var answer by remember { mutableStateOf(note?.answerPhoto) }
    var subjectId by remember { mutableStateOf(note?.subjectId ?: subjects.firstOrNull()?.id) }
    var busy by remember { mutableStateOf(false) }
    val imported = remember { mutableStateListOf<String>() } // files added in this dialog, removed again on cancel
    var forAnswer by remember { mutableStateOf(false) }
    var capture by remember { mutableStateOf<Pair<java.io.File, Uri>?>(null) }

    fun accept(name: String) { imported += name; if (forAnswer) answer = name else photo = name }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { busy = true; runCatching { c.photos.import(uri) }.onSuccess(::accept); busy = false }
    }
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val (file, uri) = capture ?: return@rememberLauncherForActivityResult
        if (ok) scope.launch { busy = true; runCatching { c.photos.import(uri) }.onSuccess(::accept); file.delete(); busy = false } else file.delete()
    }
    fun launchCamera(isAnswer: Boolean) { forAnswer = isAnswer; c.photos.newCapture().also { capture = it }.let { take.launch(it.second) } }
    fun launchGallery(isAnswer: Boolean) { forAnswer = isAnswer; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    fun cancel() { imported.forEach { c.photos.delete(it) }; onDismiss() }

    WarmDialog(
        title = stringResource(if (note == null) R.string.note_new_title else R.string.note_edit_title),
        onDismiss = ::cancel,
        confirmText = stringResource(R.string.common_save),
        confirmEnabled = photo != null && !busy,
        wide = true,
        onConfirm = {
            val p = photo ?: return@WarmDialog
            val today = studyDay(System.currentTimeMillis()).toEpochDay()
            // photos that were replaced or removed are no longer referenced
            imported.filter { it != p && it != answer }.forEach { c.photos.delete(it) }
            if (note != null) {
                if (note.photo != p) c.photos.delete(note.photo)
                if (note.answerPhoto != null && note.answerPhoto != answer) c.photos.delete(note.answerPhoto)
            }
            val saved = (note ?: WrongNote(subjectId = null, photo = p, createdDay = today, nextReviewDay = firstReviewDay(today)))
                .copy(subjectId = subjectId, memo = memo.text.toString().trim(), tags = tags.text.toString().trim(), photo = p, answerPhoto = answer)
            c.scope.launch { if (note == null) c.db.noteDao().insert(saved) else c.db.noteDao().update(saved) }
            onDismiss()
        },
    ) {
        PhotoSlot(stringResource(R.string.note_photo_problem), photo, busy, { launchCamera(false) }, { launchGallery(false) }, { photo = null })
        Text(stringResource(R.string.note_subject_label), style = AppText.caption, color = w.inkSoft)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.forEach { s -> Chip(s.name, subjectId == s.id, { subjectId = s.id }) }
        }
        LabeledTextField(stringResource(R.string.note_memo_label), memo, maxLength = 200)
        LabeledTextField(stringResource(R.string.note_tags_label), tags, maxLength = 60)
        PhotoSlot(stringResource(R.string.note_photo_answer), answer, busy, { launchCamera(true) }, { launchGallery(true) }, { answer = null })
        if (photo == null) Text(stringResource(R.string.note_need_photo), style = AppText.caption, color = w.accent)
    }
}

@Composable
private fun PhotoSlot(label: String, photo: String?, busy: Boolean, onCamera: () -> Unit, onGallery: () -> Unit, onRemove: () -> Unit) {
    val w = LocalWarm.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = AppText.caption, color = w.inkSoft)
        if (photo != null) PhotoImage(photo, 800, Modifier.fillMaxWidth().aspectRatio(4f / 3f))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextAction(stringResource(R.string.note_take), onCamera)
            TextAction(stringResource(R.string.note_gallery), onGallery)
            if (photo != null) TextAction(stringResource(R.string.note_photo_remove), onRemove, color = w.inkSoft)
            if (busy) Text(stringResource(R.string.backup_working), style = AppText.caption, color = w.inkSoft)
        }
    }
}

/** Reviews the notes that are due, one by one: look at the problem, reveal the answer, mark right or wrong. */
@Composable
fun ReviewScreen(embedded: Boolean, onClose: () -> Unit) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val scope = rememberCoroutineScope()
    val today = studyDay(System.currentTimeMillis()).toEpochDay()
    val due by produceState<List<WrongNote>?>(null) { value = c.db.noteDao().due(today) } // fixed list for this session
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    var index by remember { mutableIntStateOf(0) }
    var revealed by remember { mutableStateOf(false) }
    var wrongCount by remember { mutableIntStateOf(0) }
    BackHandler { onClose() }
    val list = due ?: return

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.review_title), style = AppText.title, modifier = Modifier.weight(1f))
            if (list.isNotEmpty() && index < list.size) Text(stringResource(R.string.review_progress, index + 1, list.size), style = AppText.number, color = w.inkSoft)
            TextAction(stringResource(R.string.common_close), onClose)
        }
        if (list.isEmpty() || index >= list.size) {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(stringResource(if (list.isEmpty()) R.string.review_none else R.string.review_done_title), style = AppText.title)
                if (list.isNotEmpty()) Text(stringResource(R.string.review_done_body), style = AppText.body, color = w.inkSoft, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                if (list.isNotEmpty()) SoftButton(stringResource(R.string.common_close), onClose)
            }
        } else {
            val note = list[index]
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                subjects.firstOrNull { it.id == note.subjectId }?.let { Text(it.name, style = AppText.bodyMedium, color = w.inkSoft) }
                PhotoImage(note.photo, 1600, Modifier.fillMaxWidth(), ContentScale.FillWidth)
                if (revealed) {
                    if (note.answerPhoto != null) PhotoImage(note.answerPhoto, 1600, Modifier.fillMaxWidth(), ContentScale.FillWidth)
                    if (note.memo.isNotBlank()) Text(note.memo, style = AppText.body)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (!revealed) {
                PrimaryButton(stringResource(R.string.review_show), { revealed = true }, Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    fun answer(correct: Boolean) {
                        val (stage, next) = afterReview(note.stage, correct, today)
                        scope.launch {
                            c.db.noteDao().update(note.copy(stage = stage, nextReviewDay = next))
                            c.db.noteDao().insertReview(NoteReview(noteId = note.id, atMs = System.currentTimeMillis(), correct = correct))
                        }
                        if (!correct) wrongCount++
                        index++; revealed = false
                    }
                    SoftButton(stringResource(R.string.review_wrong), { answer(false) }, Modifier.weight(1f))
                    PrimaryButton(stringResource(R.string.review_right), { answer(true) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

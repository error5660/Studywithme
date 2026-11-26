package com.studywithme.app.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.data.CELLS_PER_ROW
import com.studywithme.app.data.CELL_COUNT
import com.studywithme.app.data.CELL_MS
import com.studywithme.app.data.GRID_ROWS
import com.studywithme.app.data.GRID_START_HOUR
import com.studywithme.app.data.ManualCell
import com.studywithme.app.data.StudySession
import com.studywithme.app.data.autoCells
import com.studywithme.app.data.computeTotals
import com.studywithme.app.data.gridStartMs
import com.studywithme.app.data.mergeCells
import com.studywithme.app.data.studyDay
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.components.Chip
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.rememberLayout
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LabelWidth = 30.dp
private val CellGap = 3.dp

/**
 * One day as 20 rows (06:00 to 02:00) of six ten-minute cells. The timer fills cells by itself;
 * in edit mode the student paints or erases cells by hand. Swipe sideways to change the day.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimetableScreen(embedded: Boolean = false, onClose: () -> Unit = {}) {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val layout = rememberLayout()
    val scope = rememberCoroutineScope()
    val now by c.tick.collectAsStateWithLifecycle(System.currentTimeMillis())
    val timer by c.engine.state.collectAsStateWithLifecycle()
    var dayEpoch by rememberSaveable { mutableLongStateOf(studyDay(System.currentTimeMillis()).toEpochDay()) }
    var editing by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf<Long?>(null) } // a subject id, ManualCell.ERASED, or null = nothing picked

    val day = LocalDate.ofEpochDay(dayEpoch)
    val gridStart = gridStartMs(day)
    val gridEnd = gridStart + CELL_COUNT * CELL_MS
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val saved by remember(dayEpoch) { c.db.sessionDao().observeBetween(gridStart, gridEnd) }.collectAsStateWithLifecycle(emptyList())
    val manual by remember(dayEpoch) { c.db.manualCellDao().observeDay(dayEpoch) }.collectAsStateWithLifecycle(emptyList())

    // The running timer is not in the database yet; add it so cells fill in while studying.
    val live = timer.liveFocusMs(SystemClock.elapsedRealtime())
    val sessions = if (live > 0 && day == studyDay(now)) saved + StudySession(subjectId = timer.subjectId, startMs = now - live, endMs = now) else saved
    val cells = mergeCells(autoCells(sessions, gridStart), manual)
    val dayTotal = computeTotals(sessions, com.studywithme.app.data.studyDayStartOf(day), null, 0, 0).totalMs

    val dayFmt = DateTimeFormatter.ofPattern(stringResource(R.string.cal_day_pattern), Locale.KOREAN)
    val rowH = ((layout.heightDp - 300) / GRID_ROWS).coerceIn(26, 40).dp

    fun paint(slot: Int) {
        val t = tool ?: return
        if (editing) scope.launch { c.db.manualCellDao().upsert(ManualCell(dayEpoch, slot, t)) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextAction(stringResource(R.string.cal_prev), { dayEpoch-- })
            Text(dayFmt.format(day), style = AppText.bodyMedium, modifier = Modifier.weight(1f))
            TextAction(stringResource(R.string.cal_today), { dayEpoch = studyDay(System.currentTimeMillis()).toEpochDay() })
            TextAction(stringResource(R.string.cal_next), { dayEpoch++ })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tt_total, formatDuration(dayTotal)), style = AppText.caption, color = w.inkSoft, modifier = Modifier.weight(1f))
            TextAction(stringResource(if (editing) R.string.tt_edit_done else R.string.tt_edit), { editing = !editing; if (!editing) tool = null })
            if (embedded) TextAction(stringResource(R.string.tt_close), onClose, color = w.inkSoft)
        }
        if (editing) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                subjects.forEach { s -> Chip(s.name, tool == s.id, { tool = s.id }) }
                Chip(stringResource(R.string.tt_eraser), tool == ManualCell.ERASED, { tool = ManualCell.ERASED })
            }
            Text(stringResource(R.string.tt_hint), style = AppText.caption, color = w.inkSoft)
        }
        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
            Grid(cells, subjects.associate { it.id to Color(it.color) }, rowH, onPaint = ::paint, onDay = { dayEpoch += it })
        }
    }
}

@Composable
private fun Grid(cells: Map<Int, Long?>, colors: Map<Long, Color>, rowH: Dp, onPaint: (Int) -> Unit, onDay: (Long) -> Unit) {
    val w = LocalWarm.current
    Column(
        Modifier.fillMaxWidth()
            .pointerInput(Unit) {
                // slot under a touch point: rows are rowH + gap tall, the label column is on the left
                fun slotAt(x: Float, y: Float): Int? {
                    val labelPx = LabelWidth.toPx()
                    if (x < labelPx) return null
                    val col = ((x - labelPx) / ((size.width - labelPx) / CELLS_PER_ROW)).toInt().coerceIn(0, CELLS_PER_ROW - 1)
                    val row = (y / (rowH.toPx() + CellGap.toPx())).toInt()
                    return if (row in 0 until GRID_ROWS) row * CELLS_PER_ROW + col else null
                }
                detectTapGestures { o -> slotAt(o.x, o.y)?.let(onPaint) }
            }
            .pointerInput(Unit) {
                var last = -1
                fun slotAt(x: Float, y: Float): Int? {
                    val labelPx = LabelWidth.toPx()
                    if (x < labelPx) return null
                    val col = ((x - labelPx) / ((size.width - labelPx) / CELLS_PER_ROW)).toInt().coerceIn(0, CELLS_PER_ROW - 1)
                    val row = (y / (rowH.toPx() + CellGap.toPx())).toInt()
                    return if (row in 0 until GRID_ROWS) row * CELLS_PER_ROW + col else null
                }
                // long-press then drag paints a run of cells, so normal scrolling and swiping are not disturbed
                detectDragGesturesAfterLongPress(
                    onDragStart = { o -> last = -1; slotAt(o.x, o.y)?.let { onPaint(it); last = it } },
                    onDrag = { change, _ -> change.consume(); slotAt(change.position.x, change.position.y)?.let { if (it != last) { onPaint(it); last = it } } },
                )
            }
            .pointerInput(Unit) {
                var dx = 0f
                val threshold = 80.dp.toPx()
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (dx < -threshold) onDay(1) else if (dx > threshold) onDay(-1) },
                    onHorizontalDrag = { _, d -> dx += d },
                )
            },
        verticalArrangement = Arrangement.spacedBy(CellGap),
    ) {
        repeat(GRID_ROWS) { r ->
            Row(Modifier.fillMaxWidth().height(rowH), verticalAlignment = Alignment.CenterVertically) {
                Text("${(GRID_START_HOUR + r) % 24}", style = AppText.caption.copy(fontSize = 11.sp), color = w.inkFaint, modifier = Modifier.width(LabelWidth))
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(CellGap)) {
                    repeat(CELLS_PER_ROW) { col ->
                        val slot = r * CELLS_PER_ROW + col
                        val filled = slot in cells
                        val color = if (filled) colors[cells[slot]] ?: w.inkFaint else Color.Transparent
                        Box(
                            Modifier.weight(1f).height(rowH).clip(RoundedCornerShape(3.dp))
                                .background(if (filled) color.copy(alpha = 0.9f) else w.inkFaint.copy(alpha = 0.16f)),
                        )
                    }
                }
            }
        }
    }
}


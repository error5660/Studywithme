package com.studywithme.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm

/** Seven days of bars: study hours in orange, phone hours (optional) in a muted tone beside them. */
@Composable
fun WeeklyBars(labels: List<String>, study: List<Float>, phone: List<Float>?, todayIndex: Int, modifier: Modifier = Modifier) {
    val w = LocalWarm.current
    val max = maxOf(1f, study.maxOrNull() ?: 0f, phone?.maxOrNull() ?: 0f)
    Row(modifier.fillMaxWidth().height(170.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label ->
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (study[i] > 0f) "%.1f".format(study[i]) else "", style = AppText.caption.copy(fontSize = 11.sp), color = w.inkSoft)
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
                    Bar(study[i] / max, w.accent, Modifier.weight(1f))
                    if (phone != null) Bar(phone[i] / max, w.inkFaint, Modifier.weight(1f))
                }
                Text(label, style = AppText.caption, color = if (i == todayIndex) w.accent else w.inkSoft, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color, modifier: Modifier) {
    Box(modifier.fillMaxHeight(fraction.coerceIn(0.01f, 1f)).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color))
}

/** Ring split by subject. [center] is drawn in the middle. */
@Composable
fun SubjectDonut(slices: List<Pair<Color, Long>>, center: String, modifier: Modifier = Modifier) {
    val w = LocalWarm.current
    val total = slices.sumOf { it.second }.coerceAtLeast(1)
    Box(modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(w.inkFaint.copy(alpha = 0.25f), 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            var start = -90f
            slices.forEach { (color, ms) ->
                val sweep = 360f * ms / total
                drawArc(color, start + 1f, (sweep - 2f).coerceAtLeast(0.5f), false, topLeft, arcSize, style = Stroke(stroke))
                start += sweep
            }
        }
        Text(center, style = AppText.number.copy(fontSize = 20.sp), color = w.ink)
    }
}

/**
 * GitHub-style grid: one column per week (Monday on top), five orange levels. [level] returns 0..4,
 * or -1 for days that have not happened yet. Tapping a cell reports (week, row).
 */
@Composable
fun Heatmap(weeks: Int, level: (week: Int, row: Int) -> Int, selected: Pair<Int, Int>?, onTap: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val w = LocalWarm.current
    Canvas(
        modifier.fillMaxWidth().aspectRatio(weeks / 7f).pointerInput(weeks) {
            detectTapGestures { o ->
                val slot = size.width.toFloat() / weeks
                val col = (o.x / slot).toInt().coerceIn(0, weeks - 1)
                val row = (o.y / slot).toInt().coerceIn(0, 6)
                onTap(col, row)
            }
        },
    ) {
        val slot = size.width / weeks
        val gap = slot * 0.14f
        for (col in 0 until weeks) for (row in 0 until 7) {
            val lv = level(col, row)
            if (lv < 0) continue
            val color = if (lv == 0) w.inkFaint.copy(alpha = 0.22f) else w.accent.copy(alpha = 0.22f + 0.78f * lv / 4f)
            val topLeft = Offset(col * slot + gap / 2, row * slot + gap / 2)
            val cell = Size(slot - gap, slot - gap)
            drawRoundRect(color, topLeft, cell, CornerRadius(3.dp.toPx()))
            if (selected == col to row) drawRoundRect(w.ink, topLeft, cell, CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }
    }
}

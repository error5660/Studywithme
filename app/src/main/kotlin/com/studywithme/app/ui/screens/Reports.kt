package com.studywithme.app.ui.screens

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.studywithme.app.R
import com.studywithme.app.data.Subject
import com.studywithme.app.data.WeeklyReport
import com.studywithme.app.data.decodeBySubject
import com.studywithme.app.data.mondayOf
import com.studywithme.app.data.studyDay
import com.studywithme.app.ui.LocalContainer
import com.studywithme.app.ui.components.ColorDot
import com.studywithme.app.ui.components.GlassCard
import com.studywithme.app.ui.components.Hairline
import com.studywithme.app.ui.components.TextAction
import com.studywithme.app.ui.theme.AppText
import com.studywithme.app.ui.theme.LocalWarm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Past weekly reports, newest first. Tap one to open its details. */
@Composable
fun ReportsScreen() {
    val c = LocalContainer.current
    val w = LocalWarm.current
    val scope = rememberCoroutineScope()
    val reports by c.db.reportDao().observeAll().collectAsStateWithLifecycle(emptyList())
    val subjects by c.db.subjectDao().observeAll().collectAsStateWithLifecycle(emptyList())
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    val range = DateTimeFormatter.ofPattern(stringResource(R.string.cal_range_pattern), Locale.KOREAN)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextAction(stringResource(R.string.report_make_now), {
            scope.launch(Dispatchers.IO) { val now = System.currentTimeMillis(); c.reports.generate(mondayOf(studyDay(now)), now) }
        })
        if (reports.isEmpty()) Text(stringResource(R.string.report_empty), style = AppText.body, color = w.inkSoft)
        reports.forEach { r ->
            val start = LocalDate.ofEpochDay(r.weekStartDay)
            GlassCard(Modifier.fillMaxWidth(), onClick = { open = if (open == r.weekStartDay) null else r.weekStartDay }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dday_range, range.format(start), range.format(start.plusDays(6))), style = AppText.bodyMedium, modifier = Modifier.weight(1f))
                    Text(formatDuration(r.totalMs), style = AppText.number, color = w.accent)
                }
                if (open == r.weekStartDay) ReportDetail(r, subjects)
            }
        }
    }
}

@Composable
private fun ReportDetail(r: WeeklyReport, subjects: List<Subject>) {
    val w = LocalWarm.current
    val dayFmt = DateTimeFormatter.ofPattern(stringResource(R.string.cal_day_pattern), Locale.KOREAN)
    val diff = r.totalMs - r.prevTotalMs
    Spacer(Modifier.height(12.dp))
    Hairline()
    DetailRow(stringResource(R.string.report_total), formatDuration(r.totalMs))
    DetailRow(
        stringResource(R.string.report_vs_last),
        stringResource(if (diff >= 0) R.string.report_change_up else R.string.report_change_down, formatDuration(kotlin.math.abs(diff))),
    )
    DetailRow(stringResource(R.string.report_best_day), if (r.bestDayMs > 0) dayFmt.format(LocalDate.ofEpochDay(r.bestDay)) + "  " + formatDuration(r.bestDayMs) else "–")
    DetailRow(stringResource(R.string.report_streak), stringResource(R.string.report_streak_value, r.streak))
    DetailRow(stringResource(R.string.report_focus), r.avgFocus?.let { stringResource(R.string.stats_focus_avg, it) } ?: stringResource(R.string.report_no_focus))
    DetailRow(stringResource(R.string.report_phone), r.phoneMs?.let { formatDuration(it) } ?: stringResource(R.string.report_phone_none))
    DetailRow(stringResource(R.string.report_early), stringResource(R.string.report_count_value, r.earlyUnlocks))
    DetailRow(stringResource(R.string.report_notes_added), stringResource(R.string.report_items_value, r.notesAdded))
    DetailRow(stringResource(R.string.report_notes_reviewed), stringResource(R.string.report_items_value, r.notesReviewed))
    val parts = decodeBySubject(r.bySubject)
    if (parts.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.report_by_subject), style = AppText.caption, color = w.inkSoft)
        parts.forEach { (id, ms) ->
            val s = subjects.firstOrNull { it.id == id }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                ColorDot(s?.let { Color(it.color) } ?: w.inkFaint, 8.dp)
                Spacer(Modifier.width(8.dp))
                Text(s?.name ?: stringResource(R.string.subject_deleted), style = AppText.body, modifier = Modifier.weight(1f))
                Text(formatDuration(ms), style = AppText.number, color = w.inkSoft)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val w = LocalWarm.current
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = AppText.body, color = w.inkSoft, modifier = Modifier.weight(1f))
        Text(value, style = AppText.body, modifier = Modifier.padding(start = 12.dp))
    }
}

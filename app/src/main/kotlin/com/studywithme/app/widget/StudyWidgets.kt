package com.studywithme.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.LocalContext
import com.studywithme.app.MainActivity
import com.studywithme.app.R
import com.studywithme.app.StudyApp
import com.studywithme.app.data.nextEventStart
import com.studywithme.app.data.upcomingExams
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Widgets cannot blur or use Pretendard, so they use the same warm palette as flat colors.
private val Cream = ColorProvider(Color(0xFFFFEBDC))
private val Ink = ColorProvider(Color(0xFF3B2A20))
private val InkSoft = ColorProvider(Color(0xFF8C6F5D))
private val Accent = ColorProvider(Color(0xFFEA741F))

private fun hm(ms: Long): String = (ms / 60_000).let { "%d:%02d".format(it / 60, it % 60) }

private val GlanceModifier.card: GlanceModifier
    get() = this.fillMaxSize().background(Cream).cornerRadius(24.dp).padding(16.dp)

/** Small widget: today's study time. */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val ms = (context.applicationContext as StudyApp).container.todayStudyMs()
        provideContent {
            val ctx = LocalContext.current
            Column(GlanceModifier.card.clickable(actionStartActivity(android.content.Intent(ctx, MainActivity::class.java))), verticalAlignment = Alignment.CenterVertically) {
                Text(ctx.getString(R.string.widget_today_label), style = TextStyle(color = InkSoft, fontSize = 13.sp))
                Spacer(GlanceModifier.height(4.dp))
                Text(hm(ms), style = TextStyle(color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Medium))
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = TodayWidget() }

/** Medium widget: nearest exam D-day, today's study time and the next event. */
class OverviewWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = (context.applicationContext as StudyApp).container
        val now = System.currentTimeMillis()
        val events = c.db.eventDao().observeAll().first()
        val ms = c.todayStudyMs()
        val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val exam = upcomingExams(events, today).firstOrNull()
        val next = nextEventStart(events, now)
        provideContent {
            val ctx = LocalContext.current
            val dday = exam?.let { (e, date) -> ChronoUnit.DAYS.between(today, date).toInt().let { d -> (if (d <= 0) ctx.getString(R.string.dday_today) else ctx.getString(R.string.dday_format, d)) to e.title } }
            val fmt = DateTimeFormatter.ofPattern(ctx.getString(R.string.widget_next_pattern), Locale.KOREAN)
            Row(GlanceModifier.card.clickable(actionStartActivity(android.content.Intent(ctx, MainActivity::class.java))), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Text(dday?.first ?: "–", style = TextStyle(color = Accent, fontSize = 26.sp, fontWeight = FontWeight.Medium))
                    Text(dday?.second ?: ctx.getString(R.string.widget_no_exam), style = TextStyle(color = InkSoft, fontSize = 12.sp), maxLines = 1)
                }
                Spacer(GlanceModifier.width(20.dp))
                Column(GlanceModifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.widget_today_label), style = TextStyle(color = InkSoft, fontSize = 12.sp))
                    Text(hm(ms), style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Medium))
                }
                Spacer(GlanceModifier.width(20.dp))
                Column(GlanceModifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.widget_next_label), style = TextStyle(color = InkSoft, fontSize = 12.sp))
                    Text(next?.first?.title ?: ctx.getString(R.string.widget_no_event), style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    if (next != null) Text(fmt.format(Instant.ofEpochMilli(next.second).atZone(ZoneId.systemDefault())), style = TextStyle(color = InkSoft, fontSize = 12.sp))
                }
            }
        }
    }
}

class OverviewWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = OverviewWidget() }

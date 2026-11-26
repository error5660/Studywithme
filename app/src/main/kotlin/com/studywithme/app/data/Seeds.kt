package com.studywithme.app.data

import android.content.res.Resources
import androidx.compose.ui.graphics.toArgb
import com.studywithme.app.R
import com.studywithme.app.ui.theme.SubjectPalette
import java.time.LocalDate

/** First Monday of the semester; weekly classes repeat from here so past weeks in the calendar are filled too. */
private val SEMESTER_ANCHOR_SUNDAY = LocalDate.of(2026, 3, 1)

/**
 * Fills the calendar with the weekly classes and the final exams from strings.xml.
 * Class row: "weekday(1=Mon..7=Sun)|start|end|title|travelMin|paletteIndex". Exam row: "date|start|title|subjectName".
 */
suspend fun seedCalendar(db: AppDatabase, res: Resources) {
    val subjects = db.subjectDao().getAll()
    val events = db.eventDao()

    for (row in res.getStringArray(R.array.default_classes)) {
        val (dow, start, end, title, travel, color) = row.split("|")
        events.insert(
            CalendarEvent(
                title = title, dateEpochDay = SEMESTER_ANCHOR_SUNDAY.plusDays(dow.toLong()).toEpochDay(),
                startMin = parseMinutes(start), endMin = parseMinutes(end), weekly = true,
                travelMin = travel.toInt(), color = SubjectPalette[color.toInt()].toArgb(),
            ),
        )
    }
    for (row in res.getStringArray(R.array.default_exams)) {
        val (date, start, title, subjectName) = row.split("|")
        val subject = subjects.firstOrNull { it.name == subjectName }
        events.insert(
            CalendarEvent(
                title = title, kind = CalendarEvent.KIND_EXAM, dateEpochDay = LocalDate.parse(date).toEpochDay(),
                startMin = parseMinutes(start), endMin = parseMinutes(start) + 50, // every exam is 50 minutes
                subjectId = subject?.id, color = subject?.color ?: SubjectPalette[0].toArgb(),
            ),
        )
    }
}

private operator fun <T> List<T>.component6(): T = this[5]

fun parseMinutes(hhmm: String): Int = hhmm.split(":").let { it[0].toInt() * 60 + it[1].toInt() }

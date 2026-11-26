package com.studywithme.app.backup

import com.studywithme.app.data.CalendarEvent
import com.studywithme.app.data.EarlyUnlock
import com.studywithme.app.data.Exam
import com.studywithme.app.data.ExamScore
import com.studywithme.app.data.ManualCell
import com.studywithme.app.data.NoteReview
import com.studywithme.app.data.Settings
import com.studywithme.app.data.StudySession
import com.studywithme.app.data.Subject
import com.studywithme.app.data.SubjectProgress
import com.studywithme.app.data.Task
import com.studywithme.app.data.WeeklyReport
import com.studywithme.app.data.WrongNote
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val BACKUP_VERSION = 1

/** Everything the app stores, except the lock password (it never leaves the device). Photos travel next to this in the ZIP. */
@Serializable
data class BackupData(
    val version: Int = BACKUP_VERSION,
    val exportedMs: Long,
    val settings: Settings,
    val subjects: List<Subject>,
    val sessions: List<StudySession>,
    val events: List<CalendarEvent>,
    val tasks: List<Task>,
    val progress: List<SubjectProgress>,
    val earlyUnlocks: List<EarlyUnlock>,
    val manualCells: List<ManualCell>,
    val notes: List<WrongNote>,
    val noteReviews: List<NoteReview>,
    val exams: List<Exam>,
    val examScores: List<ExamScore>,
    val reports: List<WeeklyReport>,
) {
    /** Photo file names this backup refers to. */
    fun photoNames(): Set<String> = notes.flatMap { listOfNotNull(it.photo, it.answerPhoto) }.toSet()
}

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun encodeBackup(data: BackupData): String = json.encodeToString(BackupData.serializer(), data)

/** Throws if the text is not a backup or comes from a newer app version. */
fun decodeBackup(text: String): BackupData {
    val data = json.decodeFromString(BackupData.serializer(), text)
    require(data.version <= BACKUP_VERSION) { "backup version ${data.version} is newer than this app" }
    return data
}

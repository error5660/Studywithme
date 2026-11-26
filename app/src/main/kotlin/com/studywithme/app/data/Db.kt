package com.studywithme.app.data

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Entity(tableName = "subjects")
@Serializable
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB color as Int (Color.toArgb()). */
    val color: Int,
    val sortOrder: Int,
    /** Target grade for the exam scores screen, 1 (best) to 5. Shown as A-E when the A-E scale is chosen. */
    val targetGrade: Int? = null,
)

/** One continuous stretch of focused study. Breaks and pauses are never stored. */
@Entity(
    tableName = "study_sessions",
    foreignKeys = [ForeignKey(Subject::class, ["id"], ["subjectId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("subjectId"), Index("startMs")],
)
@Serializable
data class StudySession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long?,
    val startMs: Long,
    val endMs: Long,
    /** 1..5, filled in by the focus-score sheet (Phase 4). */
    val focusScore: Int? = null,
)

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects ORDER BY sortOrder, id")
    suspend fun getAll(): List<Subject>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM subjects")
    suspend fun maxOrder(): Int

    @Insert
    suspend fun insert(subject: Subject): Long

    @Update
    suspend fun update(subject: Subject)

    @Delete
    suspend fun delete(subject: Subject)

    @Transaction
    suspend fun swapOrder(a: Subject, b: Subject) {
        update(a.copy(sortOrder = b.sortOrder))
        update(b.copy(sortOrder = a.sortOrder))
    }
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: StudySession): Long

    @Query("SELECT * FROM study_sessions WHERE endMs > :fromMs AND startMs < :toMs ORDER BY startMs")
    fun observeBetween(fromMs: Long, toMs: Long): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE endMs > :fromMs AND startMs < :toMs ORDER BY startMs")
    suspend fun getBetween(fromMs: Long, toMs: Long): List<StudySession>

    /** Stores the 1-5 focus score on every session that falls inside the block just finished. */
    @Query("UPDATE study_sessions SET focusScore = :score WHERE startMs >= :fromMs AND endMs <= :toMs")
    suspend fun rate(fromMs: Long, toMs: Long, score: Int)
}

/**
 * A calendar entry. A weekly event repeats on the weekday of [dateEpochDay], starting that day.
 * shortcut: editing or deleting a weekly event changes the whole series, there are no per-day exceptions.
 */
@Entity(tableName = "events")
@Serializable
data class CalendarEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: Int = KIND_EVENT,
    val dateEpochDay: Long,
    val startMin: Int,
    val endMin: Int,
    val weekly: Boolean = false,
    val travelMin: Int = 0,
    val reminderOn: Boolean = true,
    /** Only used by exams, for the per-subject D-day. */
    val subjectId: Long? = null,
    val color: Int,
) {
    companion object {
        const val KIND_EVENT = 0
        const val KIND_EXAM = 1
    }
}

@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(Subject::class, ["id"], ["subjectId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("subjectId"), Index("dayEpoch")],
)
@Serializable
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val subjectId: Long? = null,
    /** The study day (see studyDay()) this task belongs to. */
    val dayEpoch: Long,
    val done: Boolean = false,
)

/** Textbook progress for one subject: page range and the page the student is on. */
@Entity(
    tableName = "subject_progress",
    foreignKeys = [ForeignKey(Subject::class, ["id"], ["subjectId"], onDelete = ForeignKey.CASCADE)],
)
@Serializable
data class SubjectProgress(
    @PrimaryKey val subjectId: Long,
    val startPage: Int,
    val endPage: Int,
    val currentPage: Int,
)

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY startMin, id")
    fun observeAll(): Flow<List<CalendarEvent>>

    @Query("SELECT * FROM events")
    suspend fun getAll(): List<CalendarEvent>

    @Insert
    suspend fun insert(event: CalendarEvent): Long

    @Update
    suspend fun update(event: CalendarEvent)

    @Delete
    suspend fun delete(event: CalendarEvent)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE dayEpoch = :day ORDER BY done, id")
    fun observeDay(day: Long): Flow<List<Task>>

    /** Unfinished tasks left over from earlier days. */
    @Query("SELECT * FROM tasks WHERE done = 0 AND dayEpoch < :day ORDER BY dayEpoch, id")
    fun observeStale(day: Long): Flow<List<Task>>

    @Query("UPDATE tasks SET dayEpoch = :day WHERE done = 0 AND dayEpoch < :day")
    suspend fun moveStaleTo(day: Long)

    @Query("DELETE FROM tasks WHERE done = 0 AND dayEpoch < :day")
    suspend fun deleteStale(day: Long)

    @Insert
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)
}

/** One row per time the focus lock was ended early with the password. Feeds the stats and the weekly report. */
@Entity(tableName = "early_unlocks")
@Serializable
data class EarlyUnlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long,
    val remainingMs: Long,
    val lockStartMs: Long,
)

@Dao
interface EarlyUnlockDao {
    @Query("SELECT COUNT(*) FROM early_unlocks WHERE atMs >= :fromMs AND atMs < :toMs")
    suspend fun countBetween(fromMs: Long, toMs: Long): Int

    @Insert
    suspend fun insert(unlock: EarlyUnlock): Long

    @Query("SELECT COUNT(*) FROM early_unlocks")
    fun observeCount(): Flow<Int>
}

/**
 * A timetable cell the student painted by hand. [subjectId] == [ERASED] means "leave this cell empty"
 * even though the timer would have filled it.
 */
@Entity(tableName = "manual_cells", primaryKeys = ["day", "slot"])
@Serializable
data class ManualCell(val day: Long, val slot: Int, val subjectId: Long) {
    companion object { const val ERASED = -1L }
}

@Dao
interface ManualCellDao {
    @Query("SELECT * FROM manual_cells WHERE day = :day")
    fun observeDay(day: Long): Flow<List<ManualCell>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cell: ManualCell)
}

@Dao
interface ProgressDao {
    @Query("SELECT * FROM subject_progress")
    fun observeAll(): Flow<List<SubjectProgress>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: SubjectProgress)

    @Delete
    suspend fun delete(progress: SubjectProgress)
}

/** A photographed problem the student got wrong. Review stages: 0 = not reviewed yet, 1..2 = passed that many reviews. */
@Entity(tableName = "wrong_notes", indices = [Index("subjectId"), Index("nextReviewDay")])
@Serializable
data class WrongNote(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long?,
    val memo: String = "",
    /** Comma separated. */
    val tags: String = "",
    /** File name inside the app-private notes folder. */
    val photo: String,
    val answerPhoto: String? = null,
    val createdDay: Long,
    val stage: Int = 0,
    /** Study day of the next review; null once all three reviews were passed. */
    val nextReviewDay: Long?,
)

@Entity(tableName = "note_reviews")
@Serializable
data class NoteReview(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val atMs: Long,
    val correct: Boolean,
)

@Entity(tableName = "exams")
@Serializable
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dateEpochDay: Long,
)

/** Grade is stored as 1..5 for both scales; 1 is the best (1등급, or A). */
@Entity(tableName = "exam_scores", primaryKeys = ["examId", "subjectId"])
@Serializable
data class ExamScore(
    val examId: Long,
    val subjectId: Long,
    val score: Int?,
    val grade: Int?,
)

@Entity(tableName = "weekly_reports")
@Serializable
data class WeeklyReport(
    /** Monday of the week as an epoch day. */
    @PrimaryKey val weekStartDay: Long,
    val createdMs: Long,
    val totalMs: Long,
    val prevTotalMs: Long,
    val bestDay: Long,
    val bestDayMs: Long,
    val streak: Int,
    val avgFocus: Float?,
    /** Null when usage access was not granted. */
    val phoneMs: Long?,
    val earlyUnlocks: Int,
    val notesAdded: Int,
    val notesReviewed: Int,
    /** "subjectId:ms,subjectId:ms", "-" as id for a deleted subject. */
    val bySubject: String,
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM wrong_notes ORDER BY id DESC")
    fun observeAll(): Flow<List<WrongNote>>

    @Query("SELECT COUNT(*) FROM wrong_notes WHERE nextReviewDay IS NOT NULL AND nextReviewDay <= :today")
    fun observeDueCount(today: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM wrong_notes WHERE nextReviewDay IS NOT NULL AND nextReviewDay <= :today")
    suspend fun dueCount(today: Long): Int

    @Query("SELECT * FROM wrong_notes WHERE nextReviewDay IS NOT NULL AND nextReviewDay <= :today ORDER BY nextReviewDay, id")
    suspend fun due(today: Long): List<WrongNote>

    @Insert
    suspend fun insert(note: WrongNote): Long

    @Update
    suspend fun update(note: WrongNote)

    @Delete
    suspend fun delete(note: WrongNote)

    @Insert
    suspend fun insertReview(review: NoteReview)

    @Query("SELECT COUNT(*) FROM wrong_notes WHERE createdDay BETWEEN :fromDay AND :toDay")
    suspend fun addedBetween(fromDay: Long, toDay: Long): Int

    @Query("SELECT COUNT(*) FROM note_reviews WHERE atMs >= :fromMs AND atMs < :toMs")
    suspend fun reviewsBetween(fromMs: Long, toMs: Long): Int
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams ORDER BY dateEpochDay, id")
    fun observeExams(): Flow<List<Exam>>

    @Query("SELECT * FROM exam_scores")
    fun observeScores(): Flow<List<ExamScore>>

    @Insert
    suspend fun insertExam(exam: Exam): Long

    @Delete
    suspend fun deleteExam(exam: Exam)

    @Query("DELETE FROM exam_scores WHERE examId = :examId")
    suspend fun deleteScoresOf(examId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScores(scores: List<ExamScore>)
}

@Dao
interface ReportDao {
    @Query("SELECT * FROM weekly_reports ORDER BY weekStartDay DESC")
    fun observeAll(): Flow<List<WeeklyReport>>

    @Query("SELECT * FROM weekly_reports WHERE weekStartDay = :weekStartDay")
    suspend fun get(weekStartDay: Long): WeeklyReport?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: WeeklyReport)
}

/** Whole-table reads and writes used only by backup export and import. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM subjects") suspend fun subjects(): List<Subject>
    @Query("SELECT * FROM study_sessions") suspend fun sessions(): List<StudySession>
    @Query("SELECT * FROM events") suspend fun events(): List<CalendarEvent>
    @Query("SELECT * FROM tasks") suspend fun tasks(): List<Task>
    @Query("SELECT * FROM subject_progress") suspend fun progress(): List<SubjectProgress>
    @Query("SELECT * FROM early_unlocks") suspend fun earlyUnlocks(): List<EarlyUnlock>
    @Query("SELECT * FROM manual_cells") suspend fun manualCells(): List<ManualCell>
    @Query("SELECT * FROM wrong_notes") suspend fun notes(): List<WrongNote>
    @Query("SELECT * FROM note_reviews") suspend fun noteReviews(): List<NoteReview>
    @Query("SELECT * FROM exams") suspend fun exams(): List<Exam>
    @Query("SELECT * FROM exam_scores") suspend fun examScores(): List<ExamScore>
    @Query("SELECT * FROM weekly_reports") suspend fun reports(): List<WeeklyReport>

    @Insert suspend fun putSubjects(l: List<Subject>)
    @Insert suspend fun putSessions(l: List<StudySession>)
    @Insert suspend fun putEvents(l: List<CalendarEvent>)
    @Insert suspend fun putTasks(l: List<Task>)
    @Insert suspend fun putProgress(l: List<SubjectProgress>)
    @Insert suspend fun putEarlyUnlocks(l: List<EarlyUnlock>)
    @Insert suspend fun putManualCells(l: List<ManualCell>)
    @Insert suspend fun putNotes(l: List<WrongNote>)
    @Insert suspend fun putNoteReviews(l: List<NoteReview>)
    @Insert suspend fun putExams(l: List<Exam>)
    @Insert suspend fun putExamScores(l: List<ExamScore>)
    @Insert suspend fun putReports(l: List<WeeklyReport>)
}

@Database(
    entities = [Subject::class, StudySession::class, CalendarEvent::class, Task::class, SubjectProgress::class, EarlyUnlock::class, ManualCell::class,
        WrongNote::class, NoteReview::class, Exam::class, ExamScore::class, WeeklyReport::class],
    version = 5,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5)],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun sessionDao(): SessionDao
    abstract fun eventDao(): EventDao
    abstract fun taskDao(): TaskDao
    abstract fun progressDao(): ProgressDao
    abstract fun earlyUnlockDao(): EarlyUnlockDao
    abstract fun manualCellDao(): ManualCellDao
    abstract fun noteDao(): NoteDao
    abstract fun examDao(): ExamDao
    abstract fun reportDao(): ReportDao
    abstract fun backupDao(): BackupDao
}

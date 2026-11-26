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

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB color as Int (Color.toArgb()). */
    val color: Int,
    val sortOrder: Int,
)

/** One continuous stretch of focused study. Breaks and pauses are never stored. */
@Entity(
    tableName = "study_sessions",
    foreignKeys = [ForeignKey(Subject::class, ["id"], ["subjectId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("subjectId"), Index("startMs")],
)
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
}

/**
 * A calendar entry. A weekly event repeats on the weekday of [dateEpochDay], starting that day.
 * shortcut: editing or deleting a weekly event changes the whole series, there are no per-day exceptions.
 */
@Entity(tableName = "events")
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
data class EarlyUnlock(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val atMs: Long,
    val remainingMs: Long,
    val lockStartMs: Long,
)

@Dao
interface EarlyUnlockDao {
    @Insert
    suspend fun insert(unlock: EarlyUnlock): Long

    @Query("SELECT COUNT(*) FROM early_unlocks")
    fun observeCount(): Flow<Int>
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

@Database(
    entities = [Subject::class, StudySession::class, CalendarEvent::class, Task::class, SubjectProgress::class, EarlyUnlock::class],
    version = 3,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun sessionDao(): SessionDao
    abstract fun eventDao(): EventDao
    abstract fun taskDao(): TaskDao
    abstract fun progressDao(): ProgressDao
    abstract fun earlyUnlockDao(): EarlyUnlockDao
}

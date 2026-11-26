package com.studywithme.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
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

@Database(entities = [Subject::class, StudySession::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subjectDao(): SubjectDao
    abstract fun sessionDao(): SessionDao
}

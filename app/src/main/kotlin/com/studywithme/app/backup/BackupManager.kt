package com.studywithme.app.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.studywithme.app.data.AppDatabase
import com.studywithme.app.data.SettingsRepo
import com.studywithme.app.photos.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One ZIP: data.json plus photos/. The lock password is deliberately not part of it. */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepo,
    private val photos: PhotoStore,
) {
    private suspend fun collect(): BackupData = db.backupDao().let { d ->
        BackupData(
            exportedMs = System.currentTimeMillis(), settings = settings.flow.first(),
            subjects = d.subjects(), sessions = d.sessions(), events = d.events(), tasks = d.tasks(), progress = d.progress(),
            earlyUnlocks = d.earlyUnlocks(), manualCells = d.manualCells(), notes = d.notes(), noteReviews = d.noteReviews(),
            exams = d.exams(), examScores = d.examScores(), reports = d.reports(),
        )
    }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val data = collect()
        val out = context.contentResolver.openOutputStream(uri, "w") ?: error("cannot open $uri")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_ENTRY)); zip.write(encodeBackup(data).toByteArray()); zip.closeEntry()
            for (name in data.photoNames()) {
                val f = photos.file(name)
                if (!f.exists()) continue
                zip.putNextEntry(ZipEntry("$PHOTO_DIR$name")); f.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
    }

    /** Replaces everything with the backup. If anything fails, the previous data is put back. */
    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: error("cannot open $uri")
        var data: BackupData? = null
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when {
                    entry.name == DATA_ENTRY -> data = decodeBackup(zip.readBytes().decodeToString())
                    entry.name.startsWith(PHOTO_DIR) && data != null -> {
                        // only plain file names: a crafted ZIP must not write outside the notes folder
                        val name = entry.name.removePrefix(PHOTO_DIR)
                        if (name.isNotEmpty() && '/' !in name && '\\' !in name && ".." !in name) photos.file(name).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val backup = data ?: error("data.json is missing")
        val before = collect()
        try {
            restore(backup)
        } catch (e: Exception) {
            restore(before)
            throw e
        }
    }

    private suspend fun restore(d: BackupData) {
        db.clearAllTables()
        db.withTransaction {
            val dao = db.backupDao()
            dao.putSubjects(d.subjects); dao.putSessions(d.sessions); dao.putEvents(d.events); dao.putTasks(d.tasks); dao.putProgress(d.progress)
            dao.putEarlyUnlocks(d.earlyUnlocks); dao.putManualCells(d.manualCells); dao.putNotes(d.notes); dao.putNoteReviews(d.noteReviews)
            dao.putExams(d.exams); dao.putExamScores(d.examScores); dao.putReports(d.reports)
        }
        settings.edit { d.settings }
        settings.markEventsSeeded() // the calendar now comes from the backup, never re-seed on top of it
    }

    private companion object {
        const val DATA_ENTRY = "data.json"
        const val PHOTO_DIR = "photos/"
    }
}

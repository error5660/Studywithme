package com.studywithme.app

import android.app.Application
import android.os.SystemClock
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.studywithme.app.data.AppDatabase
import com.studywithme.app.data.Settings
import com.studywithme.app.data.SettingsRepo
import com.studywithme.app.data.seedCalendar
import com.studywithme.app.data.studyDay
import com.studywithme.app.data.studyDayStart
import com.studywithme.app.data.DAY_MS
import com.studywithme.app.usage.UsageHelper
import com.studywithme.app.reminders.Notifier
import com.studywithme.app.reminders.ReminderScheduler
import com.studywithme.app.lock.LockController
import com.studywithme.app.lock.LockRepo
import com.studywithme.app.timer.Alerter
import com.studywithme.app.timer.DndController
import com.studywithme.app.timer.TimerEngine
import com.studywithme.app.ui.theme.SubjectPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Hand-rolled service locator: one place that owns the database, settings and timer. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val defaultSubjects = app.resources.getStringArray(R.array.default_subjects)

    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "study.db")
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                defaultSubjects.forEachIndexed { i, name ->
                    val color = SubjectPalette[i % SubjectPalette.size].toArgb()
                    db.execSQL("INSERT INTO subjects (name, color, sortOrder) VALUES (?, ?, ?)", arrayOf<Any>(name, color, i))
                }
            }
        })
        .build()

    val settingsRepo = SettingsRepo(app)

    /** Latest settings, readable from non-UI code. */
    @Volatile
    var settings = Settings()
        private set

    val alerter = Alerter(app)
    val usage = UsageHelper(app)
    val notifier = Notifier(app)
    val reminders = ReminderScheduler(app)

    /** True once the user chose "later" on the carry-over dialog, so it is not shown again until the next launch. */
    var carryPromptDismissed = false
    val dnd = DndController(app, settingsRepo)

    private val lockRepo = LockRepo(app)

    val engine = TimerEngine(
        settings = { settings },
        saveSession = { s -> scope.launch(Dispatchers.IO) { db.sessionDao().insert(s) } },
        rt = SystemClock::elapsedRealtime,
        wall = System::currentTimeMillis,
    )

    val lock = LockController(app, lockRepo, db, engine, scope, reminders, notifier, alerter, quiet = { settings.quiet })

    /** Wall-clock ticker for the UI; collected with lifecycle awareness so it stops in the background. */
    val tick: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }

    fun updateSettings(block: (Settings) -> Settings) {
        scope.launch { settingsRepo.edit(block) }
    }

    init {
        var lastPomodoro: Boolean? = null
        scope.launch {
            settingsRepo.flow.collect {
                settings = it
                if (lastPomodoro != null && lastPomodoro != it.pomodoroOn) engine.restartPomodoro()
                lastPomodoro = it.pomodoroOn
            }
        }
        // First launch after install/update: fill the calendar with the weekly classes and exams, once.
        scope.launch(Dispatchers.IO) {
            if (!settingsRepo.eventsSeeded()) {
                seedCalendar(db, app.resources)
                settingsRepo.markEventsSeeded()
            }
        }
        // Whenever events change (including the seed above) re-arm the next reminder alarm.
        scope.launch { db.eventDao().observeAll().collect { reminders.schedule(it) } }
        scope.launch { while (isActive) { delay(500); engine.tick() } }
        scope.launch { engine.phaseChanges.collect { alerter.alert(settings.quiet) } }

        // Do Not Disturb follows "timer is running"; on the first emission it also repairs a state left by a crash.
        scope.launch {
            combine(
                combine(engine.state.map { it.running }, lock.active) { running, locked -> running || locked }.distinctUntilChanged(),
                settingsRepo.flow.map { it.autoDnd }.distinctUntilChanged(),
            ) { running, auto -> running && auto }
                .distinctUntilChanged()
                .collect { if (it) dnd.enable() else dnd.restore() }
        }

        lock.restore()

        // "Daily goal reached" notification: checked every 30 s while a session is running, sent once per study day.
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000)
                if (!engine.state.value.running) continue
                val now = System.currentTimeMillis()
                val day = studyDay(now).toEpochDay()
                if (settingsRepo.goalNotifiedDay() == day) continue
                val start = studyDayStart(now)
                val studied = db.sessionDao().getBetween(start, start + DAY_MS).sumOf { minOf(it.endMs, now) - maxOf(it.startMs, start) } +
                    engine.state.value.liveFocusMs(SystemClock.elapsedRealtime())
                if (studied >= settings.dailyGoalMin * 60_000L) {
                    settingsRepo.markGoalNotified(day)
                    val minutes = (studied / 60_000).toInt()
                    notifier.postGoalReached(app.getString(R.string.duration_hm, minutes / 60, minutes % 60), settings.quiet)
                }
            }
        }

        // Leaving the app pauses the timer (unless the focus lock is on).
        ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) engine.onBackgrounded()
        })
    }
}

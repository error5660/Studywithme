package com.studywithme.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
enum class QuietMode { Normal, Vibrate, Silent }

@Serializable
data class Settings(
    val pomodoroOn: Boolean = false,
    val focusMin: Int = 50,
    val breakMin: Int = 10,
    val longBreakOn: Boolean = false,
    val longBreakMin: Int = 30,
    val longBreakEvery: Int = 4,
    val quiet: QuietMode = QuietMode.Normal,
    val autoDnd: Boolean = false,
    val night: Boolean = false,
    val dailyGoalMin: Int = 360,
    /** 0 = 5등급제 (1-5), 1 = 성취도 (A-E). */
    val gradeScale: Int = 0,
    /** 0 = off, 1 = rain, 2 = cafe, 3 = fan. */
    val noiseKind: Int = 0,
    val noiseVolume: Int = 40,
)

private val Context.dataStore by preferencesDataStore("settings")

private object Keys {
    val pomodoroOn = booleanPreferencesKey("pomodoro_on")
    val focusMin = intPreferencesKey("focus_min")
    val breakMin = intPreferencesKey("break_min")
    val longBreakOn = booleanPreferencesKey("long_break_on")
    val longBreakMin = intPreferencesKey("long_break_min")
    val longBreakEvery = intPreferencesKey("long_break_every")
    val quiet = intPreferencesKey("quiet_mode")
    val autoDnd = booleanPreferencesKey("auto_dnd")
    val night = booleanPreferencesKey("night_mode")
    val dailyGoalMin = intPreferencesKey("daily_goal_min")
    val gradeScale = intPreferencesKey("grade_scale")
    val noiseKind = intPreferencesKey("noise_kind")
    val noiseVolume = intPreferencesKey("noise_volume")
    val goalNotifiedDay = longPreferencesKey("goal_notified_day")
    val savedDndFilter = intPreferencesKey("saved_dnd_filter")
    val eventsSeeded = booleanPreferencesKey("events_seeded")
}

private fun Preferences.toSettings(): Settings {
    val d = Settings()
    return Settings(
        pomodoroOn = this[Keys.pomodoroOn] ?: d.pomodoroOn,
        focusMin = this[Keys.focusMin] ?: d.focusMin,
        breakMin = this[Keys.breakMin] ?: d.breakMin,
        longBreakOn = this[Keys.longBreakOn] ?: d.longBreakOn,
        longBreakMin = this[Keys.longBreakMin] ?: d.longBreakMin,
        longBreakEvery = this[Keys.longBreakEvery] ?: d.longBreakEvery,
        quiet = QuietMode.entries.getOrElse(this[Keys.quiet] ?: 0) { d.quiet },
        autoDnd = this[Keys.autoDnd] ?: d.autoDnd,
        night = this[Keys.night] ?: d.night,
        dailyGoalMin = this[Keys.dailyGoalMin] ?: d.dailyGoalMin,
        gradeScale = this[Keys.gradeScale] ?: d.gradeScale,
        noiseKind = this[Keys.noiseKind] ?: d.noiseKind,
        noiseVolume = this[Keys.noiseVolume] ?: d.noiseVolume,
    )
}

class SettingsRepo(private val context: Context) {
    val flow: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun edit(block: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = block(p.toSettings())
            p[Keys.pomodoroOn] = s.pomodoroOn
            p[Keys.focusMin] = s.focusMin
            p[Keys.breakMin] = s.breakMin
            p[Keys.longBreakOn] = s.longBreakOn
            p[Keys.longBreakMin] = s.longBreakMin
            p[Keys.longBreakEvery] = s.longBreakEvery
            p[Keys.quiet] = s.quiet.ordinal
            p[Keys.autoDnd] = s.autoDnd
            p[Keys.night] = s.night
            p[Keys.dailyGoalMin] = s.dailyGoalMin
            p[Keys.gradeScale] = s.gradeScale
            p[Keys.noiseKind] = s.noiseKind
            p[Keys.noiseVolume] = s.noiseVolume
        }
    }

    /** The Do-Not-Disturb filter to restore after a study session; null when we have not changed it. */
    suspend fun savedDndFilter(): Int? = context.dataStore.data.first()[Keys.savedDndFilter]

    suspend fun setSavedDndFilter(value: Int?) {
        context.dataStore.edit { if (value == null) it.remove(Keys.savedDndFilter) else it[Keys.savedDndFilter] = value }
    }

    suspend fun eventsSeeded(): Boolean = context.dataStore.data.first()[Keys.eventsSeeded] ?: false

    suspend fun markEventsSeeded() {
        context.dataStore.edit { it[Keys.eventsSeeded] = true }
    }

    /** The study day (epoch day) for which the "goal reached" notification was already sent. */
    suspend fun goalNotifiedDay(): Long? = context.dataStore.data.first()[Keys.goalNotifiedDay]

    suspend fun markGoalNotified(day: Long) {
        context.dataStore.edit { it[Keys.goalNotifiedDay] = day }
    }
}

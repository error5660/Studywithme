package com.studywithme.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class QuietMode { Normal, Vibrate, Silent }

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
    val savedDndFilter = intPreferencesKey("saved_dnd_filter")
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
        }
    }

    /** The Do-Not-Disturb filter to restore after a study session; null when we have not changed it. */
    suspend fun savedDndFilter(): Int? = context.dataStore.data.first()[Keys.savedDndFilter]

    suspend fun setSavedDndFilter(value: Int?) {
        context.dataStore.edit { if (value == null) it.remove(Keys.savedDndFilter) else it[Keys.savedDndFilter] = value }
    }
}

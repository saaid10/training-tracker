package com.trainingtracker.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trainingtracker.app.domain.model.Goal
import com.trainingtracker.app.domain.model.OneRepMaxFormula
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** App-wide settings: Settings screen (requirements.txt 3l) surfaces these. */
class SettingsRepository(private val context: Context) {
    private object Keys {
        val GLOBAL_DEFAULT_GOAL = stringPreferencesKey("global_default_goal")
        val ROLLING_WINDOW = intPreferencesKey("rolling_average_window")
        val ONE_REP_MAX_FORMULA = stringPreferencesKey("one_rep_max_formula")
        val LAST_SYNC_AT = longPreferencesKey("last_sync_at")
    }

    companion object {
        const val DEFAULT_ROLLING_WINDOW = 5
    }

    val globalDefaultGoal: Flow<Goal> = context.dataStore.data.map { prefs ->
        prefs[Keys.GLOBAL_DEFAULT_GOAL]?.let { runCatching { Goal.valueOf(it) }.getOrNull() } ?: Goal.STRENGTH
    }

    val rollingAverageWindow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.ROLLING_WINDOW] ?: DEFAULT_ROLLING_WINDOW
    }

    val oneRepMaxFormula: Flow<OneRepMaxFormula> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONE_REP_MAX_FORMULA]?.let { runCatching { OneRepMaxFormula.valueOf(it) }.getOrNull() }
            ?: OneRepMaxFormula.EPLEY
    }

    val lastSyncAt: Flow<Long?> = context.dataStore.data.map { prefs -> prefs[Keys.LAST_SYNC_AT] }

    suspend fun setGlobalDefaultGoal(goal: Goal) {
        context.dataStore.edit { it[Keys.GLOBAL_DEFAULT_GOAL] = goal.name }
    }

    suspend fun setRollingAverageWindow(n: Int) {
        require(n >= 1) { "Rolling average window must be at least 1" }
        context.dataStore.edit { it[Keys.ROLLING_WINDOW] = n }
    }

    suspend fun setOneRepMaxFormula(formula: OneRepMaxFormula) {
        context.dataStore.edit { it[Keys.ONE_REP_MAX_FORMULA] = formula.name }
    }

    suspend fun setLastSyncAt(epochMillis: Long) {
        context.dataStore.edit { it[Keys.LAST_SYNC_AT] = epochMillis }
    }
}

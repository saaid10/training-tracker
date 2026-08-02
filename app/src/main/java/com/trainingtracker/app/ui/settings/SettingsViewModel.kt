package com.trainingtracker.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Category
import com.trainingtracker.app.data.settings.SettingsRepository
import com.trainingtracker.app.domain.model.Goal
import com.trainingtracker.app.domain.model.OneRepMaxFormula
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val globalDefaultGoal: Goal = Goal.STRENGTH,
    val rollingAverageWindow: Int = SettingsRepository.DEFAULT_ROLLING_WINDOW,
    val oneRepMaxFormula: OneRepMaxFormula = OneRepMaxFormula.EPLEY,
    val categories: List<Category> = emptyList(),
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<SettingsState> = combine(
        container.settingsRepository.globalDefaultGoal,
        container.settingsRepository.rollingAverageWindow,
        container.settingsRepository.oneRepMaxFormula,
        container.categoryRepository.observeAll(),
    ) { goal, window, formula, categories -> SettingsState(goal, window, formula, categories) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsState())

    val restoreStatus = MutableStateFlow<String?>(null)

    fun setGlobalDefaultGoal(goal: Goal) {
        viewModelScope.launch { container.settingsRepository.setGlobalDefaultGoal(goal) }
    }

    fun setRollingAverageWindow(n: Int) {
        if (n < 1) return
        viewModelScope.launch { container.settingsRepository.setRollingAverageWindow(n) }
    }

    fun setOneRepMaxFormula(formula: OneRepMaxFormula) {
        viewModelScope.launch { container.settingsRepository.setOneRepMaxFormula(formula) }
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { container.categoryRepository.addCustom(name) }
    }

    fun restoreFromBackup() {
        viewModelScope.launch {
            restoreStatus.value = "Restoring…"
            runCatching { container.syncRepository.pullAndRestore() }
                .onSuccess { restoreStatus.value = "Restore complete." }
                .onFailure { restoreStatus.value = "Restore failed: ${it.message ?: "no connection"}" }
        }
    }
}

package com.trainingtracker.app.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.WorkoutLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingItem(val log: WorkoutLog, val exerciseName: String)

class PendingViewModel(private val container: AppContainer) : ViewModel() {
    val pendingItems: StateFlow<List<PendingItem>> = combine(
        container.logRepository.observePending(),
        container.exerciseRepository.observeAll(),
    ) { logs, exercises ->
        val byId = exercises.associateBy(Exercise::id)
        logs.map { log -> PendingItem(log, byId[log.exerciseId]?.name ?: "Unknown exercise") }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun confirm(id: String, weightKg: Double, reps: Int, sets: Int, rpe: Double?, notes: String?) {
        viewModelScope.launch { container.logRepository.confirmPending(id, weightKg, reps, sets, rpe, notes) }
    }

    fun discard(id: String) {
        viewModelScope.launch { container.logRepository.discardPending(id) }
    }
}

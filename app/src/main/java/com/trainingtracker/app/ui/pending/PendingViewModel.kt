package com.trainingtracker.app.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.data.local.entity.WorkoutSet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingItem(val log: WorkoutLog, val exerciseName: String, val exerciseType: ExerciseType)

class PendingViewModel(private val container: AppContainer) : ViewModel() {
    val pendingItems: StateFlow<List<PendingItem>> = combine(
        container.logRepository.observePending(),
        container.exerciseRepository.observeAll(),
    ) { logs, exercises ->
        val byId = exercises.associateBy(Exercise::id)
        logs.map { log ->
            val exercise = byId[log.exerciseId]
            PendingItem(log, exercise?.name ?: "Unknown exercise", exercise?.type ?: ExerciseType.WEIGHTED)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun confirm(id: String, sets: List<WorkoutSet>, notes: String?) {
        viewModelScope.launch { container.logRepository.confirmPending(id, sets, notes) }
    }

    fun discard(id: String) {
        viewModelScope.launch { container.logRepository.discardPending(id) }
    }
}

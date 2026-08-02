package com.trainingtracker.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.repository.NextSessionAdjustment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LogWorkoutViewModel(private val container: AppContainer) : ViewModel() {
    val exercises: StateFlow<List<Exercise>> = container.exerciseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logSession(
        exerciseId: String,
        weightKg: Double,
        reps: Int,
        sets: Int,
        rpe: Double?,
        notes: String?,
        nextSession: NextSessionAdjustment?,
        loggedAt: Long,
        onDone: () -> Unit,
    ) {
        if (exerciseId.isBlank() || weightKg < 0 || reps <= 0 || sets <= 0) return
        viewModelScope.launch {
            container.logRepository.logCompleted(exerciseId, weightKg, reps, sets, rpe, notes, nextSession, loggedAt)
            onDone()
        }
    }
}

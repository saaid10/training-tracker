package com.trainingtracker.app.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet
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
        exerciseType: ExerciseType,
        sets: List<WorkoutSet>,
        notes: String?,
        nextSession: NextSessionAdjustment?,
        loggedAt: Long,
        onDone: () -> Unit,
    ) {
        if (exerciseId.isBlank() || sets.isEmpty()) return
        viewModelScope.launch {
            container.logRepository.logCompleted(exerciseId, exerciseType, sets, notes, nextSession, loggedAt)
            onDone()
        }
    }
}

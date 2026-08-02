package com.trainingtracker.app.ui.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.Routine
import com.trainingtracker.app.reminders.ReminderScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutinesState(val routines: List<Routine> = emptyList(), val exercises: List<Exercise> = emptyList())

class RoutinesViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<RoutinesState> = combine(
        container.routineRepository.observeAll(),
        container.exerciseRepository.observeAll(),
    ) { routines, exercises -> RoutinesState(routines, exercises) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutinesState())

    fun createRoutine(name: String, daysOfWeek: List<Int>, hour: Int, minute: Int, exerciseIds: List<String>) {
        if (name.isBlank() || daysOfWeek.isEmpty()) return
        viewModelScope.launch {
            val routine = container.routineRepository.create(name, daysOfWeek, hour, minute, exerciseIds)
            ReminderScheduler.scheduleRoutine(container.appContext, routine)
        }
    }

    fun toggleEnabled(routine: Routine) {
        viewModelScope.launch {
            val updated = routine.copy(enabled = !routine.enabled)
            container.routineRepository.update(updated)
            ReminderScheduler.scheduleRoutine(container.appContext, updated)
        }
    }

    fun deleteRoutine(routine: Routine) {
        viewModelScope.launch {
            container.routineRepository.delete(routine.id)
            ReminderScheduler.cancelRoutine(container.appContext, routine.id)
        }
    }
}

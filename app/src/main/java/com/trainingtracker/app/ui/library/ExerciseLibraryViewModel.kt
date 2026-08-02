package com.trainingtracker.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Category
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.domain.model.Goal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExerciseLibraryState(val exercises: List<Exercise> = emptyList(), val categories: List<Category> = emptyList())

class ExerciseLibraryViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<ExerciseLibraryState> = combine(
        container.exerciseRepository.observeAll(),
        container.categoryRepository.observeAll(),
    ) { exercises, categories -> ExerciseLibraryState(exercises, categories) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ExerciseLibraryState())

    fun createExercise(name: String, categoryId: String, goalOverride: Goal?) {
        if (name.isBlank() || categoryId.isBlank()) return
        viewModelScope.launch { container.exerciseRepository.create(name, categoryId, goalOverride) }
    }

    fun updateExercise(exercise: Exercise, name: String, categoryId: String, goalOverride: Goal?) {
        if (name.isBlank() || categoryId.isBlank()) return
        viewModelScope.launch {
            container.exerciseRepository.update(
                exercise.copy(name = name.trim(), categoryId = categoryId, goalOverride = goalOverride)
            )
        }
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { container.categoryRepository.addCustom(name) }
    }

    fun deleteExercise(id: String) {
        viewModelScope.launch { container.exerciseRepository.delete(id) }
    }
}

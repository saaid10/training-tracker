package com.trainingtracker.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.domain.progress.ProgressCalculator
import com.trainingtracker.app.domain.progress.ProgressMetrics
import com.trainingtracker.app.domain.progress.ProgressResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryState(
    val exercises: List<Exercise> = emptyList(),
    val selectedExerciseId: String? = null,
    val selectedExerciseType: ExerciseType? = null,
    val logsNewestFirst: List<WorkoutLog> = emptyList(),
    val progress: ProgressResult? = null,
    val metricName: String = "",
    val metricTooltip: String = "",
    val chartPointsOldestFirst: List<ChartPoint> = emptyList(),
)

class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val selectedExerciseId = MutableStateFlow<String?>(null)

    private val settingsFlow = combine(
        container.settingsRepository.globalDefaultGoal,
        container.settingsRepository.rollingAverageWindow,
        container.settingsRepository.oneRepMaxFormula,
    ) { goal, window, formula -> Triple(goal, window, formula) }

    val state: StateFlow<HistoryState> = combine(
        container.exerciseRepository.observeAll(),
        selectedExerciseId,
        settingsFlow,
    ) { exercises, selectedId, settings -> Triple(exercises, selectedId ?: exercises.firstOrNull()?.id, settings) }
        .flatMapLatest { (exercises, effectiveId, settings) ->
            val (globalGoal, window, formula) = settings
            if (effectiveId == null) {
                flowOf(HistoryState(exercises = exercises))
            } else {
                container.logRepository.observeCompletedForExercise(effectiveId).map { logs ->
                    val exercise = exercises.firstOrNull { it.id == effectiveId }
                    val progress = exercise?.let { ProgressCalculator.evaluate(it, logs, globalGoal, window, formula) }
                    val goal = exercise?.let { ProgressCalculator.effectiveGoal(it, globalGoal) } ?: globalGoal
                    val exerciseType = exercise?.type ?: ExerciseType.WEIGHTED
                    val metric = ProgressMetrics.forGoal(goal, formula, exerciseType)
                    val chartPoints = logs.reversed().map { ChartPoint(it.loggedAt, metric.chartScore(it)) }
                    HistoryState(exercises, effectiveId, exerciseType, logs, progress, metric.displayName, metric.tooltip, chartPoints)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryState())

    fun selectExercise(exerciseId: String) {
        selectedExerciseId.value = exerciseId
    }

    /** Corrects a mistake in an already-logged session (requirements: logs must be editable). */
    fun updateLog(id: String, sets: List<WorkoutSet>, notes: String?, loggedAt: Long) {
        viewModelScope.launch {
            container.logRepository.updateCompleted(id, sets, notes, loggedAt)
        }
    }
}

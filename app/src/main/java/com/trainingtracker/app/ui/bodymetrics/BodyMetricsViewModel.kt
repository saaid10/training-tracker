package com.trainingtracker.app.ui.bodymetrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import com.trainingtracker.app.data.local.entity.BodyMetricLog
import com.trainingtracker.app.domain.bodymetrics.BodyMetricsWeeklyTrend
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BodyMetricsState(
    val entries: List<BodyMetricLog> = emptyList(),
    val weeklyTrend: BodyMetricsWeeklyTrend? = null,
)

class BodyMetricsViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<BodyMetricsState> = combine(
        container.bodyMetricsRepository.observeAll(),
        container.bodyMetricsRepository.observeWeeklyTrend(),
    ) { entries, trend -> BodyMetricsState(entries, trend) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BodyMetricsState())

    fun logEntry(weightKg: Double?, bodyFatPercent: Double?, muscleMassPercent: Double?, notes: String?, loggedAt: Long, onDone: () -> Unit) {
        if (weightKg == null && bodyFatPercent == null && muscleMassPercent == null) return
        viewModelScope.launch {
            container.bodyMetricsRepository.logEntry(weightKg, bodyFatPercent, muscleMassPercent, notes, loggedAt)
            onDone()
        }
    }

    fun updateEntry(id: String, weightKg: Double?, bodyFatPercent: Double?, muscleMassPercent: Double?, notes: String?, loggedAt: Long) {
        viewModelScope.launch {
            container.bodyMetricsRepository.updateEntry(id, weightKg, bodyFatPercent, muscleMassPercent, notes, loggedAt)
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch { container.bodyMetricsRepository.deleteEntry(id) }
    }
}

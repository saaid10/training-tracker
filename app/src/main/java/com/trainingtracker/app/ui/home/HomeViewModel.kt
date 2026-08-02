package com.trainingtracker.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trainingtracker.app.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeState(val exerciseCount: Int = 0, val pendingCount: Int = 0)

class HomeViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<HomeState> = combine(
        container.exerciseRepository.observeAll(),
        container.logRepository.observePending(),
    ) { exercises, pending -> HomeState(exercises.size, pending.size) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())
}

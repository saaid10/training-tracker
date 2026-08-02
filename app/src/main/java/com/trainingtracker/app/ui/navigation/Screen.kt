package com.trainingtracker.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Filled.Home)
    data object LogWorkout : Screen("log_workout", "Log", Icons.Filled.FitnessCenter)
    data object History : Screen("history", "History", Icons.Filled.ShowChart)
    data object Pending : Screen("pending", "Pending", Icons.Filled.PendingActions)
    data object Routines : Screen("routines", "Routines", Icons.Filled.Schedule)
    data object BodyMetrics : Screen("body_metrics", "Body Metrics", Icons.Filled.MonitorWeight)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)

    companion object {
        // Bottom bar keeps to 5 primary destinations; Exercise Library, Routines and Body
        // Metrics are reached via quick-action cards on Home (kept as full screens per
        // requirements.txt 3l).
        val bottomBarScreens = listOf(Home, LogWorkout, History, Pending, Settings)
    }
}

const val EXERCISE_LIBRARY_ROUTE = "exercise_library"
const val LOG_WORKOUT_ARG_EXERCISE_ID = "exerciseId"
const val HISTORY_ARG_EXERCISE_ID = "exerciseId"

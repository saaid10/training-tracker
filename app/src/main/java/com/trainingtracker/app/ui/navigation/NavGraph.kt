package com.trainingtracker.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.trainingtracker.app.ui.ViewModelFactory
import com.trainingtracker.app.ui.bodymetrics.BodyMetricsScreen
import com.trainingtracker.app.ui.history.HistoryScreen
import com.trainingtracker.app.ui.home.HomeScreen
import com.trainingtracker.app.ui.library.ExerciseLibraryScreen
import com.trainingtracker.app.ui.log.LogWorkoutScreen
import com.trainingtracker.app.ui.pending.PendingScreen
import com.trainingtracker.app.ui.routines.RoutinesScreen
import com.trainingtracker.app.ui.settings.SettingsScreen

@Composable
fun TrainingTrackerNavGraph(factory: ViewModelFactory) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            NavigationBar {
                Screen.bottomBarScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            // No saveState/restoreState here: BodyMetrics/Routines/Exercise
                            // Library are reached via Home's quick actions, not the bottom bar,
                            // so they'd otherwise get trapped on top of a tab's saved back
                            // stack and reappear the next time that tab is tapped instead of
                            // showing the tab's actual root screen.
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = androidx.compose.ui.Modifier.padding(padding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(factory) { route -> navController.navigate(route) }
            }
            composable(Screen.LogWorkout.route) { LogWorkoutScreen(factory) }
            composable(Screen.History.route) { HistoryScreen(factory) }
            composable(Screen.Pending.route) { PendingScreen(factory) }
            composable(Screen.Routines.route) { RoutinesScreen(factory) }
            composable(Screen.BodyMetrics.route) { BodyMetricsScreen(factory) }
            composable(Screen.Settings.route) { SettingsScreen(factory) }
            composable(EXERCISE_LIBRARY_ROUTE) {
                ExerciseLibraryScreen(factory) { navController.popBackStack() }
            }
        }
    }
}

package com.trainingtracker.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainingtracker.app.ui.ViewModelFactory
import com.trainingtracker.app.ui.navigation.EXERCISE_LIBRARY_ROUTE
import com.trainingtracker.app.ui.navigation.Screen

@Composable
fun HomeScreen(factory: ViewModelFactory, onNavigate: (String) -> Unit) {
    val viewModel: HomeViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Training Tracker", style = MaterialTheme.typography.headlineMedium)
        Text("${state.exerciseCount} exercises tracked", style = MaterialTheme.typography.bodyMedium)
        if (state.pendingCount > 0) {
            Text(
                "${state.pendingCount} pending session(s) to confirm",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        QuickActionCard(
            icon = Icons.Filled.FitnessCenter,
            title = "Exercise Library",
            subtitle = "Create and manage your exercises",
            onClick = { onNavigate(EXERCISE_LIBRARY_ROUTE) },
        )
        QuickActionCard(
            icon = Icons.Filled.Schedule,
            title = "Routines & Reminders",
            subtitle = "Set up scheduled training reminders",
            onClick = { onNavigate(Screen.Routines.route) },
        )
        QuickActionCard(
            icon = Icons.Filled.MonitorWeight,
            title = "Body Metrics",
            subtitle = "Log weight, body fat %, muscle mass % and see your weekly trend",
            onClick = { onNavigate(Screen.BodyMetrics.route) },
        )
    }
}

@Composable
private fun QuickActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(horizontalAlignment = Alignment.Start) {
                Icon(icon, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

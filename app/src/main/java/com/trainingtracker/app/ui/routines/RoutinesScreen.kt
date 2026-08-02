package com.trainingtracker.app.ui.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainingtracker.app.data.local.entity.Routine
import com.trainingtracker.app.ui.ViewModelFactory

private val DAY_LABELS = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutinesScreen(factory: ViewModelFactory) {
    val viewModel: RoutinesViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var deletingRoutine by remember { mutableStateOf<Routine?>(null) }

    androidx.compose.material3.Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New routine")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text("Routines & Reminders", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
            LazyColumn {
                items(state.routines) { routine ->
                    ListItem(
                        headlineContent = { Text(routine.name) },
                        supportingContent = {
                            val days = routine.daysOfWeek.mapNotNull { d -> DAY_LABELS.firstOrNull { it.first == d }?.second }
                            Text("${days.joinToString(", ")} at %02d:%02d".format(routine.reminderHour, routine.reminderMinute))
                        },
                        trailingContent = {
                            Row {
                                Switch(checked = routine.enabled, onCheckedChange = { viewModel.toggleEnabled(routine) })
                                IconButton(onClick = { deletingRoutine = routine }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateRoutineDialog(
            exercises = state.exercises,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, days, hour, minute, exerciseIds ->
                viewModel.createRoutine(name, days, hour, minute, exerciseIds)
                showCreateDialog = false
            },
        )
    }

    deletingRoutine?.let { routine ->
        AlertDialog(
            onDismissRequest = { deletingRoutine = null },
            title = { Text("Delete ${routine.name}?") },
            text = { Text("Its scheduled reminders will stop firing.") },
            confirmButton = {
                Button(onClick = { viewModel.deleteRoutine(routine); deletingRoutine = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingRoutine = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateRoutineDialog(
    exercises: List<com.trainingtracker.app.data.local.entity.Exercise>,
    onDismiss: () -> Unit,
    onCreate: (String, List<Int>, Int, Int, List<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var hour by remember { mutableStateOf("18") }
    var minute by remember { mutableStateOf("0") }
    var selectedExerciseIds by remember { mutableStateOf(setOf<String>()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Routine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Push Day)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Days", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DAY_LABELS.forEach { (day, label) ->
                        FilterChip(
                            selected = day in selectedDays,
                            onClick = {
                                selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day
                            },
                            label = { Text(label) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = hour, onValueChange = { hour = it }, label = { Text("Hour (0-23)") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minute, onValueChange = { minute = it }, label = { Text("Minute") },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("Exercises covered (optional)", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    exercises.forEach { exercise ->
                        FilterChip(
                            selected = exercise.id in selectedExerciseIds,
                            onClick = {
                                selectedExerciseIds = if (exercise.id in selectedExerciseIds) {
                                    selectedExerciseIds - exercise.id
                                } else {
                                    selectedExerciseIds + exercise.id
                                }
                            },
                            label = { Text(exercise.name) },
                        )
                    }
                }
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                errorMessage = when {
                    name.isBlank() -> "Enter a name"
                    selectedDays.isEmpty() -> "Select at least one day"
                    else -> null
                }
                if (errorMessage != null) return@Button
                val h = hour.toIntOrNull()?.coerceIn(0, 23) ?: 18
                val m = minute.toIntOrNull()?.coerceIn(0, 59) ?: 0
                onCreate(name, selectedDays.sorted(), h, m, selectedExerciseIds.toList())
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

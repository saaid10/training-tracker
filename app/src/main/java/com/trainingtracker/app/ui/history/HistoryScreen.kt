package com.trainingtracker.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.domain.progress.ProgressTrend
import com.trainingtracker.app.ui.ViewModelFactory
import com.trainingtracker.app.ui.components.SearchableExercisePicker
import com.trainingtracker.app.ui.theme.ProgressGreen
import com.trainingtracker.app.ui.theme.ProgressRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(factory: ViewModelFactory) {
    val viewModel: HistoryViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    var showTooltip by remember { mutableStateOf(false) }
    var editingLog by remember { mutableStateOf<WorkoutLog?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("History & Graphs", style = MaterialTheme.typography.headlineSmall)

        SearchableExercisePicker(
            exercises = state.exercises,
            selectedExerciseId = state.selectedExerciseId,
            onSelect = { viewModel.selectExercise(it.id) },
        )

        if (state.selectedExerciseId != null) {
            val trend = state.progress?.trend
            val trendColor = when (trend) {
                ProgressTrend.PROGRESSED -> ProgressGreen
                ProgressTrend.REGRESSED -> ProgressRed
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(colors = CardDefaults.cardColors(contentColor = trendColor)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(state.metricName, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { showTooltip = true }) {
                            Icon(Icons.Filled.Info, contentDescription = "What is this metric?")
                        }
                    }
                    Text(
                        trend?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "No data",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    state.progress?.explanation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            if (state.chartPointsOldestFirst.size >= 2) {
                SimpleLineChart(pointsOldestFirst = state.chartPointsOldestFirst)
            }

            Text("Session log", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap a session to fix a mistake.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn {
                items(state.logsNewestFirst, key = { it.id }) { log ->
                    ListItem(
                        headlineContent = {
                            Text("${log.weightKg}kg x ${log.reps} reps x ${log.sets} sets")
                        },
                        supportingContent = {
                            Text(
                                SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())
                                    .format(Date(log.loggedAt)) + (log.rpe?.let { " · RPE $it" } ?: ""),
                            )
                        },
                        modifier = Modifier.clickable { editingLog = log },
                    )
                }
            }
        } else {
            Text("Create an exercise and log a session to see history here.")
        }
    }

    if (showTooltip) {
        AlertDialog(
            onDismissRequest = { showTooltip = false },
            title = { Text(state.metricName) },
            text = { Text(state.metricTooltip) },
            confirmButton = { TextButton(onClick = { showTooltip = false }) { Text("Got it") } },
        )
    }

    editingLog?.let { log ->
        EditLogDialog(
            log = log,
            onDismiss = { editingLog = null },
            onSave = { weight, reps, sets, rpe, notes, loggedAt ->
                viewModel.updateLog(log.id, weight, reps, sets, rpe, notes, loggedAt)
                editingLog = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditLogDialog(
    log: WorkoutLog,
    onDismiss: () -> Unit,
    onSave: (Double, Int, Int, Double?, String?, Long) -> Unit,
) {
    var weight by remember { mutableStateOf(log.weightKg.toString()) }
    var reps by remember { mutableStateOf(log.reps.toString()) }
    var sets by remember { mutableStateOf(log.sets.toString()) }
    var rpe by remember { mutableStateOf(log.rpe?.toString() ?: "") }
    var notes by remember { mutableStateOf(log.notes ?: "") }
    var loggedAt by remember { mutableStateOf(log.loggedAt) }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit session") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date: " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(loggedAt)))
                }
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text("Reps") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sets, onValueChange = { sets = it }, label = { Text("Sets") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rpe, onValueChange = { rpe = it }, label = { Text("RPE (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = weight.toDoubleOrNull()
                val r = reps.toIntOrNull()
                val s = sets.toIntOrNull()
                errorMessage = when {
                    w == null -> "Enter a valid weight"
                    r == null -> "Enter a valid rep count"
                    s == null -> "Enter a valid set count"
                    else -> null
                }
                if (w == null || r == null || s == null) return@Button
                onSave(w, r, s, rpe.toDoubleOrNull(), notes.ifBlank { null }, loggedAt)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = loggedAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { loggedAt = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

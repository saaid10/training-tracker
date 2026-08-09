package com.trainingtracker.app.ui.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.repository.NextSessionAdjustment
import com.trainingtracker.app.ui.ViewModelFactory
import com.trainingtracker.app.ui.components.SearchableExercisePicker
import com.trainingtracker.app.ui.components.SetListEditor
import com.trainingtracker.app.ui.components.SetRowState
import com.trainingtracker.app.ui.components.hasErrors
import com.trainingtracker.app.ui.components.toWorkoutSets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWorkoutScreen(factory: ViewModelFactory) {
    val viewModel: LogWorkoutViewModel = viewModel(factory = factory)
    val exercises by viewModel.exercises.collectAsState()

    var selectedExerciseId by remember { mutableStateOf("") }
    val exerciseType = exercises.firstOrNull { it.id == selectedExerciseId }?.type ?: ExerciseType.WEIGHTED

    var setRows by remember { mutableStateOf(listOf(SetRowState())) }
    var notes by remember { mutableStateOf("") }

    var planNext by remember { mutableStateOf(false) }
    var weightDelta by remember { mutableStateOf("") }
    var repsDelta by remember { mutableStateOf("") }
    var setsDelta by remember { mutableStateOf("") }
    var rpeDelta by remember { mutableStateOf("") }

    var confirmationMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Defaults to today; the user can back-date a forgotten/late log via the date picker below.
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Set rows depend on which fields the exercise's type exposes, so reset them on exercise change.
    LaunchedEffect(selectedExerciseId) {
        setRows = listOf(SetRowState())
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Log Workout", style = MaterialTheme.typography.headlineSmall)

        SearchableExercisePicker(
            exercises = exercises,
            selectedExerciseId = selectedExerciseId,
            onSelect = { selectedExerciseId = it.id },
        )

        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Date: " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(selectedDateMillis)))
        }

        SetListEditor(exerciseType = exerciseType, rows = setRows, onRowsChange = { setRows = it })

        OutlinedTextField(
            value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = planNext, onCheckedChange = { planNext = it })
            Text("Plan next session from this log")
        }

        if (planNext) {
            Text(
                "Leave a field blank to keep it the same next time. This creates a Pending entry " +
                    "you'll confirm before/at your next workout.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = weightDelta, onValueChange = { weightDelta = it }, label = { Text("+kg") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = repsDelta, onValueChange = { repsDelta = it },
                    label = { Text(if (exerciseType == ExerciseType.TIMED) "+seconds" else "+reps") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = setsDelta, onValueChange = { setsDelta = it }, label = { Text("+sets") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = rpeDelta, onValueChange = { rpeDelta = it }, label = { Text("+RPE") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        confirmationMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                confirmationMessage = null
                errorMessage = when {
                    selectedExerciseId.isBlank() -> "Select an exercise"
                    setRows.hasErrors(exerciseType) -> "Fix the highlighted set(s)"
                    else -> null
                }
                if (errorMessage != null) return@Button
                val adjustment = if (planNext) {
                    NextSessionAdjustment(
                        weightDeltaKg = weightDelta.toDoubleOrNull(),
                        repsDelta = repsDelta.toIntOrNull(),
                        setsDelta = setsDelta.toIntOrNull(),
                        rpeDelta = rpeDelta.toDoubleOrNull(),
                    )
                } else null

                viewModel.logSession(
                    selectedExerciseId, exerciseType, setRows.toWorkoutSets(exerciseType),
                    notes.ifBlank { null }, adjustment, selectedDateMillis,
                ) {
                    confirmationMessage = "Session logged" + if (planNext) " — next session added to Pending" else ""
                    selectedExerciseId = ""
                    setRows = listOf(SetRowState())
                    notes = ""
                    weightDelta = ""; repsDelta = ""; setsDelta = ""; rpeDelta = ""; planNext = false
                    selectedDateMillis = System.currentTimeMillis()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Log Session")
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDateMillis = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

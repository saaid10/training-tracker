package com.trainingtracker.app.ui.bodymetrics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.trainingtracker.app.data.local.entity.BodyMetricLog
import com.trainingtracker.app.domain.bodymetrics.MetricTrend
import com.trainingtracker.app.domain.progress.ProgressTrend
import com.trainingtracker.app.ui.ViewModelFactory
import com.trainingtracker.app.ui.theme.ProgressGreen
import com.trainingtracker.app.ui.theme.ProgressRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodyMetricsScreen(factory: ViewModelFactory) {
    val viewModel: BodyMetricsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()

    var weight by remember { mutableStateOf("") }
    var bodyFat by remember { mutableStateOf("") }
    var muscleMass by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmationMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var editingEntry by remember { mutableStateOf<BodyMetricLog?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Body Metrics", style = MaterialTheme.typography.headlineSmall)

        state.weeklyTrend?.let { trend ->
            Text("This week's trend (7-day avg vs. the 7 days before)", style = MaterialTheme.typography.titleMedium)
            TrendRow(label = "Weight (kg)", trend = trend.weight)
            TrendRow(label = "Body Fat %", trend = trend.bodyFatPercent)
            TrendRow(label = "Muscle Mass %", trend = trend.muscleMassPercent)
        }

        HorizontalDivider()

        Text("Log a reading", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Date: " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(selectedDateMillis)))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") },
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = bodyFat, onValueChange = { bodyFat = it }, label = { Text("Body Fat %") },
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = muscleMass, onValueChange = { muscleMass = it }, label = { Text("Muscle Mass %") },
            keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = notes, onValueChange = { notes = it }, label = { Text("Notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        confirmationMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                confirmationMessage = null
                val weightVal = weight.toDoubleOrNull()
                val bodyFatVal = bodyFat.toDoubleOrNull()
                val muscleMassVal = muscleMass.toDoubleOrNull()
                if (weightVal == null && bodyFatVal == null && muscleMassVal == null) {
                    errorMessage = "Enter at least one of weight, body fat %, or muscle mass %"
                    return@Button
                }
                errorMessage = null
                viewModel.logEntry(weightVal, bodyFatVal, muscleMassVal, notes.ifBlank { null }, selectedDateMillis) {
                    confirmationMessage = "Logged"
                    weight = ""; bodyFat = ""; muscleMass = ""; notes = ""
                    selectedDateMillis = System.currentTimeMillis()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Log Reading")
        }

        HorizontalDivider()

        Text("Past entries", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap an entry to fix a mistake or delete it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The screen is already one scrolling Column (verticalScroll above), so this list is a
        // plain Column, not a LazyColumn — nesting a lazily-measured list inside a scrolling
        // Column crashes with "infinite height constraints".
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            state.entries.forEach { entry ->
                ListItem(
                    headlineContent = { Text(entrySummary(entry)) },
                    supportingContent = {
                        Text(SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(entry.loggedAt)))
                    },
                    modifier = Modifier.fillMaxWidth().clickable { editingEntry = entry },
                )
            }
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

    editingEntry?.let { entry ->
        EditBodyMetricDialog(
            entry = entry,
            onDismiss = { editingEntry = null },
            onSave = { w, f, m, n, date ->
                viewModel.updateEntry(entry.id, w, f, m, n, date)
                editingEntry = null
            },
            onDelete = {
                viewModel.deleteEntry(entry.id)
                editingEntry = null
            },
        )
    }
}

private fun entrySummary(entry: BodyMetricLog): String {
    val parts = mutableListOf<String>()
    entry.weightKg?.let { parts += "${it}kg" }
    entry.bodyFatPercent?.let { parts += "${it}% fat" }
    entry.muscleMassPercent?.let { parts += "${it}% muscle" }
    return if (parts.isEmpty()) "(no values)" else parts.joinToString(" · ")
}

@Composable
private fun TrendRow(label: String, trend: MetricTrend) {
    val color = when (trend.trend) {
        ProgressTrend.PROGRESSED -> ProgressGreen
        ProgressTrend.REGRESSED -> ProgressRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(colors = CardDefaults.cardColors(contentColor = color)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            val current = trend.currentAvg
            val previous = trend.previousAvg
            if (current == null || previous == null) {
                Text("Not enough data yet", style = MaterialTheme.typography.bodySmall)
            } else {
                val delta = current - previous
                val sign = if (delta >= 0) "+" else ""
                Text(
                    "%.1f avg this week (%s%.1f vs. last week)".format(current, sign, delta),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBodyMetricDialog(
    entry: BodyMetricLog,
    onDismiss: () -> Unit,
    onSave: (Double?, Double?, Double?, String?, Long) -> Unit,
    onDelete: () -> Unit,
) {
    var weight by remember { mutableStateOf(entry.weightKg?.toString() ?: "") }
    var bodyFat by remember { mutableStateOf(entry.bodyFatPercent?.toString() ?: "") }
    var muscleMass by remember { mutableStateOf(entry.muscleMassPercent?.toString() ?: "") }
    var notes by remember { mutableStateOf(entry.notes ?: "") }
    var loggedAt by remember { mutableStateOf(entry.loggedAt) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Date: " + SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(loggedAt)))
                }
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = bodyFat, onValueChange = { bodyFat = it }, label = { Text("Body Fat %") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = muscleMass, onValueChange = { muscleMass = it }, label = { Text("Muscle Mass %") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete entry") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = weight.toDoubleOrNull()
                val f = bodyFat.toDoubleOrNull()
                val m = muscleMass.toDoubleOrNull()
                if (w == null && f == null && m == null) {
                    errorMessage = "Enter at least one of weight, body fat %, or muscle mass %"
                    return@Button
                }
                onSave(w, f, m, notes.ifBlank { null }, loggedAt)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this entry?") },
            text = { Text("This can't be undone.") },
            confirmButton = { Button(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

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

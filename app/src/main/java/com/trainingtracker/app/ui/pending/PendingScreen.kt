package com.trainingtracker.app.ui.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.trainingtracker.app.ui.ViewModelFactory

@Composable
fun PendingScreen(factory: ViewModelFactory) {
    val viewModel: PendingViewModel = viewModel(factory = factory)
    val items by viewModel.pendingItems.collectAsState()
    var editing by remember { mutableStateOf<PendingItem?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("Pending", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(16.dp))
        if (items.isEmpty()) {
            Text(
                "No pending sessions. These appear when you plan a next session from a Log Workout entry.",
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn {
            items(items, key = { it.log.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.exerciseName) },
                    supportingContent = {
                        Text("${item.log.weightKg}kg x ${item.log.reps} reps x ${item.log.sets} sets" +
                            (item.log.rpe?.let { " · RPE $it" } ?: ""))
                    },
                    trailingContent = {
                        Button(onClick = { editing = item }) { Text("Review") }
                    },
                )
            }
        }
    }

    editing?.let { item ->
        ConfirmPendingDialog(
            item = item,
            onDismiss = { editing = null },
            onDiscard = { viewModel.discard(item.log.id); editing = null },
            onConfirm = { weight, reps, sets, rpe, notes ->
                viewModel.confirm(item.log.id, weight, reps, sets, rpe, notes)
                editing = null
            },
        )
    }
}

@Composable
private fun ConfirmPendingDialog(
    item: PendingItem,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onConfirm: (Double, Int, Int, Double?, String?) -> Unit,
) {
    var weight by remember { mutableStateOf(item.log.weightKg.toString()) }
    var reps by remember { mutableStateOf(item.log.reps.toString()) }
    var sets by remember { mutableStateOf(item.log.sets.toString()) }
    var rpe by remember { mutableStateOf(item.log.rpe?.toString() ?: "") }
    var notes by remember { mutableStateOf(item.log.notes ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm: ${item.exerciseName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Weight (kg)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = reps, onValueChange = { reps = it }, label = { Text("Reps") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sets, onValueChange = { sets = it }, label = { Text("Sets") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rpe, onValueChange = { rpe = it }, label = { Text("RPE (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text("Discard this planned session") }
            }
        },
        confirmButton = {
            Button(onClick = {
                val w = weight.toDoubleOrNull() ?: return@Button
                val r = reps.toIntOrNull() ?: return@Button
                val s = sets.toIntOrNull() ?: return@Button
                onConfirm(w, r, s, rpe.toDoubleOrNull(), notes.ifBlank { null })
            }) { Text("Confirm as done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

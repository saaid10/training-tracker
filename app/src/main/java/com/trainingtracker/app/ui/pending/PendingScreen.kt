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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.ui.ViewModelFactory
import com.trainingtracker.app.ui.components.SetListEditor
import com.trainingtracker.app.ui.components.hasErrors
import com.trainingtracker.app.ui.components.summaryText
import com.trainingtracker.app.ui.components.toRowStates
import com.trainingtracker.app.ui.components.toWorkoutSets

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
                    supportingContent = { Text(item.log.sets.summaryText(item.exerciseType)) },
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
            onConfirm = { sets, notes ->
                viewModel.confirm(item.log.id, sets, notes)
                editing = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmPendingDialog(
    item: PendingItem,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit,
    onConfirm: (List<WorkoutSet>, String?) -> Unit,
) {
    var rows by remember { mutableStateOf(item.log.sets.toRowStates()) }
    var notes by remember { mutableStateOf(item.log.notes ?: "") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm: ${item.exerciseName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetListEditor(exerciseType = item.exerciseType, rows = rows, onRowsChange = { rows = it })
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth())
                errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth()) { Text("Discard this planned session") }
            }
        },
        confirmButton = {
            Button(onClick = {
                errorMessage = if (rows.hasErrors(item.exerciseType)) "Fix the highlighted set(s)" else null
                if (errorMessage != null) return@Button
                onConfirm(rows.toWorkoutSets(item.exerciseType), notes.ifBlank { null })
            }) { Text("Confirm as done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

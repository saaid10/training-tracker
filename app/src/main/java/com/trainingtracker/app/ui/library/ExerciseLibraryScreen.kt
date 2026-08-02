package com.trainingtracker.app.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.trainingtracker.app.data.local.entity.Category
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.domain.model.Goal
import com.trainingtracker.app.ui.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseLibraryScreen(factory: ViewModelFactory, onBack: () -> Unit) {
    val viewModel: ExerciseLibraryViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var deletingExercise by remember { mutableStateOf<Exercise?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New exercise")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Exercise Library",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp),
            )
            Text(
                "Tap an exercise to rename it or change its category/goal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyColumn {
                items(state.exercises, key = { it.id }) { exercise ->
                    val categoryName = state.categories.firstOrNull { it.id == exercise.categoryId }?.name ?: "—"
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = {
                            Text(categoryName + (exercise.goalOverride?.let { " · goal: ${it.name}" } ?: ""))
                        },
                        trailingContent = {
                            IconButton(onClick = { deletingExercise = exercise }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        },
                        modifier = Modifier.clickable { editingExercise = exercise },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        ExerciseFormDialog(
            title = "New Exercise",
            initial = null,
            categories = state.categories,
            onDismiss = { showCreateDialog = false },
            onAddCategory = viewModel::addCategory,
            onSubmit = { name, categoryId, goal ->
                viewModel.createExercise(name, categoryId, goal)
                showCreateDialog = false
            },
        )
    }

    editingExercise?.let { exercise ->
        ExerciseFormDialog(
            title = "Edit Exercise",
            initial = exercise,
            categories = state.categories,
            onDismiss = { editingExercise = null },
            onAddCategory = viewModel::addCategory,
            onSubmit = { name, categoryId, goal ->
                viewModel.updateExercise(exercise, name, categoryId, goal)
                editingExercise = null
            },
        )
    }

    deletingExercise?.let { exercise ->
        AlertDialog(
            onDismissRequest = { deletingExercise = null },
            title = { Text("Delete ${exercise.name}?") },
            text = { Text("Its logged history stays in History & Graphs, but it will no longer show up here or when logging a new session.") },
            confirmButton = {
                Button(onClick = { viewModel.deleteExercise(exercise.id); deletingExercise = null }) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { deletingExercise = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ExerciseFormDialog(
    title: String,
    initial: Exercise?,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onAddCategory: (String) -> Unit,
    onSubmit: (String, String, Goal?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var selectedCategoryId by remember { mutableStateOf(initial?.categoryId ?: categories.firstOrNull()?.id ?: "") }
    var newCategoryName by remember { mutableStateOf("") }
    var selectedGoal by remember { mutableStateOf(initial?.goalOverride) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Bench Press)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                ExposedDropdownMenuBox(
                    expanded = categoryMenuExpanded,
                    onExpandedChange = { categoryMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = categories.firstOrNull { it.id == selectedCategoryId }?.name ?: "Select category",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryMenuExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategoryId = category.id
                                    categoryMenuExpanded = false
                                },
                            )
                        }
                    }
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = { newCategoryName = it },
                        label = { Text("Add custom category") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onAddCategory(newCategoryName); newCategoryName = "" }) {
                        Text("Add")
                    }
                }

                Text("Goal override (optional)", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = selectedGoal == null,
                        onClick = { selectedGoal = null },
                        label = { Text("Use default") },
                    )
                    Goal.entries.forEach { goal ->
                        FilterChip(
                            selected = selectedGoal == goal,
                            onClick = { selectedGoal = goal },
                            label = { Text(goal.name) },
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
                    selectedCategoryId.isBlank() -> "Select a category"
                    else -> null
                }
                if (errorMessage != null) return@Button
                onSubmit(name, selectedCategoryId, selectedGoal)
            }) { Text(if (initial == null) "Create" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package com.trainingtracker.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.trainingtracker.app.data.local.entity.Exercise

/**
 * Type-to-filter exercise picker (requirements.txt 3a: "picks the exercise from a searchable
 * list"). Typing narrows the dropdown to matching names; tapping a result selects it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableExercisePicker(
    exercises: List<Exercise>,
    selectedExerciseId: String?,
    onSelect: (Exercise) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Exercise",
) {
    var expanded by remember { mutableStateOf(false) }
    var query by remember(selectedExerciseId, exercises) {
        mutableStateOf(exercises.firstOrNull { it.id == selectedExerciseId }?.name ?: "")
    }
    val filtered = remember(query, exercises) {
        if (query.isBlank()) exercises else exercises.filter { it.name.contains(query, ignoreCase = true) }
    }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        if (filtered.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                filtered.forEach { exercise ->
                    DropdownMenuItem(
                        text = { Text(exercise.name) },
                        onClick = {
                            query = exercise.name
                            expanded = false
                            onSelect(exercise)
                        },
                    )
                }
            }
        }
    }
}

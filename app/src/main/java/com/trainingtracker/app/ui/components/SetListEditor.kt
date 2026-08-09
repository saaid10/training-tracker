package com.trainingtracker.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet

/** One set row's raw text-field state, converted to/from [WorkoutSet] on save. */
data class SetRowState(
    val weight: String = "",
    val reps: String = "",
    val durationSeconds: String = "",
    val rpe: String = "",
)

fun WorkoutSet.toRowState() = SetRowState(
    weight = weightKg?.toString() ?: "",
    reps = reps?.toString() ?: "",
    durationSeconds = durationSeconds?.toString() ?: "",
    rpe = rpe?.toString() ?: "",
)

fun List<WorkoutSet>.toRowStates(): List<SetRowState> = map { it.toRowState() }

/**
 * Inline validation error for one row, or null if it's valid for [type] — requirements.txt 3n:
 * never a silent no-op on bad input.
 */
fun SetRowState.validationError(type: ExerciseType): String? {
    if (weight.isNotBlank() && weight.toDoubleOrNull() == null) return "Enter a valid weight or leave it blank"
    return when (type) {
        ExerciseType.WEIGHTED -> when {
            weight.isBlank() || weight.toDoubleOrNull() == null -> "Enter a valid weight"
            reps.toIntOrNull() == null -> "Enter a valid rep count"
            else -> null
        }
        ExerciseType.BODYWEIGHT -> if (reps.toIntOrNull() == null) "Enter a valid rep count" else null
        ExerciseType.TIMED -> if (durationSeconds.toIntOrNull() == null) "Enter a valid duration (seconds)" else null
    }
}

fun List<SetRowState>.hasErrors(type: ExerciseType): Boolean = any { it.validationError(type) != null }

fun SetRowState.toWorkoutSet(type: ExerciseType): WorkoutSet = WorkoutSet(
    weightKg = weight.toDoubleOrNull(),
    reps = if (type == ExerciseType.TIMED) null else reps.toIntOrNull(),
    durationSeconds = if (type == ExerciseType.TIMED) durationSeconds.toIntOrNull() else null,
    rpe = rpe.toDoubleOrNull(),
)

fun List<SetRowState>.toWorkoutSets(type: ExerciseType): List<WorkoutSet> = map { it.toWorkoutSet(type) }

/** Session-log/Pending list row summary, e.g. "70.0kg x 8 reps · 80.0kg x 6 reps" or "20.0kg x 45s". */
fun List<WorkoutSet>.summaryText(type: ExerciseType): String = joinToString(" · ") { set ->
    val weightPart = set.weightKg?.let { "${it}kg" } ?: if (type == ExerciseType.WEIGHTED) "0kg" else "BW"
    val loadPart = when (type) {
        ExerciseType.TIMED -> "${set.durationSeconds ?: 0}s"
        else -> "${set.reps ?: 0} reps"
    }
    val rpePart = set.rpe?.let { " (RPE $it)" } ?: ""
    "$weightPart x $loadPart$rpePart"
}

/**
 * A dynamic list of set-row editors, showing/requiring fields depending on [exerciseType]
 * (weighted exercises require weight+reps, bodyweight exercises make weight optional, timed
 * exercises swap reps for a duration field). Shared by Log Workout, the History edit dialog, and
 * the Pending confirm dialog so the per-set editing logic lives in one place.
 */
@Composable
fun SetListEditor(
    exerciseType: ExerciseType,
    rows: List<SetRowState>,
    onRowsChange: (List<SetRowState>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { index, row ->
            fun updateRow(updated: SetRowState) {
                onRowsChange(rows.toMutableList().apply { this[index] = updated })
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set ${index + 1}", modifier = Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(
                        value = row.weight,
                        onValueChange = { updateRow(row.copy(weight = it)) },
                        label = { Text(if (exerciseType == ExerciseType.WEIGHTED) "Weight (kg)" else "Weight (kg, optional)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    if (exerciseType == ExerciseType.TIMED) {
                        OutlinedTextField(
                            value = row.durationSeconds,
                            onValueChange = { updateRow(row.copy(durationSeconds = it)) },
                            label = { Text("Duration (s)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        OutlinedTextField(
                            value = row.reps,
                            onValueChange = { updateRow(row.copy(reps = it)) },
                            label = { Text("Reps") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedTextField(
                        value = row.rpe,
                        onValueChange = { updateRow(row.copy(rpe = it)) },
                        label = { Text("RPE") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRowsChange(rows.toMutableList().apply { removeAt(index) }) }, enabled = rows.size > 1) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove set ${index + 1}")
                    }
                }
                row.validationError(exerciseType)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onRowsChange(rows + SetRowState()) }) { Text("+ Add set") }
            TextButton(onClick = { onRowsChange(rows + rows.last()) }) { Text("Duplicate last set") }
        }
    }
}

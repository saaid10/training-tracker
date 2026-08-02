package com.trainingtracker.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.trainingtracker.app.domain.model.Goal
import com.trainingtracker.app.domain.model.OneRepMaxFormula
import com.trainingtracker.app.domain.progress.OneRepMax
import com.trainingtracker.app.ui.ViewModelFactory

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(factory: ViewModelFactory) {
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsState()
    val restoreStatus by viewModel.restoreStatus.collectAsState()
    var windowText by remember(state.rollingAverageWindow) { mutableStateOf(state.rollingAverageWindow.toString()) }
    var newCategory by remember { mutableStateOf("") }
    var infoFormula by remember { mutableStateOf<OneRepMaxFormula?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Text("Global default goal", style = MaterialTheme.typography.titleMedium)
        Text(
            "Used for any exercise without its own goal override.",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Goal.entries.forEach { goal ->
                FilterChip(
                    selected = state.globalDefaultGoal == goal,
                    onClick = { viewModel.setGlobalDefaultGoal(goal) },
                    label = { Text(goal.name) },
                )
            }
        }

        HorizontalDivider()

        Text("Rolling average window", style = MaterialTheme.typography.titleMedium)
        Text(
            "How many of the same exercise's most recent sessions to average as the baseline (default 5).",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = windowText, onValueChange = { windowText = it }, label = { Text("Sessions") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { windowText.toIntOrNull()?.let { viewModel.setRollingAverageWindow(it) } }) {
                Text("Save")
            }
        }

        HorizontalDivider()

        Text("Estimated 1RM formula", style = MaterialTheme.typography.titleMedium)
        Text(
            "Tap the info icon on each option to see what it is and how it's calculated.",
            style = MaterialTheme.typography.bodySmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            OneRepMaxFormula.entries.forEach { formula ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    FilterChip(
                        selected = state.oneRepMaxFormula == formula,
                        onClick = { viewModel.setOneRepMaxFormula(formula) },
                        label = { Text(formula.name) },
                    )
                    IconButton(onClick = { infoFormula = formula }) {
                        Icon(Icons.Filled.Info, contentDescription = "What is ${formula.name}?")
                    }
                }
            }
        }

        HorizontalDivider()

        Text("Categories", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            state.categories.forEach { category -> Text("• ${category.name}") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = newCategory, onValueChange = { newCategory = it }, label = { Text("New category") },
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { viewModel.addCategory(newCategory); newCategory = "" }) { Text("Add") }
        }

        HorizontalDivider()

        Text("Backup", style = MaterialTheme.typography.titleMedium)
        Text(
            "Data syncs to Supabase automatically in the background when online. Use this only to " +
                "restore after a reinstall or new phone.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = { viewModel.restoreFromBackup() }, modifier = Modifier.fillMaxWidth()) {
            Text("Restore from backup")
        }
        restoreStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }

    infoFormula?.let { formula ->
        AlertDialog(
            onDismissRequest = { infoFormula = null },
            title = { Text(formula.name) },
            text = { Text(OneRepMax.description(formula)) },
            confirmButton = { TextButton(onClick = { infoFormula = null }) { Text("Got it") } },
        )
    }
}

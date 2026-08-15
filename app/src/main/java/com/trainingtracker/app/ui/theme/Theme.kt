package com.trainingtracker.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Progress color coding (requirements.txt 3c): green = progressed, red = regressed. */
val ProgressGreen = Color(0xFF2E7D32)
val ProgressRed = Color(0xFFC62828)

private val IronColors = darkColorScheme(
    primary = TrainingColors.Accent,
    onPrimary = TrainingColors.Chalk,
    secondary = TrainingColors.Accent,
    onSecondary = TrainingColors.Chalk,
    background = TrainingColors.Background,
    onBackground = TrainingColors.Chalk,
    surface = TrainingColors.Panel,
    onSurface = TrainingColors.Chalk,
    surfaceVariant = TrainingColors.PanelAlt,
    onSurfaceVariant = TrainingColors.ChalkDim,
    error = ProgressRed,
    onError = TrainingColors.Chalk,
    outline = TrainingColors.PanelAlt,
)

/**
 * Deliberately single-world: an "iron ledger" dark theme (charcoal/graphite/chalk with one
 * barbell-plate red accent), used regardless of system light/dark setting or wallpaper — no
 * Material You dynamic color, matching the sibling MartialArtsTimer app's approach of committing
 * to one fixed identity instead of following the system theme.
 */
@Composable
fun TrainingTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = IronColors,
        typography = TrainingTypography,
        content = content,
    )
}

package com.trainingtracker.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Iron-ledger palette: gym-signage charcoal and graphite, chalk-white text, a single
 * barbell-plate red accent. No Material You dynamic color — this app commits to one look.
 */
object TrainingColors {
    val Background = Color(0xFF1C1B1A)
    val Panel = Color(0xFF2A2826)
    val PanelAlt = Color(0xFF332F2C)
    val Chalk = Color(0xFFEDEAE4)
    val ChalkDim = Color(0xFFEDEAE4).copy(alpha = 0.6f)
    val Accent = Color(0xFFC8342E)
    val AccentDim = Color(0xFF8C231F)
}

package com.trainingtracker.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Tabular figures everywhere: this app is mostly weights, reps, and dates lining up in columns.
private const val TABULAR_NUMS = "tnum"

// Display/headline/title tiers get a heavy "stenciled plate" treatment — gym-signage character
// from the system font's black weight, no bundled or downloaded font file needed.
private val Stenciled = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.Black,
    letterSpacing = 0.4.sp,
    fontFeatureSettings = TABULAR_NUMS,
)

private val TabularOnly = TextStyle(fontFeatureSettings = TABULAR_NUMS)

private val Base = Typography()

val TrainingTypography = Base.copy(
    displayLarge = Base.displayLarge.merge(Stenciled),
    displayMedium = Base.displayMedium.merge(Stenciled),
    displaySmall = Base.displaySmall.merge(Stenciled),
    headlineLarge = Base.headlineLarge.merge(Stenciled),
    headlineMedium = Base.headlineMedium.merge(Stenciled),
    headlineSmall = Base.headlineSmall.merge(Stenciled),
    titleLarge = Base.titleLarge.merge(Stenciled),
    titleMedium = Base.titleMedium.merge(Stenciled),
    titleSmall = Base.titleSmall.merge(Stenciled),
    bodyLarge = Base.bodyLarge.merge(TabularOnly),
    bodyMedium = Base.bodyMedium.merge(TabularOnly),
    bodySmall = Base.bodySmall.merge(TabularOnly),
    labelLarge = Base.labelLarge.merge(TabularOnly),
    labelMedium = Base.labelMedium.merge(TabularOnly),
    labelSmall = Base.labelSmall.merge(TabularOnly),
)

package com.trainingtracker.app.domain.progress

/** Drives the green/red/default color coding on the History & Graphs screen. */
enum class ProgressTrend { PROGRESSED, REGRESSED, NEUTRAL, INSUFFICIENT_DATA }

data class ProgressResult(
    val trend: ProgressTrend,
    val currentScore: Double?,
    val baselineScore: Double?,
    /** Short human-readable reason, e.g. "Est. 1RM 82.3kg vs avg 78.1kg over last 4 sessions". */
    val explanation: String,
)

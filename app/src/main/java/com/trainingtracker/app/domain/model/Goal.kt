package com.trainingtracker.app.domain.model

/** Training goal — determines which progress metric is used for an exercise. */
enum class Goal {
    STRENGTH,
    HYPERTROPHY,
    ENDURANCE,
    AUTOREGULATED,
    SIMPLE,
}

enum class OneRepMaxFormula {
    EPLEY,
    BRZYCKI,
    LOMBARDI,
    OCONNER,
}

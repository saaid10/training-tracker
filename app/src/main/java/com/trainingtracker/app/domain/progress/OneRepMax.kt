package com.trainingtracker.app.domain.progress

import com.trainingtracker.app.domain.model.OneRepMaxFormula
import kotlin.math.pow

/** Estimated 1-rep-max formulas. All take a set's weight (kg) and reps and return an estimated 1RM in kg. */
object OneRepMax {
    fun estimate(formula: OneRepMaxFormula, weightKg: Double, reps: Int): Double {
        if (reps <= 0) return weightKg
        if (reps == 1) return weightKg
        return when (formula) {
            OneRepMaxFormula.EPLEY -> weightKg * (1 + reps / 30.0)
            OneRepMaxFormula.BRZYCKI -> weightKg * (36.0 / (37.0 - reps))
            OneRepMaxFormula.LOMBARDI -> weightKg * reps.toDouble().pow(0.10)
            OneRepMaxFormula.OCONNER -> weightKg * (1 + 0.025 * reps)
        }
    }

    /** Single source of truth for what each formula is and how it's calculated — used by both the
     * History screen's metric tooltip and the Settings screen's formula picker. */
    fun description(formula: OneRepMaxFormula): String = when (formula) {
        OneRepMaxFormula.EPLEY -> "Estimated 1-rep max = weight x (1 + reps/30). Estimates the heaviest " +
            "weight you could lift once, based on this session's weight and reps. The most commonly " +
            "used formula, good for tracking raw strength."
        OneRepMaxFormula.BRZYCKI -> "Estimated 1-rep max = weight x 36/(37 - reps). An alternative 1RM " +
            "estimate, slightly more conservative than Epley at higher rep counts."
        OneRepMaxFormula.LOMBARDI -> "Estimated 1-rep max = weight x reps^0.10. A 1RM estimate that " +
            "scales more gently with rep count than Epley/Brzycki."
        OneRepMaxFormula.OCONNER -> "Estimated 1-rep max = weight x (1 + 0.025 x reps). A more " +
            "conservative 1RM estimate than Epley."
    }
}

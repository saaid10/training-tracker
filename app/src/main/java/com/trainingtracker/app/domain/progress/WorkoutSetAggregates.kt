package com.trainingtracker.app.domain.progress

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.domain.model.OneRepMaxFormula

/**
 * Pure aggregate helpers over a session's sets, used by every ProgressMetric so per-set logging
 * (independent weight/reps per set, optional weight for bodyweight/timed exercises) has one
 * shared, tested definition of "the session's number" instead of each metric reimplementing it.
 */
object WorkoutSetAggregates {

    /**
     * The set with the highest estimated 1RM (WEIGHTED/BODYWEIGHT) or the longest duration, tie
     * broken by heavier weight (TIMED). Used by the Strength and Autoregulated metrics, which are
     * explicitly formula-based.
     */
    fun bestSet(sets: List<WorkoutSet>, type: ExerciseType, formula: OneRepMaxFormula): WorkoutSet {
        // Sessions are supposed to always have >=1 set (enforced at entry by SetListEditor's
        // hasErrors), but a corrupted/edge-case Supabase restore can still decode an empty list
        // (Converters.kt/Dtos.kt both treat a blank string as emptyList()) — fall back to an
        // all-null WorkoutSet ("no data") instead of throwing into a ViewModel's stateIn flow.
        if (sets.isEmpty()) return WorkoutSet()
        return if (type == ExerciseType.TIMED) {
            topByDuration(sets)
        } else {
            sets.maxByOrNull { OneRepMax.estimate(formula, it.weightKg ?: 0.0, it.reps ?: 0) }!!
        }
    }

    /** [bestSet]'s score: est. 1RM (WEIGHTED/BODYWEIGHT) or weight x duration, i.e. load x time (TIMED). */
    fun bestSetScore(sets: List<WorkoutSet>, type: ExerciseType, formula: OneRepMaxFormula): Double {
        val best = bestSet(sets, type, formula)
        return if (type == ExerciseType.TIMED) {
            (best.weightKg ?: 0.0) * (best.durationSeconds ?: 0)
        } else {
            OneRepMax.estimate(formula, best.weightKg ?: 0.0, best.reps ?: 0)
        }
    }

    /**
     * The heaviest-weight set (WEIGHTED/BODYWEIGHT, ties broken by more reps) or longest-duration
     * set (TIMED, ties broken by heavier weight) — no formula involved. Used only by the Simple
     * Comparison metric, which is explicitly "no formula" per its own tooltip.
     */
    fun topSetByWeight(sets: List<WorkoutSet>, type: ExerciseType): WorkoutSet {
        // See bestSet's comment above: defensive fallback for a corrupted/edge-case empty list.
        if (sets.isEmpty()) return WorkoutSet()
        return if (type == ExerciseType.TIMED) {
            topByDuration(sets)
        } else {
            sets.maxWithOrNull(compareBy({ it.weightKg ?: 0.0 }, { it.reps ?: 0 }))!!
        }
    }

    /** Total volume: sum(weight x reps) for WEIGHTED/BODYWEIGHT; sum(weight x duration) for TIMED. */
    fun totalVolume(sets: List<WorkoutSet>, type: ExerciseType): Double = sets.sumOf { set ->
        val weight = set.weightKg ?: 0.0
        if (type == ExerciseType.TIMED) weight * (set.durationSeconds ?: 0) else weight * (set.reps ?: 0)
    }

    /** Total endurance quantity: sum(reps) for WEIGHTED/BODYWEIGHT; sum(duration seconds) for TIMED. */
    fun totalEndurance(sets: List<WorkoutSet>, type: ExerciseType): Double = sets.sumOf { set ->
        if (type == ExerciseType.TIMED) (set.durationSeconds ?: 0).toDouble() else (set.reps ?: 0).toDouble()
    }

    /** The reps-or-duration value of one set, matching the exercise's type — for matched-load comparisons. */
    fun repOrDuration(set: WorkoutSet, type: ExerciseType): Int? =
        if (type == ExerciseType.TIMED) set.durationSeconds else set.reps

    private fun topByDuration(sets: List<WorkoutSet>): WorkoutSet =
        sets.maxWithOrNull(compareBy({ it.durationSeconds ?: 0 }, { it.weightKg ?: 0.0 }))!!
}

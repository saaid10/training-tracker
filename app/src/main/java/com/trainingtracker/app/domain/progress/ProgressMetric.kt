package com.trainingtracker.app.domain.progress

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.domain.model.Goal
import com.trainingtracker.app.domain.model.OneRepMaxFormula
import kotlin.math.abs

/** A tolerance band below which a change is considered noise, not real progress/regress. */
private const val NEUTRAL_BAND_PCT = 0.01 // 1%

interface ProgressMetric {
    val goal: Goal
    val displayName: String

    /** Shown in the History & Graphs screen's info tooltip. */
    val tooltip: String

    /**
     * @param current the just-logged (most recent) completed session for this exercise
     * @param priorSessions the previous sessions of the SAME exercise, newest first, already
     *   limited to the configured rolling-average window (see requirements.txt 3c)
     */
    fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult

    /** A single plottable number per session, for the History & Graphs chart. */
    fun chartScore(log: WorkoutLog): Double
}

private fun trendFromRelativeDiff(current: Double, baseline: Double): ProgressTrend {
    if (baseline == 0.0) return if (current > 0) ProgressTrend.PROGRESSED else ProgressTrend.NEUTRAL
    val relativeDiff = (current - baseline) / abs(baseline)
    return when {
        relativeDiff > NEUTRAL_BAND_PCT -> ProgressTrend.PROGRESSED
        relativeDiff < -NEUTRAL_BAND_PCT -> ProgressTrend.REGRESSED
        else -> ProgressTrend.NEUTRAL
    }
}

/**
 * Goal: Strength — the session's best set's estimated 1RM, compared to the rolling-average
 * baseline. For TIMED exercises (no rep count to plug into a 1RM formula), substitutes a
 * load x time (weight x duration) score for the best set instead.
 */
class OneRepMaxMetric(private val formula: OneRepMaxFormula, private val exerciseType: ExerciseType) : ProgressMetric {
    override val goal = Goal.STRENGTH
    override val displayName =
        if (exerciseType == ExerciseType.TIMED) "Best Set Load x Time"
        else "Estimated 1RM (${formula.name.lowercase().replaceFirstChar { it.uppercase() }})"
    override val tooltip: String
        get() = if (exerciseType == ExerciseType.TIMED) {
            "Estimated-1RM formulas need a rep count, which timed exercises don't have. Instead this " +
                "tracks your best set's load x time (weight x seconds held) — the same 'best single " +
                "set this session' idea, applied to a timed hold/carry instead of a rep max."
        } else {
            OneRepMax.description(formula)
        }

    override fun chartScore(log: WorkoutLog) = WorkoutSetAggregates.bestSetScore(log.sets, exerciseType, formula)

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentScore = chartScore(current)
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, currentScore, null, "No prior sessions yet.")
        }
        val baseline = priorSessions.map { chartScore(it) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        val label = if (exerciseType == ExerciseType.TIMED) "Best set load x time" else "Est. 1RM"
        return ProgressResult(
            trend, currentScore, baseline,
            "$label %.1f vs avg %.1f over last %d session(s)".format(currentScore, baseline, priorSessions.size),
        )
    }
}

/** Goal: Hypertrophy — total volume load per session (sum of weight x reps, or weight x duration for TIMED). */
class VolumeMetric(private val exerciseType: ExerciseType) : ProgressMetric {
    override val goal = Goal.HYPERTROPHY
    override val displayName = "Volume Load"
    override val tooltip =
        if (exerciseType == ExerciseType.TIMED) {
            "Volume load = sum of (weight x seconds held) across all sets. Tracks total load-time " +
                "under tension — a good fit for muscle-growth (hypertrophy) goals on timed holds/carries."
        } else {
            "Volume load = sets x reps x weight. The total amount of weight moved in the session. " +
                "Tracks work capacity — a good fit for muscle-growth (hypertrophy) goals."
        }

    override fun chartScore(log: WorkoutLog) = WorkoutSetAggregates.totalVolume(log.sets, exerciseType)

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentScore = chartScore(current)
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, currentScore, null, "No prior sessions yet.")
        }
        val baseline = priorSessions.map { chartScore(it) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        return ProgressResult(
            trend, currentScore, baseline,
            "Volume %.0f vs avg %.0f over last %d session(s)".format(currentScore, baseline, priorSessions.size),
        )
    }
}

/** Goal: Muscular Endurance — total reps (or total seconds held, for TIMED) per session. */
class EnduranceMetric(private val exerciseType: ExerciseType) : ProgressMetric {
    override val goal = Goal.ENDURANCE
    override val displayName = if (exerciseType == ExerciseType.TIMED) "Total Time Held (Endurance)" else "Total Reps (Endurance)"
    override val tooltip =
        if (exerciseType == ExerciseType.TIMED) {
            "Total time held = sum of every set's duration. Tracks how long you can sustain the " +
                "hold/carry in total across the session."
        } else {
            "Total reps = sum of every set's reps. Tracks how many total repetitions you can perform. " +
                "Best for muscular-endurance goals, including bodyweight exercises at a fixed load, " +
                "where doing more reps matters more than lifting heavier."
        }

    override fun chartScore(log: WorkoutLog) = WorkoutSetAggregates.totalEndurance(log.sets, exerciseType)

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentScore = chartScore(current)
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, currentScore, null, "No prior sessions yet.")
        }
        val baseline = priorSessions.map { chartScore(it) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        return ProgressResult(
            trend, currentScore, baseline,
            "%.0f vs avg %.1f over last %d session(s)".format(currentScore, baseline, priorSessions.size),
        )
    }
}

/**
 * Goal: Autoregulated / Powerlifting — RPE trend at matched load. If you hit the SAME weight and
 * reps (or weight and duration, for TIMED) for a lower RPE than before, that's progress. Falls
 * back to a best-set-score trend when no matching prior load exists.
 */
class AutoregulatedMetric(private val formula: OneRepMaxFormula, private val exerciseType: ExerciseType) : ProgressMetric {
    override val goal = Goal.AUTOREGULATED
    override val displayName = "RPE Trend"
    override val tooltip =
        "Compares the RPE (perceived effort, 1-10) you logged on your best set against previous " +
            "sessions' best sets at the SAME weight and reps (or weight and duration, for timed " +
            "exercises). A lower RPE at the same load means it felt easier — that's progress, even if " +
            "the number on the bar/clock didn't change. If no matching prior session exists, falls " +
            "back to comparing best-set score instead."

    override fun chartScore(log: WorkoutLog): Double {
        val best = WorkoutSetAggregates.bestSet(log.sets, exerciseType, formula)
        return best.rpe ?: WorkoutSetAggregates.bestSetScore(log.sets, exerciseType, formula)
    }

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentBest = WorkoutSetAggregates.bestSet(current.sets, exerciseType, formula)
        val currentRpe = currentBest.rpe
        val currentLoad = currentBest.weightKg
        val currentRepOrDuration = WorkoutSetAggregates.repOrDuration(currentBest, exerciseType)

        val matchedRpes = priorSessions.mapNotNull { log ->
            val best = WorkoutSetAggregates.bestSet(log.sets, exerciseType, formula)
            val sameLoad = best.weightKg == currentLoad &&
                WorkoutSetAggregates.repOrDuration(best, exerciseType) == currentRepOrDuration
            if (sameLoad) best.rpe else null
        }

        if (currentRpe != null && matchedRpes.isNotEmpty()) {
            val baselineRpe = matchedRpes.average()
            val trend = trendFromRelativeDiff(baselineRpe, currentRpe)
            return ProgressResult(
                trend, currentRpe, baselineRpe,
                "RPE %.1f vs avg %.1f at the same load over %d matched session(s)"
                    .format(currentRpe, baselineRpe, matchedRpes.size),
            )
        }

        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, null, null, "No prior sessions yet.")
        }
        val currentScore = WorkoutSetAggregates.bestSetScore(current.sets, exerciseType, formula)
        val baseline = priorSessions.map { WorkoutSetAggregates.bestSetScore(it.sets, exerciseType, formula) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        return ProgressResult(
            trend, currentScore, baseline,
            "No matching load history — compared best-set score %.1f vs avg %.1f instead"
                .format(currentScore, baseline),
        )
    }
}

/** Goal: Simple/beginner — direct field-by-field comparison of the session's top set, no formula. */
class SimpleComparisonMetric(private val exerciseType: ExerciseType) : ProgressMetric {
    override val goal = Goal.SIMPLE
    override val displayName = "Simple Comparison"
    override val tooltip =
        "Compares your top set's weight and reps (or weight and duration, for timed exercises), plus " +
            "your total set count, directly against the average of your recent sessions — no formula " +
            "involved. If 2 or more of those 3 fields improved, it's progress. If 2 or more got worse, " +
            "it's a regress. Otherwise it's a mixed/no-change result."

    override fun chartScore(log: WorkoutLog) = WorkoutSetAggregates.topSetByWeight(log.sets, exerciseType).weightKg ?: 0.0

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, null, null, "No prior sessions yet.")
        }
        val currentTop = WorkoutSetAggregates.topSetByWeight(current.sets, exerciseType)
        val currentWeight = currentTop.weightKg ?: 0.0
        val currentRepOrDuration = (WorkoutSetAggregates.repOrDuration(currentTop, exerciseType) ?: 0).toDouble()
        val currentSetCount = current.sets.size.toDouble()

        val priorTops = priorSessions.map { WorkoutSetAggregates.topSetByWeight(it.sets, exerciseType) }
        val avgWeight = priorTops.map { it.weightKg ?: 0.0 }.average()
        val avgRepOrDuration = priorTops.map { (WorkoutSetAggregates.repOrDuration(it, exerciseType) ?: 0).toDouble() }.average()
        val avgSetCount = priorSessions.map { it.sets.size.toDouble() }.average()

        var improved = 0
        var worsened = 0
        fun compare(cur: Double, base: Double) {
            val diff = (cur - base) / if (base == 0.0) 1.0 else abs(base)
            if (diff > NEUTRAL_BAND_PCT) improved++ else if (diff < -NEUTRAL_BAND_PCT) worsened++
        }
        compare(currentWeight, avgWeight)
        compare(currentRepOrDuration, avgRepOrDuration)
        compare(currentSetCount, avgSetCount)

        val trend = when {
            improved >= 2 -> ProgressTrend.PROGRESSED
            worsened >= 2 -> ProgressTrend.REGRESSED
            else -> ProgressTrend.NEUTRAL
        }
        val unit = if (exerciseType == ExerciseType.TIMED) "s" else "reps"
        return ProgressResult(
            trend, null, null,
            "%.1fkg x %.0f%s x %.0fsets vs avg %.1fkg x %.1f%s x %.1fsets over last %d session(s)".format(
                currentWeight, currentRepOrDuration, unit, currentSetCount,
                avgWeight, avgRepOrDuration, unit, avgSetCount, priorSessions.size,
            ),
        )
    }
}

object ProgressMetrics {
    fun forGoal(goal: Goal, formula: OneRepMaxFormula, exerciseType: ExerciseType): ProgressMetric = when (goal) {
        Goal.STRENGTH -> OneRepMaxMetric(formula, exerciseType)
        Goal.HYPERTROPHY -> VolumeMetric(exerciseType)
        Goal.ENDURANCE -> EnduranceMetric(exerciseType)
        Goal.AUTOREGULATED -> AutoregulatedMetric(formula, exerciseType)
        Goal.SIMPLE -> SimpleComparisonMetric(exerciseType)
    }

    fun all(formula: OneRepMaxFormula, exerciseType: ExerciseType): List<ProgressMetric> =
        Goal.entries.map { forGoal(it, formula, exerciseType) }
}

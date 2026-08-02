package com.trainingtracker.app.domain.progress

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

/** Goal: Strength — estimated 1RM per session, compared to the rolling-average baseline. */
class OneRepMaxMetric(private val formula: OneRepMaxFormula) : ProgressMetric {
    override val goal = Goal.STRENGTH
    override val displayName = "Estimated 1RM (${formula.name.lowercase().replaceFirstChar { it.uppercase() }})"
    override val tooltip: String
        get() = OneRepMax.description(formula)

    override fun chartScore(log: WorkoutLog) = OneRepMax.estimate(formula, log.weightKg, log.reps)

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentScore = OneRepMax.estimate(formula, current.weightKg, current.reps)
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, currentScore, null, "No prior sessions yet.")
        }
        val baseline = priorSessions.map { OneRepMax.estimate(formula, it.weightKg, it.reps) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        return ProgressResult(
            trend, currentScore, baseline,
            "Est. 1RM %.1fkg vs avg %.1fkg over last %d session(s)".format(currentScore, baseline, priorSessions.size),
        )
    }
}

/** Goal: Hypertrophy — total volume load (sets x reps x weight) per session. */
class VolumeMetric : ProgressMetric {
    override val goal = Goal.HYPERTROPHY
    override val displayName = "Volume Load"
    override val tooltip =
        "Volume load = sets x reps x weight. The total amount of weight moved in the session. " +
            "Tracks work capacity — a good fit for muscle-growth (hypertrophy) goals."

    override fun chartScore(log: WorkoutLog) = log.sets * log.reps * log.weightKg

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentScore = chartScore(current)
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, currentScore, null, "No prior sessions yet.")
        }
        val baseline = priorSessions.map { chartScore(it) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        return ProgressResult(
            trend, currentScore, baseline,
            "Volume %.0fkg vs avg %.0fkg over last %d session(s)".format(currentScore, baseline, priorSessions.size),
        )
    }
}

/** Goal: Muscular Endurance — total reps performed (sets x reps) per session. */
class EnduranceMetric : ProgressMetric {
    override val goal = Goal.ENDURANCE
    override val displayName = "Total Reps (Endurance)"
    override val tooltip =
        "Total reps = sets x reps. Tracks how many total repetitions you can perform. Best for " +
            "muscular-endurance goals where doing more reps matters more than lifting heavier."

    override fun chartScore(log: WorkoutLog) = (log.sets * log.reps).toDouble()

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentScore = chartScore(current)
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, currentScore, null, "No prior sessions yet.")
        }
        val baseline = priorSessions.map { chartScore(it) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        return ProgressResult(
            trend, currentScore, baseline,
            "%.0f total reps vs avg %.1f over last %d session(s)".format(currentScore, baseline, priorSessions.size),
        )
    }
}

/**
 * Goal: Autoregulated / Powerlifting — RPE trend at matched load. If you lift the SAME weight and
 * reps for a lower RPE than before, that's progress (the lift got easier = you got stronger).
 * Falls back to an RPE-adjusted 1RM trend when no matching prior load exists.
 */
class AutoregulatedMetric(private val formula: OneRepMaxFormula) : ProgressMetric {
    override val goal = Goal.AUTOREGULATED
    override val displayName = "RPE Trend"
    override val tooltip =
        "Compares the RPE (perceived effort, 1-10) you logged against previous sessions with the " +
            "SAME weight and reps. A lower RPE for the same weight/reps means the lift felt easier — " +
            "that's progress, even if the weight on the bar didn't change. If no matching prior " +
            "session exists, falls back to comparing estimated 1RM instead."

    /** RPE when available (lower is "better"/easier); falls back to estimated 1RM otherwise. */
    override fun chartScore(log: WorkoutLog) = log.rpe ?: OneRepMax.estimate(formula, log.weightKg, log.reps)

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        val currentRpe = current.rpe
        val matched = priorSessions.filter {
            it.weightKg == current.weightKg && it.reps == current.reps && it.rpe != null
        }

        if (currentRpe != null && matched.isNotEmpty()) {
            val baselineRpe = matched.mapNotNull { it.rpe }.average()
            // Lower RPE at the same load = progress, so the diff is inverted vs. other metrics.
            val trend = trendFromRelativeDiff(baselineRpe, currentRpe)
            return ProgressResult(
                trend, currentRpe, baselineRpe,
                "RPE %.1f vs avg %.1f at the same weight/reps over %d matched session(s)"
                    .format(currentRpe, baselineRpe, matched.size),
            )
        }

        // Fallback: no matched-load history yet, compare estimated 1RM instead.
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, null, null, "No prior sessions yet.")
        }
        val currentScore = OneRepMax.estimate(formula, current.weightKg, current.reps)
        val baseline = priorSessions.map { OneRepMax.estimate(formula, it.weightKg, it.reps) }.average()
        val trend = trendFromRelativeDiff(currentScore, baseline)
        return ProgressResult(
            trend, currentScore, baseline,
            "No matching weight/reps history — compared est. 1RM %.1fkg vs avg %.1fkg instead"
                .format(currentScore, baseline),
        )
    }
}

/** Goal: Simple/beginner — direct field-by-field comparison, no formula. */
class SimpleComparisonMetric : ProgressMetric {
    override val goal = Goal.SIMPLE
    override val displayName = "Simple Comparison"
    override val tooltip =
        "Compares weight, reps, and sets directly against the average of your recent sessions, " +
            "no formula involved. If 2 or more of those 3 fields improved, it's progress. If 2 or " +
            "more got worse, it's a regress. Otherwise it's a mixed/no-change result."

    override fun chartScore(log: WorkoutLog) = log.weightKg

    override fun evaluate(current: WorkoutLog, priorSessions: List<WorkoutLog>): ProgressResult {
        if (priorSessions.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, null, null, "No prior sessions yet.")
        }
        val avgWeight = priorSessions.map { it.weightKg }.average()
        val avgReps = priorSessions.map { it.reps }.average()
        val avgSets = priorSessions.map { it.sets }.average()

        var improved = 0
        var worsened = 0
        fun compare(cur: Double, base: Double) {
            val diff = (cur - base) / if (base == 0.0) 1.0 else abs(base)
            if (diff > NEUTRAL_BAND_PCT) improved++ else if (diff < -NEUTRAL_BAND_PCT) worsened++
        }
        compare(current.weightKg, avgWeight)
        compare(current.reps.toDouble(), avgReps)
        compare(current.sets.toDouble(), avgSets)

        val trend = when {
            improved >= 2 -> ProgressTrend.PROGRESSED
            worsened >= 2 -> ProgressTrend.REGRESSED
            else -> ProgressTrend.NEUTRAL
        }
        return ProgressResult(
            trend, null, null,
            "%.1fkg x %dreps x %dsets vs avg %.1fkg x %.1freps x %.1fsets over last %d session(s)"
                .format(current.weightKg, current.reps, current.sets, avgWeight, avgReps, avgSets, priorSessions.size),
        )
    }
}

object ProgressMetrics {
    fun forGoal(goal: Goal, formula: OneRepMaxFormula): ProgressMetric = when (goal) {
        Goal.STRENGTH -> OneRepMaxMetric(formula)
        Goal.HYPERTROPHY -> VolumeMetric()
        Goal.ENDURANCE -> EnduranceMetric()
        Goal.AUTOREGULATED -> AutoregulatedMetric(formula)
        Goal.SIMPLE -> SimpleComparisonMetric()
    }

    fun all(formula: OneRepMaxFormula): List<ProgressMetric> = Goal.entries.map { forGoal(it, formula) }
}

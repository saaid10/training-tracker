package com.trainingtracker.app.domain.progress

import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.domain.model.Goal
import com.trainingtracker.app.domain.model.OneRepMaxFormula

/**
 * Computes the progress color/result for one exercise, per requirements.txt 3c:
 * - goal is exercise.goalOverride, falling back to the app-wide default goal
 * - compared against a rolling average of that SAME exercise's last N sessions
 *   (N = rollingWindow, from Settings; defaults to 5)
 */
object ProgressCalculator {
    fun effectiveGoal(exercise: Exercise, globalDefaultGoal: Goal): Goal = exercise.goalOverride ?: globalDefaultGoal

    /**
     * @param completedLogsNewestFirst all COMPLETED logs for this exercise, sorted newest first.
     *   The first element is treated as the "current" session; the next [rollingWindow] entries
     *   form the comparison baseline.
     */
    fun evaluate(
        exercise: Exercise,
        completedLogsNewestFirst: List<WorkoutLog>,
        globalDefaultGoal: Goal,
        rollingWindow: Int,
        oneRepMaxFormula: OneRepMaxFormula,
    ): ProgressResult {
        if (completedLogsNewestFirst.isEmpty()) {
            return ProgressResult(ProgressTrend.INSUFFICIENT_DATA, null, null, "No sessions logged yet.")
        }
        val current = completedLogsNewestFirst.first()
        val priorSessions = completedLogsNewestFirst.drop(1).take(rollingWindow)
        val metric = ProgressMetrics.forGoal(effectiveGoal(exercise, globalDefaultGoal), oneRepMaxFormula)
        return metric.evaluate(current, priorSessions)
    }
}

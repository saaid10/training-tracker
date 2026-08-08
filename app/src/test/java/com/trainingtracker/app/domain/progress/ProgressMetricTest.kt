package com.trainingtracker.app.domain.progress

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.LogStatus
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.domain.model.OneRepMaxFormula
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun log(sets: List<WorkoutSet>, loggedAt: Long = 0L) = WorkoutLog(
    id = "id-$loggedAt", exerciseId = "ex", loggedAt = loggedAt, sets = sets,
    status = LogStatus.COMPLETED, sourceLogId = null, notes = null, updatedAt = loggedAt,
)

class ProgressMetricTest {

    @Test
    fun `OneRepMaxMetric reports progress when best-set score rises above baseline`() {
        val metric = OneRepMaxMetric(OneRepMaxFormula.EPLEY, ExerciseType.WEIGHTED)
        val current = log(listOf(WorkoutSet(weightKg = 100.0, reps = 5)))
        val prior = listOf(log(listOf(WorkoutSet(weightKg = 90.0, reps = 5))))
        val result = metric.evaluate(current, prior)
        assertEquals(ProgressTrend.PROGRESSED, result.trend)
    }

    @Test
    fun `OneRepMaxMetric for timed exercises scores best set as weight times duration`() {
        val metric = OneRepMaxMetric(OneRepMaxFormula.EPLEY, ExerciseType.TIMED)
        val current = log(listOf(WorkoutSet(weightKg = 24.0, durationSeconds = 40)))
        assertEquals(960.0, metric.chartScore(current), 0.001)
    }

    @Test
    fun `VolumeMetric sums weight times reps across all sets`() {
        val metric = VolumeMetric(ExerciseType.WEIGHTED)
        val current = log(listOf(WorkoutSet(weightKg = 70.0, reps = 8), WorkoutSet(weightKg = 80.0, reps = 6)))
        assertEquals(70.0 * 8 + 80.0 * 6, metric.chartScore(current), 0.001)
    }

    @Test
    fun `EnduranceMetric for bodyweight exercises sums reps even with no weight`() {
        val metric = EnduranceMetric(ExerciseType.BODYWEIGHT)
        val current = log(listOf(WorkoutSet(reps = 12), WorkoutSet(reps = 10)))
        assertEquals(22.0, metric.chartScore(current), 0.001)
    }

    @Test
    fun `AutoregulatedMetric finds a lower RPE at the same weight and reps as progress`() {
        val metric = AutoregulatedMetric(OneRepMaxFormula.EPLEY, ExerciseType.WEIGHTED)
        val current = log(listOf(WorkoutSet(weightKg = 100.0, reps = 5, rpe = 7.0)))
        val prior = listOf(log(listOf(WorkoutSet(weightKg = 100.0, reps = 5, rpe = 8.5))))
        val result = metric.evaluate(current, prior)
        assertEquals(ProgressTrend.PROGRESSED, result.trend)
    }

    @Test
    fun `AutoregulatedMetric falls back to best-set score when no matching load exists`() {
        val metric = AutoregulatedMetric(OneRepMaxFormula.EPLEY, ExerciseType.WEIGHTED)
        val current = log(listOf(WorkoutSet(weightKg = 100.0, reps = 5, rpe = 7.0)))
        val prior = listOf(log(listOf(WorkoutSet(weightKg = 90.0, reps = 5, rpe = 8.0))))
        val result = metric.evaluate(current, prior)
        assertTrue(result.explanation.contains("No matching load history"))
    }

    @Test
    fun `SimpleComparisonMetric flags progress when 2 of 3 fields improve`() {
        val metric = SimpleComparisonMetric(ExerciseType.WEIGHTED)
        val current = log(listOf(WorkoutSet(weightKg = 100.0, reps = 5), WorkoutSet(weightKg = 90.0, reps = 5)))
        val prior = listOf(
            log(listOf(WorkoutSet(weightKg = 90.0, reps = 5))),
            log(listOf(WorkoutSet(weightKg = 90.0, reps = 5))),
        )
        // current top set (100kg) beats avg top-set weight (90kg), and set count (2) beats avg (1) -> 2/3 improved
        val result = metric.evaluate(current, prior)
        assertEquals(ProgressTrend.PROGRESSED, result.trend)
    }
}

package com.trainingtracker.app.domain.progress

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.domain.model.OneRepMaxFormula
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSetAggregatesTest {

    @Test
    fun `bestSet picks the set with the highest estimated 1RM, not just the heaviest weight`() {
        // Epley: 100kg x 1 = 103.33 est 1RM; 90kg x 5 = 105.0 est 1RM — the lighter set wins.
        val sets = listOf(
            WorkoutSet(weightKg = 100.0, reps = 1),
            WorkoutSet(weightKg = 90.0, reps = 5),
        )
        val best = WorkoutSetAggregates.bestSet(sets, ExerciseType.WEIGHTED, OneRepMaxFormula.EPLEY)
        assertEquals(90.0, best.weightKg)
        assertEquals(5, best.reps)
    }

    @Test
    fun `bestSet for timed exercises picks the longest duration`() {
        val sets = listOf(
            WorkoutSet(weightKg = 20.0, durationSeconds = 30),
            WorkoutSet(weightKg = 20.0, durationSeconds = 45),
        )
        val best = WorkoutSetAggregates.bestSet(sets, ExerciseType.TIMED, OneRepMaxFormula.EPLEY)
        assertEquals(45, best.durationSeconds)
    }

    @Test
    fun `bestSetScore for timed exercises is weight times duration`() {
        val sets = listOf(WorkoutSet(weightKg = 24.0, durationSeconds = 40))
        val score = WorkoutSetAggregates.bestSetScore(sets, ExerciseType.TIMED, OneRepMaxFormula.EPLEY)
        assertEquals(960.0, score, 0.001)
    }

    @Test
    fun `topSetByWeight picks the heaviest weight regardless of estimated 1RM`() {
        val sets = listOf(
            WorkoutSet(weightKg = 100.0, reps = 1),
            WorkoutSet(weightKg = 90.0, reps = 5),
        )
        val top = WorkoutSetAggregates.topSetByWeight(sets, ExerciseType.WEIGHTED)
        assertEquals(100.0, top.weightKg)
    }

    @Test
    fun `totalVolume sums weight times reps across all sets`() {
        val sets = listOf(
            WorkoutSet(weightKg = 70.0, reps = 8),
            WorkoutSet(weightKg = 80.0, reps = 6),
            WorkoutSet(weightKg = 80.0, reps = 6),
        )
        val volume = WorkoutSetAggregates.totalVolume(sets, ExerciseType.WEIGHTED)
        assertEquals(70.0 * 8 + 80.0 * 6 + 80.0 * 6, volume, 0.001)
    }

    @Test
    fun `totalVolume for timed exercises sums weight times duration`() {
        val sets = listOf(WorkoutSet(weightKg = 24.0, durationSeconds = 40), WorkoutSet(weightKg = 24.0, durationSeconds = 35))
        val volume = WorkoutSetAggregates.totalVolume(sets, ExerciseType.TIMED)
        assertEquals(24.0 * 40 + 24.0 * 35, volume, 0.001)
    }

    @Test
    fun `totalEndurance sums reps for weighted and duration for timed`() {
        val weighted = listOf(WorkoutSet(reps = 8), WorkoutSet(reps = 6), WorkoutSet(reps = 6))
        assertEquals(20.0, WorkoutSetAggregates.totalEndurance(weighted, ExerciseType.WEIGHTED), 0.001)

        val timed = listOf(WorkoutSet(durationSeconds = 40), WorkoutSet(durationSeconds = 35))
        assertEquals(75.0, WorkoutSetAggregates.totalEndurance(timed, ExerciseType.TIMED), 0.001)
    }

    @Test
    fun `bodyweight sets with null weight are treated as zero, not excluded`() {
        val sets = listOf(WorkoutSet(weightKg = null, reps = 12))
        assertEquals(0.0, WorkoutSetAggregates.totalVolume(sets, ExerciseType.BODYWEIGHT), 0.001)
        assertEquals(12.0, WorkoutSetAggregates.totalEndurance(sets, ExerciseType.BODYWEIGHT), 0.001)
    }
}

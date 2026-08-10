package com.trainingtracker.app.data.repository

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

class LogRepositoryAdjustmentTest {

    @Test
    fun `applies weight and reps deltas to every set uniformly`() {
        val sets = listOf(WorkoutSet(weightKg = 70.0, reps = 8), WorkoutSet(weightKg = 80.0, reps = 6))
        val result = applyAdjustment(sets, ExerciseType.WEIGHTED, NextSessionAdjustment(weightDeltaKg = 2.5, repsDelta = 1))
        assertEquals(listOf(WorkoutSet(weightKg = 72.5, reps = 9), WorkoutSet(weightKg = 82.5, reps = 7)), result)
    }

    @Test
    fun `applies reps delta to duration instead, for timed exercises`() {
        val sets = listOf(WorkoutSet(weightKg = 20.0, durationSeconds = 30))
        val result = applyAdjustment(sets, ExerciseType.TIMED, NextSessionAdjustment(repsDelta = 5))
        assertEquals(35, result.single().durationSeconds)
    }

    @Test
    fun `positive sets delta duplicates the last set`() {
        val sets = listOf(WorkoutSet(weightKg = 70.0, reps = 8))
        val result = applyAdjustment(sets, ExerciseType.WEIGHTED, NextSessionAdjustment(setsDelta = 2))
        assertEquals(3, result.size)
        assertEquals(WorkoutSet(weightKg = 70.0, reps = 8), result.last())
    }

    @Test
    fun `negative sets delta trims trailing sets but always keeps at least one`() {
        val sets = listOf(WorkoutSet(reps = 8), WorkoutSet(reps = 8), WorkoutSet(reps = 8))
        val result = applyAdjustment(sets, ExerciseType.WEIGHTED, NextSessionAdjustment(setsDelta = -5))
        assertEquals(1, result.size)
    }

    @Test
    fun `null deltas leave sets unchanged`() {
        val sets = listOf(WorkoutSet(weightKg = 70.0, reps = 8, rpe = 7.0))
        val result = applyAdjustment(sets, ExerciseType.WEIGHTED, NextSessionAdjustment())
        assertEquals(sets, result)
    }

    // A corrupted/edge-case empty `sets` list (see WorkoutSetAggregatesTest's empty-list cases)
    // would otherwise crash on `bumped.last()`/`dropLast` — must be a no-op instead.
    @Test
    fun `applyAdjustment on an empty list returns an empty list without crashing`() {
        val result = applyAdjustment(emptyList(), ExerciseType.WEIGHTED, NextSessionAdjustment(setsDelta = 2))
        assertEquals(emptyList<WorkoutSet>(), result)
    }
}

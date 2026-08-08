package com.trainingtracker.app.data.local

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val converters = Converters()

    @Test
    fun `round-trips an empty set list`() {
        val encoded = converters.workoutSetListToString(emptyList())
        assertEquals(emptyList<WorkoutSet>(), converters.stringToWorkoutSetList(encoded))
    }

    @Test
    fun `round-trips a list of sets with mixed weight, reps, duration, rpe`() {
        val sets = listOf(
            WorkoutSet(weightKg = 70.0, reps = 8, durationSeconds = null, rpe = 7.5),
            WorkoutSet(weightKg = 80.0, reps = 6, durationSeconds = null, rpe = null),
            WorkoutSet(weightKg = null, reps = null, durationSeconds = 45, rpe = 8.0),
        )
        val encoded = converters.workoutSetListToString(sets)
        assertEquals(sets, converters.stringToWorkoutSetList(encoded))
    }

    @Test
    fun `blank string decodes to an empty set list`() {
        assertEquals(emptyList<WorkoutSet>(), converters.stringToWorkoutSetList(""))
    }

    @Test
    fun `round-trips every exercise type`() {
        ExerciseType.entries.forEach { type ->
            assertEquals(type, converters.stringToExerciseType(converters.exerciseTypeToString(type)))
        }
    }
}

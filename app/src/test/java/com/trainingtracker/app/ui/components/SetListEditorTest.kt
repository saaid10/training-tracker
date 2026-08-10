package com.trainingtracker.app.ui.components

import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.WorkoutSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SetListEditor.kt's top-level functions are pure, dependency-free Kotlin (no Compose/Room), so
 * per this project's testing convention they get real JUnit4 coverage, same as WorkoutSetAggregates
 * (Task 2) and ProgressMetric (Task 6) — these functions ARE the per-set logging bug-fix's entire
 * validation guarantee (requirements.txt 3n).
 */
class SetListEditorTest {

    // --- validationError: WEIGHTED ---

    @Test
    fun `validationError for WEIGHTED accepts valid weight and reps`() {
        val row = SetRowState(weight = "70.0", reps = "8")
        assertNull(row.validationError(ExerciseType.WEIGHTED))
    }

    @Test
    fun `validationError for WEIGHTED rejects blank weight`() {
        val row = SetRowState(weight = "", reps = "8")
        assertEquals("Enter a valid weight", row.validationError(ExerciseType.WEIGHTED))
    }

    @Test
    fun `validationError for WEIGHTED rejects unparseable weight`() {
        val row = SetRowState(weight = "abc", reps = "8")
        assertEquals("Enter a valid weight or leave it blank", row.validationError(ExerciseType.WEIGHTED))
    }

    @Test
    fun `validationError for WEIGHTED rejects zero reps`() {
        val row = SetRowState(weight = "70.0", reps = "0")
        assertEquals("Reps must be at least 1", row.validationError(ExerciseType.WEIGHTED))
    }

    @Test
    fun `validationError for WEIGHTED rejects negative reps`() {
        val row = SetRowState(weight = "70.0", reps = "-3")
        assertEquals("Reps must be at least 1", row.validationError(ExerciseType.WEIGHTED))
    }

    @Test
    fun `validationError for WEIGHTED rejects negative weight`() {
        val row = SetRowState(weight = "-40", reps = "8")
        assertEquals("Weight can't be negative", row.validationError(ExerciseType.WEIGHTED))
    }

    // --- validationError: BODYWEIGHT ---

    @Test
    fun `validationError for BODYWEIGHT accepts reps with blank optional weight`() {
        val row = SetRowState(weight = "", reps = "12")
        assertNull(row.validationError(ExerciseType.BODYWEIGHT))
    }

    @Test
    fun `validationError for BODYWEIGHT accepts non-negative optional weight`() {
        val row = SetRowState(weight = "5.0", reps = "12")
        assertNull(row.validationError(ExerciseType.BODYWEIGHT))
    }

    @Test
    fun `validationError for BODYWEIGHT rejects zero reps`() {
        val row = SetRowState(weight = "", reps = "0")
        assertEquals("Reps must be at least 1", row.validationError(ExerciseType.BODYWEIGHT))
    }

    @Test
    fun `validationError for BODYWEIGHT rejects negative optional weight`() {
        val row = SetRowState(weight = "-5.0", reps = "12")
        assertEquals("Weight can't be negative", row.validationError(ExerciseType.BODYWEIGHT))
    }

    // --- validationError: TIMED ---

    @Test
    fun `validationError for TIMED accepts duration with blank optional weight`() {
        val row = SetRowState(weight = "", durationSeconds = "45")
        assertNull(row.validationError(ExerciseType.TIMED))
    }

    @Test
    fun `validationError for TIMED rejects zero duration`() {
        val row = SetRowState(weight = "20.0", durationSeconds = "0")
        assertEquals("Duration must be at least 1 second", row.validationError(ExerciseType.TIMED))
    }

    @Test
    fun `validationError for TIMED rejects negative duration`() {
        val row = SetRowState(weight = "20.0", durationSeconds = "-30")
        assertEquals("Duration must be at least 1 second", row.validationError(ExerciseType.TIMED))
    }

    @Test
    fun `validationError for TIMED rejects negative optional weight`() {
        val row = SetRowState(weight = "-20.0", durationSeconds = "30")
        assertEquals("Weight can't be negative", row.validationError(ExerciseType.TIMED))
    }

    @Test
    fun `validationError for TIMED rejects missing duration`() {
        val row = SetRowState(weight = "20.0", durationSeconds = "")
        assertEquals("Duration must be at least 1 second", row.validationError(ExerciseType.TIMED))
    }

    // --- hasErrors ---

    @Test
    fun `hasErrors on an empty list is true`() {
        assertTrue(emptyList<SetRowState>().hasErrors(ExerciseType.WEIGHTED))
    }

    @Test
    fun `hasErrors is false when every row is valid`() {
        val rows = listOf(SetRowState(weight = "70.0", reps = "8"), SetRowState(weight = "80.0", reps = "6"))
        assertFalse(rows.hasErrors(ExerciseType.WEIGHTED))
    }

    @Test
    fun `hasErrors is true when any row is invalid`() {
        val rows = listOf(SetRowState(weight = "70.0", reps = "8"), SetRowState(weight = "70.0", reps = "0"))
        assertTrue(rows.hasErrors(ExerciseType.WEIGHTED))
    }

    // --- toWorkoutSet / toWorkoutSets round-trip ---

    @Test
    fun `toWorkoutSet for TIMED nulls out reps and keeps durationSeconds`() {
        val row = SetRowState(weight = "20.0", reps = "8", durationSeconds = "40", rpe = "7")
        val set = row.toWorkoutSet(ExerciseType.TIMED)
        assertEquals(WorkoutSet(weightKg = 20.0, reps = null, durationSeconds = 40, rpe = 7.0), set)
    }

    @Test
    fun `toWorkoutSet for WEIGHTED nulls out durationSeconds and keeps reps`() {
        val row = SetRowState(weight = "70.0", reps = "8", durationSeconds = "40", rpe = "7")
        val set = row.toWorkoutSet(ExerciseType.WEIGHTED)
        assertEquals(WorkoutSet(weightKg = 70.0, reps = 8, durationSeconds = null, rpe = 7.0), set)
    }

    @Test
    fun `toWorkoutSet for BODYWEIGHT nulls out durationSeconds and keeps reps`() {
        val row = SetRowState(weight = "", reps = "12", durationSeconds = "40")
        val set = row.toWorkoutSet(ExerciseType.BODYWEIGHT)
        assertEquals(WorkoutSet(weightKg = null, reps = 12, durationSeconds = null, rpe = null), set)
    }

    @Test
    fun `toWorkoutSets maps every row, and toRowStates round-trips back`() {
        val sets = listOf(WorkoutSet(weightKg = 70.0, reps = 8), WorkoutSet(weightKg = 80.0, reps = 6))
        val rows = sets.toRowStates()
        val roundTripped = rows.toWorkoutSets(ExerciseType.WEIGHTED)
        assertEquals(sets, roundTripped)
    }

    // --- summaryText ---

    @Test
    fun `summaryText for WEIGHTED formats weight, reps, and RPE`() {
        val sets = listOf(WorkoutSet(weightKg = 70.0, reps = 8, rpe = 8.0))
        assertEquals("70.0kg x 8 reps (RPE 8.0)", sets.summaryText(ExerciseType.WEIGHTED))
    }

    @Test
    fun `summaryText for BODYWEIGHT with null weight shows BW instead of 0kg`() {
        val sets = listOf(WorkoutSet(weightKg = null, reps = 12))
        assertEquals("BW x 12 reps", sets.summaryText(ExerciseType.BODYWEIGHT))
    }

    @Test
    fun `summaryText for TIMED formats weight and duration in seconds`() {
        val sets = listOf(WorkoutSet(weightKg = 20.0, durationSeconds = 45))
        assertEquals("20.0kg x 45s", sets.summaryText(ExerciseType.TIMED))
    }

    @Test
    fun `summaryText joins multiple sets with a middle dot`() {
        val sets = listOf(WorkoutSet(weightKg = 70.0, reps = 8), WorkoutSet(weightKg = 80.0, reps = 6))
        assertEquals("70.0kg x 8 reps · 80.0kg x 6 reps", sets.summaryText(ExerciseType.WEIGHTED))
    }
}

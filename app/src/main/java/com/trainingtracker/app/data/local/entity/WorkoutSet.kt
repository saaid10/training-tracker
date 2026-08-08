package com.trainingtracker.app.data.local.entity

import kotlinx.serialization.Serializable

/**
 * One set within a logged session. Which fields are populated depends on the exercise's
 * ExerciseType: WEIGHTED requires weightKg+reps, BODYWEIGHT requires reps (weightKg optional),
 * TIMED requires durationSeconds (weightKg optional, reps unused). rpe is always optional.
 */
@Serializable
data class WorkoutSet(
    val weightKg: Double? = null,
    val reps: Int? = null,
    val durationSeconds: Int? = null,
    val rpe: Double? = null,
)

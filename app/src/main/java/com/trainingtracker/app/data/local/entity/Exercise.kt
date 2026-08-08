package com.trainingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.trainingtracker.app.domain.model.Goal

enum class ExerciseType { WEIGHTED, BODYWEIGHT, TIMED }

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey val id: String,
    val name: String,
    val categoryId: String,
    /** Null = inherit the app-wide default goal (see AppSettings.globalDefaultGoal). */
    val goalOverride: Goal?,
    /** Determines which fields a logged set exposes/requires — see WorkoutSet. */
    val type: ExerciseType = ExerciseType.WEIGHTED,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

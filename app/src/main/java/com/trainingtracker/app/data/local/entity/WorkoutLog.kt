package com.trainingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class LogStatus { COMPLETED, TBD }

@Entity(tableName = "workout_logs")
data class WorkoutLog(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val loggedAt: Long,
    /** One or more sets, each with independent weight/reps/duration/RPE. Stored as JSON — see Converters. */
    val sets: List<WorkoutSet>,
    val status: LogStatus,
    /** For a TBD entry: the completed log it was suggested from. Null for completed logs. */
    val sourceLogId: String?,
    val notes: String?,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

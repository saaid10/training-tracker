package com.trainingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single body-composition reading (e.g. from a smart scale). Independent of exercise logging.
 * All three measurements are optional per entry so a partial reading (e.g. weight-only) still works.
 */
@Entity(tableName = "body_metric_logs")
data class BodyMetricLog(
    @PrimaryKey val id: String,
    val loggedAt: Long,
    val weightKg: Double?,
    val bodyFatPercent: Double?,
    val muscleMassPercent: Double?,
    val notes: String?,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

package com.trainingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey val id: String,
    val name: String,
    /** 1=Mon .. 7=Sun */
    val daysOfWeek: List<Int>,
    val reminderHour: Int,
    val reminderMinute: Int,
    val exerciseIds: List<String>,
    val enabled: Boolean = true,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

package com.trainingtracker.app.data.remote.dto

import com.trainingtracker.app.data.local.entity.BodyMetricLog
import com.trainingtracker.app.data.local.entity.Category
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.LogStatus
import com.trainingtracker.app.data.local.entity.Routine
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.domain.model.Goal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Postgrest row DTOs — mirror supabase/schema.sql (snake_case columns, epoch-millis timestamps). */

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    @SerialName("is_custom") val isCustom: Boolean,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

fun Category.toDto() = CategoryDto(id, name, isCustom, updatedAt, deleted)
fun CategoryDto.toEntity() = Category(id, name, isCustom, updatedAt, deleted)

@Serializable
data class ExerciseDto(
    val id: String,
    val name: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("goal_override") val goalOverride: String?,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

fun Exercise.toDto() = ExerciseDto(id, name, categoryId, goalOverride?.name, createdAt, updatedAt, deleted)
fun ExerciseDto.toEntity() = Exercise(
    id = id,
    name = name,
    categoryId = categoryId,
    goalOverride = goalOverride?.let { runCatching { Goal.valueOf(it) }.getOrNull() },
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = deleted,
)

@Serializable
data class WorkoutLogDto(
    val id: String,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("logged_at") val loggedAt: Long,
    @SerialName("weight_kg") val weightKg: Double,
    val reps: Int,
    val sets: Int,
    val rpe: Double?,
    val status: String,
    @SerialName("source_log_id") val sourceLogId: String?,
    val notes: String?,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

fun WorkoutLog.toDto() = WorkoutLogDto(
    id, exerciseId, loggedAt, weightKg, reps, sets, rpe, status.name, sourceLogId, notes, updatedAt, deleted,
)
fun WorkoutLogDto.toEntity() = WorkoutLog(
    id, exerciseId, loggedAt, weightKg, reps, sets, rpe,
    runCatching { LogStatus.valueOf(status) }.getOrDefault(LogStatus.COMPLETED),
    sourceLogId, notes, updatedAt, deleted,
)

@Serializable
data class RoutineDto(
    val id: String,
    val name: String,
    @SerialName("days_of_week") val daysOfWeek: String,
    @SerialName("reminder_hour") val reminderHour: Int,
    @SerialName("reminder_minute") val reminderMinute: Int,
    @SerialName("exercise_ids") val exerciseIds: String,
    val enabled: Boolean,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

private fun List<Int>.toCsv() = joinToString(",")
private fun List<String>.toCsvStr() = joinToString(",")
private fun String.toIntListCsv() = if (isBlank()) emptyList() else split(",").map { it.trim().toInt() }
private fun String.toStringListCsv() = if (isBlank()) emptyList() else split(",").map { it.trim() }

fun Routine.toDto() = RoutineDto(
    id, name, daysOfWeek.toCsv(), reminderHour, reminderMinute, exerciseIds.toCsvStr(), enabled, updatedAt, deleted,
)
fun RoutineDto.toEntity() = Routine(
    id, name, daysOfWeek.toIntListCsv(), reminderHour, reminderMinute, exerciseIds.toStringListCsv(),
    enabled, updatedAt, deleted,
)

@Serializable
data class BodyMetricLogDto(
    val id: String,
    @SerialName("logged_at") val loggedAt: Long,
    @SerialName("weight_kg") val weightKg: Double?,
    @SerialName("body_fat_percent") val bodyFatPercent: Double?,
    @SerialName("muscle_mass_percent") val muscleMassPercent: Double?,
    val notes: String?,
    @SerialName("updated_at") val updatedAt: Long,
    val deleted: Boolean,
)

fun BodyMetricLog.toDto() = BodyMetricLogDto(
    id, loggedAt, weightKg, bodyFatPercent, muscleMassPercent, notes, updatedAt, deleted,
)
fun BodyMetricLogDto.toEntity() = BodyMetricLog(
    id, loggedAt, weightKg, bodyFatPercent, muscleMassPercent, notes, updatedAt, deleted,
)

@Serializable
data class AppSettingsDto(
    val id: String = "singleton",
    @SerialName("global_default_goal") val globalDefaultGoal: String,
    @SerialName("rolling_average_window") val rollingAverageWindow: Int,
    @SerialName("one_rep_max_formula") val oneRepMaxFormula: String,
    @SerialName("updated_at") val updatedAt: Long,
)

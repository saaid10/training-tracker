package com.trainingtracker.app.data.local

import androidx.room.TypeConverter
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.LogStatus
import com.trainingtracker.app.data.local.entity.WorkoutSet
import com.trainingtracker.app.domain.model.Goal
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun goalToString(goal: Goal?): String? = goal?.name

    @TypeConverter
    fun stringToGoal(value: String?): Goal? = value?.let { Goal.valueOf(it) }

    @TypeConverter
    fun logStatusToString(status: LogStatus): String = status.name

    @TypeConverter
    fun stringToLogStatus(value: String): LogStatus = LogStatus.valueOf(value)

    @TypeConverter
    fun intListToString(list: List<Int>): String = list.joinToString(",")

    @TypeConverter
    fun stringToIntList(value: String): List<Int> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.trim().toInt() }

    @TypeConverter
    fun stringListToString(list: List<String>): String = list.joinToString(",")

    @TypeConverter
    fun stringToStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(",").map { it.trim() }

    @TypeConverter
    fun exerciseTypeToString(type: ExerciseType): String = type.name

    @TypeConverter
    fun stringToExerciseType(value: String): ExerciseType = ExerciseType.valueOf(value)

    @TypeConverter
    fun workoutSetListToString(list: List<WorkoutSet>): String = Json.encodeToString(list)

    @TypeConverter
    fun stringToWorkoutSetList(value: String): List<WorkoutSet> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(value)
}

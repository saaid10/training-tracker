package com.trainingtracker.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.trainingtracker.app.data.local.dao.BodyMetricLogDao
import com.trainingtracker.app.data.local.dao.CategoryDao
import com.trainingtracker.app.data.local.dao.ExerciseDao
import com.trainingtracker.app.data.local.dao.RoutineDao
import com.trainingtracker.app.data.local.dao.WorkoutLogDao
import com.trainingtracker.app.data.local.entity.BodyMetricLog
import com.trainingtracker.app.data.local.entity.Category
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.data.local.entity.Routine
import com.trainingtracker.app.data.local.entity.WorkoutLog

@Database(
    entities = [Category::class, Exercise::class, WorkoutLog::class, Routine::class, BodyMetricLog::class],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutLogDao(): WorkoutLogDao
    abstract fun routineDao(): RoutineDao
    abstract fun bodyMetricLogDao(): BodyMetricLogDao
}

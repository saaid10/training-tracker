package com.trainingtracker.app

import android.content.Context
import androidx.room.Room
import com.trainingtracker.app.data.local.AppDatabase
import com.trainingtracker.app.data.remote.SyncRepository
import com.trainingtracker.app.data.repository.BodyMetricsRepository
import com.trainingtracker.app.data.repository.CategoryRepository
import com.trainingtracker.app.data.repository.ExerciseRepository
import com.trainingtracker.app.data.repository.LogRepository
import com.trainingtracker.app.data.repository.RoutineRepository
import com.trainingtracker.app.data.settings.SettingsRepository

/** Manual dependency container — single instance held by [TrainingTrackerApp]. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "training-tracker.db")
        // Pre-release app, no shipped installs to migrate yet — wipe local data on schema bumps
        // rather than maintaining migrations for a personal single-user app.
        .fallbackToDestructiveMigration()
        .build()

    val settingsRepository = SettingsRepository(context)
    val categoryRepository = CategoryRepository(database.categoryDao())
    val exerciseRepository = ExerciseRepository(database.exerciseDao())
    val logRepository = LogRepository(database.workoutLogDao(), database.exerciseDao(), settingsRepository)
    val routineRepository = RoutineRepository(database.routineDao())
    val bodyMetricsRepository = BodyMetricsRepository(database.bodyMetricLogDao())
    val syncRepository = SyncRepository(database, settingsRepository)
}

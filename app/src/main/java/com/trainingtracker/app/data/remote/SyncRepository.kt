package com.trainingtracker.app.data.remote

import com.trainingtracker.app.data.local.AppDatabase
import com.trainingtracker.app.data.remote.dto.toDto
import com.trainingtracker.app.data.remote.dto.toEntity
import com.trainingtracker.app.data.remote.dto.BodyMetricLogDto
import com.trainingtracker.app.data.remote.dto.CategoryDto
import com.trainingtracker.app.data.remote.dto.ExerciseDto
import com.trainingtracker.app.data.remote.dto.RoutineDto
import com.trainingtracker.app.data.remote.dto.WorkoutLogDto
import com.trainingtracker.app.data.settings.SettingsRepository
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.first

/**
 * Backup-only sync (requirements.txt 3i): Room is always the source of truth. This pushes local
 * changes up to Supabase as a safety copy, and can pull everything back down once to restore
 * after a reinstall/new phone. No multi-device conflict resolution — last write wins.
 *
 * If Supabase isn't configured (no local.properties credentials), every function here is a no-op
 * so the app keeps working fully offline, per the offline-first requirement.
 */
class SyncRepository(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
) {
    /** Pushes everything changed since the last successful sync. Called by SyncWorker. */
    suspend fun pushChanges() {
        val client = SupabaseClientProvider.client ?: return
        val since = settingsRepository.lastSyncAt.first() ?: 0L

        database.categoryDao().changedSince(since).forEach { client.from("categories").upsert(it.toDto()) }
        database.exerciseDao().changedSince(since).forEach { client.from("exercises").upsert(it.toDto()) }
        database.workoutLogDao().changedSince(since).forEach { client.from("workout_logs").upsert(it.toDto()) }
        database.routineDao().changedSince(since).forEach { client.from("routines").upsert(it.toDto()) }
        database.bodyMetricLogDao().changedSince(since).forEach { client.from("body_metric_logs").upsert(it.toDto()) }

        settingsRepository.setLastSyncAt(System.currentTimeMillis())
    }

    /** One-time restore for a fresh install: pulls every remote row down into the empty local DB. */
    suspend fun pullAndRestore() {
        val client = SupabaseClientProvider.client ?: return

        client.from("categories").select().decodeList<CategoryDto>().forEach {
            database.categoryDao().upsert(it.toEntity())
        }
        client.from("exercises").select().decodeList<ExerciseDto>().forEach {
            database.exerciseDao().upsert(it.toEntity())
        }
        client.from("workout_logs").select().decodeList<WorkoutLogDto>().forEach {
            database.workoutLogDao().upsert(it.toEntity())
        }
        client.from("routines").select().decodeList<RoutineDto>().forEach {
            database.routineDao().upsert(it.toEntity())
        }
        client.from("body_metric_logs").select().decodeList<BodyMetricLogDto>().forEach {
            database.bodyMetricLogDao().upsert(it.toEntity())
        }

        settingsRepository.setLastSyncAt(System.currentTimeMillis())
    }
}

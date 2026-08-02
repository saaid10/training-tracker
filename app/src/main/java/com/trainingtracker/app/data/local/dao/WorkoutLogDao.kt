package com.trainingtracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trainingtracker.app.data.local.entity.LogStatus
import com.trainingtracker.app.data.local.entity.WorkoutLog
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutLogDao {
    @Query(
        "SELECT * FROM workout_logs WHERE exerciseId = :exerciseId AND status = 'COMPLETED' " +
            "AND deleted = 0 ORDER BY loggedAt DESC"
    )
    fun observeCompletedForExercise(exerciseId: String): Flow<List<WorkoutLog>>

    /** Most recent N completed sessions for one exercise, newest first — used for the rolling average. */
    @Query(
        "SELECT * FROM workout_logs WHERE exerciseId = :exerciseId AND status = 'COMPLETED' " +
            "AND deleted = 0 ORDER BY loggedAt DESC LIMIT :limit"
    )
    suspend fun recentCompletedForExercise(exerciseId: String, limit: Int): List<WorkoutLog>

    @Query("SELECT * FROM workout_logs WHERE status = 'TBD' AND deleted = 0 ORDER BY loggedAt ASC")
    fun observePending(): Flow<List<WorkoutLog>>

    @Query("SELECT * FROM workout_logs WHERE id = :id")
    suspend fun getById(id: String): WorkoutLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: WorkoutLog)

    @Update
    suspend fun update(log: WorkoutLog)

    @Query("UPDATE workout_logs SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM workout_logs WHERE updatedAt > :since")
    suspend fun changedSince(since: Long): List<WorkoutLog>
}

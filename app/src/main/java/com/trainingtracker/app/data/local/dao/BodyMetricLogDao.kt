package com.trainingtracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trainingtracker.app.data.local.entity.BodyMetricLog
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyMetricLogDao {
    @Query("SELECT * FROM body_metric_logs WHERE deleted = 0 ORDER BY loggedAt DESC")
    fun observeAll(): Flow<List<BodyMetricLog>>

    @Query("SELECT * FROM body_metric_logs WHERE id = :id")
    suspend fun getById(id: String): BodyMetricLog?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: BodyMetricLog)

    @Update
    suspend fun update(log: BodyMetricLog)

    @Query("UPDATE body_metric_logs SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM body_metric_logs WHERE updatedAt > :since")
    suspend fun changedSince(since: Long): List<BodyMetricLog>
}

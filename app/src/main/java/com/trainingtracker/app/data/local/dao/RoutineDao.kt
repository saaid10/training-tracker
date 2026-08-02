package com.trainingtracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.trainingtracker.app.data.local.entity.Routine
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines WHERE deleted = 0 ORDER BY name ASC")
    fun observeAll(): Flow<List<Routine>>

    @Query("SELECT * FROM routines WHERE deleted = 0 AND enabled = 1")
    suspend fun getAllEnabled(): List<Routine>

    @Query("SELECT * FROM routines WHERE id = :id")
    suspend fun getById(id: String): Routine?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(routine: Routine)

    @Update
    suspend fun update(routine: Routine)

    @Query("UPDATE routines SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT * FROM routines WHERE updatedAt > :since")
    suspend fun changedSince(since: Long): List<Routine>
}

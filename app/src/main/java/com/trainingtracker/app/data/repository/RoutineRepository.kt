package com.trainingtracker.app.data.repository

import com.trainingtracker.app.data.local.dao.RoutineDao
import com.trainingtracker.app.data.local.entity.Routine
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Routines drive the fixed-schedule reminders (requirements.txt 3d). */
class RoutineRepository(private val dao: RoutineDao) {
    fun observeAll(): Flow<List<Routine>> = dao.observeAll()

    suspend fun getAllEnabled(): List<Routine> = dao.getAllEnabled()

    suspend fun getById(id: String): Routine? = dao.getById(id)

    suspend fun create(
        name: String,
        daysOfWeek: List<Int>,
        reminderHour: Int,
        reminderMinute: Int,
        exerciseIds: List<String>,
    ): Routine {
        val routine = Routine(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            daysOfWeek = daysOfWeek,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute,
            exerciseIds = exerciseIds,
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(routine)
        return routine
    }

    suspend fun update(routine: Routine) {
        dao.update(routine.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }
}

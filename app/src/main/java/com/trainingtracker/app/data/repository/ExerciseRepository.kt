package com.trainingtracker.app.data.repository

import com.trainingtracker.app.data.local.dao.ExerciseDao
import com.trainingtracker.app.data.local.entity.Exercise
import com.trainingtracker.app.domain.model.Goal
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Exercises are created once and reused from a searchable list when logging (requirements.txt 3a). */
class ExerciseRepository(private val dao: ExerciseDao) {
    fun observeAll(): Flow<List<Exercise>> = dao.observeAll()

    fun observeById(id: String): Flow<Exercise?> = dao.observeById(id)

    suspend fun getById(id: String): Exercise? = dao.getById(id)

    suspend fun create(name: String, categoryId: String, goalOverride: Goal?): Exercise {
        val now = System.currentTimeMillis()
        val exercise = Exercise(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            categoryId = categoryId,
            goalOverride = goalOverride,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(exercise)
        return exercise
    }

    suspend fun update(exercise: Exercise) {
        dao.update(exercise.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun delete(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }
}

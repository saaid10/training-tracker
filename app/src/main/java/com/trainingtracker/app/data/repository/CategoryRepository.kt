package com.trainingtracker.app.data.repository

import com.trainingtracker.app.data.local.dao.CategoryDao
import com.trainingtracker.app.data.local.entity.Category
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class CategoryRepository(private val dao: CategoryDao) {
    fun observeAll(): Flow<List<Category>> = dao.observeAll()

    /** Seeds the predefined categories on first launch (requirements.txt 3e). No-op if already seeded. */
    suspend fun seedDefaultsIfEmpty() {
        if (dao.getAll().isNotEmpty()) return
        val now = System.currentTimeMillis()
        Category.DEFAULTS.forEach { name ->
            dao.upsert(Category(id = UUID.randomUUID().toString(), name = name, isCustom = false, updatedAt = now))
        }
    }

    /**
     * User-added custom category (requirements.txt 3e). Reuses an existing category with the
     * same name (case-insensitive) instead of creating a duplicate.
     */
    suspend fun addCustom(name: String): Category {
        val trimmed = name.trim()
        dao.getAll().firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }

        val category = Category(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            isCustom = true,
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(category)
        return category
    }
}

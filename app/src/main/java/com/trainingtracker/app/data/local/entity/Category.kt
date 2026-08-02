package com.trainingtracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val id: String,
    val name: String,
    val isCustom: Boolean,
    val updatedAt: Long,
    val deleted: Boolean = false,
) {
    companion object {
        val DEFAULTS = listOf("Chest", "Back", "Legs", "Shoulders", "Arms", "Core", "Cardio")
    }
}

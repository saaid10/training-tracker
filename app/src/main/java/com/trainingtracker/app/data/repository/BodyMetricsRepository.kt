package com.trainingtracker.app.data.repository

import com.trainingtracker.app.data.local.dao.BodyMetricLogDao
import com.trainingtracker.app.data.local.entity.BodyMetricLog
import com.trainingtracker.app.domain.bodymetrics.BodyMetricsCalculator
import com.trainingtracker.app.domain.bodymetrics.BodyMetricsWeeklyTrend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** requirements.txt 3m: weight/body fat %/muscle mass % logging, independent of exercise logging. */
class BodyMetricsRepository(private val dao: BodyMetricLogDao) {
    fun observeAll(): Flow<List<BodyMetricLog>> = dao.observeAll()

    fun observeWeeklyTrend(): Flow<BodyMetricsWeeklyTrend> =
        dao.observeAll().map { BodyMetricsCalculator.evaluate(it) }

    /** Defaults to right now, but can be backdated (consistent with requirements.txt 3b). */
    suspend fun logEntry(
        weightKg: Double?,
        bodyFatPercent: Double?,
        muscleMassPercent: Double?,
        notes: String?,
        loggedAt: Long = System.currentTimeMillis(),
    ): BodyMetricLog {
        val now = System.currentTimeMillis()
        val entry = BodyMetricLog(
            id = UUID.randomUUID().toString(),
            loggedAt = loggedAt,
            weightKg = weightKg,
            bodyFatPercent = bodyFatPercent,
            muscleMassPercent = muscleMassPercent,
            notes = notes,
            updatedAt = now,
        )
        dao.upsert(entry)
        return entry
    }

    suspend fun updateEntry(
        id: String,
        weightKg: Double?,
        bodyFatPercent: Double?,
        muscleMassPercent: Double?,
        notes: String?,
        loggedAt: Long,
    ) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                loggedAt = loggedAt,
                weightKg = weightKg,
                bodyFatPercent = bodyFatPercent,
                muscleMassPercent = muscleMassPercent,
                notes = notes,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun deleteEntry(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }
}

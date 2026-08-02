package com.trainingtracker.app.data.repository

import com.trainingtracker.app.data.local.dao.ExerciseDao
import com.trainingtracker.app.data.local.dao.WorkoutLogDao
import com.trainingtracker.app.data.local.entity.LogStatus
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.data.settings.SettingsRepository
import com.trainingtracker.app.domain.progress.ProgressCalculator
import com.trainingtracker.app.domain.progress.ProgressResult
import com.trainingtracker.app.domain.progress.ProgressTrend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.util.UUID

/**
 * Increments requested when confirming a completed log, used to auto-generate the next TBD
 * session (requirements.txt 3b). Any field left null means "keep the same as this session".
 */
data class NextSessionAdjustment(
    val weightDeltaKg: Double? = null,
    val repsDelta: Int? = null,
    val setsDelta: Int? = null,
    val rpeDelta: Double? = null,
)

class LogRepository(
    private val logDao: WorkoutLogDao,
    private val exerciseDao: ExerciseDao,
    private val settingsRepository: SettingsRepository,
) {
    fun observeCompletedForExercise(exerciseId: String): Flow<List<WorkoutLog>> =
        logDao.observeCompletedForExercise(exerciseId)

    fun observePending(): Flow<List<WorkoutLog>> = logDao.observePending()

    suspend fun getById(id: String): WorkoutLog? = logDao.getById(id)

    /**
     * Logs a completed session. If [nextSession] is non-null, also creates a TBD entry for the
     * next workout with the requested increments already applied — the user just has to confirm
     * it later from the Pending screen instead of re-entering everything (requirements.txt 3b).
     */
    suspend fun logCompleted(
        exerciseId: String,
        weightKg: Double,
        reps: Int,
        sets: Int,
        rpe: Double?,
        notes: String?,
        nextSession: NextSessionAdjustment? = null,
        /** Defaults to right now, but can be backdated (e.g. logging yesterday's forgotten session). */
        loggedAt: Long = System.currentTimeMillis(),
    ): WorkoutLog {
        val now = System.currentTimeMillis()
        val completed = WorkoutLog(
            id = UUID.randomUUID().toString(),
            exerciseId = exerciseId,
            loggedAt = loggedAt,
            weightKg = weightKg,
            reps = reps,
            sets = sets,
            rpe = rpe,
            status = LogStatus.COMPLETED,
            sourceLogId = null,
            notes = notes,
            updatedAt = now,
        )
        logDao.upsert(completed)

        if (nextSession != null) {
            val tbd = WorkoutLog(
                id = UUID.randomUUID().toString(),
                exerciseId = exerciseId,
                loggedAt = loggedAt, // placeholder until the routine's actual scheduled date/confirmation
                weightKg = weightKg + (nextSession.weightDeltaKg ?: 0.0),
                reps = reps + (nextSession.repsDelta ?: 0),
                sets = sets + (nextSession.setsDelta ?: 0),
                rpe = rpe?.plus(nextSession.rpeDelta ?: 0.0),
                status = LogStatus.TBD,
                sourceLogId = completed.id,
                notes = null,
                updatedAt = now,
            )
            logDao.upsert(tbd)
        }
        return completed
    }

    /**
     * Confirms a TBD entry from the Pending screen (requirements.txt 3g): the user reviews/edits
     * the auto-suggested numbers, then it becomes a real COMPLETED log for today.
     */
    suspend fun confirmPending(
        id: String,
        weightKg: Double,
        reps: Int,
        sets: Int,
        rpe: Double?,
        notes: String?,
    ) {
        val existing = logDao.getById(id) ?: return
        logDao.update(
            existing.copy(
                loggedAt = System.currentTimeMillis(),
                weightKg = weightKg,
                reps = reps,
                sets = sets,
                rpe = rpe,
                status = LogStatus.COMPLETED,
                notes = notes,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Discards a suggested TBD session the user doesn't want to keep. */
    suspend fun discardPending(id: String) {
        logDao.softDelete(id, System.currentTimeMillis())
    }

    /** Corrects a mistake in an already-completed log (e.g. wrong weight/reps entered). */
    suspend fun updateCompleted(
        id: String,
        weightKg: Double,
        reps: Int,
        sets: Int,
        rpe: Double?,
        notes: String?,
        loggedAt: Long,
    ) {
        val existing = logDao.getById(id) ?: return
        logDao.update(
            existing.copy(
                loggedAt = loggedAt,
                weightKg = weightKg,
                reps = reps,
                sets = sets,
                rpe = rpe,
                notes = notes,
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Progress result for one exercise, per requirements.txt 3c (goal-based, 5-session rolling average). */
    fun observeProgress(exerciseId: String): Flow<ProgressResult> {
        val exerciseFlow = exerciseDao.observeById(exerciseId)
        val logsFlow = logDao.observeCompletedForExercise(exerciseId)
        return combine(
            exerciseFlow,
            logsFlow,
            settingsRepository.globalDefaultGoal,
            settingsRepository.rollingAverageWindow,
            settingsRepository.oneRepMaxFormula,
        ) { exercise, logs, globalGoal, window, formula ->
            if (exercise == null) {
                ProgressResult(ProgressTrend.INSUFFICIENT_DATA, null, null, "Exercise not found.")
            } else {
                ProgressCalculator.evaluate(exercise, logs, globalGoal, window, formula)
            }
        }
    }
}

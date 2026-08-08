package com.trainingtracker.app.data.repository

import com.trainingtracker.app.data.local.dao.ExerciseDao
import com.trainingtracker.app.data.local.dao.WorkoutLogDao
import com.trainingtracker.app.data.local.entity.ExerciseType
import com.trainingtracker.app.data.local.entity.LogStatus
import com.trainingtracker.app.data.local.entity.WorkoutLog
import com.trainingtracker.app.data.local.entity.WorkoutSet
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
 * Applied uniformly to every set in the generated TBD entry.
 */
data class NextSessionAdjustment(
    val weightDeltaKg: Double? = null,
    /** Also serves as the duration-delta (seconds) for TIMED exercises' sets. */
    val repsDelta: Int? = null,
    val setsDelta: Int? = null,
    val rpeDelta: Double? = null,
)

/**
 * Applies [adjustment] to every set, then adds (duplicating the last set) or trims trailing sets
 * per [NextSessionAdjustment.setsDelta]. Always leaves at least one set. Pure and unit-tested
 * independently of the DAOs this repository otherwise depends on.
 */
internal fun applyAdjustment(
    sets: List<WorkoutSet>,
    exerciseType: ExerciseType,
    adjustment: NextSessionAdjustment,
): List<WorkoutSet> {
    val bumped = sets.map { set ->
        set.copy(
            weightKg = set.weightKg?.let { it + (adjustment.weightDeltaKg ?: 0.0) },
            reps = if (exerciseType == ExerciseType.TIMED) set.reps else set.reps?.let { it + (adjustment.repsDelta ?: 0) },
            durationSeconds = if (exerciseType == ExerciseType.TIMED) {
                set.durationSeconds?.let { it + (adjustment.repsDelta ?: 0) }
            } else {
                set.durationSeconds
            },
            rpe = set.rpe?.let { it + (adjustment.rpeDelta ?: 0.0) },
        )
    }
    val delta = adjustment.setsDelta ?: 0
    return when {
        delta > 0 -> bumped + List(delta) { bumped.last() }
        delta < 0 -> bumped.dropLast(minOf(-delta, bumped.size - 1))
        else -> bumped
    }
}

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
        exerciseType: ExerciseType,
        sets: List<WorkoutSet>,
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
            sets = sets,
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
                sets = applyAdjustment(sets, exerciseType, nextSession),
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
    suspend fun confirmPending(id: String, sets: List<WorkoutSet>, notes: String?) {
        val existing = logDao.getById(id) ?: return
        logDao.update(
            existing.copy(
                loggedAt = System.currentTimeMillis(),
                sets = sets,
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
    suspend fun updateCompleted(id: String, sets: List<WorkoutSet>, notes: String?, loggedAt: Long) {
        val existing = logDao.getById(id) ?: return
        logDao.update(
            existing.copy(
                loggedAt = loggedAt,
                sets = sets,
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

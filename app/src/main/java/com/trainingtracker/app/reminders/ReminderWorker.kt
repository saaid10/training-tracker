package com.trainingtracker.app.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.trainingtracker.app.TrainingTrackerApp
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Fires roughly once a day (approximate timing is fine — requirements.txt 3j, no exact-alarm
 * permission needed) for a single Routine, and only actually shows a notification if today
 * matches one of the routine's scheduled days.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val routineId = inputData.getString(KEY_ROUTINE_ID) ?: return Result.failure()
        val container = (applicationContext as TrainingTrackerApp).container
        val routine = container.routineRepository.getById(routineId)

        if (routine != null && routine.enabled) {
            val todayIso = LocalDate.now().dayOfWeek.let { isoDayOfWeekToRoutineDay(it) }
            if (todayIso in routine.daysOfWeek) {
                NotificationHelper.showReminder(applicationContext, routine.id, routine.name)
            }
        }
        return Result.success()
    }

    companion object {
        const val KEY_ROUTINE_ID = "routine_id"

        /** Routine.daysOfWeek uses 1=Mon..7=Sun, matching DayOfWeek.getValue(). */
        private fun isoDayOfWeekToRoutineDay(day: DayOfWeek): Int = day.value
    }
}

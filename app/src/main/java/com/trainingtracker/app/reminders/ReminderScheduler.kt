package com.trainingtracker.app.reminders

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.trainingtracker.app.data.local.entity.Routine
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Schedules the fixed-schedule reminders (requirements.txt 3d/3j). One daily-repeating
 * WorkManager job per routine; the job itself checks day-of-week and no-ops on days the routine
 * isn't scheduled. Approximate timing is acceptable, so no exact-alarm permission is needed.
 */
object ReminderScheduler {
    private fun workName(routineId: String) = "reminder_$routineId"

    fun scheduleRoutine(context: Context, routine: Routine) {
        val workManager = WorkManager.getInstance(context)
        if (!routine.enabled) {
            cancelRoutine(context, routine.id)
            return
        }

        val initialDelayMillis = millisUntilNext(routine.reminderHour, routine.reminderMinute)
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(ReminderWorker.KEY_ROUTINE_ID, routine.id).build())
            .build()

        workManager.enqueueUniquePeriodicWork(
            workName(routine.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancelRoutine(context: Context, routineId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(routineId))
    }

    fun rescheduleAll(context: Context, routines: List<Routine>) {
        routines.forEach { routine ->
            if (routine.enabled) scheduleRoutine(context, routine) else cancelRoutine(context, routine.id)
        }
    }

    private fun millisUntilNext(hour: Int, minute: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var target = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return java.time.Duration.between(now, target).toMillis()
    }
}

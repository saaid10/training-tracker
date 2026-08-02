package com.trainingtracker.app.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.trainingtracker.app.TrainingTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** WorkManager persists periodic work across reboots on its own, but delays can drift after a
 * long-off phone; this realigns each routine's next fire time to its configured hour/minute. */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val container = (context.applicationContext as TrainingTrackerApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val routines = container.routineRepository.getAllEnabled()
                ReminderScheduler.rescheduleAll(context.applicationContext, routines)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

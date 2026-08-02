package com.trainingtracker.app

import android.app.Application
import com.trainingtracker.app.data.remote.SyncWorker
import com.trainingtracker.app.reminders.NotificationHelper
import com.trainingtracker.app.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TrainingTrackerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.ensureChannel(this)
        SyncWorker.schedulePeriodic(this)

        CoroutineScope(Dispatchers.IO).launch {
            container.categoryRepository.seedDefaultsIfEmpty()
            val routines = container.routineRepository.getAllEnabled()
            ReminderScheduler.rescheduleAll(this@TrainingTrackerApp, routines)
        }
    }
}

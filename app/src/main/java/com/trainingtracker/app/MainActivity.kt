package com.trainingtracker.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.trainingtracker.app.ui.ViewModelFactory
import com.trainingtracker.app.ui.navigation.TrainingTrackerNavGraph
import com.trainingtracker.app.ui.theme.TrainingTrackerTheme

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val container = (application as TrainingTrackerApp).container
        val factory = ViewModelFactory(container)

        setContent {
            TrainingTrackerTheme {
                TrainingTrackerNavGraph(factory)
            }
        }
    }
}

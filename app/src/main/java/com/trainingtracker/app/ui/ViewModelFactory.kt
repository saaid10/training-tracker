package com.trainingtracker.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.trainingtracker.app.AppContainer

/** Manual DI: every ViewModel takes an [AppContainer] constructor arg, wired through here. */
class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return modelClass.getConstructor(AppContainer::class.java).newInstance(container)
    }
}

package com.swordfish.lemuroid.app.tv.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class MainTVViewModel(appContext: Context) : ViewModel() {
    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainTVViewModel(appContext) as T
        }
    }

    // Only used to know if the app is busy (no progress indicator on screen): it is collected from the start.
    val inProgress =
        PendingOperationsMonitor(appContext)
            .anyOperationInProgress()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    // Same as the "Syncing games" banner (see SaveSyncBanner).
    val saveSyncBannerShown =
        PendingOperationsMonitor(appContext)
            .saveSyncRunning()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )
}

package com.swordfish.lemuroid.app.shared.savesync

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.lib.library.findByName
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Whether a sync of the saves has something to do: the sync buttons of the top bar of the phone and of the TV are only
 * shown then, and "Manual sync" in the settings is only enabled then. Checked while the main screen is open: when it
 * opens or comes back, every [CHECK_INTERVAL_MS] (for the changes made on other devices) and when a sync ends. While a
 * sync is running or waiting to start (e.g. the automatic one when the app opens) there is nothing to do: it takes
 * care of the changes, and they are checked again when it ends.
 */
object SaveSyncChanges {
    private val pending = MutableStateFlow(false)
    val hasChanges: StateFlow<Boolean> = pending.asStateFlow()

    private val checkLock = Mutex()

    suspend fun check(
        appContext: Context,
        saveSyncManager: SaveSyncManager,
        settingsManager: SettingsManager,
    ) {
        // A check already running gives the same answer.
        if (!checkLock.tryLock()) return
        try {
            pending.value =
                withContext(Dispatchers.IO) {
                    runCatching {
                        !PendingOperationsMonitor(appContext).anySaveOperationInProgress().first() &&
                            saveSyncManager.isSupported() &&
                            saveSyncManager.isConfigured() &&
                            settingsManager.syncSaves() &&
                            saveSyncManager.hasChanges(
                                settingsManager.syncStatesCores().mapNotNull { findByName(it) }.toSet(),
                            )
                    }.onFailure { Timber.e(it, "Error while checking the save sync") }
                        .getOrDefault(false)
                }
        } finally {
            checkLock.unlock()
        }
    }

    /** Keeps [hasChanges] up to date while the [owner] (the main screen) is on screen. */
    fun keepChecking(
        owner: LifecycleOwner,
        appContext: Context,
        saveSyncManager: SaveSyncManager,
        settingsManager: SettingsManager,
    ) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    while (true) {
                        check(appContext, saveSyncManager, settingsManager)
                        delay(CHECK_INTERVAL_MS)
                    }
                }
                launch {
                    var wasInProgress = false
                    PendingOperationsMonitor(appContext).anySaveOperationInProgress().collect { inProgress ->
                        if (wasInProgress && !inProgress) check(appContext, saveSyncManager, settingsManager)
                        wasInProgress = inProgress
                    }
                }
            }
        }
    }

    private const val CHECK_INTERVAL_MS = 60_000L
}

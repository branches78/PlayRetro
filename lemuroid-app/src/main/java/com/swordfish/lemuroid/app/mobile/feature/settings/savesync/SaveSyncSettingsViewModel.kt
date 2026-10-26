package com.swordfish.lemuroid.app.mobile.feature.settings.savesync

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SaveSyncSettingsViewModel(
    private val application: Application,
    private val saveSyncManager: SaveSyncManager,
) : ViewModel() {
    class Factory(
        private val application: Application,
        private val saveSyncManager: SaveSyncManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SaveSyncSettingsViewModel(application, saveSyncManager) as T
        }
    }

    val saveSyncInProgress = PendingOperationsMonitor(getContext()).anySaveOperationInProgress()

    data class State(
        val isConfigured: Boolean = false,
        val configInfo: String = "",
        val savesSpace: String = "",
        val lastSyncInfo: String = "",
        val coreNames: List<String> = emptyList(),
        val coreVisibleNames: List<String> = emptyList(),
        val provider: String = "",
        val settingsActivity: Class<out Activity>? = null,
        val hasProviderChoice: Boolean = false,
    )

    private val state = MutableStateFlow(State())
    val uiState: StateFlow<State> = state.asStateFlow()

    init {
        refresh()
        // When a sync ends, show its date and what changed (e.g. the space of the saves).
        viewModelScope.launch {
            var wasInProgress = false
            saveSyncInProgress.collect { inProgress ->
                if (wasInProgress && !inProgress) refresh()
                wasInProgress = inProgress
            }
        }
    }

    /** Read again, e.g. after choosing where to sync (Google Drive or the network share). */
    fun refresh() {
        viewModelScope.launch { state.value = withContext(Dispatchers.IO) { buildState() } }
    }

    private fun buildState(): State {
        val cores = computeCores()
        return State(
            saveSyncManager.isConfigured(),
            saveSyncManager.getConfigInfo(),
            saveSyncManager.computeSavesSpace(),
            saveSyncManager.getLastSyncInfo(),
            cores.map { it.first },
            cores.map { it.second },
            saveSyncManager.getProvider(),
            saveSyncManager.getSettingsActivity(),
            saveSyncManager.hasProviderChoice(),
        )
    }

    /** Name and visible name of each core, in alphabetical order of the visible name. */
    private fun computeCores(): List<Pair<String, String>> {
        val context = getContext()
        return CoreID.values()
            .map { it.coreName to saveSyncManager.getDisplayNameForCore(context, it) }
            .sortedBy { it.second.lowercase() }
    }

    private fun getContext(): Context {
        return application.applicationContext
    }
}

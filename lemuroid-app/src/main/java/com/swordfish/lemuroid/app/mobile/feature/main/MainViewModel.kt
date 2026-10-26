package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class MainViewModel(appContext: Context, private val saveSyncManager: SaveSyncManager) : ViewModel() {
    class Factory(
        private val appContext: Context,
        private val saveSyncManager: SaveSyncManager,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(appContext, saveSyncManager) as T
        }
    }

    data class UiState(
        val operationInProgress: Boolean = false,
        val saveSyncEnabled: Boolean = false,
        val displaySearch: Boolean = false,
        val searchQuery: String = "",
        val gamesGridMode: Boolean = true,
        val systemsGridMode: Boolean = true,
    )

    private val sharedPreferences = SharedPreferencesHelper.getSharedPreferences(appContext)

    private val currentRouteFlow = MutableStateFlow(MainRoute.HOME)
    private val saveSyncEnabledFlow = MutableStateFlow(false)
    private val operationInProgressFlow = PendingOperationsMonitor(appContext).anyOperationInProgress()
    private val searchQueryFlow = MutableStateFlow("")
    private val gamesGridModeFlow = MutableStateFlow(sharedPreferences.getBoolean(PREF_KEY_GAMES_GRID_MODE, true))
    private val systemsGridModeFlow =
        MutableStateFlow(sharedPreferences.getBoolean(PREF_KEY_SYSTEMS_GRID_MODE, true))

    val state = buildStateFlow()

    // Same as the "Syncing games" banner (see SaveSyncBanner).
    val saveSyncBannerShown =
        PendingOperationsMonitor(appContext)
            .saveSyncRunning()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = false,
            )

    private fun buildStateFlow(): StateFlow<UiState> {
        val combinedFlows =
            combine(
                currentRouteFlow,
                saveSyncEnabledFlow,
                operationInProgressFlow,
                searchQueryFlow,
                // Both view modes in one flow: combine takes at most five typed flows.
                combine(gamesGridModeFlow, systemsGridModeFlow) { games, systems -> games to systems },
            ) { currentRoute, saveSyncEnabled, operationInProgress, searchQuery, (gamesGridMode, systemsGridMode) ->
                UiState(
                    operationInProgress = operationInProgress,
                    saveSyncEnabled = saveSyncEnabled,
                    displaySearch = currentRoute == MainRoute.SEARCH,
                    searchQuery = searchQuery,
                    gamesGridMode = gamesGridMode,
                    systemsGridMode = systemsGridMode,
                )
            }

        return combinedFlows
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Lazily,
                initialValue = UiState(),
            )
    }

    fun changeRoute(currentRoute: MainRoute) {
        val current = saveSyncManager.isSupported() && saveSyncManager.isConfigured()
        saveSyncEnabledFlow.value = current

        currentRouteFlow.value = currentRoute
    }

    fun changeQueryString(newSearchQuery: String) {
        searchQueryFlow.value = newSearchQuery
    }

    fun toggleGamesViewMode() {
        val newValue = !gamesGridModeFlow.value
        gamesGridModeFlow.value = newValue
        sharedPreferences.edit().putBoolean(PREF_KEY_GAMES_GRID_MODE, newValue).apply()
    }

    fun toggleSystemsViewMode() {
        val newValue = !systemsGridModeFlow.value
        systemsGridModeFlow.value = newValue
        sharedPreferences.edit().putBoolean(PREF_KEY_SYSTEMS_GRID_MODE, newValue).apply()
    }

    companion object {
        private const val PREF_KEY_GAMES_GRID_MODE = "games_grid_mode"
        private const val PREF_KEY_SYSTEMS_GRID_MODE = "systems_grid_mode"
    }
}

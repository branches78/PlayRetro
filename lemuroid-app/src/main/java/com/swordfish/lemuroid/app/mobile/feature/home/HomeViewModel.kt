package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.home.HomeDiscovery
import com.swordfish.lemuroid.app.shared.home.HomeDynamicRows
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.LibrarySourceActivity
import com.swordfish.lemuroid.common.coroutines.combine
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class)
class HomeViewModel(
    appContext: Context,
    retrogradeDb: RetrogradeDatabase,
    private val coresSelection: CoresSelection,
) : ViewModel() {
    companion object {
        const val CAROUSEL_MAX_ITEMS = 10
        const val DEBOUNCE_TIME = 100L
    }

    class Factory(
        val appContext: Context,
        val retrogradeDb: RetrogradeDatabase,
        val coresSelection: CoresSelection,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(appContext, retrogradeDb, coresSelection) as T
        }
    }

    data class UIState(
        val favoritesGames: List<Game> = emptyList(),
        val recentGames: List<Game> = emptyList(),
        val discoveryGames: List<Game> = emptyList(),
        val dynamicRows: List<HomeDynamicRows.Row> = emptyList(),
        val indexInProgress: Boolean = true,
        val showNoNotificationPermissionCard: Boolean = false,
        val showNoMicrophonePermissionCard: Boolean = false,
        val showNoGamesCard: Boolean = false,
        // More favorites than the ones shown in the row: a "Show all" card is added at its end.
        val hasMoreFavorites: Boolean = false,
    )

    private val microphonePermissionEnabledState = MutableStateFlow(true)
    private val notificationsPermissionEnabledState = MutableStateFlow(true)
    private val uiStates = MutableStateFlow(UIState())

    // A StateFlow, so the screen starts with the current rows instead of an empty state: with no rows for a moment
    // the scroll position would be lost when coming back from another screen.
    fun getViewStates(): StateFlow<UIState> {
        return uiStates
    }

    // Asks first whether the games come from a folder of the device or a network share.
    fun changeLocalStorageFolder(context: Context) {
        LibrarySourceActivity.show(context)
    }

    fun updatePermissions(context: Context) {
        notificationsPermissionEnabledState.value = isNotificationsPermissionGranted(context)
        microphonePermissionEnabledState.value = isMicrophonePermissionGranted(context)
    }

    private fun isNotificationsPermissionGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        val permissionResult =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            )

        return permissionResult == PackageManager.PERMISSION_GRANTED
    }

    private fun isMicrophonePermissionGranted(context: Context): Boolean {
        val permissionResult =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            )

        return permissionResult == PackageManager.PERMISSION_GRANTED
    }

    private fun buildViewState(
        favoritesGames: List<Game>,
        recentGames: List<Game>,
        discoveryGames: List<Game>,
        indexInProgress: Boolean,
        notificationsPermissionEnabled: Boolean,
        showMicrophoneCard: Boolean,
        dynamicRows: List<HomeDynamicRows.Row>,
    ): UIState {
        val noGames = recentGames.isEmpty() && favoritesGames.isEmpty() && discoveryGames.isEmpty()

        return UIState(
            favoritesGames = favoritesGames.take(CAROUSEL_MAX_ITEMS),
            recentGames = recentGames,
            discoveryGames = discoveryGames,
            indexInProgress = indexInProgress,
            showNoNotificationPermissionCard = !notificationsPermissionEnabled,
            showNoMicrophonePermissionCard = showMicrophoneCard,
            showNoGamesCard = noGames,
            dynamicRows = dynamicRows,
            hasMoreFavorites = favoritesGames.size > CAROUSEL_MAX_ITEMS,
        )
    }

    init {
        viewModelScope.launch {
            val uiStatesFlow =
                combine(
                    favoritesGames(retrogradeDb),
                    recentGames(retrogradeDb),
                    discoveryGames(retrogradeDb),
                    indexingInProgress(appContext),
                    notificationsPermissionEnabledState,
                    microphoneNotification(retrogradeDb),
                    HomeDynamicRows(appContext, retrogradeDb).getRows(),
                    ::buildViewState,
                )

            uiStatesFlow
                .debounce(DEBOUNCE_TIME)
                .flowOn(Dispatchers.IO)
                .collect { uiStates.value = it }
        }
    }

    private fun indexingInProgress(appContext: Context) =
        PendingOperationsMonitor(appContext).anyLibraryOperationInProgress()

    private fun discoveryGames(retrogradeDb: RetrogradeDatabase) = HomeDiscovery.games(retrogradeDb, CAROUSEL_MAX_ITEMS)

    private fun recentGames(retrogradeDb: RetrogradeDatabase) =
        retrogradeDb.gameDao().selectFirstUnfavoriteRecents(CAROUSEL_MAX_ITEMS)

    private fun favoritesGames(retrogradeDb: RetrogradeDatabase) =
        // One more than shown, to know if the "Show all" card is needed.
        retrogradeDb.gameDao().selectFirstFavorites(CAROUSEL_MAX_ITEMS + 1)

    private fun dsGamesCount(retrogradeDb: RetrogradeDatabase): Flow<Int> {
        return retrogradeDb.gameDao().selectSystemsWithCount()
            .map { systems ->
                systems
                    .firstOrNull { it.systemId == SystemID.NDS.dbname }
                    ?.count
                    ?: 0
            }
            .distinctUntilChanged()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun microphoneNotification(db: RetrogradeDatabase): Flow<Boolean> {
        return microphonePermissionEnabledState
            .flatMapLatest { isMicrophoneEnabled ->
                if (isMicrophoneEnabled) {
                    flowOf(false)
                } else {
                    combine(
                        ndsCoreSupportsMicrophone(),
                        dsGamesCount(db),
                    ) { supportsMicrophone, dsCount ->
                        supportsMicrophone && dsCount > 0
                    }
                }
                    .distinctUntilChanged()
            }
    }

    private fun ndsCoreSupportsMicrophone(): Flow<Boolean> {
        return flow {
            val ndsSystem = GameSystem.findById(SystemID.NDS.dbname)
            emit(coresSelection.getCoreConfigForSystem(ndsSystem).supportsMicrophone)
        }
    }
}

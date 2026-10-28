package com.swordfish.lemuroid.app.mobile.feature.main

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.details.GameDetailsScreen
import com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesScreen
import com.swordfish.lemuroid.app.mobile.feature.favorites.FavoritesViewModel
import com.swordfish.lemuroid.app.mobile.feature.games.GamesScreen
import com.swordfish.lemuroid.app.mobile.feature.games.GamesViewModel
import com.swordfish.lemuroid.app.mobile.feature.home.HomeScreen
import com.swordfish.lemuroid.app.mobile.feature.home.HomeViewModel
import com.swordfish.lemuroid.app.mobile.feature.search.SearchScreen
import com.swordfish.lemuroid.app.mobile.feature.search.SearchViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoreOptionsSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.smb.SmbSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.smb.SmbSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.shortcuts.ShortcutsGenerator
import com.swordfish.lemuroid.app.mobile.feature.systems.MetaSystemsScreen
import com.swordfish.lemuroid.app.mobile.feature.systems.MetaSystemsViewModel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.about.AboutContent
import com.swordfish.lemuroid.app.shared.details.GameDescriptionProvider
import com.swordfish.lemuroid.app.shared.details.GameDetailsViewModel
import com.swordfish.lemuroid.app.shared.game.BaseGameActivity
import com.swordfish.lemuroid.app.shared.game.GameLauncher
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.main.BusyActivity
import com.swordfish.lemuroid.app.shared.main.GameLaunchTaskHandler
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncBanner
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncChanges
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.common.coroutines.safeLaunch
import com.swordfish.lemuroid.ext.feature.review.ReviewManager
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.injection.PerActivity
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.MetaSystemID
import com.swordfish.lemuroid.lib.library.covers.CustomCoverManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import dagger.Provides
import javax.inject.Inject
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope

@OptIn(DelicateCoroutinesApi::class)
class MainActivity : RetrogradeComponentActivity(), BusyActivity {
    @Inject
    lateinit var gameLaunchTaskHandler: GameLaunchTaskHandler

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var settingsManager: SettingsManager

    @Inject
    lateinit var retrogradeDb: RetrogradeDatabase

    @Inject
    lateinit var gameInteractor: GameInteractor

    @Inject
    lateinit var biosManager: BiosManager

    @Inject
    lateinit var coresSelection: CoresSelection

    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var descriptionProvider: GameDescriptionProvider

    private val reviewManager = ReviewManager()

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext, saveSyncManager)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) SaveSyncWork.enqueueStartupWork(applicationContext)
        SaveSyncChanges.keepChecking(this, applicationContext, saveSyncManager, settingsManager)

        GlobalScope.safeLaunch {
            reviewManager.initialize(applicationContext)
        }

        setContent {
            val navController = rememberNavController()
            MainScreen(navController)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun MainScreen(navController: NavHostController) {
        AppTheme {
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination
            val currentRoute =
                currentDestination?.route
                    ?.let { MainRoute.findByRoute(it) }
                    ?: MainRoute.HOME

            // Inside a system the top bar shows the system name instead of the generic "Games" title.
            val currentSystemTitleId =
                navBackStackEntry.value?.arguments?.getString("metaSystemId")
                    ?.let { runCatching { MetaSystemID.valueOf(it).titleResId }.getOrNull() }
                    // The core options of a system and the details of a game also show the system name.
                    ?: navBackStackEntry.value?.arguments?.getString("systemId")
                        ?.let { runCatching { GameSystem.findById(it).titleResId }.getOrNull() }

            LaunchedEffect(currentRoute) {
                mainViewModel.changeRoute(currentRoute)
            }

            val selectedGameState =
                remember {
                    mutableStateOf<Game?>(null)
                }

            // "Continue playing" always starts the game and keeps the long press menu.
            val onRecentGameClick = { game: Game ->
                gameInteractor.onGamePlay(game)
            }

            val onRecentGameLongClick = { game: Game ->
                selectedGameState.value = game
            }

            // Elsewhere a game opens its details, which have all the actions of the menu (setting "Show game
            // details"); without them it starts right away and the long press opens the menu, as before.
            val onGameClick = { game: Game ->
                if (gameInteractor.showDetailsOnClick()) {
                    navController.navigateToGameDetails(game)
                } else {
                    gameInteractor.onGamePlay(game)
                }
            }

            val onGameLongClick = { game: Game ->
                if (gameInteractor.showDetailsOnClick()) {
                    navController.navigateToGameDetails(game)
                } else {
                    selectedGameState.value = game
                }
            }

            val onGameFavoriteToggle = { game: Game, isFavorite: Boolean ->
                gameInteractor.onFavoriteToggle(game, isFavorite)
            }

            val mainUIState =
                mainViewModel.state
                    .collectAsState(MainViewModel.UiState())
                    .value

            Scaffold(
                topBar = {
                    // The details of a game are immersive: their screenshot goes up to the top of the screen and
                    // they draw the back and search buttons over it.
                    if (currentRoute == MainRoute.GAME_DETAILS) return@Scaffold
                    MainTopBar(
                        currentRoute = currentRoute,
                        titleOverrideId = currentSystemTitleId,
                        navController = navController,
                        mainUIState = mainUIState,
                        onUpdateQueryString = { mainViewModel.changeQueryString(it) },
                        onToggleGamesViewMode = { mainViewModel.toggleGamesViewMode() },
                        onToggleSystemsViewMode = { mainViewModel.toggleSystemsViewMode() },
                    )
                },
                bottomBar = {
                    MainNavigationBar(currentRoute, navBackStackEntry.value.mainTab(currentRoute), navController)
                },
                // Over the content, just above the bottom bar.
                snackbarHost = { SaveSyncBanner() },
            ) { padding ->
                NavHost(
                    modifier = Modifier.fillMaxSize(),
                    navController = navController,
                    startDestination = MainRoute.HOME.route,
                ) {
                    composable(MainRoute.HOME) { entry ->
                        HomeScreen(
                            modifier = Modifier.padding(entry.stablePadding(padding, navBackStackEntry.value)),
                            viewModel =
                                viewModel(
                                    factory =
                                        HomeViewModel.Factory(
                                            applicationContext,
                                            retrogradeDb,
                                            coresSelection,
                                        ),
                                ),
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onRecentGameClick = onRecentGameClick,
                            onRecentGameLongClick = onRecentGameLongClick,
                            onShowAllFavorites = { navController.navigateToRoute(MainRoute.FAVORITES) },
                        )
                    }
                    composable(MainRoute.GAME_DETAILS) { entry ->
                        val gameId = entry.arguments?.getInt("gameId") ?: return@composable
                        GameDetailsScreen(
                            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
                            viewModel =
                                viewModel(
                                    key = "game_details_$gameId",
                                    factory =
                                        GameDetailsViewModel.Factory(
                                            gameId,
                                            retrogradeDb,
                                            descriptionProvider,
                                        ),
                                ),
                            gameInteractor = gameInteractor,
                            onBack = { navController.popBackStack() },
                            onSearch = { navController.navigate(MainRoute.SEARCH.route) { launchSingleTop = true } },
                            onOpenCoreOptions = { game -> navController.navigateToCoreOptions(game.systemId, game.id) },
                            onOpenGame = { game -> navController.navigateToGameDetails(game) },
                        )
                    }
                    composable(MainRoute.FAVORITES) { entry ->
                        FavoritesScreen(
                            modifier = Modifier.padding(entry.stablePadding(padding, navBackStackEntry.value)),
                            viewModel = viewModel(factory = FavoritesViewModel.Factory(retrogradeDb)),
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                        )
                    }
                    composable(MainRoute.SEARCH) { entry ->
                        SearchScreen(
                            modifier = Modifier.padding(entry.stablePadding(padding, navBackStackEntry.value)),
                            viewModel =
                                viewModel(
                                    factory = SearchViewModel.Factory(retrogradeDb),
                                ),
                            searchQuery = mainUIState.searchQuery,
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onGameFavoriteToggle = onGameFavoriteToggle,
                            onResetSearchQuery = { mainViewModel.changeQueryString("") },
                        )
                    }
                    composable(MainRoute.SYSTEMS) {
                        MetaSystemsScreen(
                            modifier = Modifier.padding(padding),
                            navController = navController,
                            viewModel =
                                viewModel(
                                    factory =
                                        MetaSystemsViewModel.Factory(
                                            retrogradeDb,
                                            applicationContext,
                                        ),
                                ),
                            gridMode = mainUIState.systemsGridMode,
                        )
                    }
                    composable(MainRoute.SYSTEM_GAMES) { entry ->
                        val metaSystemId = entry.arguments?.getString("metaSystemId")
                        GamesScreen(
                            modifier = Modifier.padding(entry.stablePadding(padding, navBackStackEntry.value)),
                            viewModel =
                                viewModel(
                                    factory =
                                        GamesViewModel.Factory(
                                            retrogradeDb,
                                            MetaSystemID.valueOf(metaSystemId!!),
                                        ),
                                ),
                            gridMode = mainUIState.gamesGridMode,
                            onGameClick = onGameClick,
                            onGameLongClick = onGameLongClick,
                            onGameFavoriteToggle = onGameFavoriteToggle,
                        )
                    }
                    composable(MainRoute.SETTINGS) {
                        SettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SettingsViewModel.Factory(
                                            applicationContext,
                                            settingsInteractor,
                                            saveSyncManager,
                                            FlowSharedPreferences(
                                                SharedPreferencesHelper.getLegacySharedPreferences(
                                                    applicationContext,
                                                ),
                                            ),
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_BIOS) {
                        BiosScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory = BiosSettingsViewModel.Factory(biosManager),
                                ),
                        )
                    }
                    composable(MainRoute.SETTINGS_CORES_SELECTION) {
                        CoresSelectionScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        CoresSelectionViewModel.Factory(
                                            applicationContext,
                                            coresSelection,
                                        ),
                                ),
                            navController = navController,
                        )
                    }
                    composable(MainRoute.SETTINGS_CORE_OPTIONS) { entry ->
                        // Opened from "Configure core" of a game: its options can also be saved only for it.
                        val gameId = entry.arguments?.getInt("gameId", -1) ?: -1
                        val game by produceState<Game?>(null, gameId) {
                            value = if (gameId >= 0) retrogradeDb.gameDao().selectById(gameId) else null
                        }
                        if (gameId >= 0 && game == null) return@composable
                        CoreOptionsSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        CoresSelectionViewModel.Factory(
                                            applicationContext,
                                            coresSelection,
                                        ),
                                ),
                            systemId = entry.arguments?.getString("systemId")!!,
                            game = game,
                        )
                    }
                    composable(MainRoute.SETTINGS_INPUT_DEVICES) {
                        InputDevicesSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        InputDevicesSettingsViewModel.Factory(
                                            applicationContext,
                                            inputDeviceManager,
                                        ),
                                ),
                        )
                    }
                    composable(MainRoute.ABOUT) {
                        // Same information as the TV "About" tab.
                        AboutContent(
                            modifier =
                                Modifier
                                    .padding(padding)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    composable(MainRoute.SETTINGS_SMB) {
                        SmbSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel = viewModel(factory = SmbSettingsViewModel.Factory(applicationContext)),
                            onClose = { navController.popBackStack() },
                        )
                    }
                    composable(MainRoute.SETTINGS_SMB_SYNC) {
                        SmbSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel = viewModel(factory = SmbSettingsViewModel.syncFolderFactory(applicationContext)),
                            onClose = { navController.popBackStack() },
                        )
                    }
                    composable(MainRoute.SETTINGS_SAVE_SYNC) {
                        SaveSyncSettingsScreen(
                            modifier = Modifier.padding(padding),
                            viewModel =
                                viewModel(
                                    factory =
                                        SaveSyncSettingsViewModel.Factory(
                                            application,
                                            saveSyncManager,
                                        ),
                                ),
                            onConfigureNetworkFolder = { navController.navigateToRoute(MainRoute.SETTINGS_SMB_SYNC) },
                        )
                    }
                }
            }

            MainGameContextActions(
                selectedGameState = selectedGameState,
                shortcutSupported = gameInteractor.supportShortcuts(),
                onGamePlay = { gameInteractor.onGamePlay(it) },
                onGameRestart = { gameInteractor.onGameRestart(it) },
                onFavoriteToggle = { game: Game, isFavorite: Boolean ->
                    gameInteractor.onFavoriteToggle(game, isFavorite)
                },
                onCreateShortcut = { gameInteractor.onCreateShortcut(it) },
                onSetCustomName = { game, name -> gameInteractor.onSetCustomName(game, name) },
                onClearCustomName = { game -> gameInteractor.onClearCustomName(game) },
                onSetCustomCoverUri = { game, uri -> gameInteractor.onSetCustomCoverUri(game, uri) },
                onClearCustomCoverUri = { game -> gameInteractor.onClearCustomCoverUri(game) },
                onOpenCoreOptions = { game -> navController.navigateToCoreOptions(game.systemId, game.id) },
            )
        }
    }

    override fun activity(): Activity = this

    override fun isBusy(): Boolean = mainViewModel.state.value.operationInProgress ?: false

    override fun isSaveSyncBannerShown(): Boolean = mainViewModel.saveSyncBannerShown.value

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?,
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            BaseGameActivity.REQUEST_PLAY_GAME -> {
                GlobalScope.safeLaunch {
                    gameLaunchTaskHandler.handleGameFinish(
                        true,
                        this@MainActivity,
                        resultCode,
                        data,
                    )
                }
            }
        }
    }

    @dagger.Module
    abstract class Module {
        @dagger.Module
        companion object {
            @Provides
            @PerActivity
            @JvmStatic
            fun settingsInteractor(
                activity: MainActivity,
                directoriesManager: DirectoriesManager,
            ) = SettingsInteractor(activity, directoriesManager)

            @Provides
            @PerActivity
            @JvmStatic
            fun gameInteractor(
                activity: MainActivity,
                retrogradeDb: RetrogradeDatabase,
                shortcutsGenerator: ShortcutsGenerator,
                gameLauncher: GameLauncher,
                customCoverManager: CustomCoverManager,
            ) = GameInteractor(activity, retrogradeDb, false, shortcutsGenerator, gameLauncher, customCoverManager)
        }
    }
}

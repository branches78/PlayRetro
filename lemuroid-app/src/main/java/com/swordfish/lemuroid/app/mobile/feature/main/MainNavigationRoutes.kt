package com.swordfish.lemuroid.app.mobile.feature.main

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideogameAsset
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideogameAsset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.lib.library.db.entity.Game

fun NavGraphBuilder.composable(
    route: MainRoute,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    this.composable(route = route.route, arguments = route.arguments, content = content)
}

fun NavController.navigateToRoute(route: MainRoute) {
    this.navigate(route.route)
}

/**
 * Opens the options of the core used by a system ([systemId] is the database name of the system). With a [gameId]
 * ("Configure core" of a game) they can also be chosen only for that game.
 */
fun NavController.navigateToCoreOptions(
    systemId: String,
    gameId: Int? = null,
) {
    val route = MainRoute.SETTINGS_CORE_OPTIONS.route.substringBefore("?").replace("{systemId}", systemId)
    this.navigate(if (gameId != null) "$route?gameId=$gameId" else route)
}

/**
 * Padding of a screen of the navigation. While another screen opens over it (e.g. the details of a game, which hide
 * the top and bottom bars) it keeps the padding it had: otherwise it would grow during the transition and lose part of
 * its scroll position.
 */
@Composable
fun NavBackStackEntry.stablePadding(
    padding: PaddingValues,
    currentEntry: NavBackStackEntry?,
): PaddingValues {
    // Not a state: it is only read while composing this screen.
    val kept = remember { arrayOf(padding) }
    if (currentEntry == null || currentEntry.id == id) kept[0] = padding
    return kept[0]
}

/**
 * Opens the details of a [game]. They keep the tab they are opened from selected in the bottom navigation (also when
 * opened from other details), and the top bar shows the name of its system.
 */
fun NavController.navigateToGameDetails(game: Game) {
    val current = currentBackStackEntry
    val tab =
        current?.arguments?.getString("tab")
            ?: current?.destination?.route?.let { MainRoute.findByRoute(it).root.name }
            ?: MainRoute.HOME.name
    val route =
        MainRoute.GAME_DETAILS.route
            .replace("{gameId}", game.id.toString())
            .replace("{systemId}", game.systemId)
            .replace("{tab}", tab)
    this.navigate(route)
}

/** Tab selected in the bottom navigation: the root of the [route], or the one the details of a game come from. */
fun NavBackStackEntry?.mainTab(route: MainRoute): MainRoute {
    val tab = this?.arguments?.getString("tab")
    return tab?.let { runCatching { MainRoute.valueOf(it) }.getOrNull() } ?: route.root
}

enum class MainRoute(
    val route: String,
    @StringRes val titleId: Int,
    val parent: MainRoute? = null,
    val arguments: List<NamedNavArgument> = emptyList(),
    val showBottomNavigation: Boolean = true,
) {
    HOME(
        route = "home",
        titleId = R.string.title_home,
    ),
    // Not a tab: opened from the "Show all" card of the home favorites row.
    FAVORITES(
        route = "favorites",
        titleId = R.string.favorites,
        parent = HOME,
    ),
    // Not a tab: opened from the magnifier of the top bar.
    SEARCH(
        route = "search",
        titleId = R.string.title_search,
    ),
    SYSTEMS(
        route = "systems/home",
        titleId = R.string.title_consoles,
    ),
    SYSTEM_GAMES(
        route = "systems/{metaSystemId}",
        titleId = R.string.title_games,
        parent = SYSTEMS,
        listOf(navArgument("metaSystemId") { type = NavType.StringType }),
    ),
    SETTINGS(
        route = "settings/home",
        titleId = R.string.title_settings,
    ),
    SETTINGS_BIOS(
        route = "settings/bios",
        titleId = R.string.settings_title_display_bios_info,
        parent = SETTINGS,
    ),
    SETTINGS_CORES_SELECTION(
        route = "settings/cores",
        titleId = R.string.settings_title_open_cores_selection,
        parent = SETTINGS,
    ),
    SETTINGS_CORE_OPTIONS(
        route = "settings/cores/{systemId}?gameId={gameId}",
        titleId = R.string.settings_title_open_cores_selection,
        parent = SETTINGS_CORES_SELECTION,
        listOf(
            navArgument("systemId") { type = NavType.StringType },
            navArgument("gameId") {
                type = NavType.IntType
                defaultValue = -1
            },
        ),
    ),
    SETTINGS_INPUT_DEVICES(
        route = "settings/inputdevices",
        titleId = R.string.settings_title_gamepad_settings,
        parent = SETTINGS,
    ),
    SETTINGS_SAVE_SYNC(
        route = "settings/savesync",
        titleId = R.string.settings_title_save_sync,
        parent = SETTINGS,
    ),
    // Network share (SMB) with the games library, opened from "Directory".
    SETTINGS_SMB(
        route = "settings/smb",
        titleId = R.string.smb_title,
        parent = SETTINGS,
    ),
    // The same form, opened from the save sync settings (see SmbSettingsViewModel.syncFolderFactory).
    SETTINGS_SMB_SYNC(
        route = "settings/smb/sync",
        titleId = R.string.smb_title,
        parent = SETTINGS,
    ),
    ABOUT(
        route = "about",
        titleId = R.string.title_about,
    ),
    // Not a tab: opened when a game is chosen (see navigateToGameDetails). The top bar shows the name of the system
    // of the game, and the bottom navigation the tab it was opened from.
    GAME_DETAILS(
        route = "games/details/{gameId}?systemId={systemId}&tab={tab}",
        titleId = R.string.title_games,
        arguments =
            listOf(
                navArgument("gameId") { type = NavType.IntType },
                navArgument("systemId") {
                    type = NavType.StringType
                    nullable = true
                },
                navArgument("tab") {
                    type = NavType.StringType
                    nullable = true
                },
            ),
    ),
    ;

    val root = root()

    // Search and the details of a game have no tab of their own, so they go back to the screen they were opened from.
    val hasBackButton: Boolean
        get() = parent != null || this == SEARCH || this == GAME_DETAILS

    private fun root(): MainRoute {
        return parent?.root() ?: this
    }

    companion object {
        fun findByRoute(route: String): MainRoute {
            return values().first { it.route == route }
        }
    }
}

enum class MainNavigationRoutes(
    val route: MainRoute,
    @StringRes val titleId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(MainRoute.HOME, R.string.title_home, Icons.Filled.Home, Icons.Outlined.Home),
    SYSTEMS(MainRoute.SYSTEMS, R.string.title_consoles, Icons.Filled.VideogameAsset, Icons.Outlined.VideogameAsset),
    SETTINGS(MainRoute.SETTINGS, R.string.title_settings, Icons.Filled.Settings, Icons.Outlined.Settings),
    ABOUT(MainRoute.ABOUT, R.string.title_about, Icons.Filled.Info, Icons.Outlined.Info),
}

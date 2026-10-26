package com.swordfish.lemuroid.app.shared.details

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.CustomNameDialog
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.lib.core.CoreOptionsCache
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.Game
import timber.log.Timber

/** One action of the details screen of a game. */
class GameAction(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Actions of the details screen: the same ones as the long press menu of a game. Restart and core options only exist
 * once the game has been played (core options are only known after the core has run), and core options only when
 * there is something to configure; before that, the core can be chosen when the system has more than one. The
 * shortcut only exists where Android allows it.
 */
class GameActions(
    val play: GameAction,
    val restart: GameAction?,
    val coreOptions: GameAction?,
    val selectCore: GameAction?,
    val favorite: GameAction,
    val customName: GameAction,
    val customCover: GameAction,
    val shortcut: GameAction?,
)

/**
 * Builds the actions of [game] and shows the dialogs they need (custom name, image picker), then gives them to
 * [content] to lay them out (TV or mobile).
 */
@Composable
fun GameActionsHost(
    game: Game,
    gameInteractor: GameInteractor,
    onOpenCoreOptions: (Game) -> Unit,
    content: @Composable (GameActions) -> Unit,
) {
    val context = LocalContext.current
    var showCustomNameDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: SecurityException) {
                    Timber.e(e, "Failed to persist read URI permission for custom cover")
                }
                gameInteractor.onSetCustomCoverUri(game, uri.toString())
            }
        }

    val hasBeenPlayed = game.lastPlayedAt != null
    val coreConfigurable = remember(game.id, hasBeenPlayed) { canConfigureCore(context, game) }
    val actions =
        GameActions(
            play =
                GameAction(
                    Icons.Default.PlayArrow,
                    stringResource(if (hasBeenPlayed) R.string.game_context_menu_resume else R.string.game_context_menu_start),
                ) { gameInteractor.onGamePlay(game) },
            restart =
                GameAction(Icons.Default.RestartAlt, stringResource(R.string.game_context_menu_restart)) {
                    gameInteractor.onGameRestart(game)
                }.takeIf { hasBeenPlayed },
            coreOptions =
                GameAction(Icons.Default.Tune, stringResource(R.string.game_context_menu_core_options)) {
                    onOpenCoreOptions(game)
                }.takeIf { coreConfigurable },
            // Same screen as "Configure core" ("Only this game" selected), where the core is the only option yet.
            selectCore =
                GameAction(Icons.Default.Memory, stringResource(R.string.game_context_menu_select_core)) {
                    onOpenCoreOptions(game)
                }.takeIf { canSelectCore(game) },
            favorite =
                if (game.isFavorite) {
                    GameAction(Icons.Default.Favorite, stringResource(R.string.game_context_menu_remove_from_favorites)) {
                        gameInteractor.onFavoriteToggle(game, false)
                    }
                } else {
                    GameAction(Icons.Default.FavoriteBorder, stringResource(R.string.game_context_menu_add_to_favorites)) {
                        gameInteractor.onFavoriteToggle(game, true)
                    }
                },
            customName =
                if (game.customName != null) {
                    GameAction(Icons.Default.Clear, stringResource(R.string.game_context_menu_remove_custom_name)) {
                        gameInteractor.onClearCustomName(game)
                    }
                } else {
                    GameAction(Icons.Default.Edit, stringResource(R.string.game_context_menu_add_custom_name)) {
                        showCustomNameDialog = true
                    }
                },
            customCover =
                if (game.customCoverUri != null) {
                    GameAction(Icons.Default.Clear, stringResource(R.string.game_context_menu_remove_custom_artwork)) {
                        gameInteractor.onClearCustomCoverUri(game)
                    }
                } else {
                    GameAction(Icons.Default.Image, stringResource(R.string.game_context_menu_add_custom_artwork)) {
                        // Many TVs (and some phones) have no app to pick images.
                        try {
                            imagePickerLauncher.launch("image/*")
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.game_context_menu_no_image_picker, Toast.LENGTH_LONG).show()
                        }
                    }
                },
            shortcut =
                GameAction(Icons.Default.AppShortcut, stringResource(R.string.game_context_menu_create_shortcut)) {
                    gameInteractor.onCreateShortcut(game)
                }.takeIf { gameInteractor.supportShortcuts() },
        )

    content(actions)

    if (showCustomNameDialog) {
        CustomNameDialog(
            initialName = game.fileName,
            onDismiss = { showCustomNameDialog = false },
            onConfirm = { newName ->
                showCustomNameDialog = false
                gameInteractor.onSetCustomName(game, newName)
            },
        )
    }
}

/**
 * A game never played can have its core chosen before playing, when its system has more than one (e.g. PlayStation):
 * its core options are only known once the core has run.
 */
fun canSelectCore(game: Game): Boolean {
    if (game.lastPlayedAt != null) return false
    val system = runCatching { GameSystem.findById(game.systemId) }.getOrNull() ?: return false
    return system.systemCoreConfigs.size > 1
}

/**
 * A game already played has its core options offered only when there is something to configure: another core of its
 * system, or options of its core that the app exposes and the core reported when it ran (some cores, like Snes9x or
 * DOSBox Pure, have none). Until the core reports its options, the ones exposed by the app are enough.
 */
fun canConfigureCore(
    context: Context,
    game: Game,
): Boolean {
    if (game.lastPlayedAt == null) return false
    val system = runCatching { GameSystem.findById(game.systemId) }.getOrNull() ?: return false
    if (system.systemCoreConfigs.size > 1) return true
    val core = system.systemCoreConfigs.firstOrNull() ?: return false
    val exposedKeys = (core.exposedSettings + core.exposedAdvancedSettings).map { it.key }
    if (exposedKeys.isEmpty()) return false
    val reportedKeys = CoreOptionsCache(context.applicationContext).get(core.coreID)?.map { it.key }?.toSet()
    return reportedKeys == null || exposedKeys.any { it in reportedKeys }
}

/** Name of the system of the game ("Nintendo 64"), null for an unknown system. */
@Composable
fun gameSystemName(game: Game): String? {
    val system = remember(game.systemId) { runCatching { GameSystem.findById(game.systemId) }.getOrNull() }
    return system?.let { stringResource(it.titleResId) }
}

/** Region of the game from its file name ("Banjo-Tooie (USA)" -> "USA"), if it has one. */
fun gameRegion(game: Game): String? {
    return Regex("\\(([^)]+)\\)").find(game.title)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
}

/** Title without the region and other tags of the file name, unless the user gave it a name. */
fun gameCleanTitle(game: Game): String {
    return game.customName ?: game.title.replace(TITLE_TAGS, "").trim().ifEmpty { game.title }
}

private val TITLE_TAGS = Regex("\\s*[(\\[][^)\\]]*[)\\]]")

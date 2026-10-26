@file:Suppress("UNUSED", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.coreoptions.GameMenuCoreOptionsViewModel
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.states.GameMenuStatesScreen
import com.swordfish.lemuroid.app.mobile.feature.gamemenu.states.GameMenuStatesViewModel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameMenuContract
import com.swordfish.lemuroid.app.shared.coreoptions.LemuroidCoreOption
import com.swordfish.lemuroid.app.shared.details.gameCleanTitle
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.app.utils.android.TVNavigationSounds
import com.swordfish.lemuroid.app.utils.android.settings.SettingsFocusDestination
import com.swordfish.lemuroid.common.kotlin.serializable
import com.swordfish.lemuroid.lib.android.RetrogradeComponentActivity
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.saves.StatesManager
import com.swordfish.lemuroid.lib.saves.StatesPreviewManager
import com.swordfish.touchinput.radial.sensors.TiltConfiguration
import java.security.InvalidParameterException
import javax.inject.Inject

class GameMenuActivity : RetrogradeComponentActivity() {
    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    @Inject
    lateinit var statesManager: StatesManager

    @Inject
    lateinit var statesPreviewManager: StatesPreviewManager

    data class GameMenuRequest(
        val coreOptions: List<LemuroidCoreOption>,
        val advancedCoreOptions: List<LemuroidCoreOption>,
        val game: Game,
        val coreConfig: SystemCoreConfig,
        val audioEnabled: Boolean,
        val fastForwardSupported: Boolean,
        val fastForwardEnabled: Boolean,
        val numDisks: Int,
        val currentDisk: Int,
        val currentTiltConfiguration: TiltConfiguration,
        val allTiltConfigurations: List<TiltConfiguration>,
    )

    // Also used on TV: the same menu, moved with the remote or the gamepad.
    private val isTV by lazy { TVHelper.isTV(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the TV in low latency mode while the in-game menu is on top of the game,
        // otherwise some TVs blank the picture when switching modes back and forth.
        if (isTV) TVHelper.requestLowLatencyMode(this)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            SystemBarStyle.dark(Color.TRANSPARENT),
            SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val extras = intent.extras

        val gameMenuRequest =
            GameMenuRequest(
                coreOptions =
                    intent.serializable<Array<LemuroidCoreOption>>(GameMenuContract.EXTRA_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_CORE_OPTIONS"),
                advancedCoreOptions =
                    intent.serializable<Array<LemuroidCoreOption>>(GameMenuContract.EXTRA_ADVANCED_CORE_OPTIONS)
                        ?.toList()
                        ?: throw InvalidParameterException("Missing EXTRA_ADVANCED_CORE_OPTIONS"),
                game =
                    intent.serializable<Game>(GameMenuContract.EXTRA_GAME)
                        ?: throw InvalidParameterException("Missing EXTRA_GAME"),
                coreConfig =
                    intent.serializable<SystemCoreConfig>(GameMenuContract.EXTRA_SYSTEM_CORE_CONFIG)
                        ?: throw InvalidParameterException("Missing EXTRA_SYSTEM_CORE_CONFIG"),
                audioEnabled =
                    extras?.getBoolean(GameMenuContract.EXTRA_AUDIO_ENABLED, false) ?: false,
                fastForwardSupported =
                    extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD_SUPPORTED, false) ?: false,
                fastForwardEnabled =
                    extras?.getBoolean(GameMenuContract.EXTRA_FAST_FORWARD, false) ?: false,
                numDisks =
                    extras?.getInt(GameMenuContract.EXTRA_DISKS, 0) ?: 0,
                currentDisk =
                    extras?.getInt(GameMenuContract.EXTRA_CURRENT_DISK, 0) ?: 0,
                currentTiltConfiguration =
                    intent.serializable<TiltConfiguration>(GameMenuContract.EXTRA_CURRENT_TILT_CONFIG)
                        ?: TiltConfiguration.Disabled,
                allTiltConfigurations =
                    intent.serializable<Array<TiltConfiguration>>(GameMenuContract.EXTRA_TILT_ALL_CONFIGS)
                        ?.toList()
                        ?: emptyList(),
            )

        setContent {
            TVNavigationSounds {
                GameMenuScreen(gameMenuRequest)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun GameMenuScreen(gameMenuRequest: GameMenuRequest) {
        AppTheme {
            val navController = rememberNavController()
            val navBackStackEntry = navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry.value?.destination

            val currentRoute =
                currentDestination?.route
                    ?.let { GameMenuRoute.findByRoute(it) }
                    ?: GameMenuRoute.HOME

            CenteredMenu {
                // Centered title; no close button (back closes the menu, also a touch outside of it on mobile), only
                // the arrow to go back from the other screens.
                CenterAlignedTopAppBar(
                    title = {
                        if (currentRoute == GameMenuRoute.HOME) {
                            MenuGameTitle(gameMenuRequest)
                        } else {
                            Text(stringResource(currentRoute.titleId))
                        }
                    },
                    windowInsets = WindowInsets(0.dp),
                    navigationIcon = {
                        AnimatedVisibility(visible = currentRoute.canGoBack(), enter = fadeIn(), exit = fadeOut()) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    stringResource(R.string.back),
                                )
                            }
                        }
                    },
                )
                Divider(modifier = Modifier.fillMaxWidth())
                NavHost(
                    // Not filled: the window is as tall as the screen shown, up to its maximum height.
                    modifier = Modifier.weight(1f, fill = false),
                    navController = navController,
                    startDestination = GameMenuRoute.HOME.route,
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    // Each screen focuses its first item when it opens, and the one that opened another screen when
                    // coming back, so the menu can be used with a remote or a gamepad.
                    composable(GameMenuRoute.HOME) {
                        SettingsFocusDestination {
                            GameMenuHomeScreen(navController, gameMenuRequest, isTV, ::onResult)
                        }
                    }
                    composable(GameMenuRoute.SAVE) {
                        SettingsFocusDestination {
                            GameMenuStatesScreen(
                                viewModel(
                                    factory =
                                        GameMenuStatesViewModel.Factory(
                                            application,
                                            gameMenuRequest,
                                            statesManager,
                                            false,
                                            statesPreviewManager,
                                        ),
                                ),
                                onStateClicked = {
                                    onResult { putExtra(GameMenuContract.RESULT_SAVE, it) }
                                },
                            )
                        }
                    }
                    composable(GameMenuRoute.LOAD) {
                        SettingsFocusDestination {
                            GameMenuStatesScreen(
                                viewModel(
                                    factory =
                                        GameMenuStatesViewModel.Factory(
                                            application,
                                            gameMenuRequest,
                                            statesManager,
                                            true,
                                            statesPreviewManager,
                                        ),
                                ),
                                onStateClicked = {
                                    onResult { putExtra(GameMenuContract.RESULT_LOAD, it) }
                                },
                            )
                        }
                    }
                    composable(GameMenuRoute.OPTIONS) {
                        SettingsFocusDestination {
                            GameMenuCoreOptionsScreen(
                                viewModel(
                                    factory = GameMenuCoreOptionsViewModel.Factory(inputDeviceManager),
                                ),
                                gameMenuRequest,
                            )
                        }
                    }
                }
            }
        }
    }

    /** Game name with its system and core, at the top of the first screen of the menu. */
    @Composable
    private fun MenuGameTitle(gameMenuRequest: GameMenuRequest) {
        val game = gameMenuRequest.game
        val system = remember(game.systemId) { runCatching { GameSystem.findById(game.systemId) }.getOrNull() }
        val systemName = system?.let { stringResource(it.titleResId) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = gameCleanTitle(game),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = listOfNotNull(systemName, gameMenuRequest.coreConfig.coreID.coreDisplayName).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }

    /**
     * Modal window centered over the game, like the menu of a game on TV: rounded, as tall as its content (without an
     * empty space at the bottom when a game has fewer options) up to a maximum height, changing size smoothly between
     * screens. A touch outside closes it and the game goes on.
     */
    @Composable
    private fun CenteredMenu(content: @Composable ColumnScope.() -> Unit) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onResult { } },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .widthIn(max = MENU_MAX_WIDTH.dp)
                        .fillMaxWidth(MENU_WIDTH_FRACTION)
                        .heightIn(max = minOf(maxHeight * MENU_HEIGHT_FRACTION, MENU_MAX_HEIGHT.dp))
                        .animateContentSize()
                        // Touches inside the window don't close it.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { },
                        ),
                shape = RoundedCornerShape(MENU_CORNER_RADIUS.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                Column(content = content)
            }
        }
    }

    /**
     * Back goes to the previous screen of the menu or closes it at once. Compose would use it first to move the focus
     * out of each focus group around the focused item, so with a remote or a gamepad it took several presses.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                onBackPressedDispatcher.onBackPressed()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun onResult(block: Intent.() -> Unit) {
        val resultIntent = Intent()
        resultIntent.block()
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    companion object {
        private const val MENU_MAX_WIDTH = 520
        private const val MENU_MAX_HEIGHT = 560
        private const val MENU_WIDTH_FRACTION = 0.92f
        private const val MENU_HEIGHT_FRACTION = 0.9f
        private const val MENU_CORNER_RADIUS = 20
    }

    @dagger.Module
    abstract class Module
}

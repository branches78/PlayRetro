package com.swordfish.lemuroid.app.tv.shared

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.KeyEvent
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swordfish.lemuroid.app.mobile.feature.main.GameContextMenuContent
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoreOptionsSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionViewModel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.utils.android.TVNavigationSounds
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import kotlinx.coroutines.delay

/**
 * Long press menu of a game on TV: the same actions and icons as on mobile, in a dialog centered on screen.
 * "Configure core" shows the core options inside the same dialog; back returns to the menu.
 */
object TVGameContextMenu {
    /**
     * With [startWithCoreOptions] (button of the details screen) the dialog only shows the core options. With
     * [onOpenDetails] the menu ends with "Go to game details".
     */
    fun show(
        context: Context,
        game: Game,
        gameInteractor: GameInteractor,
        startWithCoreOptions: Boolean = false,
        onOpenDetails: ((Game) -> Unit)? = null,
    ) {
        val dialog = LongPressSafeDialog(context)
        // The core options screen uses a ViewModel: the dialog keeps its own store, cleared when it closes.
        val viewModelStoreOwner =
            object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
        dialog.setOnDismissListener { viewModelStoreOwner.viewModelStore.clear() }

        val content =
            ComposeView(context).apply {
                setContent {
                    TVNavigationSounds {
                        AppTheme {
                            CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelStoreOwner) {
                                TVGameContextMenuContent(
                                    context,
                                    game,
                                    gameInteractor,
                                    startWithCoreOptions,
                                    onOpenDetails,
                                ) {
                                    dialog.dismiss()
                                }
                            }
                        }
                    }
                }
            }
        dialog.setContentView(content)
        // The rounded surface draws the background, the window itself stays transparent.
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    @Composable
    private fun TVGameContextMenuContent(
        context: Context,
        game: Game,
        gameInteractor: GameInteractor,
        startWithCoreOptions: Boolean,
        onOpenDetails: ((Game) -> Unit)?,
        dismiss: () -> Unit,
    ) {
        var coreOptionsGame by remember { mutableStateOf(game.takeIf { startWithCoreOptions }) }
        val showingCoreOptions = coreOptionsGame != null

        Surface(
            modifier =
                Modifier
                    .widthIn(max = if (showingCoreOptions) CORE_OPTIONS_MAX_WIDTH.dp else DIALOG_MAX_WIDTH.dp)
                    .heightIn(max = CORE_OPTIONS_MAX_HEIGHT.dp),
            shape = RoundedCornerShape(DIALOG_CORNER_RADIUS.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            val optionsGame = coreOptionsGame
            if (optionsGame != null) {
                // Opened only for the core options, back closes the dialog instead of showing the menu.
                BackHandler { if (startWithCoreOptions) dismiss() else coreOptionsGame = null }
                TVCoreOptions(context, optionsGame)
            } else {
                GameContextMenuContent(
                    selectedGame = game,
                    onGamePlay = { gameInteractor.onGamePlay(it) },
                    // The menu closes itself after "Configure core": the dialog stays open to show them.
                    onDismiss = { if (coreOptionsGame == null) dismiss() },
                    onGameRestart = { gameInteractor.onGameRestart(it) },
                    onFavoriteToggle = { selected, isFavorite ->
                        gameInteractor.onFavoriteToggle(selected, isFavorite)
                    },
                    shortcutSupported = gameInteractor.supportShortcuts(),
                    onCreateShortcut = { gameInteractor.onCreateShortcut(it) },
                    onSetCustomName = { selected, name -> gameInteractor.onSetCustomName(selected, name) },
                    onClearCustomName = { gameInteractor.onClearCustomName(it) },
                    onSetCustomCoverUri = { selected, uri ->
                        gameInteractor.onSetCustomCoverUri(selected, uri)
                    },
                    onClearCustomCoverUri = { gameInteractor.onClearCustomCoverUri(it) },
                    onOpenCoreOptions = { coreOptionsGame = it },
                    onOpenDetails = onOpenDetails,
                    requestInitialFocus = true,
                )
            }
        }
    }

    /** Same screen as Settings > Cores > system, for this game ("Only this game" selected at first). */
    @Composable
    private fun TVCoreOptions(
        context: Context,
        game: Game,
    ) {
        val appContext = context.applicationContext
        val coresSelection = remember { CoresSelection { SharedPreferencesHelper.getSharedPreferences(appContext) } }
        val focusManager = LocalFocusManager.current

        CoreOptionsSettingsScreen(
            viewModel = viewModel(factory = CoresSelectionViewModel.Factory(appContext, coresSelection)),
            systemId = game.systemId,
            game = game,
        )

        // The focused menu entry is gone: the focus goes to the first option once the options are shown.
        LaunchedEffect(game.id) {
            repeat(FOCUS_ATTEMPTS) {
                delay(FOCUS_ATTEMPT_DELAY_MS)
                if (focusManager.moveFocus(FocusDirection.Down)) return@LaunchedEffect
            }
        }
    }

    /**
     * The menu opens with a long press of the confirm button, which is still held down: its repeated presses and its
     * release would select the first entry. They are ignored until the button is pressed again inside the menu.
     */
    private class LongPressSafeDialog(context: Context) : ComponentDialog(context) {
        private var confirmPressedInDialog = false

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (!isConfirmKey(event.keyCode)) {
                return super.dispatchKeyEvent(event)
            }
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                confirmPressedInDialog = true
            }
            if (!confirmPressedInDialog) {
                return true
            }
            return super.dispatchKeyEvent(event)
        }

        private fun isConfirmKey(keyCode: Int): Boolean {
            return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                keyCode == KeyEvent.KEYCODE_BUTTON_A
        }
    }

    private const val DIALOG_MAX_WIDTH = 460
    private const val CORE_OPTIONS_MAX_WIDTH = 600
    private const val CORE_OPTIONS_MAX_HEIGHT = 460
    private const val FOCUS_ATTEMPTS = 20
    private const val FOCUS_ATTEMPT_DELAY_MS = 50L
    private const val DIALOG_CORNER_RADIUS = 20
}

package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameTexts
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidSmallGameImage
import com.swordfish.lemuroid.app.shared.details.canConfigureCore
import com.swordfish.lemuroid.app.shared.details.canSelectCore
import com.swordfish.lemuroid.lib.library.db.entity.Game
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainGameContextActions(
    selectedGameState: MutableState<Game?>,
    shortcutSupported: Boolean,
    onGamePlay: (Game) -> Unit,
    onGameRestart: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    onCreateShortcut: (Game) -> Unit,
    onSetCustomName: (Game, String) -> Unit,
    onClearCustomName: (Game) -> Unit,
    onSetCustomCoverUri: (Game, String) -> Unit,
    onClearCustomCoverUri: (Game) -> Unit,
    onOpenCoreOptions: (Game) -> Unit,
) {
    val modalSheetState = rememberModalBottomSheetState(true)
    val selectedGame = selectedGameState.value

    LaunchedEffect(selectedGame) {
        if (selectedGame != null) {
            modalSheetState.show()
        } else {
            modalSheetState.hide()
        }
    }

    if (selectedGame != null) {
        ModalBottomSheet(
            sheetState = modalSheetState,
            onDismissRequest = { selectedGameState.value = null },
        ) {
            GameContextMenuContent(
                selectedGame = selectedGame,
                onGamePlay = onGamePlay,
                onDismiss = { selectedGameState.value = null },
                onGameRestart = onGameRestart,
                onFavoriteToggle = onFavoriteToggle,
                shortcutSupported = shortcutSupported,
                onCreateShortcut = onCreateShortcut,
                onSetCustomName = onSetCustomName,
                onClearCustomName = onClearCustomName,
                onSetCustomCoverUri = onSetCustomCoverUri,
                onClearCustomCoverUri = onClearCustomCoverUri,
                onOpenCoreOptions = onOpenCoreOptions,
            )
        }
    }
}

/**
 * Actions of a game, opened with a long press. Shared by the mobile bottom sheet and the TV dialog
 * ([requestInitialFocus] focuses the first action, needed when it is used with a remote).
 */
@Composable
fun GameContextMenuContent(
    selectedGame: Game,
    onGamePlay: (Game) -> Unit,
    onDismiss: () -> Unit,
    onGameRestart: (Game) -> Unit,
    onFavoriteToggle: (Game, Boolean) -> Unit,
    shortcutSupported: Boolean,
    onCreateShortcut: (Game) -> Unit,
    onSetCustomName: (Game, String) -> Unit,
    onClearCustomName: (Game) -> Unit,
    onSetCustomCoverUri: (Game, String) -> Unit,
    onClearCustomCoverUri: (Game) -> Unit,
    onOpenCoreOptions: (Game) -> Unit,
    requestInitialFocus: Boolean = false,
) {
    val context = LocalContext.current
    val firstEntryFocusRequester = remember { FocusRequester() }
    var showCustomNameDialog by remember { mutableStateOf(false) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to persist read URI permission for custom cover")
            }
            onSetCustomCoverUri(selectedGame, uri.toString())
            onDismiss()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeContent.only(WindowInsetsSides.Bottom)),
    ) {
        ContextActionHeader(game = selectedGame)
        Divider()
        // A game never played has nothing to resume or restart: it can only be started.
        val hasBeenPlayed = selectedGame.lastPlayedAt != null
        ContextActionEntry(
            modifier = Modifier.focusRequester(firstEntryFocusRequester),
            label =
                stringResource(
                    id = if (hasBeenPlayed) R.string.game_context_menu_resume else R.string.game_context_menu_start,
                ),
            icon = Icons.Default.PlayArrow,
            onClick = {
                onGamePlay(selectedGame)
                onDismiss()
            },
        )
        if (hasBeenPlayed) {
            ContextActionEntry(
                label = stringResource(id = R.string.game_context_menu_restart),
                icon = Icons.Default.RestartAlt,
                onClick = {
                    onGameRestart(selectedGame)
                    onDismiss()
                },
            )
        }

        if (selectedGame.isFavorite) {
            ContextActionEntry(
                label = stringResource(id = R.string.game_context_menu_remove_from_favorites),
                icon = Icons.Default.FavoriteBorder,
                onClick = {
                    onFavoriteToggle(selectedGame, false)
                    onDismiss()
                },
            )
        } else {
            ContextActionEntry(
                label = stringResource(id = R.string.game_context_menu_add_to_favorites),
                icon = Icons.Default.Favorite,
                onClick = {
                    onFavoriteToggle(selectedGame, true)
                    onDismiss()
                },
            )
        }

        if (shortcutSupported) {
            ContextActionEntry(
                label = stringResource(id = R.string.game_context_menu_create_shortcut),
                icon = Icons.Default.AppShortcut,
                onClick = {
                    onCreateShortcut(selectedGame)
                    onDismiss()
                },
            )
        }

        if (selectedGame.customName != null) {
            ContextActionEntry(
                label = stringResource(id = R.string.game_context_menu_remove_custom_name),
                icon = Icons.Default.Clear,
                onClick = {
                    onClearCustomName(selectedGame)
                    onDismiss()
                },
            )
        } else {
            ContextActionEntry(
                label = stringResource(id = R.string.game_context_menu_add_custom_name),
                icon = Icons.Default.Edit,
                onClick = {
                    showCustomNameDialog = true
                },
            )
        }

        if (selectedGame.customCoverUri != null) {
            ContextActionEntry(
                label = stringResource(id = R.string.game_context_menu_remove_custom_artwork),
                icon = Icons.Default.Clear,
                onClick = {
                    onClearCustomCoverUri(selectedGame)
                    onDismiss()
                },
            )
        } else {
            ContextActionEntry(
                label = stringResource(id = R.string.game_context_menu_add_custom_artwork),
                icon = Icons.Default.Image,
                onClick = {
                    // Many TVs (and some phones) have no app to pick images.
                    try {
                        imagePickerLauncher.launch("image/*")
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, R.string.game_context_menu_no_image_picker, Toast.LENGTH_LONG).show()
                    }
                },
            )
        }

        // Core options are only known once the core has run, so they are offered after the first play, and only
        // when there is something to configure.
        val coreConfigurable = remember(selectedGame.id, hasBeenPlayed) { canConfigureCore(context, selectedGame) }
        if (coreConfigurable) {
            ContextActionEntry(
                label = stringResource(id = R.string.game_context_menu_core_options),
                icon = Icons.Default.Tune,
                onClick = {
                    onOpenCoreOptions(selectedGame)
                    onDismiss()
                },
            )
        }

        // Before that, the core can be chosen for this game when its system has more than one.
        if (canSelectCore(selectedGame)) {
            ContextActionEntry(
                label = stringResource(id = R.string.game_context_menu_select_core),
                icon = Icons.Default.Memory,
                onClick = {
                    onOpenCoreOptions(selectedGame)
                    onDismiss()
                },
            )
        }
    }

    LaunchedEffect(Unit) {
        if (requestInitialFocus) {
            runCatching { firstEntryFocusRequester.requestFocus() }
        }
    }

    if (showCustomNameDialog) {
        CustomNameDialog(
            initialName = selectedGame.fileName,
            onDismiss = { showCustomNameDialog = false },
            onConfirm = { newName ->
                showCustomNameDialog = false
                onSetCustomName(selectedGame, newName)
                onDismiss()
            },
        )
    }
}

@Composable
private fun ContextActionHeader(game: Game) {
    Row(
        modifier =
            Modifier.padding(
                start = 16.dp,
                top = 8.dp,
                bottom = 8.dp,
                end = 16.dp,
            ),
    ) {
        LemuroidSmallGameImage(
            modifier =
                Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .align(Alignment.CenterVertically),
            game = game,
        )
        LemuroidGameTexts(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            game = game,
        )
    }
}

@Composable
private fun ContextActionEntry(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    // With a remote (TV) the focused action is highlighted in white, like the TV tabs.
    var focused by remember { mutableStateOf(false) }
    val contentColor = if (focused) Color(0xFF1A1C19) else LocalContentColor.current

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (focused) Color.White else Color.Transparent)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier.padding(start = 8.dp),
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
        )
        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = label,
            color = contentColor,
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun FakeScrim(modalSheetState: SheetState) {
    AnimatedVisibility(
        visible = modalSheetState.targetValue != SheetValue.Hidden,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(BottomSheetDefaults.ScrimColor),
        )
    }
}

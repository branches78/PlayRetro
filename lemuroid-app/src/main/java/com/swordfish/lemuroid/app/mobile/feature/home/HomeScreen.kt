package com.swordfish.lemuroid.app.mobile.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameGridCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LibraryScanProgress
import com.swordfish.lemuroid.app.shared.covers.CoverAspectRatio
import com.swordfish.lemuroid.app.shared.home.HomeDynamicRows
import com.swordfish.lemuroid.app.shared.library.EmptyLibraryLoading
import com.swordfish.lemuroid.app.shared.library.LibraryLoadingProgress
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.utils.android.ComposableLifecycle
import com.swordfish.lemuroid.common.displayDetailsSettingsScreen
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlin.math.floor
import kotlinx.coroutines.flow.combine

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    // "Continue playing" starts the game right away, also with the game details.
    onRecentGameClick: (Game) -> Unit,
    onRecentGameLongClick: (Game) -> Unit,
    onShowAllFavorites: () -> Unit,
) {
    val context = LocalContext.current
    val applicationContext = context.applicationContext

    ComposableLifecycle { _, event ->
        when (event) {
            Lifecycle.Event.ON_RESUME -> {
                viewModel.updatePermissions(applicationContext)
            }
            else -> { }
        }
    }

    val permissionsLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            if (!isGranted) {
                context.displayDetailsSettingsScreen()
            }
        }

    val state = viewModel.getViewStates().collectAsState()

    // A scan started with an empty library (e.g. after choosing the folder) shows its progress instead of the
    // directory card, and the rows appear when it ends.
    // The flag lives outside the screen (EmptyLibraryLoading), so it survives a rotation in the middle of the scan.
    val scanProgressFlow = remember { PendingOperationsMonitor(applicationContext).directoryScanProgress() }
    var scanProgress by remember { mutableStateOf<LibraryLoadingProgress?>(null) }
    var loadingEmptyLibrary by remember { mutableStateOf(EmptyLibraryLoading.isLoading()) }
    LaunchedEffect(Unit) {
        combine(scanProgressFlow, viewModel.getViewStates()) { progress, uiState ->
            progress to EmptyLibraryLoading.update(uiState.showNoGamesCard, progress)
        }.collect { (progress, loading) ->
            scanProgress = progress
            loadingEmptyLibrary = loading
        }
    }

    HomeScreen(
        modifier,
        state.value,
        scanProgress.takeIf { loadingEmptyLibrary },
        onGameClick,
        onGameLongClick,
        onRecentGameClick,
        onRecentGameLongClick,
        onShowAllFavorites,
        {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return@HomeScreen
            }

            permissionsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        { permissionsLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        { viewModel.changeLocalStorageFolder(context) },
    ) // TODO COMPOSE We need to understand what's going to happen here.
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeViewModel.UIState,
    libraryLoadingProgress: LibraryLoadingProgress?,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onRecentGameClick: (Game) -> Unit,
    onRecentGameLongClick: (Game) -> Unit,
    onShowAllFavorites: () -> Unit,
    onEnableNotificationsClicked: () -> Unit,
    onEnableMicrophoneClicked: () -> Unit,
    onSetDirectoryClicked: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnimatedVisibility(state.showNoNotificationPermissionCard) {
            HomeNotification(
                titleId = R.string.home_notification_title,
                messageId = R.string.home_notification_message,
                actionId = R.string.home_notification_action,
                onAction = onEnableNotificationsClicked,
            )
        }
        if (libraryLoadingProgress != null) {
            HomeLibraryLoading(libraryLoadingProgress)
            return@Column
        }
        AnimatedVisibility(state.showNoGamesCard) {
            HomeNotification(
                titleId = R.string.home_empty_title,
                messageId = R.string.home_empty_message,
                actionId = R.string.home_empty_action,
                onAction = onSetDirectoryClicked,
                enabled = !state.indexInProgress,
                outlined = true,
            )
        }
        AnimatedVisibility(state.showNoMicrophonePermissionCard) {
            HomeNotification(
                titleId = R.string.home_microphone_title,
                messageId = R.string.home_microphone_message,
                actionId = R.string.home_microphone_action,
                onAction = onEnableMicrophoneClicked,
            )
        }
        HomeRow(
            stringResource(id = R.string.recent),
            state.recentGames,
            null,
            onRecentGameClick,
            onRecentGameLongClick,
        )
        HomeRow(
            stringResource(id = R.string.favorites),
            state.favoritesGames,
            null,
            onGameClicked,
            onGameLongClick,
            onShowAll = onShowAllFavorites.takeIf { state.hasMoreFavorites },
        )
        DynamicHomeRows(state.dynamicRows, HomeDynamicRows.Placement.BEFORE_DISCOVER, onGameClicked, onGameLongClick)
        HomeRow(
            stringResource(id = R.string.discover),
            state.discoveryGames,
            // With an empty library (e.g. first launch) the row stays hidden: the directory card is shown instead.
            stringResource(id = R.string.home_row_empty_discover).takeUnless { state.showNoGamesCard },
            onGameClicked,
            onGameLongClick,
        )
        DynamicHomeRows(state.dynamicRows, HomeDynamicRows.Placement.AFTER_DISCOVER, onGameClicked, onGameLongClick)
    }
}

@Composable
private fun DynamicHomeRows(
    rows: List<HomeDynamicRows.Row>,
    placement: HomeDynamicRows.Placement,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    rows.filter { it.placement == placement }.forEach { row ->
        key(row.key) {
            HomeRow(row.title, row.games, null, onGameClicked, onGameLongClick)
        }
    }
}

@Composable
private fun HomeRow(
    title: String,
    games: List<Game>,
    emptyMessage: String?,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    // When set, a "Show all" card closes the row.
    onShowAll: (() -> Unit)? = null,
) {
    // Rows without an empty message (recents, favorites) stay hidden until they have games.
    if (games.isEmpty() && emptyMessage == null) {
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Bigger than the names of the games (titleSmall), like the row titles on TV.
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp),
        )
        if (games.isEmpty() && emptyMessage != null) {
            HomeRowEmptyMessage(emptyMessage)
        } else {
            HomeRowGames(games, onGameClicked, onGameLongClick, onShowAll)
        }
    }
}

@Composable
private fun HomeRowEmptyMessage(message: String) {
    // When an always-visible row is empty, a hint with the same height as the cards is shown instead.
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(HOME_ROW_PADDING)
                .height(HOME_ROW_EMPTY_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeRowGames(
    games: List<Game>,
    onGameClicked: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onShowAll: (() -> Unit)?,
) {
    val listState = rememberLazyListState()

    // Lazy rows keep the previous first item in place when a game is added at the start (e.g. the game just
    // played), which leaves the new first cover cut on the left. Go back to the start unless the user scrolled.
    val firstGameId = games.firstOrNull()?.id
    LaunchedEffect(firstGameId) {
        if (listState.firstVisibleItemIndex <= 1) {
            listState.scrollToItem(0)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Measured on the row itself, not on the screen width, which may differ from the room the row really has.
        val cardWidth = homeCardWidth(maxWidth)
        // One cell shape for the whole row (not one per visible card): otherwise the height of the row follows the
        // cards on screen and the cards move up and down. The shape of its system when all the games are of
        // one, square with several, like the TV home (the tallest shape left big gaps around the wide covers).
        val cellRatio =
            remember(games) {
                games.map { CoverAspectRatio.forGameSystem(it) }.distinct().singleOrNull() ?: 1f
            }

        LazyRow(
            state = listState,
            modifier =
                Modifier
                    .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(HOME_ROW_SPACING),
            contentPadding = PaddingValues(HOME_ROW_PADDING),
            // Cards of different sizes: all of them start at the top of the row, like the cards of the TV home.
            verticalAlignment = Alignment.Top,
        ) {
            items(games.size, key = { games[it].id }) { index ->
                val game = games[index]
                // A cell as wide as a third of the row, like the grid of the games of a console, with the cover inside
                // it with its real shape.
                LemuroidGameGridCard(
                    modifier =
                        Modifier
                            .width(cardWidth)
                            .animateItem(),
                    game = game,
                    cellRatio = cellRatio,
                    onClick = { onGameClicked(game) },
                    onLongClick = { onGameLongClick(game) },
                )
            }
            if (onShowAll != null) {
                item(key = SHOW_ALL_KEY) {
                    HomeShowAllCard(
                        modifier =
                            Modifier
                                .animateItem()
                                .width(cardWidth)
                                .height(cardWidth + TEXTS_HEIGHT),
                        onClick = onShowAll,
                    )
                }
            }
        }
    }
}

/** Last card of a row with more games than the ones shown, like the one of the TV home. */
@Composable
private fun HomeShowAllCard(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = modifier,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
                text = stringResource(id = R.string.show_all),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val SHOW_ALL_KEY = "show_all"

/**
 * Three whole covers in the [rowWidth], like the games grid of a system, and the fourth one starting right at the edge
 * of the screen: the end padding of the row only shows at its end, so while scrolling the gap after the third cover is
 * the spacing between cards, and no part of the fourth one may show there. Rounded down to whole pixels.
 */
@Composable
private fun homeCardWidth(rowWidth: Dp): Dp {
    val density = LocalDensity.current
    val width = (rowWidth - HOME_ROW_PADDING - HOME_ROW_SPACING * GAMES_GRID_COLUMNS) / GAMES_GRID_COLUMNS
    return with(density) { floor(width.toPx()).toDp() }
}

// Same layout as the games grid of a system (GamesScreen).
private const val GAMES_GRID_COLUMNS = 3
private val HOME_ROW_PADDING = 16.dp
private val HOME_ROW_SPACING = 12.dp

// Padding, two title lines and the system name below each cover.
private val TEXTS_HEIGHT = 72.dp
private val HOME_ROW_COVER_HEIGHT = 112.dp

// Cover height plus the texts below it: padding, two title lines and the system name.
private val HOME_ROW_EMPTY_HEIGHT = HOME_ROW_COVER_HEIGHT + TEXTS_HEIGHT

@Composable
private fun HomeLibraryLoading(progress: LibraryLoadingProgress) {
    // The same card as "Choose a folder" while its first scan runs, with the same border.
    ElevatedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
                .border(CARD_BORDER_WIDTH, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.library_loading_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.library_loading_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            LibraryScanProgress(progress = progress, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun HomeNotification(
    titleId: Int,
    messageId: Int,
    actionId: Int,
    enabled: Boolean = true,
    // The thin border of the settings cards ("Choose a folder" of an empty library).
    outlined: Boolean = false,
    onAction: () -> Unit = { },
) {
    val borderColor = if (outlined) MaterialTheme.colorScheme.outlineVariant else Color.Transparent
    ElevatedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp)
                .border(CARD_BORDER_WIDTH, borderColor, MaterialTheme.shapes.medium),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(titleId),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(messageId),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                modifier = Modifier.align(Alignment.End),
                onClick = onAction,
                enabled = enabled,
            ) {
                Text(stringResource(id = actionId))
            }
        }
    }
}

private val CARD_BORDER_WIDTH = 1.dp

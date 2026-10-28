package com.swordfish.lemuroid.app.mobile.feature.main

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.about.PlayRetroLogo
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncChanges
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork

@Composable
fun MainTopBar(
    currentRoute: MainRoute,
    // Optional title shown instead of the route title (e.g. the system name inside a system).
    titleOverrideId: Int?,
    navController: NavHostController,
    onUpdateQueryString: (String) -> Unit,
    onToggleGamesViewMode: () -> Unit,
    onToggleSystemsViewMode: () -> Unit,
    mainUIState: MainViewModel.UiState,
) {
    LemuroidTopAppBar(
        route = currentRoute,
        titleOverrideId = titleOverrideId,
        navController = navController,
        mainUIState = mainUIState,
        onUpdateQueryString = onUpdateQueryString,
        onToggleGamesViewMode = onToggleGamesViewMode,
        onToggleSystemsViewMode = onToggleSystemsViewMode,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LemuroidTopAppBar(
    route: MainRoute,
    titleOverrideId: Int?,
    navController: NavController,
    mainUIState: MainViewModel.UiState,
    onUpdateQueryString: (String) -> Unit,
    onToggleGamesViewMode: () -> Unit,
    onToggleSystemsViewMode: () -> Unit,
) {
    val context = LocalContext.current
    // Same color as the screen background: no visible edge between the bar and the content.
    val topBarColor = MaterialTheme.colorScheme.background

    TopAppBar(
        title = {
            if (route == MainRoute.SEARCH) {
                LemuroidSearchView(
                    mainUIState = mainUIState,
                    onUpdateQueryString = onUpdateQueryString,
                )
            } else if (!route.hasBackButton) {
                // Top-level tabs show the logo of the app instead of the tab name.
                PlayRetroLogo(height = TOP_BAR_LOGO_HEIGHT)
            } else {
                Text(text = stringResource(titleOverrideId ?: route.titleId))
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                scrolledContainerColor = topBarColor,
                containerColor = topBarColor,
            ),
        navigationIcon = {
            AnimatedVisibility(
                visible = route.hasBackButton,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(id = R.string.back),
                    )
                }
            }
        },
        actions = {
            LemuroidTopBarActions(
                route = route,
                context = context,
                saveSyncEnabled = mainUIState.saveSyncEnabled,
                onSearchPressed = {
                    navController.navigate(MainRoute.SEARCH.route) { launchSingleTop = true }
                },
                operationsInProgress = mainUIState.operationInProgress,
                gamesGridMode = mainUIState.gamesGridMode,
                onToggleGamesViewMode = onToggleGamesViewMode,
                systemsGridMode = mainUIState.systemsGridMode,
                onToggleSystemsViewMode = onToggleSystemsViewMode,
            )
        },
    )
}

@Composable
fun LemuroidTopBarActions(
    route: MainRoute,
    context: Context,
    saveSyncEnabled: Boolean,
    operationsInProgress: Boolean,
    onSearchPressed: () -> Unit,
    gamesGridMode: Boolean,
    onToggleGamesViewMode: () -> Unit,
    systemsGridMode: Boolean,
    onToggleSystemsViewMode: () -> Unit,
) {
    Row {
        when (route) {
            MainRoute.SYSTEMS -> ViewModeToggle(systemsGridMode, onToggleSystemsViewMode)
            MainRoute.SYSTEM_GAMES -> ViewModeToggle(gamesGridMode, onToggleGamesViewMode)
            else -> { }
        }
        // Left of the search button, and only when there is something to sync and nothing running.
        val hasChanges = SaveSyncChanges.hasChanges.collectAsState().value
        if (saveSyncEnabled && hasChanges && !operationsInProgress) {
            IconButton(
                onClick = { SaveSyncWork.enqueueManualWork(context.applicationContext) },
            ) {
                Icon(
                    Icons.Outlined.CloudSync,
                    stringResource(R.string.save_sync),
                )
            }
        }
        if (route != MainRoute.SEARCH) {
            IconButton(
                onClick = { onSearchPressed() },
            ) {
                Icon(
                    Icons.Filled.Search,
                    stringResource(R.string.title_search),
                )
            }
        }
    }
}

@Composable
private fun LemuroidSearchView(
    mainUIState: MainViewModel.UiState,
    onUpdateQueryString: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(56.dp),
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp, bottom = 8.dp, end = 8.dp),
            shape = RoundedCornerShape(100),
            tonalElevation = 16.dp,
        ) { }

        TextField(
            value = mainUIState.searchQuery,
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            onValueChange = { onUpdateQueryString(it) },
            singleLine = true,
            keyboardActions =
                KeyboardActions(
                    onDone = { focusManager.clearFocus(true) },
                ),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
        )
    }
}

/** Grid/list switch of the Consoles tab and of the games of a console. The icon shows the mode to switch to. */
@Composable
private fun ViewModeToggle(
    gridMode: Boolean,
    onToggle: () -> Unit,
) {
    IconButton(onClick = onToggle) {
        if (gridMode) {
            Icon(
                Icons.AutoMirrored.Outlined.ViewList,
                stringResource(R.string.games_view_mode_list),
            )
        } else {
            Icon(
                Icons.Outlined.GridView,
                stringResource(R.string.games_view_mode_grid),
            )
        }
    }
}

// Logo of the app in the top bar of the tabs (about the height of the title text).
private val TOP_BAR_LOGO_HEIGHT = 22.dp

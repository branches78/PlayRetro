package com.swordfish.lemuroid.app.mobile.feature.games

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidEmptyView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameGridCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameListRow
import com.swordfish.lemuroid.app.shared.covers.CoverAspectRatio
import com.swordfish.lemuroid.app.utils.games.GameCardSubtitle
import com.swordfish.lemuroid.lib.library.db.entity.Game

private const val GRID_COLUMNS = 3

@Composable
fun GamesScreen(
    modifier: Modifier = Modifier,
    viewModel: GamesViewModel,
    gridMode: Boolean,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
) {
    val games = viewModel.games.collectAsLazyPagingItems()
    // Kept here and not inside the grid or list: they are not shown while the games load (also for a moment when
    // coming back from the details of a game), and their position must survive it.
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    if (games.itemCount == 0) {
        if (games.loadState.refresh is LoadState.NotLoading) LemuroidEmptyView()
        return
    }

    if (gridMode) {
        GamesGrid(modifier, games, gridState, onGameClick, onGameLongClick)
    } else {
        GamesList(modifier, games, listState, onGameClick, onGameLongClick, onGameFavoriteToggle)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GamesList(
    modifier: Modifier,
    games: LazyPagingItems<Game>,
    state: LazyListState,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
    onGameFavoriteToggle: (Game, Boolean) -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), state = state) {
        items(games.itemCount, key = { games[it]?.id ?: it }) { index ->
            val game = games[index] ?: return@items

            LemuroidGameListRow(
                modifier = Modifier.animateItem(),
                game = game,
                // Inside a system: its developer instead of the system, the same for every game.
                subtitle = GameCardSubtitle.DEVELOPER,
                onClick = { onGameClick(game) },
                onLongClick = { onGameLongClick(game) },
                onFavoriteToggle = { isFavorite -> onGameFavoriteToggle(game, isFavorite) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GamesGrid(
    modifier: Modifier,
    games: LazyPagingItems<Game>,
    state: LazyGridState,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        state = state,
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(games.itemCount, key = { games[it]?.id ?: it }) { index ->
            val game = games[index] ?: return@items

            LemuroidGameGridCard(
                modifier = Modifier.animateItem(),
                game = game,
                // Same cell for every game so the grid rows stay aligned: the usual shape of the covers of the system.
                cellRatio = CoverAspectRatio.forGameSystem(game),
                subtitle = GameCardSubtitle.DEVELOPER,
                onClick = { onGameClick(game) },
                onLongClick = { onGameLongClick(game) },
            )
        }
    }
}

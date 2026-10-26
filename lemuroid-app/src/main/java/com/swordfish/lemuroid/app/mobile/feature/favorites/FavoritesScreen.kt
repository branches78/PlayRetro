package com.swordfish.lemuroid.app.mobile.feature.favorites

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidEmptyView
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidGameGridCard
import com.swordfish.lemuroid.lib.library.db.entity.Game

/** Every favorite game, opened from the "Show all" card of the home favorites row. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel,
    onGameClick: (Game) -> Unit,
    onGameLongClick: (Game) -> Unit,
) {
    val games = viewModel.favorites.collectAsLazyPagingItems()
    // Kept outside the grid, which is not shown while the games load (also for a moment when coming back from the
    // details of a game): its position must survive it.
    val gridState = rememberLazyGridState()

    if (games.itemCount == 0) {
        if (games.loadState.refresh is LoadState.NotLoading) LemuroidEmptyView()
        return
    }

    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        state = gridState,
        // Same grid as the games of a system.
        contentPadding = PaddingValues(16.dp),
        columns = GridCells.Fixed(GRID_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(games.itemCount, key = { games[it]?.id ?: it }) { index ->
            val game = games[index] ?: return@items
            // Games of every system: square cells, each card with the shape of its cover.
            LemuroidGameGridCard(
                modifier = Modifier.animateItem(),
                game = game,
                cellRatio = 1f,
                onClick = { onGameClick(game) },
                onLongClick = { onGameLongClick(game) },
            )
        }
    }
}

private const val GRID_COLUMNS = 3

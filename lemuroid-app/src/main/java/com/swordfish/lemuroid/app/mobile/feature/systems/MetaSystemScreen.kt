package com.swordfish.lemuroid.app.mobile.feature.systems

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.swordfish.lemuroid.app.mobile.feature.home.EmptyLibraryCard
import com.swordfish.lemuroid.app.mobile.feature.home.HomeLibraryLoading
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidSystemCard
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LemuroidSystemListRow
import com.swordfish.lemuroid.app.shared.library.EmptyLibraryLoading
import com.swordfish.lemuroid.app.shared.library.PendingOperationsMonitor
import com.swordfish.lemuroid.app.shared.settings.LibrarySourceActivity
import com.swordfish.lemuroid.app.shared.systems.MetaSystemInfo

@Composable
fun MetaSystemsScreen(
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: MetaSystemsViewModel,
    gridMode: Boolean,
) {
    val context = LocalContext.current
    val scanProgressFlow = remember { PendingOperationsMonitor(context.applicationContext).directoryScanProgress() }
    val scanProgress = scanProgressFlow.collectAsState(null).value
    // Null until the library is read, so the empty library card doesn't show for a moment.
    val metaSystems = viewModel.availableMetaSystems.collectAsState(null).value ?: return

    // Like the "Home" tab: the progress of the first scan, or the "Choose a folder" card of an empty library.
    val loadingEmptyLibrary = EmptyLibraryLoading.update(metaSystems.isEmpty(), scanProgress)
    if (loadingEmptyLibrary || metaSystems.isEmpty()) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 16.dp),
        ) {
            if (loadingEmptyLibrary && scanProgress != null) {
                HomeLibraryLoading(scanProgress)
            } else {
                EmptyLibraryCard(enabled = scanProgress == null, onSetDirectory = { LibrarySourceActivity.show(context) })
            }
        }
        return
    }

    MetaSystemsScreen(
        modifier = modifier,
        metaSystems = metaSystems,
        gridMode = gridMode,
        onSystemClicked = { navController.navigate("systems/${it.metaSystem.name}") },
    )
}

@Composable
private fun MetaSystemsScreen(
    modifier: Modifier = Modifier,
    metaSystems: List<MetaSystemInfo>,
    gridMode: Boolean,
    onSystemClicked: (MetaSystemInfo) -> Unit,
) {
    if (gridMode) {
        MetaSystemsGrid(modifier, metaSystems, onSystemClicked)
    } else {
        MetaSystemsList(modifier, metaSystems, onSystemClicked)
    }
}

@Composable
private fun MetaSystemsList(
    modifier: Modifier,
    metaSystems: List<MetaSystemInfo>,
    onSystemClicked: (MetaSystemInfo) -> Unit,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(metaSystems.size, key = { metaSystems[it].metaSystem }) { index ->
            val system = metaSystems[index]
            LemuroidSystemListRow(
                modifier = Modifier.animateItem(),
                system = system,
                onClick = { onSystemClicked(system) },
            )
        }
    }
}

@Composable
private fun MetaSystemsGrid(
    modifier: Modifier,
    metaSystems: List<MetaSystemInfo>,
    onSystemClicked: (MetaSystemInfo) -> Unit,
) {
    // Same grid as the games of a console.
    LazyVerticalGrid(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        columns = GridCells.Fixed(GRID_COLUMNS),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(metaSystems.size, key = { metaSystems[it].metaSystem }) { index ->
            val system = metaSystems[index]
            LemuroidSystemCard(
                modifier = Modifier.animateItem(),
                system = system,
                onClick = { onSystemClicked(system) },
            )
        }
    }
}

private const val GRID_COLUMNS = 3

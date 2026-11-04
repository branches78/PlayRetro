package com.swordfish.lemuroid.app.mobile.feature.home

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LibraryScanProgress
import com.swordfish.lemuroid.app.shared.library.LibraryLoadingProgress

/** Cards of the "Home" and "Consoles" tabs (permissions, empty library and its first scan). */

@Composable
internal fun HomeLibraryLoading(progress: LibraryLoadingProgress) {
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
            CardTitle(R.string.library_loading_title)
            Text(
                text = stringResource(R.string.library_loading_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            LibraryScanProgress(progress = progress, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun HomeNotification(
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
            CardTitle(titleId)
            Text(
                text = stringResource(messageId),
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                onClick = onAction,
                enabled = enabled,
            ) {
                Text(stringResource(id = actionId))
            }
        }
    }
}

/** "Choose a folder" card of an empty library. */
@Composable
internal fun EmptyLibraryCard(
    enabled: Boolean,
    onSetDirectory: () -> Unit,
) {
    HomeNotification(
        titleId = R.string.home_empty_title,
        messageId = R.string.home_empty_message,
        actionId = R.string.home_empty_action,
        onAction = onSetDirectory,
        enabled = enabled,
        outlined = true,
    )
}

// Titles and buttons of the cards are centered, also on TV (tv_home_empty_library.xml).
@Composable
private fun CardTitle(titleId: Int) {
    Text(
        text = stringResource(titleId),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private val CARD_BORDER_WIDTH = 1.dp

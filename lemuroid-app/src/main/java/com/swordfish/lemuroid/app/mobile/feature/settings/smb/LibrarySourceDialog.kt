package com.swordfish.lemuroid.app.mobile.feature.settings.smb

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.utils.android.PlayRetroButton
import com.swordfish.lemuroid.lib.storage.smb.SmbConfigStore

/**
 * Asks where the games library comes from: a folder of the device or a network share (SMB). Only one of them is used
 * at a time; the current one is marked. Opened by "Directory" in the settings.
 */
@Composable
fun LibrarySourceDialog(
    currentSource: SmbConfigStore.LibrarySource,
    // Name of the network share in use, if any.
    smbLabel: String?,
    onLocalFolder: () -> Unit,
    onNetworkShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val firstOption = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_source_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.library_source_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SourceOption(
                    modifier = Modifier.focusRequester(firstOption),
                    icon = Icons.Outlined.Folder,
                    title = stringResource(R.string.library_source_local),
                    subtitle = stringResource(R.string.library_source_local_description),
                    selected = currentSource == SmbConfigStore.LibrarySource.LOCAL,
                    onClick = onLocalFolder,
                )
                SourceOption(
                    icon = Icons.Outlined.Dns,
                    title = stringResource(R.string.library_source_smb),
                    subtitle =
                        smbLabel?.let { stringResource(R.string.library_source_smb_in_use, it) }
                            ?: stringResource(R.string.library_source_smb_description),
                    selected = currentSource == SmbConfigStore.LibrarySource.SMB,
                    onClick = onNetworkShare,
                )
            }
        },
        // Cancel centered below the options (AlertDialog would put it at the end).
        confirmButton = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PlayRetroButton(text = stringResource(R.string.cancel), onClick = onDismiss)
            }
        },
    )

    // With a remote the focus starts on the first option.
    LaunchedEffect(Unit) { runCatching { firstOption.requestFocus() } }
}

@Composable
private fun SourceOption(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    val borderColor =
        when {
            focused -> Color.White
            selected -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .border(BorderStroke(if (focused || selected) 2.dp else 1.dp, borderColor), shape)
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

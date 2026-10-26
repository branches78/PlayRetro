package com.swordfish.lemuroid.app.mobile.feature.settings.savesync

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncChanges
import com.swordfish.lemuroid.app.shared.savesync.SaveSyncWork
import com.swordfish.lemuroid.app.shared.settings.LibrarySourceActivity
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsListMultiSelect
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.stringsSetPreferenceState

@Composable
fun SaveSyncSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SaveSyncSettingsViewModel,
) {
    val context = LocalContext.current

    val saveSyncState =
        viewModel.uiState
            .collectAsState()
            .value

    val hasChanges = SaveSyncChanges.hasChanges.collectAsState().value

    val isSyncInProgress =
        viewModel.saveSyncInProgress
            .collectAsState(true)
            .value

    // The rows stay enabled during a sync, so the focus doesn't jump on TV: the actions are ignored instead.
    LemuroidSettingsPage(modifier = modifier.fillMaxSize()) {
        LemuroidCardSettingsGroup {
            // Play variant: Google Drive or the network share of the library.
            if (saveSyncState.hasProviderChoice) {
                val providerNames =
                    listOf(
                        stringResource(R.string.save_sync_provider_drive),
                        stringResource(com.swordfish.lemuroid.lib.R.string.smb_storage),
                    )
                LemuroidSettingsList(
                    state =
                        indexPreferenceState(
                            com.swordfish.lemuroid.lib.R.string.pref_key_save_sync_provider,
                            "drive",
                            listOf("drive", "smb"),
                        ),
                    title = { Text(text = stringResource(id = R.string.save_sync_provider)) },
                    items = providerNames,
                    enabled = !isSyncInProgress,
                    onItemSelected = { _, _ -> viewModel.refresh() },
                )
            }
            LemuroidSettingsMenuLink(
                title = {
                    Text(
                        text =
                            stringResource(
                                id = R.string.settings_save_sync_configure,
                                saveSyncState.provider,
                            ),
                    )
                },
                subtitle = { Text(text = saveSyncState.configInfo) },
                // The network share is configured with "Directory": the same chooser of the source of the games.
                onClick = onClick@{
                    if (isSyncInProgress) return@onClick
                    val activity = saveSyncState.settingsActivity
                    if (activity != null) {
                        context.startActivity(Intent(context, activity))
                    } else {
                        LibrarySourceActivity.show(context)
                    }
                },
            )
            LemuroidSettingsSwitch(
                state = booleanPreferenceState(R.string.pref_key_save_sync_enable, default = true),
                title = { Text(text = stringResource(id = R.string.settings_save_sync_include_saves)) },
                subtitle = {
                    Text(
                        text =
                            stringResource(
                                id = R.string.settings_save_sync_include_saves_description,
                                saveSyncState.savesSpace,
                            ),
                    )
                },
                enabled = saveSyncState.isConfigured,
            )
            LemuroidSettingsListMultiSelect(
                state =
                    stringsSetPreferenceState(
                        stringResource(R.string.pref_key_save_sync_cores),
                        emptySet(),
                    ),
                title = { Text(text = stringResource(id = R.string.settings_save_sync_include_states)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_save_sync_include_states_description)) },
                entryValues = saveSyncState.coreNames,
                entries = saveSyncState.coreVisibleNames,
                enabled = saveSyncState.isConfigured,
                confirmButton = stringResource(id = R.string.ok),
            )
            LemuroidSettingsSwitch(
                state = booleanPreferenceState(R.string.pref_key_save_sync_auto, default = false),
                title = { Text(text = stringResource(id = R.string.settings_save_sync_enable_auto)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_save_sync_enable_auto_description)) },
                enabled = saveSyncState.isConfigured,
            )
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.settings_save_sync_refresh)) },
                subtitle = {
                    Text(
                        text =
                            stringResource(
                                id = R.string.settings_save_sync_refresh_description,
                                saveSyncState.lastSyncInfo,
                            ),
                    )
                },
                // Only when there is something to sync.
                enabled = saveSyncState.isConfigured && hasChanges,
                onClick = { if (!isSyncInProgress) SaveSyncWork.enqueueManualWork(context) },
            )
        }
    }
}

package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.feature.main.navigateToRoute
import com.swordfish.lemuroid.app.mobile.feature.settings.smb.LibrarySourceDialog
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.LibraryScanProgress
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.rememberLibraryScanProgress
import com.swordfish.lemuroid.app.shared.GameInteractor
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.app.utils.android.PlayRetroButton
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsList
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSlider
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.intPreferenceState
import com.swordfish.lemuroid.app.utils.android.stringListResource
import com.swordfish.lemuroid.lib.storage.smb.SmbConfigStore

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    navController: NavController,
    // On TV, touch and phone only options are hidden and the TV folder picker is used.
    isTV: Boolean = false,
) {
    val context = LocalContext.current
    val state =
        viewModel.uiState
            .collectAsState(SettingsViewModel.State())
            .value

    val scanInProgress =
        viewModel.directoryScanInProgress
            .collectAsState(false)
            .value

    val indexingInProgress =
        viewModel.indexingInProgress
            .collectAsState(false)
            .value

    val cacheState =
        viewModel.cacheState
            .collectAsState()
            .value

    // "Directory" asks first whether the games come from a folder of the device or a network share.
    var showSourceDialog by remember { mutableStateOf(false) }
    val smbStore = remember { SmbConfigStore(context) }
    if (showSourceDialog) {
        LibrarySourceDialog(
            currentSource = smbStore.librarySource,
            smbLabel = smbStore.load()?.label?.takeIf { smbStore.librarySource == SmbConfigStore.LibrarySource.SMB },
            onLocalFolder = {
                showSourceDialog = false
                if (isTV) TVHelper.launchFolderPicker(context) else viewModel.changeLocalStorageFolder()
            },
            onNetworkShare = {
                showSourceDialog = false
                navController.navigateToRoute(MainRoute.SETTINGS_SMB)
            },
            onDismiss = { showSourceDialog = false },
        )
    }

    LemuroidSettingsPage(modifier = modifier) {
        RomsSettings(
            state = state,
            cacheState = cacheState,
            onChangeFolder = { showSourceDialog = true },
            indexingInProgress = indexingInProgress,
            scanInProgress = scanInProgress,
            navController = navController,
        )
        DisplaySettings(isTV = isTV)
        SoundSettings()
        InputSettings(navController = navController, isTV = isTV)
        MiscSettings(
            indexingInProgress = indexingInProgress,
            navController = navController,
            onResetAllSettings = { viewModel.resetAllSettings() },
        )
    }
}

@Composable
private fun MiscSettings(
    indexingInProgress: Boolean,
    navController: NavController,
    onResetAllSettings: () -> Unit,
) {
    val factoryResetDialogState = remember { mutableStateOf(false) }

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_misc)) },
    ) {
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_display_bios_info)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_display_bios_info))
            },
            enabled = !indexingInProgress,
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_BIOS) },
        )
        // Always shown, even when every system has a single core.
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_open_cores_selection)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_open_cores_selection))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_CORES_SELECTION) },
        )
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_reset_settings)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_reset_settings)) },
            onClick = { factoryResetDialogState.value = true },
        )
    }

    if (factoryResetDialogState.value) {
        FactoryResetDialog(factoryResetDialogState, onResetAllSettings)
    }
}

@Composable
private fun InputSettings(
    navController: NavController,
    isTV: Boolean,
) {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_input)) },
    ) {
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_gamepad_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_gamepad_settings))
            },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_INPUT_DEVICES) },
        )
        if (!isTV) {
            LemuroidSettingsList(
                state =
                    indexPreferenceState(
                        R.string.pref_key_haptic_feedback_mode,
                        "press",
                        stringListResource(R.array.pref_key_haptic_feedback_mode_values),
                    ),
                title = {
                    Text(text = stringResource(id = R.string.settings_title_enable_touch_feedback))
                },
                items = stringListResource(R.array.pref_key_haptic_feedback_mode_display_names),
            )
        }
        val rumbleEnabled = booleanPreferenceState(R.string.pref_key_enable_rumble, false)
        LemuroidSettingsSwitch(
            state = rumbleEnabled,
            title = { Text(text = stringResource(id = R.string.settings_title_enable_rumble)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_rumble)) },
        )
        if (!isTV) {
            LemuroidSettingsSwitch(
                enabled = rumbleEnabled.value,
                state = booleanPreferenceState(R.string.pref_key_enable_device_rumble, false),
                title = { Text(text = stringResource(id = R.string.settings_title_enable_device_rumble)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_device_rumble)) },
            )
            LemuroidSettingsSlider(
                state =
                    intPreferenceState(
                        key = stringResource(id = R.string.pref_key_tilt_sensitivity_index),
                        default = 6,
                    ),
                steps = 10,
                valueRange = 0f..10f,
                enabled = true,
                title = { Text(text = stringResource(R.string.settings_title_tilt_sensitivity)) },
            )
        }
    }
}

@Composable
private fun SoundSettings() {
    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_sound)) },
    ) {
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_low_latency_audio, false),
            title = { Text(text = stringResource(id = R.string.settings_title_low_latency_audio)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_low_latency_audio)) },
        )
    }
}

@Composable
private fun DisplaySettings(isTV: Boolean) {
    val hdMode = booleanPreferenceState(R.string.pref_key_hd_mode, false)
    val immersiveMode = booleanPreferenceState(R.string.pref_key_enable_immersive_mode, false)

    LemuroidCardSettingsGroup(
        title = { Text(text = stringResource(id = R.string.settings_category_display)) },
    ) {
        LemuroidSettingsList(
            enabled = !hdMode.value,
            state =
                indexPreferenceState(
                    R.string.pref_key_shader_filter,
                    "auto",
                    stringListResource(R.array.pref_key_shader_filter_values).toList(),
                ),
            title = { Text(text = stringResource(id = R.string.display_filter)) },
            items = stringListResource(R.array.pref_key_shader_filter_display_names),
        )
        if (!isTV) {
            LemuroidSettingsSwitch(
                state = immersiveMode,
                title = { Text(text = stringResource(id = R.string.settings_title_immersive_mode)) },
                subtitle = { Text(text = stringResource(id = R.string.settings_description_immersive_mode)) },
            )
        }
        LemuroidSettingsSwitch(
            state = hdMode,
            title = { Text(text = stringResource(id = R.string.settings_title_hd_mode)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_mode)) },
        )
        LemuroidSettingsSlider(
            enabled = hdMode.value,
            state =
                intPreferenceState(
                    key = stringResource(id = R.string.pref_key_hd_mode_quality),
                    default = 2,
                ),
            steps = 1,
            valueRange = 0f..2f,
            title = { Text(text = stringResource(R.string.settings_title_hd_quality)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_hd_quality)) },
        )
        if (!isTV) {
            val alignmentState =
                indexPreferenceState(
                    R.string.pref_key_viewport_alignment,
                    "center",
                    stringListResource(R.array.pref_key_viewport_alignment_values).toList(),
                )
            val alignmentNames = stringListResource(R.array.pref_key_viewport_alignment_display_names)
            LemuroidSettingsList(
                state = alignmentState,
                title = { Text(text = stringResource(id = R.string.settings_title_viewport_alignment)) },
                items = alignmentNames,
                // Show the description together with the selected value.
                useSelectedValueAsSubtitle = false,
                subtitle = {
                    val selected = alignmentNames.getOrNull(alignmentState.value)
                    val description = stringResource(id = R.string.settings_description_viewport_alignment)
                    Text(text = if (selected != null) "$selected\n$description" else description)
                },
            )

            // Orientation of the screen when a game starts; "Auto" follows the device, as before.
            val orientationState =
                indexPreferenceState(
                    R.string.pref_key_game_orientation,
                    "auto",
                    stringListResource(R.array.pref_key_game_orientation_values).toList(),
                )
            val orientationNames = stringListResource(R.array.pref_key_game_orientation_display_names)
            LemuroidSettingsList(
                state = orientationState,
                title = { Text(text = stringResource(id = R.string.settings_title_game_orientation)) },
                items = orientationNames,
                useSelectedValueAsSubtitle = false,
                subtitle = {
                    val selected = orientationNames.getOrNull(orientationState.value)
                    val description = stringResource(id = R.string.settings_description_game_orientation)
                    Text(text = if (selected != null) "$selected\n$description" else description)
                },
            )
        }
    }
}

@Composable
private fun FactoryResetDialog(
    factoryResetDialogState: MutableState<Boolean>,
    onResetAllSettings: () -> Unit,
) {
    val onDismiss = {
        factoryResetDialogState.value = false
    }
    AlertDialog(
        title = { Text(stringResource(id = R.string.reset_settings_warning_message_title)) },
        text = { Text(stringResource(id = R.string.reset_settings_warning_message_description)) },
        onDismissRequest = onDismiss,
        confirmButton = {
            PlayRetroButton(
                text = stringResource(id = R.string.ok),
                onClick = {
                    onResetAllSettings()
                    onDismiss()
                },
            )
        },
        dismissButton = {
            PlayRetroButton(text = stringResource(id = R.string.cancel), onClick = onDismiss)
        },
    )
}

@Composable
private fun RomsSettings(
    state: SettingsViewModel.State,
    cacheState: SettingsViewModel.CacheState?,
    onChangeFolder: () -> Unit,
    indexingInProgress: Boolean,
    scanInProgress: Boolean,
    navController: NavController,
) {
    val context = LocalContext.current

    val currentDirectory = state.currentDirectory
    val emptyDirectory = stringResource(R.string.none)

    // The network share when it is the source of the library, otherwise the local folder.
    val currentDirectoryName =
        remember(state.currentDirectory) {
            val smbStore = SmbConfigStore(context)
            val smbLabel = smbStore.load()?.label?.takeIf { smbStore.librarySource == SmbConfigStore.LibrarySource.SMB }
            smbLabel ?: runCatching {
                DocumentFile.fromTreeUri(context, Uri.parse(currentDirectory))?.name
            }.getOrNull() ?: emptyDirectory
        }

    LemuroidCardSettingsGroup(title = { Text(text = stringResource(id = R.string.roms)) }) {
        val scanProgress by rememberLibraryScanProgress()
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.directory)) },
            subtitle = {
                Column {
                    Text(text = currentDirectoryName)
                    // While the directory is scanned (e.g. "Rescan"), its progress is shown below the folder.
                    scanProgress?.let {
                        LibraryScanProgress(progress = it, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            onClick = { onChangeFolder() },
            enabled = !indexingInProgress,
        )
        // A single row that changes between "Rescan" and "Stop": with two different rows the focused one would be
        // removed and the remote focus would jump elsewhere. It stays enabled for the same reason, and does nothing
        // while the cores are updated after a scan.
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = if (scanInProgress) R.string.stop else R.string.rescan)) },
            onClick = {
                when {
                    scanInProgress -> LibraryIndexScheduler.cancelLibrarySync(context)
                    !indexingInProgress -> LibraryIndexScheduler.scheduleLibrarySync(context)
                }
            },
        )
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_show_game_details, GameInteractor.SHOW_GAME_DETAILS_DEFAULT),
            title = { Text(text = stringResource(id = R.string.settings_title_show_game_details)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_show_game_details)) },
        )
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_autosave, true),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_autosave)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_autosave)) },
        )
        if (cacheState != null) {
            LemuroidSettingsList(
                title = { Text(text = stringResource(R.string.settings_title_maximum_cache_usage)) },
                items = cacheState.displayNames,
                state =
                    indexPreferenceState(
                        R.string.pref_key_max_cache_size,
                        cacheState.default,
                        cacheState.values,
                    ),
            )
        }
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_allow_direct_game_load, true),
            title = { Text(text = stringResource(id = R.string.settings_title_direct_game_load)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_direct_game_load)) },
        )
        // Only available in the "play" flavor (Google Drive).
        if (state.isSaveSyncSupported) {
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.settings_title_save_sync)) },
                subtitle = {
                    Text(text = stringResource(id = R.string.settings_description_save_sync))
                },
                onClick = { navController.navigateToRoute(MainRoute.SETTINGS_SAVE_SYNC) },
            )
        }
    }
}

package com.swordfish.lemuroid.app.mobile.feature.settings.general

import android.net.Uri
import androidx.annotation.StringRes
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
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSection
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSlider
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsSwitch
import com.swordfish.lemuroid.app.utils.android.settings.LocalSettingsChoiceHost
import com.swordfish.lemuroid.app.utils.android.settings.SettingsChoiceRequest
import com.swordfish.lemuroid.app.utils.android.settings.SettingsOpenArrow
import com.swordfish.lemuroid.app.utils.android.settings.booleanPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.indexPreferenceState
import com.swordfish.lemuroid.app.utils.android.settings.intPreferenceState
import com.swordfish.lemuroid.app.utils.android.stringListResource
import com.swordfish.lemuroid.lib.storage.smb.SmbConfigStore

/** Sections of the settings: screens of their own on mobile, the list on the left of the TV settings. */
enum class SettingsSection(
    @StringRes val titleId: Int,
    val route: MainRoute,
) {
    ROMS(R.string.roms, MainRoute.SETTINGS_ROMS),
    DISPLAY(R.string.settings_category_display, MainRoute.SETTINGS_DISPLAY),
    SOUND(R.string.settings_category_sound, MainRoute.SETTINGS_SOUND),
    INPUT(R.string.settings_category_input, MainRoute.SETTINGS_INPUT),
    MISC(R.string.settings_category_misc, MainRoute.SETTINGS_MISC),
}

/**
 * Options of a [section], in a card without title. On TV ([isTV]) touch and phone only options are hidden and the TV
 * folder picker is used.
 */
@Composable
fun SettingsSectionContent(
    section: SettingsSection,
    viewModel: SettingsViewModel,
    navController: NavController,
    isTV: Boolean,
) {
    when (section) {
        SettingsSection.ROMS -> RomsSettings(viewModel = viewModel, navController = navController, isTV = isTV)
        SettingsSection.DISPLAY -> DisplaySettings(isTV = isTV)
        SettingsSection.SOUND -> SoundSettings()
        SettingsSection.INPUT -> InputSettings(navController = navController, isTV = isTV)
        SettingsSection.MISC -> MiscSettings(viewModel = viewModel, navController = navController)
    }
}

/**
 * Main settings screen on mobile: a card for each section, with its name, that opens its options. The TV has its own
 * (TVSettingsHome).
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    navController: NavController,
) {
    LemuroidSettingsPage(modifier = modifier) {
        SettingsSection.entries.forEach { section ->
            LemuroidSettingsSection {
                LemuroidSettingsMenuLink(
                    title = { Text(text = stringResource(id = section.titleId)) },
                    action = { SettingsOpenArrow() },
                    onClick = { navController.navigateToRoute(section.route) },
                )
            }
        }
    }
}

/** Options of a section of the settings on mobile, opened from its card. */
@Composable
fun SettingsSectionScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    navController: NavController,
    section: SettingsSection,
) {
    // The top bar shows the name of the section: the card has no title.
    LemuroidSettingsPage(modifier = modifier) {
        SettingsSectionContent(section, viewModel, navController, isTV = false)
    }
}

/** The directory of the games and, while it is scanned (e.g. "Rescan"), the progress below it. */
@Composable
private fun DirectorySubtitle(currentDirectory: String) {
    val scanProgress by rememberLibraryScanProgress()
    Column {
        Text(text = rememberDirectoryName(currentDirectory) ?: stringResource(R.string.not_configured))
        scanProgress?.let {
            LibraryScanProgress(progress = it, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

/** Name of the network share when it is the source of the library, otherwise of the local folder; null without one. */
@Composable
private fun rememberDirectoryName(currentDirectory: String): String? {
    val context = LocalContext.current
    return remember(currentDirectory) {
        val smbStore = SmbConfigStore(context)
        val smbLabel = smbStore.load()?.label?.takeIf { smbStore.librarySource == SmbConfigStore.LibrarySource.SMB }
        smbLabel ?: runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(currentDirectory))?.name
        }.getOrNull()
    }
}

@Composable
private fun MiscSettings(
    viewModel: SettingsViewModel,
    navController: NavController,
) {
    val factoryResetDialogState = remember { mutableStateOf(false) }
    val indexingInProgress = viewModel.indexingInProgress.collectAsState(false).value
    val onResetAllSettings = { viewModel.resetAllSettings() }

    LemuroidCardSettingsGroup(
        title = null,
    ) {
        DebugLanguageSetting()
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_display_bios_info)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_display_bios_info))
            },
            enabled = !indexingInProgress,
            action = { SettingsOpenArrow() },
            onClick = { navController.navigateToRoute(MainRoute.SETTINGS_BIOS) },
        )
        // Always shown, even when every system has a single core.
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_open_cores_selection)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_open_cores_selection))
            },
            action = { SettingsOpenArrow() },
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
        title = null,
    ) {
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.settings_title_gamepad_settings)) },
            subtitle = {
                Text(text = stringResource(id = R.string.settings_description_gamepad_settings))
            },
            action = { SettingsOpenArrow() },
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
    LemuroidCardSettingsGroup {
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_low_latency_audio, false),
            title = { Text(text = stringResource(id = R.string.settings_title_low_latency_audio)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_low_latency_audio)) },
        )
    }
}

@Composable
private fun DisplaySettings(
    isTV: Boolean,
) {
    val hdMode = booleanPreferenceState(R.string.pref_key_hd_mode, false)
    val immersiveMode = booleanPreferenceState(R.string.pref_key_enable_immersive_mode, false)

    LemuroidCardSettingsGroup(
        title = null,
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
    viewModel: SettingsViewModel,
    navController: NavController,
    isTV: Boolean,
) {
    val context = LocalContext.current
    val state = viewModel.uiState.collectAsState().value
    val scanInProgress = viewModel.directoryScanInProgress.collectAsState(false).value
    val indexingInProgress = viewModel.indexingInProgress.collectAsState(false).value
    val cacheState = viewModel.cacheState

    // "Directory" asks first whether the games come from a folder of the device or a network share.
    var showSourceDialog by remember { mutableStateOf(false) }
    if (showSourceDialog) {
        val smbStore = remember { SmbConfigStore(context) }
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

    val configuredDirectoryName = rememberDirectoryName(state.currentDirectory)
    val hasDirectory = configuredDirectoryName != null

    // On mobile the two sources are a screen of their own instead of the dialog.
    val choiceHost = LocalSettingsChoiceHost.current
    val sourceItems =
        listOf(stringResource(R.string.library_source_local), stringResource(R.string.library_source_smb))
    val smbSubtitle =
        if (configuredDirectoryName != null && SmbConfigStore(context).librarySource == SmbConfigStore.LibrarySource.SMB) {
            stringResource(R.string.library_source_smb_in_use, configuredDirectoryName)
        } else {
            stringResource(R.string.library_source_smb_description)
        }
    val sourceSubtitles = listOf(stringResource(R.string.library_source_local_description), smbSubtitle)
    val openArrow: @Composable () -> Unit = { SettingsOpenArrow() }
    val openSourceChoice = {
        val currentSource = SmbConfigStore(context).librarySource
        choiceHost?.open(
            SettingsChoiceRequest(
                title = { Text(text = stringResource(R.string.library_source_title)) },
                subtitle = { Text(text = stringResource(R.string.library_source_description)) },
                items = sourceItems,
                itemSubtitles = sourceSubtitles,
                isSelected = { (it == 1) == (currentSource == SmbConfigStore.LibrarySource.SMB) },
                onClick = { index ->
                    if (index == 0) {
                        if (isTV) TVHelper.launchFolderPicker(context) else viewModel.changeLocalStorageFolder()
                    } else {
                        navController.navigateToRoute(MainRoute.SETTINGS_SMB)
                    }
                },
            ),
        )
    }

    LemuroidCardSettingsGroup {
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = R.string.directory)) },
            subtitle = { DirectorySubtitle(state.currentDirectory) },
            action = openArrow.takeIf { choiceHost != null },
            onClick = { if (choiceHost != null) openSourceChoice() else showSourceDialog = true },
            enabled = !indexingInProgress,
        )
        // A single row that changes between "Rescan" and "Stop": with two different rows the focused one would be
        // removed and the remote focus would jump elsewhere. It stays enabled for the same reason, and does nothing
        // while the cores are updated after a scan.
        // Without a directory every option of the card but "Directory" is disabled: there is nothing to scan or play.
        LemuroidSettingsMenuLink(
            title = { Text(text = stringResource(id = if (scanInProgress) R.string.stop else R.string.rescan)) },
            enabled = hasDirectory || scanInProgress,
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
            enabled = hasDirectory,
        )
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_autosave, true),
            title = { Text(text = stringResource(id = R.string.settings_title_enable_autosave)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_enable_autosave)) },
            enabled = hasDirectory,
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
                enabled = hasDirectory,
            )
        }
        LemuroidSettingsSwitch(
            state = booleanPreferenceState(R.string.pref_key_allow_direct_game_load, true),
            title = { Text(text = stringResource(id = R.string.settings_title_direct_game_load)) },
            subtitle = { Text(text = stringResource(id = R.string.settings_description_direct_game_load)) },
            enabled = hasDirectory,
        )
        // Only available in the "play" flavor (Google Drive).
        if (state.isSaveSyncSupported) {
            LemuroidSettingsMenuLink(
                title = { Text(text = stringResource(id = R.string.settings_title_save_sync)) },
                subtitle = {
                    Text(text = stringResource(id = R.string.settings_description_save_sync))
                },
                action = { SettingsOpenArrow() },
                onClick = { navController.navigateToRoute(MainRoute.SETTINGS_SAVE_SYNC) },
                enabled = hasDirectory,
            )
        }
    }
}

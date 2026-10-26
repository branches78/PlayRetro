package com.swordfish.lemuroid.app.tv.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.feature.main.composable
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoreOptionsSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.inputdevices.InputDevicesSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.savesync.SaveSyncSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.smb.SmbSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.smb.SmbSettingsViewModel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.shared.input.InputDeviceManager
import com.swordfish.lemuroid.app.shared.settings.SettingsInteractor
import com.swordfish.lemuroid.app.utils.android.TVNavigationSounds
import com.swordfish.lemuroid.app.utils.android.settings.SettingsFocusDestination
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

/**
 * Content of the "Settings" tab on TV. It reuses the mobile settings screens (same cards and options),
 * hiding the ones that only make sense on phones.
 */
class TVSettingsTabFragment : Fragment() {
    @Inject
    lateinit var settingsInteractor: SettingsInteractor

    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var biosManager: BiosManager

    @Inject
    lateinit var coresSelection: CoresSelection

    @Inject
    lateinit var inputDeviceManager: InputDeviceManager

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val appContext = requireContext().applicationContext
        val application = requireActivity().application

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                TVNavigationSounds {
                    AppTheme {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                            val navController = rememberNavController()
                            // The settings cards have their own side padding: they end up at the side margins of the
                            // other tabs.
                            val sideMargin = dimensionResource(R.dimen.tv_content_side_margin) - CARDS_SIDE_PADDING
                            val contentModifier = Modifier.fillMaxWidth().padding(horizontal = sideMargin)

                            NavHost(
                                modifier = contentModifier,
                                navController = navController,
                                startDestination = MainRoute.SETTINGS.route,
                            ) {
                                composable(MainRoute.SETTINGS) {
                                    // The first screen is shown with the tab: the focus stays on the tabs bar.
                                    SettingsFocusDestination(focusFirstItem = false) {
                                        SettingsScreen(
                                            viewModel =
                                                viewModel(
                                                    factory =
                                                        SettingsViewModel.Factory(
                                                            appContext,
                                                            settingsInteractor,
                                                            saveSyncManager,
                                                            FlowSharedPreferences(
                                                                SharedPreferencesHelper.getLegacySharedPreferences(appContext),
                                                            ),
                                                        ),
                                                ),
                                            navController = navController,
                                            isTV = true,
                                        )
                                    }
                                }
                                composable(MainRoute.SETTINGS_BIOS) {
                                    SettingsFocusDestination {
                                        BiosScreen(
                                            viewModel = viewModel(factory = BiosSettingsViewModel.Factory(biosManager)),
                                        )
                                    }
                                }
                                composable(MainRoute.SETTINGS_CORES_SELECTION) {
                                    SettingsFocusDestination {
                                        CoresSelectionScreen(
                                            viewModel =
                                                viewModel(
                                                    factory = CoresSelectionViewModel.Factory(appContext, coresSelection),
                                                ),
                                            navController = navController,
                                        )
                                    }
                                }
                                composable(MainRoute.SETTINGS_CORE_OPTIONS) { entry ->
                                    SettingsFocusDestination {
                                        CoreOptionsSettingsScreen(
                                            viewModel =
                                                viewModel(
                                                    factory = CoresSelectionViewModel.Factory(appContext, coresSelection),
                                                ),
                                            systemId = entry.arguments?.getString("systemId")!!,
                                            showTitle = true,
                                        )
                                    }
                                }
                                composable(MainRoute.SETTINGS_INPUT_DEVICES) {
                                    SettingsFocusDestination {
                                        InputDevicesSettingsScreen(
                                            viewModel =
                                                viewModel(
                                                    factory = InputDevicesSettingsViewModel.Factory(appContext, inputDeviceManager),
                                                ),
                                        )
                                    }
                                }
                                composable(MainRoute.SETTINGS_SMB) {
                                    SettingsFocusDestination {
                                        SmbSettingsScreen(
                                            viewModel = viewModel(factory = SmbSettingsViewModel.Factory(appContext)),
                                            isTV = true,
                                            onClose = { navController.popBackStack() },
                                        )
                                    }
                                }
                                composable(MainRoute.SETTINGS_SAVE_SYNC) {
                                    SettingsFocusDestination {
                                        SaveSyncSettingsScreen(
                                            viewModel =
                                                viewModel(
                                                    factory = SaveSyncSettingsViewModel.Factory(application, saveSyncManager),
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        // Side padding of the settings cards.
        private val CARDS_SIDE_PADDING = 16.dp
    }

    @dagger.Module
    class Module
}

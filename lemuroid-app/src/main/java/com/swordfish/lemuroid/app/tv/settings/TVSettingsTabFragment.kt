package com.swordfish.lemuroid.app.tv.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsSection
import androidx.navigation.NavController
import com.swordfish.lemuroid.app.utils.android.settings.SettingsChoiceScreen
import com.swordfish.lemuroid.app.utils.android.settings.SettingsChoiceHost
import com.swordfish.lemuroid.app.utils.android.settings.LocalSettingsChoiceHost
import androidx.compose.runtime.remember
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.MainRoute
import com.swordfish.lemuroid.app.mobile.feature.main.composable
import com.swordfish.lemuroid.app.mobile.feature.main.navigateToRoute
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.bios.BiosSettingsViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoreOptionsSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.coreselection.CoresSelectionViewModel
import com.swordfish.lemuroid.app.mobile.feature.settings.general.SettingsSectionContent
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
import com.swordfish.lemuroid.app.utils.android.NoFocusFill
import com.swordfish.lemuroid.app.utils.android.TVNavigationSounds
import com.swordfish.lemuroid.app.utils.android.settings.LocalSettingsItemCards
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
@OptIn(ExperimentalFoundationApi::class)
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

    private var navController: NavHostController? = null

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    /**
     * Closes the inner screens (BIOS, cores...). The tab is kept while another one is selected (see
     * TVMainTabsFragment), and it opens on the main list again.
     */
    fun showMainScreen() {
        navController?.popBackStack(MainRoute.SETTINGS.route, inclusive = false)
    }

    override fun onDestroyView() {
        navController = null
        super.onDestroyView()
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
                        // The focused options only get a white border.
                        NoFocusFill {
                            val navController = rememberNavController().also { this@TVSettingsTabFragment.navController = it }
                            val settingsViewModel: SettingsViewModel =
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
                                )
                            // The values of the options are a screen of their own, like on mobile, instead of a dialog.
                            val choiceHost =
                                remember(navController) {
                                    SettingsChoiceHost { navController.navigateToRoute(MainRoute.SETTINGS_CHOICE) }
                                }
                            CompositionLocalProvider(LocalSettingsChoiceHost provides choiceHost) {
                                NavHost(
                                    // The same side margins as the other tabs.
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = dimensionResource(R.dimen.tv_content_side_margin)),
                                    navController = navController,
                                    startDestination = MainRoute.SETTINGS.route,
                                    // The screens slide in from the side, like in the settings of Google TV.
                                    enterTransition = { slideInHorizontally(tween(SLIDE_MS)) { it } },
                                    exitTransition = { slideOutHorizontally(tween(SLIDE_MS)) { -it } },
                                    popEnterTransition = { slideInHorizontally(tween(SLIDE_MS)) { -it } },
                                    popExitTransition = { slideOutHorizontally(tween(SLIDE_MS)) { it } },
                                ) {
                                    composable(MainRoute.SETTINGS_CHOICE) {
                                        TVSettingsSubScreen(onBack = { navController.popBackStack() }) {
                                            SettingsChoiceScreen(
                                                host = choiceHost,
                                                onClose = { navController.popBackStack() },
                                                focusSelected = true,
                                            )
                                        }
                                    }
                                    composable(MainRoute.SETTINGS) {
                                        // The first screen is shown with the tab: the focus stays on the tabs bar.
                                        SettingsFocusDestination(focusFirstItem = false) {
                                            TVSettingsHome(
                                                onOpenSection = { navController.navigateToRoute(it.route) },
                                            ) { section ->
                                                SectionOptions(section, settingsViewModel, navController)
                                            }
                                        }
                                    }
                                    // The options of a section, opened from the list on the left.
                                    for (section in SettingsSection.entries) {
                                        composable(section.route) {
                                            TVSettingsSubScreen(onBack = { navController.popBackStack() }) {
                                                SettingsFocusDestination {
                                                    SectionOptions(section, settingsViewModel, navController)
                                                }
                                            }
                                        }
                                    }
                                    composable(MainRoute.SETTINGS_BIOS) {
                                        TVSettingsSubScreen(onBack = { navController.popBackStack() }) {
                                            SettingsFocusDestination {
                                                BiosScreen(
                                                    viewModel = viewModel(factory = BiosSettingsViewModel.Factory(biosManager)),
                                                )
                                            }
                                        }
                                    }
                                    composable(MainRoute.SETTINGS_CORES_SELECTION) {
                                        TVSettingsSubScreen(onBack = { navController.popBackStack() }) {
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
                                    }
                                    composable(MainRoute.SETTINGS_CORE_OPTIONS) { entry ->
                                        TVSettingsSubScreen(onBack = { navController.popBackStack() }) {
                                            SettingsFocusDestination {
                                                CoreOptionsSettingsScreen(
                                                    viewModel =
                                                        viewModel(
                                                            factory = CoresSelectionViewModel.Factory(appContext, coresSelection),
                                                        ),
                                                    systemId = entry.arguments?.getString("systemId")!!,
                                                    showTitle = false,
                                                )
                                            }
                                        }
                                    }
                                    composable(MainRoute.SETTINGS_INPUT_DEVICES) {
                                        TVSettingsSubScreen(onBack = { navController.popBackStack() }) {
                                            SettingsFocusDestination {
                                                InputDevicesSettingsScreen(
                                                    viewModel =
                                                        viewModel(
                                                            factory = InputDevicesSettingsViewModel.Factory(appContext, inputDeviceManager),
                                                        ),
                                                )
                                            }
                                        }
                                    }
                                    composable(MainRoute.SETTINGS_SMB) {
                                        TVSettingsSubScreen(onBack = { navController.popBackStack() }) {
                                            SettingsFocusDestination {
                                                // Text fields scroll themselves into view: with the TV way of keeping the focus at a fixed
                                                // place they fought with it and the form shook.
                                                CompositionLocalProvider(LocalBringIntoViewSpec provides rememberEdgeBringIntoViewSpec()) {
                                                    SmbSettingsScreen(
                                                        viewModel = viewModel(factory = SmbSettingsViewModel.Factory(appContext)),
                                                        isTV = true,
                                                        showTitle = false,
                                                        onClose = { navController.popBackStack() },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    composable(MainRoute.SETTINGS_SMB_SYNC) {
                                        TVSettingsSubScreen(onBack = { navController.popBackStack() }) {
                                            SettingsFocusDestination {
                                                // Text fields scroll themselves into view: with the TV way of keeping the focus at a fixed
                                                // place they fought with it and the form shook.
                                                CompositionLocalProvider(LocalBringIntoViewSpec provides rememberEdgeBringIntoViewSpec()) {
                                                    SmbSettingsScreen(
                                                        viewModel = viewModel(factory = SmbSettingsViewModel.syncFolderFactory(appContext)),
                                                        isTV = true,
                                                        showTitle = false,
                                                        onClose = { navController.popBackStack() },
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    composable(MainRoute.SETTINGS_SAVE_SYNC) {
                                        TVSettingsSubScreen(onBack = { navController.popBackStack() }) {
                                            SettingsFocusDestination {
                                                SaveSyncSettingsScreen(
                                                    viewModel =
                                                        viewModel(
                                                            factory = SaveSyncSettingsViewModel.Factory(application, saveSyncManager),
                                                        ),
                                                    onConfigureNetworkFolder = {
                                                        navController.navigateToRoute(MainRoute.SETTINGS_SMB_SYNC)
                                                    },
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
        }
    }

    /** The options of a [section], each one a card of its own. */
    @Composable
    private fun SectionOptions(
        section: SettingsSection,
        viewModel: SettingsViewModel,
        navController: NavController,
    ) {
        CompositionLocalProvider(LocalSettingsItemCards provides true) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
            ) {
                SettingsSectionContent(
                    section = section,
                    viewModel = viewModel,
                    navController = navController,
                    isTV = true,
                )
            }
        }
    }

    companion object {
        // As long as the slide of the tabs.
        private const val SLIDE_MS = 280
    }

    @dagger.Module
    class Module
}

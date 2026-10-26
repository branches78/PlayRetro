package com.swordfish.lemuroid.app.mobile.feature.settings.coreselection

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.main.navigateToCoreOptions
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidCardSettingsGroup
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsMenuLink
import com.swordfish.lemuroid.app.utils.android.settings.LemuroidSettingsPage
import java.text.Collator

/** Every system with its core, in alphabetical order of the system name; each one opens the options of that core. */
@Composable
fun CoresSelectionScreen(
    modifier: Modifier = Modifier,
    viewModel: CoresSelectionViewModel,
    navController: NavController,
) {
    val context = LocalContext.current
    val selectedCores = viewModel.getSelectedCores().collectAsState(emptyList()).value
    // The collator follows the device language (accents, case).
    val cores =
        remember(selectedCores) {
            val collator = Collator.getInstance()
            selectedCores.sortedWith(compareBy(collator) { context.getString(it.system.titleResId) })
        }

    LemuroidSettingsPage(modifier = modifier.fillMaxWidth()) {
        LemuroidCardSettingsGroup {
            cores.forEach { (system, core) ->
                val definitions = remember(core.coreID) { viewModel.getCachedOptions(core) }
                val coreName = core.coreID.coreDisplayName
                val summary =
                    when {
                        definitions == null -> stringResource(R.string.core_options_summary_unused, coreName)
                        else -> {
                            val count = viewModel.countExposedOptions(core, definitions)
                            if (count == 0) {
                                stringResource(R.string.core_options_summary_none, coreName)
                            } else {
                                stringResource(R.string.core_options_summary_count, coreName, count)
                            }
                        }
                    }

                LemuroidSettingsMenuLink(
                    title = { Text(text = stringResource(system.titleResId)) },
                    subtitle = { Text(text = summary) },
                    onClick = { navController.navigateToCoreOptions(system.id.dbname) },
                )
            }
        }
    }
}

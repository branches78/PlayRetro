package com.swordfish.lemuroid.app.shared.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swordfish.lemuroid.R
import com.swordfish.lemuroid.app.mobile.feature.settings.smb.LibrarySourceDialog
import com.swordfish.lemuroid.app.mobile.feature.settings.smb.SmbSettingsScreen
import com.swordfish.lemuroid.app.mobile.feature.settings.smb.SmbSettingsViewModel
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AppTheme
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.app.utils.android.settings.SettingsFocusDestination
import com.swordfish.lemuroid.lib.storage.smb.SmbConfigStore

/**
 * "Choose a folder" of an empty library (mobile home and TV "Home" and "Consoles" tabs): asks whether the games
 * come from a folder of the device or a network share, like "Directory" in the settings, and shows the network share
 * form when chosen. A folder of the device opens the usual folder picker.
 */
class LibrarySourceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTV = TVHelper.isTV(this)
        val store = SmbConfigStore(this)

        setContent {
            AppTheme {
                var showSmbForm by rememberSaveable { mutableStateOf(false) }

                if (!showSmbForm) {
                    LibrarySourceDialog(
                        currentSource = store.librarySource,
                        smbLabel = store.load()?.label?.takeIf { store.librarySource == SmbConfigStore.LibrarySource.SMB },
                        onLocalFolder = {
                            if (isTV) TVHelper.launchFolderPicker(this) else StorageFrameworkPickerLauncher.pickFolder(this)
                            finish()
                        },
                        onNetworkShare = { showSmbForm = true },
                        onDismiss = { finish() },
                    )
                } else {
                    BackHandler { finish() }
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        // On TV with the side margins of the tabs, and the focus on the first field.
                        val sideMargin = if (isTV) dimensionResource(R.dimen.tv_content_side_margin) else 0.dp
                        Box(modifier = Modifier.padding(horizontal = sideMargin)) {
                            SettingsFocusDestination {
                                SmbSettingsScreen(
                                    viewModel = viewModel(factory = SmbSettingsViewModel.Factory(applicationContext)),
                                    isTV = isTV,
                                    onClose = { finish() },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun show(context: Context) {
            context.startActivity(Intent(context, LibrarySourceActivity::class.java))
        }
    }
}

package com.swordfish.lemuroid.app.mobile.feature.settings.bios

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.lib.bios.Bios
import com.swordfish.lemuroid.lib.bios.BiosManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

class BiosSettingsViewModel(private val biosManager: BiosManager) : ViewModel() {
    class Factory
        @Inject
        constructor(private val biosManager: BiosManager) : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BiosSettingsViewModel(biosManager) as T
            }
        }

    // Both groups in alphabetical order of the name shown, then of the file name.
    val uiState =
        flow {
            val info = biosManager.getBiosInfoAsync()
            emit(BiosManager.BiosInfo(info.detected.sortedWith(BIOS_ORDER), info.notDetected.sortedWith(BIOS_ORDER)))
        }
            .stateIn(
                viewModelScope,
                SharingStarted.Lazily,
                BiosManager.BiosInfo(emptyList(), emptyList()),
            )

    companion object {
        private val BIOS_ORDER =
            compareBy<Bios, String>(String.CASE_INSENSITIVE_ORDER) { it.description }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName() }
    }
}

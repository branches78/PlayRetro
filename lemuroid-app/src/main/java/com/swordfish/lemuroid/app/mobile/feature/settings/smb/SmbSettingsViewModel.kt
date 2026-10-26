package com.swordfish.lemuroid.app.mobile.feature.settings.smb

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.app.shared.library.LibraryIndexScheduler
import com.swordfish.lemuroid.lib.storage.smb.SmbConfig
import com.swordfish.lemuroid.lib.storage.smb.SmbConfigStore
import com.swordfish.lemuroid.lib.storage.smb.SmbShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Settings of the network share with the games library: test the connection and use it as the library source. */
class SmbSettingsViewModel(private val appContext: Context) : ViewModel() {
    class Factory(private val appContext: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SmbSettingsViewModel(appContext) as T
        }
    }

    sealed interface TestResult {
        data object Testing : TestResult

        data class Success(val entries: Int) : TestResult

        data class Failure(val message: String) : TestResult
    }

    data class State(
        val config: SmbConfig = SmbConfig("", "", "", false, "", ""),
        val testResult: TestResult? = null,
    )

    private val store = SmbConfigStore(appContext)

    private val state = MutableStateFlow(State(config = store.load() ?: State().config))
    val uiState: StateFlow<State> = state.asStateFlow()

    fun update(config: SmbConfig) {
        state.update { it.copy(config = config, testResult = null) }
    }

    fun testConnection() {
        val config = state.value.config
        state.update { it.copy(testResult = TestResult.Testing) }
        viewModelScope.launch {
            val result =
                withContext(Dispatchers.IO) {
                    runCatching { SmbShare.open(config).use { it.list(config.folder).size } }
                }
            val testResult =
                result.fold(
                    onSuccess = { TestResult.Success(it) },
                    onFailure = { TestResult.Failure(it.message ?: it.javaClass.simpleName) },
                )
            // Only if the fields didn't change meanwhile.
            state.update { if (it.config == config) it.copy(testResult = testResult) else it }
        }
    }

    /** Saves the share and makes it the source of the library, which is scanned again. */
    fun save() {
        store.save(state.value.config)
        store.librarySource = SmbConfigStore.LibrarySource.SMB
        LibraryIndexScheduler.scheduleLibrarySync(appContext)
    }
}

package com.swordfish.lemuroid.ext.feature.savesync

import android.app.Activity
import android.content.Context
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.savesync.SmbSaveSync
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/** Without Google services the saves are synced through the network share of the library ([SmbSaveSync]). */
class SaveSyncManagerImpl(
    private val appContext: Context,
    private val directoriesManager: DirectoriesManager,
    retrogradeDb: RetrogradeDatabase,
) : SaveSyncManager() {
    private val smbSaveSync = SmbSaveSync(appContext, directoriesManager, retrogradeDb)

    override fun getProvider(): String = smbSaveSync.getProvider()

    // Configured with "Directory" in the settings.
    override fun getSettingsActivity(): Class<out Activity>? = null

    override fun isSupported(): Boolean = true

    override fun isConfigured(): Boolean = smbSaveSync.isConfigured()

    override fun getLastSyncInfo(): String = smbSaveSync.getLastSyncInfo()

    override fun getConfigInfo(): String = smbSaveSync.getConfigInfo()

    override suspend fun hasChanges(cores: Set<CoreID>): Boolean =
        withContext(Dispatchers.IO) {
            SYNC_LOCK.withLock {
                runCatching { smbSaveSync.hasChanges(cores) }
                    .onFailure { Timber.e(it, "Error while checking the save sync.") }
                    .getOrDefault(false)
            }
        }

    override suspend fun sync(
        cores: Set<CoreID>,
        onCopyStarted: () -> Unit,
    ) =
        withContext(Dispatchers.IO) {
            SYNC_LOCK.withLock {
                runCatching { smbSaveSync.sync(cores, onCopyStarted) }.onFailure { Timber.e(it, "Error while performing save sync.") }
            }
            Unit
        }

    override fun computeSavesSpace() = sizeOf(directoriesManager.getSavesDirectory())

    override fun computeStatesSpace(core: CoreID) = sizeOf(File(directoriesManager.getStatesDirectory(), core.coreName))

    private fun sizeOf(directory: File): String {
        val size = directory.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        return android.text.format.Formatter.formatShortFileSize(appContext, size)
    }

    companion object {
        private val SYNC_LOCK = Mutex()
    }
}

package com.swordfish.lemuroid.lib.savesync

import android.app.Activity
import android.content.Context
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem

abstract class SaveSyncManager {
    abstract fun getProvider(): String

    abstract fun getSettingsActivity(): Class<out Activity>?

    abstract fun isSupported(): Boolean

    abstract fun isConfigured(): Boolean

    abstract fun getLastSyncInfo(): String

    abstract fun getConfigInfo(): String

    /**
     * [onCopyStarted] is called once it's known that something has to be copied, so the app shows the sync only then
     * and not while checking that nothing changed.
     */
    abstract suspend fun sync(
        cores: Set<CoreID>,
        onCopyStarted: () -> Unit = {},
    )

    /** Whether a [sync] would have something to copy, so the sync buttons are only enabled then. */
    abstract suspend fun hasChanges(cores: Set<CoreID>): Boolean

    abstract fun computeSavesSpace(): String

    abstract fun computeStatesSpace(core: CoreID): String

    /** Whether the sync can go to more than one place (Google Drive or the network share, in the Play variant). */
    open fun hasProviderChoice(): Boolean = false

    fun getDisplayNameForCore(
        context: Context,
        coreID: CoreID,
    ): String {
        val systems = GameSystem.findSystemForCore(coreID)
        val systemHasMultipleCores = systems.any { it.systemCoreConfigs.size > 1 }

        val chunks =
            mutableListOf<String>().apply {
                add(systems.joinToString(", ") { context.getString(it.shortTitleResId) })

                if (systemHasMultipleCores) {
                    add(coreID.coreDisplayName)
                }

                add(computeStatesSpace(coreID))
            }

        return chunks.joinToString(" - ")
    }
}

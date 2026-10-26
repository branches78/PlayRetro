package com.swordfish.lemuroid.lib.savesync

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import com.swordfish.lemuroid.lib.storage.smb.SmbConfig
import com.swordfish.lemuroid.lib.storage.smb.SmbConfigStore
import com.swordfish.lemuroid.lib.storage.smb.SmbShare
import com.swordfish.lemuroid.lib.storage.smb.SmbStorageProvider
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat

/**
 * Sync of saves, states, "Continue playing" and favorites through the network share of the library (both variants).
 *
 * Saves and states go next to their game on the share: in the folder of the game, or of its system when the game has
 * none. States carry the core in their name, so the ones of two cores of the same game don't mix:
 * "Game (USA).cue.pcsx_rearmed.state", ".slot1", their ".metadata" and the ".jpg" preview of each slot.
 * A file only on one side is copied to the other; on both, the side that changed since the last sync wins, or the most
 * recent when both changed. "Continue playing" and favorites go in [METADATA_FILE] at the root of the library.
 *
 * Nothing is done when nothing changed: the files of this device are compared with a fingerprint of the last sync,
 * and the share only with the revision of [METADATA_FILE], which goes up each time a device changes something there.
 */
class SmbSaveSync(
    private val appContext: Context,
    private val directoriesManager: DirectoriesManager,
    private val retrogradeDb: RetrogradeDatabase,
) {
    private val configStore = SmbConfigStore(appContext)
    private val records = appContext.getSharedPreferences(RECORDS_PREFERENCES, Context.MODE_PRIVATE)
    private val metadata = SyncMetadata(retrogradeDb, records)

    /** Name shown as the sync provider. */
    fun getProvider(): String = appContext.getString(R.string.smb_storage)

    /** Only when the library comes from a network share: the saves go next to its games. */
    fun isConfigured(): Boolean =
        configStore.librarySource == SmbConfigStore.LibrarySource.SMB && configStore.load()?.isValid() == true

    fun getConfigInfo(): String {
        val config = configStore.load()?.takeIf { isConfigured() }
        return if (config != null) {
            appContext.getString(R.string.smb_sync_connected, config.label)
        } else {
            appContext.getString(R.string.smb_sync_not_configured)
        }
    }

    fun getLastSyncInfo(): String {
        if (metadata.isUnreadable()) return appContext.getString(R.string.sync_metadata_unreadable, METADATA_FILE)
        val timestamp = records.getLong(KEY_LAST_SYNC, 0L)
        val date = if (timestamp > 0) SimpleDateFormat.getDateTimeInstance().format(timestamp) else "-"
        return appContext.getString(R.string.smb_sync_last, date)
    }

    /**
     * Saves, states of the [cores] chosen in the settings, then "Continue playing" and favorites. Returns quickly
     * when neither this device nor the share changed since the last sync.
     */
    suspend fun sync(
        cores: Set<CoreID>,
        onCopyStarted: () -> Unit = {},
    ) {
        if (!isConfigured()) return
        val config = configStore.load() ?: return
        val prefix = libraryPrefix(config)
        val games = retrogradeDb.gameDao().selectByFileUriPrefix(prefix)
        val metadataPath = SmbShare.join(config.folder, METADATA_FILE)

        SmbShare.open(config).use { share ->
            // Read first: if it exists but can't be read, it is not written (the data of other devices is kept).
            val remoteJson = readMetadata(share, metadataPath)
            val localFingerprint = fingerprint(games, cores)
            val upToDate =
                if (remoteJson != null) {
                    metadata.isUpToDate(remoteJson, localFingerprint)
                } else {
                    metadata.isLocalUpToDate(localFingerprint)
                }
            if (upToDate) {
                Timber.i("Save sync: nothing changed")
                records.edit { putLong(KEY_LAST_SYNC, System.currentTimeMillis()) }
                return
            }
            onCopyStarted()

            var changedRemote = false
            val folders = RemoteFolders(share)
            games.forEach { game ->
                runCatching { changedRemote = syncGame(share, folders, game, cores) || changedRemote }
                    .onFailure { Timber.e(it, "Error while syncing ${game.fileName}") }
            }

            if (remoteJson == null) {
                // The saves and states are synced anyway; "Continue playing" and favorites wait for a readable file.
                metadata.setReadable(false, fingerprint(retrogradeDb.gameDao().selectByFileUriPrefix(prefix), cores))
            } else {
                changedRemote = metadata.merge(remoteJson, games) || changedRemote
                metadata.prepareWrite(remoteJson, changedRemote)
                if (changedRemote) {
                    share.openWrite(metadataPath).use { it.write(remoteJson.toString().encodeToByteArray()) }
                }
                // After the downloads, the changes to the library and the write, so they don't count as changes.
                metadata.commit(remoteJson, fingerprint(retrogradeDb.gameDao().selectByFileUriPrefix(prefix), cores))
            }
            records.edit { putLong(KEY_LAST_SYNC, System.currentTimeMillis()) }
        }
    }

    /** Whether a [sync] would have something to do: the same quick check it does first. */
    suspend fun hasChanges(cores: Set<CoreID>): Boolean {
        if (!isConfigured()) return false
        val config = configStore.load() ?: return false
        val games = retrogradeDb.gameDao().selectByFileUriPrefix(libraryPrefix(config))
        return SmbShare.open(config).use { share ->
            val remoteJson = readMetadata(share, SmbShare.join(config.folder, METADATA_FILE))
            val localFingerprint = fingerprint(games, cores)
            if (remoteJson != null) {
                !metadata.isUpToDate(remoteJson, localFingerprint)
            } else {
                !metadata.isLocalUpToDate(localFingerprint)
            }
        }
    }

    private fun libraryPrefix(config: SmbConfig): String =
        SmbStorageProvider.buildUri(config, "").toString().trimEnd('/') + "/"

    /**
     * The content of [path], empty when it doesn't exist yet, null when it is damaged. Network errors are thrown:
     * the sync stops, as it can't go on anyway.
     */
    private fun readMetadata(
        share: SmbShare,
        path: String,
    ): JSONObject? {
        if (share.info(path) == null) return JSONObject()
        val text = share.openRead(path).use { it.readBytes().decodeToString() }
        return try {
            JSONObject(text)
        } catch (e: JSONException) {
            Timber.e(e, "Damaged $path")
            null
        }
    }

    /** Names, sizes and dates of the files to sync, and the played games and favorites of this device. */
    private fun fingerprint(
        games: List<Game>,
        cores: Set<CoreID>,
    ): String {
        val builder = StringBuilder(cores.map { it.coreName }.sorted().joinToString(","))
        games.sortedBy { it.id }.forEach { game ->
            builder.append('|').append(game.systemId).append('/').append(game.fileName)
                .append(':').append(game.lastPlayedAt).append(':').append(game.isFavorite)
            localFiles(game, cores).forEach { file ->
                if (file.exists()) builder.append(':').append(file.lastModified()).append('/').append(file.length())
            }
        }
        return builder.toString().hashCode().toString() + ":" + builder.length
    }

    private fun localFiles(
        game: Game,
        cores: Set<CoreID>,
    ): List<File> = filePairs(game, cores).map { it.first }

    /** Local files of a [game] with their path on the share. */
    private fun filePairs(
        game: Game,
        cores: Set<CoreID>,
    ): List<Pair<File, String>> {
        val romPath = SmbStorageProvider.pathInShare(Uri.parse(game.fileUri))
        val folder = romPath.substringBeforeLast(SmbConfig.SMB_SEPARATOR, "")
        val saveName = "${game.fileName.substringBeforeLast(".")}.srm"
        val pairs = mutableListOf(File(directoriesManager.getSavesDirectory(), saveName) to SmbShare.join(folder, saveName))

        val system = runCatching { GameSystem.findById(game.systemId) }.getOrNull() ?: return pairs
        system.systemCoreConfigs
            .map { it.coreID }
            .filter { it in cores }
            .forEach { core ->
                val statesDir = File(directoriesManager.getStatesDirectory(), core.coreName)
                val previewsDir = File(directoriesManager.getStatesPreviewDirectory(), core.coreName)
                val stateNames = listOf("${game.fileName}.state") + (1..MAX_SLOTS).map { "${game.fileName}.slot$it" }
                stateNames.forEach { name ->
                    val remote = remoteStateName(name, game.fileName, core)
                    pairs += File(statesDir, name) to SmbShare.join(folder, remote)
                    pairs += File(statesDir, "$name.metadata") to SmbShare.join(folder, "$remote.metadata")
                }
                (1..MAX_SLOTS).forEach { slot ->
                    val name = "${game.fileName}.slot$slot.jpg"
                    pairs += File(previewsDir, name) to SmbShare.join(folder, "${game.fileName}.${core.coreName}.slot$slot.jpg")
                }
            }
        return pairs
    }

    /** True when something was uploaded to the share. */
    private fun syncGame(
        share: SmbShare,
        folders: RemoteFolders,
        game: Game,
        cores: Set<CoreID>,
    ): Boolean {
        var uploaded = false
        filePairs(game, cores).forEach { (local, remotePath) ->
            uploaded = syncFile(share, local, remotePath, folders.entry(remotePath)) || uploaded
        }
        return uploaded
    }

    /**
     * Files of the folders of the share, each folder listed once and only when needed: one request per folder
     * instead of one for every file that a game may have (most of them don't exist on either side).
     */
    private class RemoteFolders(private val share: SmbShare) {
        private val folders = mutableMapOf<String, Map<String, SmbShare.Entry>>()

        fun entry(path: String): SmbShare.Entry? {
            val folder = path.substringBeforeLast(SmbConfig.SMB_SEPARATOR, "")
            val files =
                folders.getOrPut(folder) {
                    // Names on a share are not case sensitive.
                    share.list(folder).filter { !it.isDirectory }.associateBy { it.name.lowercase() }
                }
            return files[path.substringAfterLast(SmbConfig.SMB_SEPARATOR).lowercase()]
        }
    }

    // "Game.cue.slot1" -> "Game.cue.pcsx_rearmed.slot1".
    private fun remoteStateName(
        localName: String,
        fileName: String,
        core: CoreID,
    ): String = fileName + "." + core.coreName + localName.removePrefix(fileName)

    /** Copies the newest side over the other (see the class documentation); true when it uploaded. */
    private fun syncFile(
        share: SmbShare,
        local: File,
        remotePath: String,
        remote: SmbShare.Entry?,
    ): Boolean {
        val localExists = local.exists()
        if (!localExists && remote == null) return false

        // A damaged record counts as none: the file is compared as in a first sync.
        val record =
            records.getString(remotePath, null)?.split(':')?.mapNotNull { it.toLongOrNull() }?.takeIf { it.size == 4 }
        val localChanged = localExists && (record == null || record[0] != local.lastModified() || record[1] != local.length())
        val remoteChanged = remote != null && (record == null || record[2] != remote.lastModified || record[3] != remote.size)

        val upload =
            when {
                remote == null -> true
                !localExists -> false
                !localChanged && !remoteChanged -> return false
                localChanged && !remoteChanged -> true
                !localChanged && remoteChanged -> false
                // Both changed (or never synced): the same file needs nothing, otherwise the newest wins.
                local.length() == remote.size && local.lastModified() == remote.lastModified -> return false
                else -> local.lastModified() > remote.lastModified
            }

        if (upload) {
            local.inputStream().use { input -> share.openWrite(remotePath).use { input.copyTo(it) } }
        } else {
            local.parentFile?.mkdirs()
            val partFile = File(local.parentFile, local.name + PART_SUFFIX)
            share.openRead(remotePath).use { input -> partFile.outputStream().use { input.copyTo(it) } }
            partFile.setLastModified(remote!!.lastModified)
            if (!partFile.renameTo(local)) {
                partFile.copyTo(local, overwrite = true)
                partFile.delete()
                local.setLastModified(remote.lastModified)
            }
        }

        val newRemote = share.info(remotePath) ?: return upload
        records.edit {
            putString(remotePath, "${local.lastModified()}:${local.length()}:${newRemote.lastModified}:${newRemote.size}")
        }
        return upload
    }

    companion object {
        const val METADATA_FILE = SyncMetadata.FILE_NAME
        private const val RECORDS_PREFERENCES = "smb_save_sync"
        private const val KEY_LAST_SYNC = "last_sync"
        private const val PART_SUFFIX = ".part"
        private const val MAX_SLOTS = 4
    }
}

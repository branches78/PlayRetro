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
 * Sync of saves, states, "Continue playing" and favorites through a network share (both variants): the one of the
 * library, or with the library in a folder of the device, the one configured for the sync.
 *
 * Saves and states go next to their game on the share: in the folder of the game, or of its system when the game has
 * none. With the library in a folder of the device, they go in the folder of the same game on the share, so all the
 * devices share the same saves: written in [METADATA_FILE] by the devices with their library there, or else found by
 * this device on the share (the folder with the same ROM file) and written there too. A game that is not on the share
 * uses a folder of its system ("snes"). States carry the core in their name, so the ones of two cores of the same game don't mix:
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

    /** With a network share configured, as the library or only for the sync. */
    fun isConfigured(): Boolean = configStore.load()?.isValid() == true

    private fun isLibraryOnShare(): Boolean = configStore.librarySource == SmbConfigStore.LibrarySource.SMB

    /** The games of the library on the share, or all the games of a library in a folder of the device. */
    private suspend fun libraryGames(config: SmbConfig): List<Game> =
        if (isLibraryOnShare()) {
            retrogradeDb.gameDao().selectByFileUriPrefix(libraryPrefix(config))
        } else {
            retrogradeDb.gameDao().selectByFileUriPrefix("").filter { !it.fileUri.startsWith("${SmbStorageProvider.SCHEME}:") }
        }

    /** Folder of the saves of a [game] on the share; see the class documentation. */
    private fun gameFolder(
        game: Game,
        config: SmbConfig,
        remoteJson: JSONObject?,
        foundFolders: Map<String, String>,
    ): String =
        if (isLibraryOnShare()) {
            romFolder(game)
        } else {
            remoteJson?.let { metadata.romFolderOf(it, game) }
                ?: foundFolders[gameKey(game)]
                ?: SmbShare.join(config.folder, game.systemId)
        }

    private fun gameKey(game: Game): String = "${game.systemId}/${game.fileName}"

    /**
     * With the library in a folder of the device: the folders of the share with the ROM of the games that have
     * something to sync but no folder in [METADATA_FILE] yet, by game key. Looked for in the folders of the share up to
     * [MAX_SEARCH_DEPTH] levels ("PSX/Game (USA)/Game (USA).cue"), each listed once, and only until all are found;
     * once written in [METADATA_FILE] they are not looked for again.
     */
    private fun findGameFolders(
        share: SmbShare,
        config: SmbConfig,
        games: List<Game>,
        cores: Set<CoreID>,
        remoteJson: JSONObject?,
    ): Map<String, String> {
        if (isLibraryOnShare()) return emptyMap()
        val missing =
            games
                .filter { game -> remoteJson?.let { metadata.romFolderOf(it, game) } == null }
                .filter { game -> localFiles(game, cores).any { it.exists() } }
                .groupBy { it.fileName.lowercase() }
                .toMutableMap()
        if (missing.isEmpty()) return emptyMap()

        val found = mutableMapOf<String, String>()
        var level = listOf(config.folder)
        repeat(MAX_SEARCH_DEPTH + 1) {
            val next = mutableListOf<String>()
            level.forEach { folder ->
                if (missing.isEmpty()) return found
                runCatching { share.list(folder) }
                    .onFailure { Timber.w(it, "Can't list $folder") }
                    .getOrDefault(emptyList())
                    .forEach { entry ->
                        if (entry.isDirectory) {
                            next += entry.path
                        } else {
                            missing.remove(entry.name.lowercase())?.forEach { found[gameKey(it)] = folder }
                        }
                    }
            }
            level = next
        }
        return found
    }

    private fun romFolder(game: Game): String =
        SmbStorageProvider.pathInShare(Uri.parse(game.fileUri)).substringBeforeLast(SmbConfig.SMB_SEPARATOR, "")

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
        val games = libraryGames(config)
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
            val foundFolders = findGameFolders(share, config, games, cores, remoteJson)
            games.forEach { game ->
                runCatching {
                    val folder = gameFolder(game, config, remoteJson, foundFolders)
                    changedRemote = syncGame(share, folders, game, cores, folder) || changedRemote
                }
                    .onFailure { Timber.e(it, "Error while syncing ${game.fileName}") }
            }

            if (remoteJson == null) {
                // The saves and states are synced anyway; "Continue playing" and favorites wait for a readable file.
                metadata.setReadable(false, fingerprint(libraryGames(config), cores))
            } else {
                val romFolders =
                    if (isLibraryOnShare()) games.associate { gameKey(it) to romFolder(it) } else foundFolders
                changedRemote = metadata.merge(remoteJson, games, romFolders) || changedRemote
                metadata.prepareWrite(remoteJson, changedRemote)
                if (changedRemote) {
                    share.openWrite(metadataPath).use { it.write(remoteJson.toString().encodeToByteArray()) }
                }
                // After the downloads, the changes to the library and the write, so they don't count as changes.
                metadata.commit(remoteJson, fingerprint(libraryGames(config), cores))
            }
            records.edit { putLong(KEY_LAST_SYNC, System.currentTimeMillis()) }
        }
    }

    /** Whether a [sync] would have something to do: the same quick check it does first. */
    suspend fun hasChanges(cores: Set<CoreID>): Boolean {
        if (!isConfigured()) return false
        val config = configStore.load() ?: return false
        val games = libraryGames(config)
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
    ): List<File> = filePairs(game, cores, "").map { it.first }

    /** Local files of a [game] with their path in the [folder] of the share. */
    private fun filePairs(
        game: Game,
        cores: Set<CoreID>,
        folder: String,
    ): List<Pair<File, String>> {
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
        folder: String,
    ): Boolean {
        var uploaded = false
        filePairs(game, cores, folder).forEach { (local, remotePath) ->
            val remote = folders.entry(remotePath)
            // The folder of a system may not exist yet (library in a folder of the device).
            if (remote == null && local.exists()) folders.makeFolder(remotePath)
            uploaded = syncFile(share, local, remotePath, remote) || uploaded
        }
        return uploaded
    }

    /**
     * Files of the folders of the share, each folder listed once and only when needed: one request per folder
     * instead of one for every file that a game may have (most of them don't exist on either side).
     */
    private class RemoteFolders(private val share: SmbShare) {
        private val folders = mutableMapOf<String, Map<String, SmbShare.Entry>>()
        private val missingFolders = mutableSetOf<String>()

        fun entry(path: String): SmbShare.Entry? {
            val folder = path.substringBeforeLast(SmbConfig.SMB_SEPARATOR, "")
            val files =
                folders.getOrPut(folder) {
                    if (!share.folderExists(folder)) {
                        missingFolders += folder
                        return@getOrPut emptyMap()
                    }
                    // Names on a share are not case sensitive.
                    share.list(folder).filter { !it.isDirectory }.associateBy { it.name.lowercase() }
                }
            return files[path.substringAfterLast(SmbConfig.SMB_SEPARATOR).lowercase()]
        }

        /** Creates the folder of [path] when it was found missing, before uploading the file. */
        fun makeFolder(path: String) {
            val folder = path.substringBeforeLast(SmbConfig.SMB_SEPARATOR, "")
            if (missingFolders.remove(folder)) share.makeDirectories(folder)
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

        // Levels of folders below the root where the ROM of a game is looked for: system, then game.
        private const val MAX_SEARCH_DEPTH = 3
    }
}

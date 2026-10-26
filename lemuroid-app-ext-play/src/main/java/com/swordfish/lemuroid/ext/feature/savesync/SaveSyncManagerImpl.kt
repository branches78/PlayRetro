package com.swordfish.lemuroid.ext.feature.savesync

import android.app.Activity
import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.util.DateTime
import com.google.api.services.drive.Drive
import com.swordfish.lemuroid.common.kotlin.SharedPreferencesDelegates
import com.swordfish.lemuroid.common.kotlin.calculateMd5
import com.swordfish.lemuroid.ext.R
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.preferences.SharedPreferencesHelper
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import com.swordfish.lemuroid.lib.savesync.SmbSaveSync
import com.swordfish.lemuroid.lib.savesync.SyncMetadata
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import java.io.File
import java.text.SimpleDateFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber

/**
 * Google Drive, or the network share of the library ([SmbSaveSync]) when chosen in the settings
 * ([com.swordfish.lemuroid.lib.R.string.pref_key_save_sync_provider]).
 *
 * On Google Drive, saves and states go in folders of the private space of the app, and "Continue playing" and
 * favorites in [SyncMetadata.FILE_NAME] next to them. Like on the network share, nothing is done when nothing changed.
 */
class SaveSyncManagerImpl(
    private val appContext: Context,
    private val directoriesManager: DirectoriesManager,
    private val retrogradeDb: RetrogradeDatabase,
) : SaveSyncManager() {
    private val smbSaveSync = SmbSaveSync(appContext, directoriesManager, retrogradeDb)

    private val driveMetadata =
        SyncMetadata(retrogradeDb, appContext.getSharedPreferences(DRIVE_RECORDS_PREFERENCES, Context.MODE_PRIVATE))

    private fun useNetworkShare(): Boolean =
        SharedPreferencesHelper.getSharedPreferences(appContext)
            .getString(appContext.getString(com.swordfish.lemuroid.lib.R.string.pref_key_save_sync_provider), null) ==
            PROVIDER_SMB

    override fun hasProviderChoice(): Boolean = true

    private var lastSyncTimestamp: Long by SharedPreferencesDelegates.LongDelegate(
        SharedPreferencesHelper.getSharedPreferences(appContext),
        appContext.getString(com.swordfish.lemuroid.lib.R.string.pref_key_last_save_sync),
        0L,
    )

    override fun getProvider(): String = if (useNetworkShare()) smbSaveSync.getProvider() else "Google Drive"

    override fun getSettingsActivity(): Class<out Activity>? =
        if (useNetworkShare()) null else ActivateGoogleDriveActivity::class.java

    override fun isSupported(): Boolean = true

    override fun isConfigured(): Boolean =
        if (useNetworkShare()) smbSaveSync.isConfigured() else GoogleSignIn.getLastSignedInAccount(appContext) != null

    override fun getLastSyncInfo(): String {
        if (useNetworkShare()) return smbSaveSync.getLastSyncInfo()
        if (driveMetadata.isUnreadable()) {
            return appContext.getString(
                com.swordfish.lemuroid.lib.R.string.sync_metadata_unreadable,
                SyncMetadata.FILE_NAME,
            )
        }
        val dateString =
            if (lastSyncTimestamp > 0) {
                SimpleDateFormat.getDateTimeInstance().format(lastSyncTimestamp)
            } else {
                "-"
            }
        return appContext.getString(R.string.gdrive_last_sync_completed, dateString)
    }

    override fun getConfigInfo(): String {
        if (useNetworkShare()) return smbSaveSync.getConfigInfo()
        val email = GoogleSignIn.getLastSignedInAccount(appContext)?.email
        return if (email != null) {
            appContext.getString(R.string.gdrive_connected_summary, email)
        } else {
            appContext.getString(R.string.gdrive_connected_none_summary)
        }
    }

    override suspend fun hasChanges(cores: Set<CoreID>): Boolean =
        withContext(Dispatchers.IO) {
            if (useNetworkShare()) {
                return@withContext SMB_LOCK.withLock {
                    runCatching { smbSaveSync.hasChanges(cores) }
                        .onFailure { Timber.e(it, "Error while checking the save sync.") }
                        .getOrDefault(false)
                }
            }
            val games = retrogradeDb.gameDao().selectByFileUriPrefix("")
            SYNC_LOCK.withLock {
                runCatching {
                    val drive = DriveFactory(appContext).create() ?: return@runCatching false
                    val remoteJson = findMetadataFile(drive)?.let { readMetadata(drive, it.id) } ?: JSONObject()
                    val localFingerprint = driveFingerprint(games, cores)
                    if (remoteJson !== DAMAGED) {
                        !driveMetadata.isUpToDate(remoteJson, localFingerprint)
                    } else {
                        !driveMetadata.isLocalUpToDate(localFingerprint)
                    }
                }.onFailure { Timber.e(it, "Error while checking the save sync.") }
                    .getOrDefault(false)
            }
        }

    override suspend fun sync(
        cores: Set<CoreID>,
        onCopyStarted: () -> Unit,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (useNetworkShare()) {
                SMB_LOCK.withLock {
                    runCatching { smbSaveSync.sync(cores, onCopyStarted) }
                        .onFailure { Timber.e(it, "Error while performing save sync.") }
                }
                return@withContext
            }
            SYNC_LOCK.withLock {
                runCatching { performSaveSyncForCores(cores, onCopyStarted) }
                    .onFailure { Timber.e(it, "Error while performing save sync.") }
            }
        }

    private suspend fun performSaveSyncForCores(
        cores: Set<CoreID>,
        onCopyStarted: () -> Unit,
    ) {
        val drive = DriveFactory(appContext).create() ?: return
        val games = retrogradeDb.gameDao().selectByFileUriPrefix("")

        // Read first: if it is damaged it is not written (the data of the other devices is kept).
        val metadataFile = findMetadataFile(drive)
        val remoteJson = metadataFile?.let { readMetadata(drive, it.id) } ?: JSONObject()
        val localFingerprint = driveFingerprint(games, cores)
        val upToDate =
            if (remoteJson !== DAMAGED) {
                driveMetadata.isUpToDate(remoteJson, localFingerprint)
            } else {
                driveMetadata.isLocalUpToDate(localFingerprint)
            }
        if (upToDate) {
            Timber.i("Save sync: nothing changed")
            lastSyncTimestamp = System.currentTimeMillis()
            return
        }
        onCopyStarted()

        var changedRemote =
            syncLocalAndRemoteFolder(
                drive,
                getOrCreateAppDataFolder("saves"),
                directoriesManager.getSavesDirectory(),
                null,
            )

        if (cores.isNotEmpty()) {
            changedRemote =
                syncLocalAndRemoteFolder(
                    drive,
                    getOrCreateAppDataFolder("states"),
                    directoriesManager.getStatesDirectory(),
                    cores.map { it.coreName }.toSet(),
                ) || changedRemote
            changedRemote =
                syncLocalAndRemoteFolder(
                    drive,
                    getOrCreateAppDataFolder("state-previews"),
                    directoriesManager.getStatesPreviewDirectory(),
                    cores.map { it.coreName }.toSet(),
                ) || changedRemote
        }

        if (remoteJson === DAMAGED) {
            // The saves and states are synced anyway; "Continue playing" and favorites wait for a readable file.
            driveMetadata.setReadable(false, driveFingerprint(retrogradeDb.gameDao().selectByFileUriPrefix(""), cores))
        } else {
            changedRemote = driveMetadata.merge(remoteJson, games) || changedRemote
            driveMetadata.prepareWrite(remoteJson, changedRemote)
            if (changedRemote) writeMetadata(drive, metadataFile?.id, remoteJson)
            // After the downloads, the changes to the library and the write, so they don't count as changes.
            driveMetadata.commit(remoteJson, driveFingerprint(retrogradeDb.gameDao().selectByFileUriPrefix(""), cores))
        }

        lastSyncTimestamp = System.currentTimeMillis()
    }

    /** Played games and favorites, and names, sizes and dates of the saves and of the states of the [cores]. */
    private fun driveFingerprint(
        games: List<Game>,
        cores: Set<CoreID>,
    ): String {
        val coreNames = cores.map { it.coreName }.toSet()
        val builder = StringBuilder(coreNames.sorted().joinToString(","))
        builder.append(SyncMetadata.gamesFingerprint(games))
        val folders =
            listOf(directoriesManager.getSavesDirectory() to null) +
                listOf(directoriesManager.getStatesDirectory(), directoriesManager.getStatesPreviewDirectory())
                    .map { it to coreNames }
        folders.forEach { (folder, prefixes) ->
            buildLocalFileMap(folder)
                .filterKeys { key -> prefixes == null || prefixes.any { key.startsWith(it) } }
                .toSortedMap()
                .forEach { (path, file) ->
                    builder.append('|').append(path).append(':').append(file.lastModified()).append('/')
                        .append(file.length())
                }
        }
        return builder.toString().hashCode().toString() + ":" + builder.length
    }

    private fun findMetadataFile(drive: Drive): com.google.api.services.drive.model.File? =
        drive.files().list()
            .setSpaces("appDataFolder")
            .setQ("name = '${SyncMetadata.FILE_NAME}' and 'appDataFolder' in parents and trashed = false")
            .setFields("files(id)")
            .execute()
            .files
            .firstOrNull()

    /** The content of the file, or [DAMAGED] when it can't be parsed. Network errors are thrown. */
    private fun readMetadata(
        drive: Drive,
        fileId: String,
    ): JSONObject {
        val text = drive.files().get(fileId).executeMediaAsInputStream().use { it.readBytes().decodeToString() }
        return try {
            JSONObject(text)
        } catch (e: JSONException) {
            Timber.e(e, "Damaged ${SyncMetadata.FILE_NAME}")
            DAMAGED
        }
    }

    private fun writeMetadata(
        drive: Drive,
        fileId: String?,
        json: JSONObject,
    ) {
        val content = ByteArrayContent("application/json", json.toString().encodeToByteArray())
        if (fileId != null) {
            drive.files().update(fileId, com.google.api.services.drive.model.File(), content).execute()
        } else {
            val metadata = com.google.api.services.drive.model.File()
            metadata.parents = listOf("appDataFolder")
            metadata.name = SyncMetadata.FILE_NAME
            drive.files().create(metadata, content).setFields("id").execute()
        }
    }

    override fun computeSavesSpace() = getSizeHumanReadable(directoriesManager.getSavesDirectory())

    override fun computeStatesSpace(core: CoreID) =
        getSizeHumanReadable(File(directoriesManager.getStatesDirectory(), core.coreName))

    private fun getSizeHumanReadable(directory: File): String {
        val size =
            directory.walkBottomUp()
                .fold(0L) { acc, file -> acc + file.length() }
        return android.text.format.Formatter.formatShortFileSize(appContext, size)
    }

    private fun syncLocalAndRemoteFolder(
        drive: Drive,
        remoteFolderId: String,
        localFolder: File,
        prefixes: Set<String>?,
    ): Boolean {
        val remoteFiles = getRemoteFiles(drive, remoteFolderId)
        val remoteFilesMap = buildRemoteFileMap(remoteFiles)
        val localFilesMap = buildLocalFileMap(localFolder)

        var uploaded = false
        getFilteredKeys(remoteFilesMap.keys + localFilesMap.keys, prefixes).forEach {
            uploaded =
                handleFileSync(drive, remoteFolderId, localFolder, remoteFilesMap[it], localFilesMap[it]) || uploaded
        }
        return uploaded
    }

    private fun getFilteredKeys(
        keys: Set<String>,
        prefixes: Set<String>?,
    ): Set<String> {
        if (prefixes == null) return keys
        return keys.filter { key -> prefixes.any { key.startsWith(it) } }.toSet()
    }

    private fun handleFileSync(
        drive: Drive,
        remoteParentFolderId: String,
        localParentFolder: File,
        remoteFile: com.google.api.services.drive.model.File?,
        localFile: File?,
    ): Boolean {
        Timber.i("Handling file pair: $localFile $remoteFile")

        // True when something was uploaded: the other devices then have something to download.
        return runCatching {
            if (remoteFile != null && localFile == null) {
                onRemoteOnly(localParentFolder, remoteFile, drive)
                false
            } else if (remoteFile == null && localFile != null) {
                onLocalOnly(remoteParentFolderId, localFile, localParentFolder, drive)
                true
            } else if (remoteFile != null && localFile != null && areFileDifferent(remoteFile, localFile)) {
                if (remoteFile.modifiedTime.value < localFile.lastModified()) {
                    onLocalUpdated(localFile, drive, remoteFile)
                    true
                } else {
                    if (remoteFile.modifiedTime.value > localFile.lastModified()) {
                        onRemoteUpdated(drive, remoteFile, localFile)
                    }
                    false
                }
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun areFileDifferent(
        remoteFile: com.google.api.services.drive.model.File,
        localFile: File,
    ): Boolean {
        if (remoteFile.modifiedTime.value == localFile.lastModified()) {
            return false
        }

        if (remoteFile.size.toLong() != localFile.length()) {
            return true
        }

        return remoteFile.md5Checksum != localFile.calculateMd5()
    }

    private fun onLocalUpdated(
        localFile: File,
        drive: Drive,
        remoteFile: com.google.api.services.drive.model.File,
    ) {
        Timber.i("Local file updated $localFile")

        val mediaContent = FileContent("application/x-binary", localFile)
        val metadata = com.google.api.services.drive.model.File()
        metadata.modifiedTime = DateTime(localFile.lastModified())
        drive.files().update(remoteFile.id, metadata, mediaContent)
            .execute()
    }

    private fun onLocalOnly(
        remoteParentFolderId: String,
        localFile: File,
        localParentFolder: File,
        drive: Drive,
    ) {
        Timber.i("Local-only file detected $localFile")

        val metadata = com.google.api.services.drive.model.File()
        metadata.parents = listOf(remoteParentFolderId)
        metadata.name = localFile.name
        metadata.appProperties =
            mapOf(
                GDRIVE_PROPERTY_LOCAL_PATH to
                    localFile.toRelativeString(
                        localParentFolder,
                    ),
            )
        metadata.modifiedTime = DateTime(localFile.lastModified())
        val mediaContent = FileContent("application/x-binary", localFile)
        drive.files().create(metadata, mediaContent)
            .setFields("id")
            .execute()
    }

    private fun onRemoteOnly(
        localParentFolder: File,
        remoteFile: com.google.api.services.drive.model.File,
        drive: Drive,
    ) {
        Timber.i("Remote only file detected $remoteFile")
        val outputFile =
            File(
                localParentFolder,
                remoteFile.appProperties[GDRIVE_PROPERTY_LOCAL_PATH]!!,
            ).apply {
                parentFile?.mkdirs()
            }
        downloadToLocal(drive, remoteFile, outputFile)
    }

    private fun onRemoteUpdated(
        drive: Drive,
        remoteFile: com.google.api.services.drive.model.File,
        localFile: File,
    ) {
        Timber.i("Remote file updated $remoteFile")
        downloadToLocal(drive, remoteFile, localFile)
    }

    private fun downloadToLocal(
        drive: Drive,
        remoteFile: com.google.api.services.drive.model.File,
        localFile: File,
    ) {
        if (remoteFile.size == 0) return
        Timber.i("Downloading file to $localFile")
        drive.files()
            .get(remoteFile.id)
            .executeMediaAndDownloadTo(localFile.outputStream())
        localFile.setLastModified(remoteFile.modifiedTime.value)
    }

    private fun buildRemoteFileMap(
        remoteFiles: Sequence<com.google.api.services.drive.model.File>,
    ): Map<String, com.google.api.services.drive.model.File> {
        return remoteFiles
            .filter { it.appProperties?.get(GDRIVE_PROPERTY_LOCAL_PATH) != null }
            .map { it.appProperties?.get(GDRIVE_PROPERTY_LOCAL_PATH)!! to it }
            .toMap()
    }

    private fun buildLocalFileMap(folder: File): Map<String, File> {
        return folder
            .walkBottomUp()
            .filter { it.exists() && !it.isDirectory && it.length() > 0 }
            .map { it.toRelativeString(folder) to it }
            .toMap()
    }

    private fun getOrCreateAppDataFolder(folderName: String): String {
        val drive =
            DriveFactory(appContext).create()
                ?: throw UnsupportedOperationException()

        val query =
            drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name = '$folderName' and mimeType = 'application/vnd.google-apps.folder'")
                .setFields("files(id)")
                .execute()

        if (query.files.size > 0) {
            return query.files[0].id
        }

        val metadata = com.google.api.services.drive.model.File()
        metadata.parents = listOf("appDataFolder")
        metadata.name = folderName
        metadata.mimeType = "application/vnd.google-apps.folder"

        val file =
            drive.files().create(metadata)
                .setFields("id")
                .execute()

        return file.id
    }

    private fun getRemoteFiles(
        drive: Drive,
        folderId: String,
    ): Sequence<com.google.api.services.drive.model.File> {
        var pageToken: String? = null
        return sequence {
            do {
                val query =
                    "'$folderId' in parents and trashed = false and mimeType = 'application/x-binary'"

                val fields =
                    "nextPageToken, " +
                        "files(id, name, size, appProperties, modifiedTime, parents, md5Checksum)"

                val result =
                    drive.files().list()
                        .setPageSize(500)
                        .setSpaces("appDataFolder")
                        .setQ(query)
                        .setFields(fields)
                        .setPageToken(pageToken)
                        .execute()

                yieldAll(result.files)
                pageToken = result.nextPageToken
            } while (pageToken != null)
        }
    }

    companion object {
        const val PROVIDER_SMB = "smb"
        const val GDRIVE_PROPERTY_LOCAL_PATH = "localPath"
        private const val DRIVE_RECORDS_PREFERENCES = "gdrive_save_sync"

        // Marker of a damaged file (compared by identity).
        private val DAMAGED = JSONObject()
        private val SYNC_LOCK = Mutex()
        private val SMB_LOCK = Mutex()
    }
}

package com.swordfish.lemuroid.lib.library

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import com.swordfish.lemuroid.lib.storage.BaseStorageFile
import com.swordfish.lemuroid.lib.storage.StorageFile
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Files of the library that are not a game (e.g. loose audio tracks of a disc, or a BIOS). Identifying a file reads all
 * of it, which over a network folder takes seconds for each one, so their result is remembered: while a file keeps the
 * same size and modification date it is not read again. The CRC is kept for the BIOS check.
 *
 * The list starts again with each version of the app, whose database may recognize files that it did not before.
 */
class UnrecognizedFilesStore(private val appContext: Context) {
    private data class Entry(val size: Long, val lastModified: Long, val crc: String?)

    private val file = File(appContext.filesDir, FILE_NAME)
    private var previous: Map<String, Entry> = emptyMap()
    private val current = ConcurrentHashMap<String, Entry>()

    /** Loads the files remembered by the last scan. */
    fun startScan() {
        current.clear()
        previous =
            runCatching { load() }
                .onFailure { Timber.w(it, "Unable to read $FILE_NAME") }
                .getOrNull()
                ?: emptyMap()
    }

    /** The file as it was read in a previous scan, or null if it is not known or changed since. */
    fun find(baseStorageFile: BaseStorageFile): StorageFile? {
        val uri = baseStorageFile.uri.toString()
        val entry = previous[uri] ?: return null
        if (entry.size != baseStorageFile.size || entry.lastModified != baseStorageFile.lastModified) return null
        current[uri] = entry
        return StorageFile(
            name = baseStorageFile.name,
            size = baseStorageFile.size,
            crc = entry.crc,
            uri = baseStorageFile.uri,
            path = baseStorageFile.path,
        )
    }

    fun remember(
        baseStorageFile: BaseStorageFile,
        storageFile: StorageFile,
    ) {
        current[baseStorageFile.uri.toString()] =
            Entry(baseStorageFile.size, baseStorageFile.lastModified, storageFile.crc)
    }

    /**
     * Saves the files of this scan. After a scan that did not reach every file, the ones it did not reach are kept.
     */
    fun finishScan(completed: Boolean) {
        val entries = if (completed) current.toMap() else previous + current
        runCatching { save(entries) }.onFailure { Timber.w(it, "Unable to write $FILE_NAME") }
        previous = emptyMap()
        current.clear()
    }

    private fun load(): Map<String, Entry> {
        if (!file.exists()) return emptyMap()
        val json = JSONObject(file.readText())
        if (json.optLong(KEY_APP_VERSION) != appVersion()) return emptyMap()
        val files = json.optJSONObject(KEY_FILES) ?: return emptyMap()
        return files.keys().asSequence().associateWith { uri ->
            val entry = files.getJSONObject(uri)
            Entry(
                size = entry.getLong(KEY_SIZE),
                lastModified = entry.getLong(KEY_LAST_MODIFIED),
                crc = entry.optString(KEY_CRC).ifEmpty { null },
            )
        }
    }

    private fun save(entries: Map<String, Entry>) {
        val files = JSONObject()
        entries.forEach { (uri, entry) ->
            files.put(
                uri,
                JSONObject()
                    .put(KEY_SIZE, entry.size)
                    .put(KEY_LAST_MODIFIED, entry.lastModified)
                    .put(KEY_CRC, entry.crc ?: ""),
            )
        }
        val json = JSONObject().put(KEY_APP_VERSION, appVersion()).put(KEY_FILES, files)
        // Written to a temporary file first, so a crash never leaves half a list.
        val partFile = File(file.parentFile, "$FILE_NAME.part")
        partFile.writeText(json.toString())
        if (!partFile.renameTo(file)) {
            file.delete()
            partFile.renameTo(file)
        }
    }

    private fun appVersion(): Long {
        val packageInfo = appContext.packageManager.getPackageInfo(appContext.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(packageInfo)
    }

    companion object {
        private const val FILE_NAME = "unrecognized-files.json"
        private const val KEY_APP_VERSION = "appVersion"
        private const val KEY_FILES = "files"
        private const val KEY_SIZE = "size"
        private const val KEY_LAST_MODIFIED = "lastModified"
        private const val KEY_CRC = "crc"
    }
}

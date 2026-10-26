package com.swordfish.lemuroid.lib.storage.smb

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.leanback.preference.LeanbackPreferenceFragment
import com.swordfish.lemuroid.common.kotlin.extractEntryToFile
import com.swordfish.lemuroid.common.kotlin.writeToFile
import com.swordfish.lemuroid.lib.R
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.db.entity.DataFile
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.BaseStorageFile
import com.swordfish.lemuroid.lib.storage.IgnoredFolders
import com.swordfish.lemuroid.lib.storage.RomFiles
import com.swordfish.lemuroid.lib.storage.StorageFile
import com.swordfish.lemuroid.lib.storage.StorageProvider
import com.swordfish.lemuroid.lib.storage.local.DocumentFileParser
import com.swordfish.lemuroid.lib.storage.local.GameCacheUtils
import com.swordfish.lemuroid.lib.storage.local.StorageAccessFrameworkProvider
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

/**
 * Games library on a network share (SMB), used instead of the local folder when it is the chosen source
 * ([SmbConfigStore.librarySource]). Files are "smb://server/share/folder/file" URIs. Cores that can read through a
 * file descriptor (e.g. PCSX ReARMed) read games straight from the share ([SmbProxyFile]); for the others they are
 * copied to the cache before playing.
 */
class SmbStorageProvider(private val context: Context) : StorageProvider {
    override val id: String = "smb"

    override val name: String = context.getString(R.string.smb_storage)

    override val uriSchemes = listOf(SCHEME)

    override val prefsFragmentClass: Class<LeanbackPreferenceFragment>? = null

    override val enabledByDefault = true

    private val configStore = SmbConfigStore(context)

    // The same connection is used for the whole scan and for the games, instead of one per file.
    private var openShare: SmbShare? = null
    private var openShareConfig: SmbConfig? = null

    override fun listBaseStorageFiles(): Flow<List<BaseStorageFile>> {
        if (configStore.librarySource != SmbConfigStore.LibrarySource.SMB) return emptyFlow()
        val config = configStore.load()?.takeIf { it.isValid() } ?: return emptyFlow()

        return flow {
            val directories = mutableListOf(config.folder)
            while (directories.isNotEmpty()) {
                val directory = directories.removeAt(0)
                val entries =
                    runCatching { share(config).list(directory) }
                        .onFailure { Timber.e(it, "Error while listing $directory") }
                        .getOrDefault(emptyList())

                // Consoles that the app doesn't support (e.g. PS2) and PC games are not entered.
                directories += entries.filter { it.isDirectory && !IgnoredFolders.isIgnored(it.name) }.map { it.path }
                // Folders on a network share often have other things too (e.g. PC games): those files are skipped
                // without reading them, since each file read goes through the network.
                emit(
                    entries.filter { !it.isDirectory && isScannable(it.name) }.map {
                        val uri = buildUri(config, it.path)
                        BaseStorageFile(
                            name = it.name,
                            size = it.size,
                            uri = uri,
                            path = uri.path,
                            lastModified = it.lastModified,
                        )
                    },
                )
            }
        }
    }

    override fun getStorageFile(baseStorageFile: BaseStorageFile): StorageFile? {
        return DocumentFileParser.parseDocumentFile(context, baseStorageFile) { getInputStream(it) }
    }

    override fun getInputStream(uri: Uri): InputStream? {
        val config = configStore.load() ?: return null
        return share(config).openRead(pathInShare(uri))
    }

    override fun getGameRomFiles(
        game: Game,
        dataFiles: List<DataFile>,
        allowVirtualFiles: Boolean,
    ): RomFiles {
        val uri = Uri.parse(game.fileUri)
        val isZipped = uri.lastPathSegment?.endsWith(".zip", ignoreCase = true) == true &&
            uri.lastPathSegment != game.fileName

        if (isZipped && dataFiles.isEmpty()) {
            val cacheFile = GameCacheUtils.getCacheFileForGame(SMB_CACHE_SUBFOLDER, context, game)
            if (!cacheFile.exists()) {
                ZipInputStream(getInputStream(uri)).use { it.extractEntryToFile(game.fileName, cacheFile) }
            }
            return RomFiles.Standard(listOf(cacheFile))
        }

        if (allowVirtualFiles && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val config = configStore.load()!!
            val gameEntry = virtualEntry(config, game.fileName, uri)
            val dataEntries = dataFiles.map { virtualEntry(config, it.fileName, Uri.parse(it.fileUri)) }
            return RomFiles.Virtual(listOf(gameEntry) + dataEntries)
        }

        val gameFile = copyToCache(uri, GameCacheUtils.getCacheFileForGame(SMB_CACHE_SUBFOLDER, context, game))
        val dataEntries =
            dataFiles.map {
                copyToCache(
                    Uri.parse(it.fileUri),
                    GameCacheUtils.getDataFileForGame(SMB_CACHE_SUBFOLDER, context, game, it),
                )
            }
        return RomFiles.Standard(listOf(gameFile) + dataEntries)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun virtualEntry(
        config: SmbConfig,
        fileName: String,
        uri: Uri,
    ): RomFiles.Virtual.Entry {
        val path = pathInShare(uri)
        // share() connects again when the connection was closed, so a game can go on after a network drop.
        val fd = SmbProxyFile.open(context) { share(config).openRandomAccess(path) }
        return RomFiles.Virtual.Entry("${StorageAccessFrameworkProvider.VIRTUAL_FILE_PATH}/$fileName", fd)
    }

    // Copied once: next time the game starts from the cache. A partial copy is not kept.
    private fun copyToCache(
        uri: Uri,
        cacheFile: File,
    ): File {
        if (cacheFile.exists()) return cacheFile
        val partFile = File(cacheFile.parentFile, cacheFile.name + PART_SUFFIX)
        try {
            getInputStream(uri)!!.writeToFile(partFile)
            partFile.renameTo(cacheFile)
        } finally {
            partFile.delete()
        }
        return cacheFile
    }

    @Synchronized
    private fun share(config: SmbConfig): SmbShare {
        val current = openShare
        if (current != null && current.isConnected && openShareConfig == config) return current
        // Without waiting: a broken connection would wait for the whole request timeout.
        current?.closeInBackground()
        return SmbShare.open(config).also {
            openShare = it
            openShareConfig = config
        }
    }

    companion object {
        const val SCHEME = "smb"
        const val SMB_CACHE_SUBFOLDER = "smb-games"
        private const val PART_SUFFIX = ".part"

        // Discs split in tracks or with extra files, and the archives that may hold games or BIOS files.
        private val EXTRA_EXTENSIONS = setOf("bin", "img", "sub", "ccd", "mdf", "cue", "m3u", "iso", "chd", "zip")

        private val SCANNABLE_EXTENSIONS: Set<String> by lazy {
            (GameSystem.all().flatMap { it.uniqueExtensions + it.supportedExtensions } + EXTRA_EXTENSIONS)
                .map { it.lowercase() }
                .toSet() + BiosManager.BIOS_EXTENSIONS
        }

        private fun isScannable(name: String): Boolean =
            name.substringAfterLast('.', "").lowercase() in SCANNABLE_EXTENSIONS

        /** "smb://server/share/folder/file" for a [path] inside the share of the [config]. */
        fun buildUri(
            config: SmbConfig,
            path: String,
        ): Uri {
            val builder = Uri.Builder().scheme(SCHEME).authority(config.server.trim()).appendPath(config.shareName)
            path.split(SmbConfig.SMB_SEPARATOR).filter { it.isNotEmpty() }.forEach { builder.appendPath(it) }
            return builder.build()
        }

        /** Path inside the share of an "smb://" [uri]: its path without the share name. */
        fun pathInShare(uri: Uri): String = uri.pathSegments.drop(1).joinToString(SmbConfig.SMB_SEPARATOR)
    }
}

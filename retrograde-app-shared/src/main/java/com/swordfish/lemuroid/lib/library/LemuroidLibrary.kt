/*
 * GameLibrary.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.lib.library

import com.swordfish.lemuroid.common.coroutines.batchWithSizeAndTime
import com.swordfish.lemuroid.lib.bios.BiosManager
import com.swordfish.lemuroid.lib.library.covers.CustomCoverManager
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.DataFile
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.library.metadata.GameMetadata
import com.swordfish.lemuroid.lib.library.metadata.GameMetadataProvider
import com.swordfish.lemuroid.lib.storage.BaseStorageFile
import com.swordfish.lemuroid.lib.storage.GroupedStorageFiles
import com.swordfish.lemuroid.lib.storage.RomFiles
import com.swordfish.lemuroid.lib.storage.StorageFile
import com.swordfish.lemuroid.lib.storage.StorageProvider
import com.swordfish.lemuroid.lib.storage.StorageProviderRegistry
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

class LemuroidLibrary(
    private val retrogradedb: RetrogradeDatabase,
    private val storageProviderRegistry: Lazy<StorageProviderRegistry>,
    private val gameMetadataProvider: Lazy<GameMetadataProvider>,
    private val biosManager: BiosManager,
    private val customCoverManager: CustomCoverManager,
) {
    /** Progress of an indexing: [processed] of [total] files. [total] is null while the files are being listed. */
    data class IndexProgress(val processed: Int, val total: Int?)

    suspend fun indexLibrary(onProgress: suspend (IndexProgress) -> Unit = {}) {
        val startedAtMs = System.currentTimeMillis()

        // Games, data files and BIOS not seen in this pass are removed only when it went through every file:
        // after a stop or an error (e.g. a folder that can't be read) the ones not reached yet would be lost.
        var completed = false
        try {
            indexProviders(startedAtMs, onProgress)
            completed = true
        } catch (e: CancellationException) {
            Timber.i("Library indexing stopped")
            throw e
        } catch (e: Throwable) {
            Timber.e(e, "Library indexing stopped due to exception")
        } finally {
            if (completed) {
                cleanUp(startedAtMs)
            }
        }

        val executionTime = System.currentTimeMillis() - startedAtMs
        Timber.i("Library indexing completed in: $executionTime ms")
    }

    @OptIn(FlowPreview::class)
    private suspend fun indexProviders(
        startedAtMs: Long,
        onProgress: suspend (IndexProgress) -> Unit,
    ) {
        val gameMetadata = gameMetadataProvider.get()
        val enabledProviders = storageProviderRegistry.get().enabledProviders

        // Every file is listed before processing, so the progress can be shown as a percentage.
        onProgress(IndexProgress(0, null))
        val providersFiles = enabledProviders.map { it to listGroupedFiles(it) }
        val total = providersFiles.sumOf { (_, files) -> files.size }
        onProgress(IndexProgress(0, total))

        // Batches finish in parallel: counting and reporting together keeps the progress from going back.
        val progressMutex = Mutex()
        var processed = 0
        val onBatchProcessed: suspend (Int) -> Unit = { batchSize ->
            progressMutex.withLock {
                processed += batchSize
                onProgress(IndexProgress(processed, total))
            }
        }

        providersFiles.asFlow()
            .flatMapConcat { (provider, files) ->
                indexSingleProvider(provider, files, startedAtMs, gameMetadata, onBatchProcessed)
            }
            .collect()
    }

    private suspend fun listGroupedFiles(provider: StorageProvider): List<GroupedStorageFiles> {
        return provider.listBaseStorageFiles()
            .toList()
            .flatMap { StorageFilesMerger.mergeDataFiles(provider, it) }
    }

    @OptIn(FlowPreview::class)
    private fun indexSingleProvider(
        provider: StorageProvider,
        files: List<GroupedStorageFiles>,
        startedAtMs: Long,
        gameMetadata: GameMetadataProvider,
        onBatchProcessed: suspend (Int) -> Unit,
    ): Flow<Unit> {
        return files.asFlow()
            .batchWithSizeAndTime(MAX_BUFFER_SIZE, MAX_TIME)
            .flatMapMerge { processBatch(it, provider, startedAtMs, gameMetadata, onBatchProcessed) }
    }

    private suspend fun processBatch(
        batch: List<GroupedStorageFiles>,
        provider: StorageProvider,
        startedAtMs: Long,
        gameMetadata: GameMetadataProvider,
        onBatchProcessed: suspend (Int) -> Unit,
    ) = flow<Unit> {
        val entries = batch.map { fetchEntriesFromDatabase(it) }

        val existingEntries = entries.filterIsInstance<ScanEntry.GameFile>()
        handleExistingEntries(existingEntries, startedAtMs, gameMetadata, provider)
        onBatchProcessed(existingEntries.size)

        // New files are read to identify them, which is slow over the network: the progress moves with each one
        // instead of waiting for the whole batch (a library smaller than a batch went from 0% to 100% at the end).
        val newFiles = entries.filterIsInstance<ScanEntry.File>()
        val newEntries =
            newFiles.map {
                buildEntryFromMetadata(it.file, provider, gameMetadata, startedAtMs).also { onBatchProcessed(1) }
            }

        handleNewEntries(newEntries, startedAtMs, provider)
        onBatchProcessed(batch.size - existingEntries.size - newFiles.size)
    }

    private fun fetchEntriesFromDatabase(storageFile: GroupedStorageFiles): ScanEntry {
        Timber.d("Retrieving scan entry for uri: ${storageFile.primaryFile}")
        val game = retrogradedb.gameDao().selectByFileUri(storageFile.primaryFile.uri.toString())
        return buildScanEntry(storageFile, game)
    }

    private fun buildScanEntry(
        storageFile: GroupedStorageFiles,
        game: Game?,
    ): ScanEntry {
        return if (game != null) {
            ScanEntry.GameFile(storageFile, game)
        } else {
            ScanEntry.File(storageFile)
        }
    }

    private suspend fun handleExistingEntries(
        entries: List<ScanEntry.GameFile>,
        startedAtMs: Long,
        gameMetadata: GameMetadataProvider,
        provider: StorageProvider,
    ) {
        updateGames(entries, startedAtMs, gameMetadata, provider)
        updateDataFiles(entries, startedAtMs)
    }

    private suspend fun updateGames(
        entries: List<ScanEntry.GameFile>,
        startedAtMs: Long,
        gameMetadata: GameMetadataProvider,
        provider: StorageProvider,
    ) {
        val updatedGames =
            entries
                .map { entry ->
                    val primaryFile = entry.file.primaryFile
                    val fileChanged =
                        entry.game.fileSize != primaryFile.size ||
                            entry.game.fileLastModified != primaryFile.lastModified

                    val refreshedGame =
                        if (fileChanged) {
                            reidentifyGame(entry, provider, gameMetadata, startedAtMs)
                        } else {
                            runCatching { gameMetadata.refreshMetadata(entry.game) }.getOrNull() ?: entry.game
                        }

                    refreshedGame.copy(
                        lastIndexedAt = startedAtMs,
                        fileSize = primaryFile.size,
                        fileLastModified = primaryFile.lastModified,
                    )
                }

        updatedGames
            .forEach { Timber.d("Updating game: $it") }

        retrogradedb.gameDao().update(updatedGames)
    }

    /**
     * The file changed since it was indexed (e.g. it was replaced or completed), so it is identified again
     * from its content. User data (favorites, play history, custom name and cover) is kept.
     */
    private suspend fun reidentifyGame(
        entry: ScanEntry.GameFile,
        provider: StorageProvider,
        gameMetadata: GameMetadataProvider,
        startedAtMs: Long,
    ): Game {
        Timber.d("File changed since last scan, identifying it again: ${entry.file.primaryFile}")

        val identified =
            runCatching { buildEntryFromMetadata(entry.file, provider, gameMetadata, startedAtMs) }
                .getOrNull()
                .let { it as? ScanEntry.GameFile }
                ?.game
                ?: return entry.game

        return entry.game.copy(
            fileName = identified.fileName,
            title = identified.title,
            systemId = identified.systemId,
            developer = identified.developer,
            coverFrontUrl = identified.coverFrontUrl,
        )
    }

    private fun updateDataFiles(
        entries: List<ScanEntry.GameFile>,
        startedAtMs: Long,
    ) {
        val dataFiles =
            entries.flatMap { (storageFile, game) ->
                storageFile.dataFiles.map { convertIntoDataFile(game.id, it, startedAtMs) }
            }

        dataFiles
            .forEach { Timber.d("Updating data file: $it") }

        retrogradedb.dataFileDao().insert(dataFiles)
    }

    private fun convertIntoDataFile(
        gameId: Int,
        baseStorageFile: BaseStorageFile,
        startedAtMs: Long,
    ): DataFile {
        return DataFile(
            gameId = gameId,
            fileUri = baseStorageFile.uri.toString(),
            fileName = baseStorageFile.name,
            lastIndexedAt = startedAtMs,
            path = baseStorageFile.path,
        )
    }

    private fun handleNewEntries(
        entries: List<ScanEntry>,
        startedAtMs: Long,
        provider: StorageProvider,
    ) {
        val gameFiles =
            entries
                .filterIsInstance<ScanEntry.GameFile>()

        val unknownFiles =
            entries
                .filterIsInstance<ScanEntry.File>()
                .flatMap { it.file.allFiles() }

        handleNewGames(gameFiles, startedAtMs)
        handleUnknownFiles(provider, unknownFiles, startedAtMs)
    }

    private fun handleNewGames(
        pairs: List<ScanEntry.GameFile>,
        startedAtMs: Long,
    ) {
        val games =
            pairs
                .map { it.game }

        games.forEach { Timber.d("Insert: $it") }

        val gameIds = retrogradedb.gameDao().insert(games)
        val dataFiles =
            pairs
                .map { it.file.dataFiles }
                .zip(gameIds)
                .flatMap { (files, gameId) ->
                    files.map {
                        convertIntoDataFile(gameId.toInt(), it, startedAtMs)
                    }
                }

        retrogradedb.dataFileDao().insert(dataFiles)
    }

    private fun handleUnknownFiles(
        provider: StorageProvider,
        files: List<BaseStorageFile>,
        startedAtMs: Long,
    ) {
        files.forEach { baseStorageFile ->
            val storageFile = safeStorageFile(provider, baseStorageFile)
            val inputStream = storageFile?.uri?.let { provider.getInputStream(it) }

            if (storageFile != null && inputStream != null) {
                biosManager.tryAddBiosAfter(storageFile, inputStream, startedAtMs)
            }
        }
    }

    private suspend fun buildEntryFromMetadata(
        groupedStorageFile: GroupedStorageFiles,
        provider: StorageProvider,
        metadataProvider: GameMetadataProvider,
        startedAtMs: Long,
    ): ScanEntry {
        val game =
            sortedFilesForScanning(groupedStorageFile).asFlow()
                .mapNotNull { safeStorageFile(provider, it) }
                .mapNotNull { storageFile ->
                    val metadata = metadataProvider.retrieveMetadata(storageFile)
                    convertGameMetadataToGame(groupedStorageFile, storageFile, metadata, startedAtMs)
                }
                .firstOrNull()

        return buildScanEntry(groupedStorageFile, game)
    }

    private fun safeStorageFile(
        provider: StorageProvider,
        baseStorageFile: BaseStorageFile,
    ): StorageFile? {
        return runCatching { provider.getStorageFile(baseStorageFile) }
            .getOrNull()
    }

    private fun cleanUp(startedAtMs: Long) {
        kotlin.runCatching {
            removeDeletedBios(startedAtMs)
        }
        kotlin.runCatching {
            removeDeletedGames(startedAtMs)
        }
        kotlin.runCatching {
            removeDeletedDataFiles(startedAtMs)
        }
    }

    private fun removeDeletedBios(startedAtMs: Long) {
        biosManager.deleteBiosBefore(startedAtMs)
    }

    private fun sortedFilesForScanning(groupedStorageFile: GroupedStorageFiles): List<BaseStorageFile> {
        return groupedStorageFile.dataFiles.sortedBy { it.name } + listOf(groupedStorageFile.primaryFile)
    }

    private fun convertGameMetadataToGame(
        groupedStorageFile: GroupedStorageFiles,
        storageFile: StorageFile,
        gameMetadata: GameMetadata?,
        lastIndexedAt: Long,
    ): Game? {
        if (gameMetadata == null) {
            return null
        }

        val gameSystem = GameSystem.findById(gameMetadata.system!!)

        // If the databased matched a data file (as with bin/cue) we force link the primary filename
        val fileName =
            if (groupedStorageFile.dataFiles.isNotEmpty()) {
                groupedStorageFile.primaryFile.name
            } else {
                storageFile.name
            }

        return Game(
            fileName = fileName,
            fileUri = groupedStorageFile.primaryFile.uri.toString(),
            title = gameMetadata.name ?: groupedStorageFile.primaryFile.name,
            systemId = gameSystem.id.dbname,
            developer = gameMetadata.developer,
            coverFrontUrl = gameMetadata.thumbnail,
            lastIndexedAt = lastIndexedAt,
            fileSize = groupedStorageFile.primaryFile.size,
            fileLastModified = groupedStorageFile.primaryFile.lastModified,
        )
    }

    private fun removeDeletedDataFiles(startedAtMs: Long) {
        Timber.d("Deleting data files from db before: $startedAtMs")
        val dataFiles = retrogradedb.dataFileDao().selectByLastIndexedAtLessThan(startedAtMs)
        retrogradedb.dataFileDao().delete(dataFiles)
    }

    private fun removeDeletedGames(startedAtMs: Long) {
        Timber.d("Deleting games from db before: $startedAtMs")
        val games = retrogradedb.gameDao().selectByLastIndexedAtLessThan(startedAtMs)

        // Clean up custom cover files before removing the database rows
        customCoverManager.deleteCoversForGames(games)

        retrogradedb.gameDao().delete(games)
    }

    fun getGameFiles(
        game: Game,
        dataFiles: List<DataFile>,
        allowVirtualFiles: Boolean,
    ): RomFiles {
        val provider = storageProviderRegistry.get()
        return provider.getProvider(game).getGameRomFiles(game, dataFiles, allowVirtualFiles)
    }

    private sealed class ScanEntry {
        data class GameFile(val file: GroupedStorageFiles, val game: Game) : ScanEntry()

        data class File(val file: GroupedStorageFiles) : ScanEntry()
    }

    companion object {
        // We batch database updates to avoid unnecessary UI updates.
        const val MAX_BUFFER_SIZE = 200
        const val MAX_TIME = 5000
    }
}

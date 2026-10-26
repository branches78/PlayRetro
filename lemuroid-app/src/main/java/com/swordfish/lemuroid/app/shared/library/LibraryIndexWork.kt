package com.swordfish.lemuroid.app.shared.library

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.app.utils.android.withNotificationInBackground
import com.swordfish.lemuroid.lib.core.CoreUpdater
import com.swordfish.lemuroid.lib.core.CoresSelection
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import com.swordfish.lemuroid.lib.library.LemuroidLibrary
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class LibraryIndexWork(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var lemuroidLibrary: LemuroidLibrary

    @Inject
    lateinit var retrogradeDatabase: RetrogradeDatabase

    @Inject
    lateinit var coreUpdater: CoreUpdater

    @Inject
    lateinit var coresSelection: CoresSelection

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)

        val notificationsManager = NotificationsManager(applicationContext)

        val foregroundInfo = {
            createSyncForegroundInfo(
                NotificationsManager.LIBRARY_INDEXING_NOTIFICATION_ID,
                notificationsManager.libraryIndexingNotification(),
            )
        }

        withNotificationInBackground(foregroundInfo) {
            val result =
                withContext(Dispatchers.IO) {
                    kotlin.runCatching {
                        lemuroidLibrary.indexLibrary { setProgress(filesProgress(it)) }
                    }
                }

            result.exceptionOrNull()?.let {
                Timber.e("Library indexing work terminated with an exception:", it)
            }

            // The cores of the systems found are prepared by this same work, so the loading progress only reaches
            // 100% (and the games are shown) when they are ready too.
            if (!isStopped) {
                withContext(Dispatchers.IO) { downloadLibraryCores() }
            }
        }

        return Result.success()
    }

    private suspend fun downloadLibraryCores() {
        val cores =
            runCatching { CoreUpdateWork.libraryCores(retrogradeDatabase, coresSelection) }
                .getOrDefault(emptyList())

        cores.forEachIndexed { index, coreID ->
            setProgress(coresProgress(index, cores.size))
            runCatching { coreUpdater.downloadCores(applicationContext, listOf(coreID)) }
                .onFailure { Timber.e(it, "Core download failed: $coreID") }
        }
        setProgress(coresProgress(cores.size, cores.size))
    }

    companion object {
        private const val KEY_STAGE = "stage"
        private const val KEY_PROCESSED = "processed"
        private const val KEY_TOTAL = "total"

        private fun filesProgress(progress: LemuroidLibrary.IndexProgress): Data {
            return workDataOf(
                KEY_STAGE to LibraryLoadingProgress.Stage.FILES.name,
                KEY_PROCESSED to progress.processed,
                KEY_TOTAL to (progress.total ?: -1),
            )
        }

        private fun coresProgress(
            processed: Int,
            total: Int,
        ): Data {
            return workDataOf(
                KEY_STAGE to LibraryLoadingProgress.Stage.CORES.name,
                KEY_PROCESSED to processed,
                KEY_TOTAL to total,
            )
        }

        /** Progress sent by a running indexing; before its first update the files are still being listed. */
        fun progressFromWorkData(data: Data): LibraryLoadingProgress {
            val stage =
                data.getString(KEY_STAGE)
                    ?.let { runCatching { LibraryLoadingProgress.Stage.valueOf(it) }.getOrNull() }
                    ?: LibraryLoadingProgress.Stage.FILES
            val total = data.getInt(KEY_TOTAL, -1)
            return LibraryLoadingProgress(stage, data.getInt(KEY_PROCESSED, 0), total.takeIf { it >= 0 })
        }
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(LibraryIndexWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<LibraryIndexWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<LibraryIndexWork>()
    }
}

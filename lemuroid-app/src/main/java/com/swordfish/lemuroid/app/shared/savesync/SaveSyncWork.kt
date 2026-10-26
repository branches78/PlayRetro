package com.swordfish.lemuroid.app.shared.savesync

import android.app.ActivityManager
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.swordfish.lemuroid.app.mobile.feature.settings.SettingsManager
import com.swordfish.lemuroid.app.mobile.shared.NotificationsManager
import com.swordfish.lemuroid.app.tv.channel.ChannelUpdateWork
import com.swordfish.lemuroid.app.tv.shared.TVHelper
import com.swordfish.lemuroid.app.utils.android.createSyncForegroundInfo
import com.swordfish.lemuroid.lib.injection.AndroidWorkerInjection
import com.swordfish.lemuroid.lib.injection.WorkerKey
import com.swordfish.lemuroid.lib.library.findByName
import com.swordfish.lemuroid.lib.savesync.SaveSyncManager
import dagger.Binds
import dagger.android.AndroidInjector
import dagger.multibindings.IntoMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import timber.log.Timber

class SaveSyncWork(context: Context, workerParams: WorkerParameters) :
    CoroutineWorker(context, workerParams) {
    @Inject
    lateinit var saveSyncManager: SaveSyncManager

    @Inject
    lateinit var settingsManager: SettingsManager

    override suspend fun doWork(): Result {
        AndroidWorkerInjection.inject(this)

        if (!shouldPerformSaveSync()) {
            return Result.success()
        }

        val coresToSync =
            settingsManager.syncStatesCores()
                .mapNotNull { findByName(it) }
                .toSet()

        coroutineScope {
            // With the app on screen the banner shows the sync: the notification is only for when it's not.
            val notification =
                launch {
                    while (isAppInForeground()) delay(FOREGROUND_CHECK_MS)
                    displayNotification()
                }
            var copied = false
            try {
                saveSyncManager.sync(coresToSync) {
                    copied = true
                    setProgressAsync(workDataOf(PROGRESS_COPYING to true))
                }
            } catch (e: Throwable) {
                Timber.e(e, "Error in saves sync")
            } finally {
                notification.cancel()
            }
            // "Continue playing" may have changed: the channel of the TV home screen shows it too.
            if (copied && TVHelper.isTV(applicationContext)) ChannelUpdateWork.enqueue(applicationContext)
        }

        return Result.success()
    }

    private fun isAppInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private suspend fun shouldPerformSaveSync(): Boolean {
        val conditionsToRunThisWork =
            flow {
                emit(saveSyncManager.isSupported())
                emit(saveSyncManager.isConfigured())
                emit(settingsManager.syncSaves())
                emit(shouldScheduleThisSync())
            }

        return conditionsToRunThisWork.firstOrNull { !it } ?: true
    }

    private suspend fun shouldScheduleThisSync(): Boolean {
        val isAutoSync = inputData.getBoolean(IS_AUTO, false)
        val isManualSync = !isAutoSync
        return settingsManager.autoSaveSync() && isAutoSync || isManualSync
    }

    private suspend fun displayNotification() {
        val notificationsManager = NotificationsManager(applicationContext)

        val foregroundInfo =
            createSyncForegroundInfo(
                NotificationsManager.SAVE_SYNC_NOTIFICATION_ID,
                notificationsManager.saveSyncNotification(),
            )
        // Android may not allow it from the background (Android 12+): the sync goes on without the notification.
        runCatching { setForeground(foregroundInfo) }
            .onFailure { Timber.w(it, "Unable to show the sync notification") }
    }

    companion object {
        val UNIQUE_WORK_ID: String = SaveSyncWork::class.java.simpleName
        val UNIQUE_PERIODIC_WORK_ID: String = SaveSyncWork::class.java.simpleName + "Periodic"
        private const val IS_AUTO = "IS_AUTO"

        /** Progress set once the sync found something to copy (see [PendingOperationsMonitor.saveSyncRunning]). */
        const val PROGRESS_COPYING = "PROGRESS_COPYING"
        private const val FOREGROUND_CHECK_MS = 1000L

        /** When the app opens, if automatic sync is on (e.g. to get the progress made on another device). */
        fun enqueueStartupWork(applicationContext: Context) {
            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_ID,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<SaveSyncWork>()
                    .setInputData(workDataOf(IS_AUTO to true))
                    .build(),
            )
        }

        fun enqueueManualWork(applicationContext: Context) {
            val inputData: Data = workDataOf(IS_AUTO to false)

            WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_ID,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SaveSyncWork>()
                    .setInputData(inputData)
                    .build(),
            )
        }

        fun enqueueAutoWork(
            applicationContext: Context,
            delayMinutes: Long = 0,
        ) {
            val inputData: Data = workDataOf(IS_AUTO to true)

            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_WORK_ID,
                ExistingPeriodicWorkPolicy.REPLACE,
                PeriodicWorkRequestBuilder<SaveSyncWork>(3, TimeUnit.HOURS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .setRequiresBatteryNotLow(true)
                            .build(),
                    )
                    .setInputData(inputData)
                    .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                    .build(),
            )
        }

        fun cancelManualWork(applicationContext: Context) {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(UNIQUE_WORK_ID)
        }

        fun cancelAutoWork(applicationContext: Context) {
            WorkManager.getInstance(applicationContext).cancelUniqueWork(UNIQUE_PERIODIC_WORK_ID)
        }
    }

    @dagger.Module(subcomponents = [Subcomponent::class])
    abstract class Module {
        @Binds
        @IntoMap
        @WorkerKey(SaveSyncWork::class)
        abstract fun bindMyWorkerFactory(builder: Subcomponent.Builder): AndroidInjector.Factory<out ListenableWorker>
    }

    @dagger.Subcomponent
    interface Subcomponent : AndroidInjector<SaveSyncWork> {
        @dagger.Subcomponent.Builder
        abstract class Builder : AndroidInjector.Builder<SaveSyncWork>()
    }
}

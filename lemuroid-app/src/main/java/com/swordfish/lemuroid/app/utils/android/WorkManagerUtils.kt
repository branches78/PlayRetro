package com.swordfish.lemuroid.app.utils.android

import android.app.ActivityManager
import android.app.Notification
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

fun createSyncForegroundInfo(
    notificationId: Int,
    notification: Notification,
): ForegroundInfo {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(notificationId, notification)
    }
}

/**
 * Runs [block] showing the notification of [foregroundInfo] only while the app is not on screen: with the app open
 * its own progress is shown instead (e.g. "Loading your games").
 */
suspend fun <T> CoroutineWorker.withNotificationInBackground(
    foregroundInfo: () -> ForegroundInfo,
    block: suspend () -> T,
): T =
    coroutineScope {
        val notification =
            launch {
                while (isAppInForeground()) delay(FOREGROUND_CHECK_MS)
                // Android may not allow it from the background (Android 12+): the work goes on without it.
                runCatching { setForeground(foregroundInfo()) }
                    .onFailure { Timber.w(it, "Unable to show the work notification") }
            }
        try {
            block()
        } finally {
            notification.cancel()
        }
    }

private fun isAppInForeground(): Boolean {
    val info = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(info)
    return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
}

private const val FOREGROUND_CHECK_MS = 1000L

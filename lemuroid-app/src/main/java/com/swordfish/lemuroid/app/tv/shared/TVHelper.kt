package com.swordfish.lemuroid.app.tv.shared

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Environment
import com.swordfish.lemuroid.app.shared.settings.StorageFrameworkPickerLauncher
import com.swordfish.lemuroid.app.tv.folderpicker.TVFolderPickerLauncher

object TVHelper {
    fun isSAFSupported(context: Context): Boolean {
        val packageManager = context.packageManager

        val isStandardHardware =
            listOf(
                !packageManager.hasSystemFeature("android.hardware.type.television"),
                !packageManager.hasSystemFeature("android.hardware.type.watch"),
                !packageManager.hasSystemFeature("android.hardware.type.automotive"),
            ).all { it }

        val isNotLegacyStorage = !Environment.isExternalStorageLegacy()

        return isStandardHardware || isNotLegacyStorage
    }

    /**
     * Asks the connected display to switch to its low latency mode (HDMI ALLM / game mode).
     * Only available on Android 11+, and the TV must support it and have it enabled.
     */
    fun requestLowLatencyMode(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.setPreferMinimalPostProcessing(true)
        }
    }

    fun launchFolderPicker(context: Context) {
        if (isSAFSupported(context)) {
            StorageFrameworkPickerLauncher.pickFolder(context)
        } else {
            TVFolderPickerLauncher.pickFolder(context)
        }
    }

    fun isTV(context: Context): Boolean {
        val packageManager = context.packageManager
        return packageManager.hasSystemFeature("android.hardware.type.television")
    }
}

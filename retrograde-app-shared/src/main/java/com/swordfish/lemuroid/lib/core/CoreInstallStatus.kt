package com.swordfish.lemuroid.lib.core

import android.content.Context

/**
 * Why the cores could not be installed, when the reason needs its own message. Written by the core updater (main
 * process) and read when a game fails to load the core (game process), so it lives in its own preferences file.
 */
object CoreInstallStatus {
    private const val PREFERENCES = "core_install_status"
    private const val KEY_APP_NOT_OWNED = "app_not_owned"

    /**
     * Google Play refused to install the core modules because the app was not installed from Google Play on this
     * device (e.g. an APK copied from another device or from a website).
     */
    fun setAppNotOwned(
        context: Context,
        notOwned: Boolean,
    ) {
        preferences(context).edit().putBoolean(KEY_APP_NOT_OWNED, notOwned).commit()
    }

    fun isAppNotOwned(context: Context): Boolean = preferences(context).getBoolean(KEY_APP_NOT_OWNED, false)

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
}

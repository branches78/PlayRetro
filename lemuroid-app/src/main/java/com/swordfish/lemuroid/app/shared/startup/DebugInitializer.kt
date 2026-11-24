package com.swordfish.lemuroid.app.shared.startup

import android.content.Context
import android.os.StrictMode
import android.util.Log
import androidx.startup.Initializer
import com.swordfish.lemuroid.BuildConfig
import timber.log.Timber

class DebugInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            enableStrictMode()
        } else {
            // Warnings and errors only, so the diagnostic report (Settings > Miscellaneous) has them in its log.
            Timber.plant(WarningTree())
        }
    }

    private class WarningTree : Timber.DebugTree() {
        override fun isLoggable(
            tag: String?,
            priority: Int,
        ): Boolean = priority >= Log.WARN
    }

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build(),
        )
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}

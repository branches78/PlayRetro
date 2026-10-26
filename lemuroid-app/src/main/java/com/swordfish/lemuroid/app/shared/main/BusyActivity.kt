package com.swordfish.lemuroid.app.shared.main

import android.app.Activity

interface BusyActivity {
    fun activity(): Activity

    fun isBusy(): Boolean

    /** Whether the "Syncing games" banner is on screen: it already says why games can't start. */
    fun isSaveSyncBannerShown(): Boolean
}

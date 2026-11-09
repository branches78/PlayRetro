package com.swordfish.lemuroid.app.utils.android

import android.app.ActivityManager
import android.content.Context
import dagger.android.support.DaggerApplication

fun Context.isMainProcess(): Boolean {
    return DaggerApplication.getProcessName() == this.packageName
}

fun Context.getGLSLVersion(): Int {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return if (activityManager.deviceConfigurationInfo.reqGlEsVersion >= 0x30000) {
        3
    } else {
        2
    }
}

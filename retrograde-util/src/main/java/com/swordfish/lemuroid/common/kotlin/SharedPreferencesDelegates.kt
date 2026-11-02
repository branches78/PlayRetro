package com.swordfish.lemuroid.common.kotlin

import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class SharedPreferencesDelegates {
    class LongDelegate(
        private val sharedPreferences: SharedPreferences,
        private val key: String,
        private val defaultValue: Long,
    ) : ReadWriteProperty<Any, Long> {
        override fun getValue(
            thisRef: Any,
            property: KProperty<*>,
        ): Long {
            return sharedPreferences.getLong(key, defaultValue)
        }

        override fun setValue(
            thisRef: Any,
            property: KProperty<*>,
            value: Long,
        ) {
            sharedPreferences.edit().putLong(key, value).apply()
        }
    }
}

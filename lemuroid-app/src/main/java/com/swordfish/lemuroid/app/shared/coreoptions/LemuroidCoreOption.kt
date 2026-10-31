package com.swordfish.lemuroid.app.shared.coreoptions

import android.content.Context
import com.swordfish.lemuroid.lib.library.ExposedSetting
import java.io.Serializable

data class LemuroidCoreOption(
    private val exposedSetting: ExposedSetting,
    private val coreOption: CoreOption,
) : Serializable {
    fun getKey(): String {
        return exposedSetting.key
    }

    fun getDisplayName(context: Context): String {
        return context.getString(exposedSetting.titleId)
    }

    fun getDescription(context: Context): String? {
        return exposedSetting.descriptionId?.let { context.getString(it) }
    }

    fun isBoolean(): Boolean {
        return getEntriesValues().toSet() == BOOLEAN_VALUES
    }

    fun getEntries(context: Context): List<String> {
        if (getCorrectExposedSettings().isEmpty()) {
            return coreOption.optionValues.map { it.capitalize() }
        }

        return getCorrectExposedSettings().map { context.getString(it.titleId) }
    }

    fun getEntriesValues(): List<String> {
        if (getCorrectExposedSettings().isEmpty()) {
            return coreOption.optionValues.map { it }
        }

        return getCorrectExposedSettings().map { it.key }
    }

    fun getCurrentValue(): String {
        return coreOption.variable.value
    }

    // When none of the values known by the app is reported by the core (e.g. another core version), the core
    // values are shown as they are.
    private fun getCorrectExposedSettings(): List<ExposedSetting.Value> {
        return exposedSetting.values
            .filter { it.key in coreOption.optionValues }
    }
}

// Values of the options shown as a switch.
private val BOOLEAN_VALUES = setOf("enabled", "disabled")

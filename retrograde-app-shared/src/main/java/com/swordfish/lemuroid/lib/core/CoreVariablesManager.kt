package com.swordfish.lemuroid.lib.core

import android.content.SharedPreferences
import androidx.core.content.edit
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.SystemID
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.InvalidParameterException

class CoreVariablesManager(private val sharedPreferences: Lazy<SharedPreferences>) {
    /**
     * Values sent to the core: the app defaults, then the ones chosen for the whole system and, when [gameId] is
     * given, the ones chosen only for that game.
     */
    suspend fun getOptionsForCore(
        systemID: SystemID,
        systemCoreConfig: SystemCoreConfig,
        gameId: Int? = null,
    ): List<CoreVariable> {
        val defaultMap = convertCoreVariablesToMap(systemCoreConfig.defaultSettings)
        val coreVariables = retrieveCustomCoreVariables(systemID, systemCoreConfig)
        val gameVariables = gameId?.let { retrieveGameCoreVariables(it, systemCoreConfig) } ?: emptyList()
        val coreVariablesMap =
            defaultMap + convertCoreVariablesToMap(coreVariables) + convertCoreVariablesToMap(gameVariables)
        return convertMapToCoreVariables(coreVariablesMap)
    }

    private suspend fun retrieveGameCoreVariables(
        gameId: Int,
        systemCoreConfig: SystemCoreConfig,
    ): List<CoreVariable> =
        withContext(Dispatchers.IO) {
            val prefix = computeGamePreferencesPrefix(gameId)
            val requestedKeys =
                (systemCoreConfig.exposedSettings + systemCoreConfig.exposedAdvancedSettings)
                    .map { computeGamePreferenceKey(it.key, gameId) }

            sharedPreferences.get().all.filter { it.key in requestedKeys }
                .mapNotNull { (key, value) ->
                    val result =
                        when (value) {
                            is Boolean -> if (value) "enabled" else "disabled"
                            is String -> value
                            else -> null
                        }
                    result?.let { CoreVariable(key.removePrefix(prefix), it) }
                }
        }

    /** Forgets the options chosen only for games no longer in the library. */
    fun clearOptionsForGames(gameIds: List<Int>) {
        val ids = gameIds.toSet()
        val keyPrefix = "${GAME_OPTION_PREFIX}_"
        val preferences = sharedPreferences.get()
        val keys =
            preferences.all.keys.filter { key ->
                key.startsWith(keyPrefix) && key.removePrefix(keyPrefix).substringBefore('_').toIntOrNull() in ids
            }
        preferences.edit { keys.forEach { remove(it) } }
    }

    private fun convertMapToCoreVariables(variablesMap: Map<String, String>): List<CoreVariable> {
        return variablesMap.entries.map { CoreVariable(it.key, it.value) }
    }

    private fun convertCoreVariablesToMap(coreVariables: List<CoreVariable>): Map<String, String> {
        return coreVariables.associate { it.key to it.value }
    }

    private suspend fun retrieveCustomCoreVariables(
        systemID: SystemID,
        systemCoreConfig: SystemCoreConfig,
    ): List<CoreVariable> =
        withContext(Dispatchers.IO) {
            val exposedKeys = systemCoreConfig.exposedSettings
            val exposedAdvancedKeys = systemCoreConfig.exposedAdvancedSettings

            val requestedKeys =
                (exposedKeys + exposedAdvancedKeys).map { it.key }
                    .map { computeSharedPreferenceKey(it, systemID.dbname) }

            sharedPreferences.get().all.filter { it.key in requestedKeys }
                .map { (key, value) ->
                    val result =
                        when (value!!) {
                            is Boolean -> if (value as Boolean) "enabled" else "disabled"
                            is String -> value as String
                            else -> throw InvalidParameterException("Invalid setting in SharedPreferences")
                        }
                    CoreVariable(computeOriginalKey(key, systemID.dbname), result)
                }
        }

    companion object {
        private const val RETRO_OPTION_PREFIX = "cv"

        fun computeSharedPreferenceKey(
            retroVariableName: String,
            systemID: String,
        ): String {
            return "${computeSharedPreferencesPrefix(systemID)}$retroVariableName"
        }

        fun computeOriginalKey(
            sharedPreferencesKey: String,
            systemID: String,
        ): String {
            return sharedPreferencesKey.replace(computeSharedPreferencesPrefix(systemID), "")
        }

        private fun computeSharedPreferencesPrefix(systemID: String): String {
            return "${RETRO_OPTION_PREFIX}_${systemID}_"
        }

        private const val GAME_OPTION_PREFIX = "cvg"

        /** Key of a core option value chosen only for one game ("Only this game"). */
        fun computeGamePreferenceKey(
            retroVariableName: String,
            gameId: Int,
        ): String {
            return "${computeGamePreferencesPrefix(gameId)}$retroVariableName"
        }

        fun computeGamePreferencesPrefix(gameId: Int): String {
            return "${GAME_OPTION_PREFIX}_${gameId}_"
        }
    }
}

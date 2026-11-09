package com.swordfish.lemuroid.lib.core

import android.content.SharedPreferences
import androidx.core.content.edit
import com.fredporciuncula.flow.preferences.FlowSharedPreferences
import com.swordfish.lemuroid.lib.library.CoreID
import com.swordfish.lemuroid.lib.library.GameSystem
import com.swordfish.lemuroid.lib.library.SystemCoreConfig
import com.swordfish.lemuroid.lib.library.SystemID
import dagger.Lazy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CoresSelection(
    private val sharedPreferencesFactory: Lazy<SharedPreferences>,
) {
    private val sharedPreferences by lazy { sharedPreferencesFactory.get() }

    private val flowSharedPreferences by lazy { FlowSharedPreferences(sharedPreferences) }

    data class SelectedCore(
        val system: GameSystem,
        val coreConfig: SystemCoreConfig,
    )

    fun getSelectedCores(): Flow<List<SelectedCore>> {
        // Every system is listed, including the ones with a single core.
        val configurationFlows =
            GameSystem.all().map { system ->
                getSelectedCoreConfigForSystem(system)
                    .map { SelectedCore(system, it) }
            }

        return combine(configurationFlows) { it.toList() }
    }

    suspend fun updateCoreConfigForSystem(
        system: GameSystem,
        coreID: CoreID,
    ) = withContext(Dispatchers.IO) {
        sharedPreferences.edit()
            .putString(computeSystemPreferenceKey(system.id), coreID.coreName)
            .commit()
    }

    suspend fun getCoreConfigForSystem(system: GameSystem): SystemCoreConfig {
        return getSelectedCoreConfigForSystem(system).first()
    }

    /** Core used to play a game: the one chosen only for it ("Only this game") or else the one of its system. */
    suspend fun getCoreConfigForGame(
        system: GameSystem,
        gameId: Int,
    ): SystemCoreConfig {
        return getSelectedCoreConfigForGame(system, gameId).first()
    }

    fun getSelectedCoreConfigForGame(
        system: GameSystem,
        gameId: Int,
    ): Flow<SystemCoreConfig> {
        val gameCoreName = flowSharedPreferences.getString(computeGamePreferenceKey(gameId), "").asFlow()
        return combine(getSelectedCoreConfigForSystem(system), gameCoreName) { systemCore, coreName ->
            system.systemCoreConfigs.firstOrNull { it.coreID.coreName == coreName } ?: systemCore
        }.flowOn(Dispatchers.IO)
    }

    /** Whether the game has its own core instead of the one of its system. */
    fun hasGameCore(gameId: Int): Flow<Boolean> {
        return flowSharedPreferences.getString(computeGamePreferenceKey(gameId), "").asFlow()
            .map { it.isNotEmpty() }
            .flowOn(Dispatchers.IO)
    }

    /** Sets the core of a single game, or goes back to the core of its system when [coreID] is null. */
    suspend fun updateCoreConfigForGame(
        gameId: Int,
        coreID: CoreID?,
    ) = withContext(Dispatchers.IO) {
        sharedPreferences.edit().apply {
            if (coreID == null) {
                remove(computeGamePreferenceKey(gameId))
            } else {
                putString(computeGamePreferenceKey(gameId), coreID.coreName)
            }
        }.commit()
    }

    /** Forgets the core chosen for games no longer in the library, so it isn't kept downloaded for them. */
    fun clearCoresForGames(gameIds: List<Int>) {
        sharedPreferences.edit { gameIds.forEach { remove(computeGamePreferenceKey(it)) } }
    }

    /** Cores chosen for single games, so they are downloaded with the ones of the systems. */
    fun getGameSpecificCores(): List<CoreID> {
        return sharedPreferences.all
            .filterKeys { it.startsWith(GAME_CORE_PREFERENCE_BASE_KEY) }
            .values
            .mapNotNull { value -> CoreID.values().firstOrNull { it.coreName == value } }
            .distinct()
    }

    private fun getSelectedCoreConfigForSystem(system: GameSystem): Flow<SystemCoreConfig> {
        return getSelectedCoreNameForSystem(system)
            .map { coreName ->
                // Fall back to the default core if the stored one is no longer available (e.g. DeSmuME).
                system.systemCoreConfigs.firstOrNull { it.coreID.coreName == coreName }
                    ?: system.systemCoreConfigs.first()
            }
    }

    private fun getSelectedCoreNameForSystem(system: GameSystem): Flow<String> {
        return flow {
            val preferenceKey = computeSystemPreferenceKey(system.id)
            val currentValue = flowSharedPreferences.sharedPreferences.getString(preferenceKey, null)
            var defaultValue = currentValue

            if (currentValue == null) {
                defaultValue = getDefaultCoreForSystem(system)
                flowSharedPreferences.sharedPreferences.edit(true) {
                    putString(preferenceKey, defaultValue)
                }
            }

            val preference =
                flowSharedPreferences.getString(
                    preferenceKey,
                    defaultValue,
                )
            emitAll(preference.asFlow())
        }.flowOn(Dispatchers.IO)
    }

    private fun getDefaultCoreForSystem(system: GameSystem): String {
        return system.systemCoreConfigs.first().coreID.coreName
    }

    companion object {
        private const val CORE_SELECTION_BINDING_PREFERENCE_BASE_KEY = "pref_key_core_selection"

        fun computeSystemPreferenceKey(systemID: SystemID) =
            "${CORE_SELECTION_BINDING_PREFERENCE_BASE_KEY}_${systemID.dbname}"

        private const val GAME_CORE_PREFERENCE_BASE_KEY = "pref_key_game_core_selection_"

        fun computeGamePreferenceKey(gameId: Int) = "$GAME_CORE_PREFERENCE_BASE_KEY$gameId"
    }
}

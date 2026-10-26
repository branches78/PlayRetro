package com.swordfish.lemuroid.app.shared.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * Data of the details screen of a game, shared by TV and mobile: the game itself (it changes when it is added to the
 * favorites or renamed), its description from Wikipedia and other games of the same system.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GameDetailsViewModel(
    gameId: Int,
    retrogradeDb: RetrogradeDatabase,
    descriptionProvider: GameDescriptionProvider,
) : ViewModel() {
    class Factory(
        private val gameId: Int,
        private val retrogradeDb: RetrogradeDatabase,
        private val descriptionProvider: GameDescriptionProvider,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GameDetailsViewModel(gameId, retrogradeDb, descriptionProvider) as T
        }
    }

    data class State(
        val game: Game? = null,
        // Null while it is loading or when Wikipedia has no page for the game.
        val description: String? = null,
        val moreGames: List<Game> = emptyList(),
        // Everything read from the device is there (all but the description, which may need the network): the screen
        // is shown at once instead of growing while the data arrives.
        val isReady: Boolean = false,
    )

    /** A value that has been loaded, even when it is null. */
    private class Loaded<T>(val value: T)

    private val game = retrogradeDb.gameDao().observeById(gameId)

    // Looked up once: renaming the game or adding it to the favorites doesn't change its Wikipedia page.
    private val firstGame = game.filterNotNull().distinctUntilChanged { old, new -> old.id == new.id }

    private val description: Flow<String?> =
        firstGame.flatMapLatest { game ->
            flow { emit(descriptionProvider.getDescription(game)) }
                .onStart { emit(descriptionProvider.getCachedDescription(game)) }
        }

    private val moreGames: Flow<List<Game>> =
        firstGame.flatMapLatest { game ->
            retrogradeDb.gameDao()
                .selectBySystemShuffled(game.systemId, game.id, MORE_GAMES_COUNT + 1)
                .map { games -> games.filter { it.id != game.id }.take(MORE_GAMES_COUNT) }
        }

    val state =
        combine(
            game,
            description.onStart { emit(null) },
            moreGames.map { Loaded(it) }.onStart<Loaded<List<Game>>?> { emit(null) },
        ) { game, description, moreGames ->
            State(
                game = game,
                description = description,
                moreGames = moreGames?.value.orEmpty(),
                isReady = game != null && moreGames != null,
            )
        }.stateIn(viewModelScope, SharingStarted.Lazily, State())

    companion object {
        private const val MORE_GAMES_COUNT = 10
    }
}

package com.swordfish.lemuroid.app.utils.games

import android.content.Context
import com.swordfish.lemuroid.lib.library.db.entity.Game

/**
 * Line below the name of a game in its card: the system where games of several systems are mixed (home, favorites,
 * search) and the developer inside a system, where the system would be the same for every card (mobile and TV).
 */
enum class GameCardSubtitle {
    SYSTEM,
    DEVELOPER,
    ;

    fun textFor(
        context: Context,
        game: Game,
    ): String =
        when (this) {
            SYSTEM -> GameUtils.getSystemNameForGame(context, game)
            DEVELOPER -> game.developer?.takeIf { it.isNotBlank() }.orEmpty()
        }
}

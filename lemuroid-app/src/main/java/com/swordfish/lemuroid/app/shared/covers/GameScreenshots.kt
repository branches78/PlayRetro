package com.swordfish.lemuroid.app.shared.covers

import com.swordfish.lemuroid.lib.library.db.entity.Game

/** Screenshots and logo of a game on the libretro thumbnails server, next to its cover. */
object GameScreenshots {
    private const val BOX_ARTS_FOLDER = "/Named_Boxarts/"
    private const val LOGOS_FOLDER = "/Named_Logos/"
    private val IMAGE_FOLDERS = listOf("/Named_Snaps/", "/Named_Titles/")

    /**
     * Logo of the game (its title drawn on a transparent background). Many games have none, and some systems none at
     * all (e.g. PSP or DS): null only for games without a cover from the server.
     */
    fun logoUrl(game: Game): String? {
        val coverUrl = game.coverFrontUrl ?: return null
        if (!coverUrl.contains(BOX_ARTS_FOLDER)) return null
        return coverUrl.replace(BOX_ARTS_FOLDER, LOGOS_FOLDER)
    }

    /** In-game screenshot first, then the title screen. Empty for games without a cover from the server. */
    fun urls(game: Game): List<String> {
        val coverUrl = game.coverFrontUrl ?: return emptyList()
        if (!coverUrl.contains(BOX_ARTS_FOLDER)) return emptyList()
        return IMAGE_FOLDERS.map { coverUrl.replace(BOX_ARTS_FOLDER, it) }
    }
}

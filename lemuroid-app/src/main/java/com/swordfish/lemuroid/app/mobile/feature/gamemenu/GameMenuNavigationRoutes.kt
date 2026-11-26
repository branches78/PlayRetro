package com.swordfish.lemuroid.app.mobile.feature.gamemenu

import com.swordfish.lemuroid.R

enum class GameMenuRoute(
    val route: String,
    val titleId: Int,
    val parent: GameMenuRoute?,
) {
    HOME(
        route = "home",
        titleId = R.string.game_menu_title,
        parent = null,
    ),
    SAVE(
        route = "save",
        titleId = R.string.game_menu_save,
        parent = HOME,
    ),
    LOAD(
        route = "load",
        titleId = R.string.game_menu_load,
        parent = HOME,
    ),
    OPTIONS(
        route = "options",
        titleId = R.string.game_menu_settings,
        parent = HOME,
    ),
    ;

    fun canGoBack(): Boolean {
        return parent != null
    }

    companion object {
        fun findByRoute(route: String): GameMenuRoute {
            return values().first { it.route == route }
        }
    }
}

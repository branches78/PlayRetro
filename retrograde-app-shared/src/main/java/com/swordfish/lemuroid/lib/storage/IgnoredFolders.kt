package com.swordfish.lemuroid.lib.storage

/**
 * Folders that the scan of the library doesn't enter: consoles that the app doesn't support and PC games. Their discs
 * look like the ones of supported consoles (e.g. PS2 discs are ".iso" files with "SLUS" serials, like PlayStation ones)
 * and on a network share each one would be read for nothing. Names are compared ignoring case and separators.
 * ("NGC" is not here: it is also the Neo Geo Pocket Color.)
 */
object IgnoredFolders {
    private val NAMES =
        setOf(
            "ps2",
            "playstation2",
            "ps3",
            "playstation3",
            "ps4",
            "playstation4",
            "ps5",
            "playstation5",
            "psvita",
            "vita",
            "gcn",
            "gc",
            "gamecube",
            "nintendogamecube",
            "wii",
            "wiiu",
            "switch",
            "nintendoswitch",
            "xbox",
            "xbox360",
            "xboxone",
            "steam",
            "steamapps",
        )

    fun isIgnored(folderName: String): Boolean = folderName.lowercase().filter { it.isLetterOrDigit() } in NAMES
}

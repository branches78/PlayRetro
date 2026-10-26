package com.swordfish.lemuroid.lib.savesync

import android.content.SharedPreferences
import androidx.core.content.edit
import com.swordfish.lemuroid.lib.library.db.RetrogradeDatabase
import com.swordfish.lemuroid.lib.library.db.entity.Game
import org.json.JSONObject
import timber.log.Timber
import java.util.UUID

/**
 * "Continue playing" and favorites in [FILE_NAME], shared by the network share and Google Drive. Games are found by
 * system and file name ("snes/Top Gear (USA).sfc"), so each device can have its library in a different place.
 *
 * The file also has a revision, which goes up each time a device changes something (in it or in the saves), and the
 * id of the write that set it: with the fingerprint of this device, they tell quickly if a sync has anything to do.
 * When two devices write at the same time, the second one replaces the first one with the same revision: the id tells
 * the first one that its write was lost, and its next sync merges again (see [merge]).
 */
class SyncMetadata(
    private val retrogradeDb: RetrogradeDatabase,
    private val records: SharedPreferences,
) {
    // Favorites merged by the last [merge], remembered by [commit] once the file is written.
    private var mergedFavorites: Map<String, Boolean> = emptyMap()

    /** True when this device and the [remoteJson] didn't change since the last sync. */
    fun isUpToDate(
        remoteJson: JSONObject,
        localFingerprint: String,
    ): Boolean =
        revision(remoteJson) == records.getLong(KEY_REVISION, -1L) &&
            writer(remoteJson) == records.getString(KEY_WRITER, null) &&
            isLocalUpToDate(localFingerprint)

    /** True when the files, played games and favorites of this device didn't change since the last sync. */
    fun isLocalUpToDate(localFingerprint: String): Boolean = localFingerprint == records.getString(KEY_FINGERPRINT, null)

    fun revision(remoteJson: JSONObject): Long = remoteJson.optLong(KEY_REVISION, 0L)

    private fun writer(remoteJson: JSONObject): String = remoteJson.optString(KEY_WRITER, "")

    /** The last write of this device was replaced by the one of another device that had read the file before it. */
    private fun isOverwritten(remoteJson: JSONObject): Boolean {
        val lastWriter = records.getString(KEY_WRITER, null) ?: return false
        val lastRevision = records.getLong(KEY_REVISION, -1L)
        return revision(remoteJson) < lastRevision ||
            (revision(remoteJson) == lastRevision && writer(remoteJson) != lastWriter)
    }

    /**
     * Folder of the [game] on the network share (from the share root), written by the devices with their library there:
     * a device with its library in a folder of its own keeps the saves of a game with the ones of the others.
     */
    fun romFolderOf(
        remoteJson: JSONObject,
        game: Game,
    ): String? =
        remoteJson.optJSONObject(KEY_GAMES)
            ?.optJSONObject("${game.systemId}/${game.fileName}")
            ?.optString(KEY_FOLDER)
            ?.takeIf { it.isNotEmpty() }

    /** Whether the file couldn't be read in the last sync (see [setReadable]), shown in the settings. */
    fun isUnreadable(): Boolean = records.getBoolean(KEY_UNREADABLE, false)

    /**
     * When the file exists but can't be read, it is not written (the data of the other devices is kept), but the saves
     * and states are synced anyway: only the local [localFingerprint] is remembered then.
     */
    fun setReadable(
        readable: Boolean,
        localFingerprint: String? = null,
    ) {
        records.edit {
            putBoolean(KEY_UNREADABLE, !readable)
            localFingerprint?.let { putString(KEY_FINGERPRINT, it) }
        }
    }

    /**
     * Played date (the most recent) and favorite (the side that changed since the last sync wins: the other side's
     * change is the one that differs from the last synced value; the first sync of a device adds its favorites to the
     * ones of the other side) of the [games] of this device, merged into [remoteJson]. Games that this device doesn't
     * have are kept in the file for the others. True when [remoteJson] changed.
     *
     * When the last write of this device was lost (see [isOverwritten]), the favorites that differ are added to the
     * ones of the other side, as in a first sync: the change of this device is not in the file. The played dates need
     * nothing: the most recent one always wins.
     */
    suspend fun merge(
        remoteJson: JSONObject,
        games: List<Game>,
        romFolders: Map<String, String> = emptyMap(),
    ): Boolean {
        val overwritten = isOverwritten(remoteJson)
        if (overwritten) Timber.w("Save sync: the last write of this device was replaced by another device")
        val before = remoteJson.optJSONObject(KEY_GAMES)?.toString()
        val remoteGames = remoteJson.optJSONObject(KEY_GAMES) ?: JSONObject()
        var fromRemote = 0
        val now = System.currentTimeMillis()
        val updatedGames = mutableListOf<Game>()
        val favorites = mutableMapOf<String, Boolean>()

        games.forEach { game ->
            val key = "${game.systemId}/${game.fileName}"
            val entry = remoteGames.optJSONObject(key) ?: JSONObject()

            val remotePlayed = entry.optLong(KEY_LAST_PLAYED, 0L).takeIf { it > 0 }
            val lastPlayed = listOfNotNull(game.lastPlayedAt, remotePlayed).maxOrNull()

            val lastSyncedFavorite = records.getString("$FAVORITE_RECORD$key", null)?.toBooleanStrictOrNull()
            val favorite =
                when {
                    !entry.has(KEY_FAVORITE) -> game.isFavorite
                    // First sync of this device, or its last write was lost: the favorites of both sides.
                    lastSyncedFavorite == null || overwritten -> game.isFavorite || entry.getBoolean(KEY_FAVORITE)
                    game.isFavorite != lastSyncedFavorite -> game.isFavorite
                    else -> entry.getBoolean(KEY_FAVORITE)
                }
            val favoriteChangedAt =
                if (favorite != entry.optBoolean(KEY_FAVORITE, !favorite)) now else entry.optLong(KEY_FAVORITE_AT, now)

            if (lastPlayed != game.lastPlayedAt || favorite != game.isFavorite) {
                updatedGames += game.copy(lastPlayedAt = lastPlayed, isFavorite = favorite)
                fromRemote++
            }
            // Folder of the game on the network share: set by the devices with the library there, or found on the share by
            // the others (see SmbSaveSync), and kept.
            val folder = romFolders[key] ?: entry.optString(KEY_FOLDER).takeIf { it.isNotEmpty() }
            remoteGames.put(
                key,
                JSONObject().apply {
                    lastPlayed?.let { put(KEY_LAST_PLAYED, it) }
                    put(KEY_FAVORITE, favorite)
                    put(KEY_FAVORITE_AT, favoriteChangedAt)
                    folder?.let { put(KEY_FOLDER, it) }
                },
            )
            favorites[key] = favorite
        }
        mergedFavorites = favorites

        updatedGames.forEach { retrogradeDb.gameDao().update(it) }
        remoteJson.put(KEY_GAMES, remoteGames)
        val changed = remoteGames.toString() != before
        Timber.i("Save sync: ${games.size} games, $fromRemote updated from the other side, file changed: $changed")
        return changed
    }

    /**
     * Sets the next revision and a new write id in [remoteJson] when [changedRemote]: it has to be written then, and
     * [commit] called once it is written.
     */
    fun prepareWrite(
        remoteJson: JSONObject,
        changedRemote: Boolean,
    ) {
        if (!changedRemote) return
        remoteJson.put(KEY_REVISION, revision(remoteJson) + 1)
        remoteJson.put(KEY_WRITER, UUID.randomUUID().toString())
    }

    /**
     * Remembers the state of this sync once the file is written (if it had to be): favorites, revision, write id and
     * the [localFingerprint] taken after the downloads, so they don't count as changes. When the write fails nothing
     * is remembered, and the next sync merges again.
     */
    fun commit(
        remoteJson: JSONObject,
        localFingerprint: String,
    ) {
        records.edit {
            mergedFavorites.forEach { (key, favorite) -> putString("$FAVORITE_RECORD$key", favorite.toString()) }
            putLong(KEY_REVISION, revision(remoteJson))
            putString(KEY_WRITER, writer(remoteJson))
            putString(KEY_FINGERPRINT, localFingerprint)
            putBoolean(KEY_UNREADABLE, false)
        }
        mergedFavorites = emptyMap()
    }

    companion object {
        const val FILE_NAME = "playretro-sync.json"
        private const val KEY_REVISION = "revision"
        private const val KEY_FINGERPRINT = "fingerprint"
        private const val KEY_WRITER = "writer"
        private const val KEY_UNREADABLE = "metadata_unreadable"
        private const val FAVORITE_RECORD = "favorite:"
        private const val KEY_GAMES = "games"
        private const val KEY_LAST_PLAYED = "lastPlayedAt"
        private const val KEY_FAVORITE = "favorite"
        private const val KEY_FAVORITE_AT = "favoriteChangedAt"
        private const val KEY_FOLDER = "folder"

        /** Played dates and favorites of the [games], sorted so the same library always gives the same text. */
        fun gamesFingerprint(games: List<Game>): StringBuilder {
            val builder = StringBuilder()
            games.sortedBy { it.id }.forEach { game ->
                builder.append('|').append(game.systemId).append('/').append(game.fileName)
                    .append(':').append(game.lastPlayedAt).append(':').append(game.isFavorite)
            }
            return builder
        }
    }
}

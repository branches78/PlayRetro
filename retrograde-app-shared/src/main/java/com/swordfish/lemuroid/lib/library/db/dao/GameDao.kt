/*
 * GameDao.kt
 *
 * Copyright (C) 2017 Retrograde Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.swordfish.lemuroid.lib.library.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.swordfish.lemuroid.lib.library.db.entity.Game
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun selectById(id: Int): Game?

    /** The game and its later changes (favorite, custom name or cover), for its details screen. */
    @Query("SELECT * FROM games WHERE id = :id")
    fun observeById(id: Int): Flow<Game?>

    @Query("SELECT * FROM games WHERE fileUri = :fileUri")
    fun selectByFileUri(fileUri: String): Game?

    /** Games whose file is under [prefix] (e.g. the games of a network share, "smb://server/share/"). */
    /** Games whose file has this size: candidates when a file was moved to another folder (see LemuroidLibrary). */
    @Query("SELECT * FROM games WHERE fileSize = :fileSize")
    fun selectByFileSize(fileSize: Long): List<Game>

    @Query("SELECT * FROM games WHERE fileUri LIKE :prefix || '%'")
    suspend fun selectByFileUriPrefix(prefix: String): List<Game>

    @Query("SELECT * FROM games WHERE lastIndexedAt < :lastIndexedAt")
    fun selectByLastIndexedAtLessThan(lastIndexedAt: Long): List<Game>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY COALESCE(customName, title) ASC")
    fun selectFavorites(): PagingSource<Int, Game>

    @Query(
        """
        SELECT * FROM games WHERE lastPlayedAt IS NOT NULL AND isFavorite = 0 ORDER BY lastPlayedAt DESC LIMIT :limit
        """,
    )
    fun selectFirstUnfavoriteRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavoritesRecents(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun selectFirstFavorites(limit: Int): Flow<List<Game>>

    // Games never played in an order that changes with the seed (see HomeDiscovery).
    @Query("SELECT * FROM games WHERE lastPlayedAt IS NULL ORDER BY (id * :seed) % 7919 LIMIT :limit")
    fun selectNotPlayedShuffled(
        seed: Int,
        limit: Int,
    ): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt ASC LIMIT :limit")
    fun selectLeastRecentlyPlayed(limit: Int): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE systemId = :systemId ORDER BY COALESCE(customName, title) ASC, id DESC")
    fun selectBySystem(systemId: String): PagingSource<Int, Game>

    @Query("SELECT * FROM games WHERE systemId IN (:systemIds) ORDER BY COALESCE(customName, title) ASC, id DESC")
    fun selectBySystems(systemIds: List<String>): PagingSource<Int, Game>

    // Names in the order of selectBySystem/selectBySystems: the TV alphabet bar finds the position of each letter.
    @Query(
        "SELECT COALESCE(customName, title) FROM games WHERE systemId IN (:systemIds) " +
            "ORDER BY COALESCE(customName, title) ASC, id DESC",
    )
    fun selectNamesBySystems(systemIds: List<String>): Flow<List<String>>

    @Query("SELECT DISTINCT systemId FROM games ORDER BY systemId ASC")
    suspend fun selectSystems(): List<String>

    @Query("SELECT count(*) count, systemId systemId FROM games GROUP BY systemId")
    fun selectSystemsWithCount(): Flow<List<SystemCount>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 1")
    fun selectLastPlayed(): Flow<Game?>

    // Unplayed games from the same developer first, then from the same system.
    @Query(
        """
        SELECT * FROM games
        WHERE lastPlayedAt IS NULL AND id != :gameId AND (developer = :developer OR systemId = :systemId)
        ORDER BY CASE WHEN developer = :developer THEN 0 ELSE 1 END, COALESCE(customName, title) ASC
        LIMIT :limit
        """,
    )
    fun selectSimilarNotPlayed(
        gameId: Int,
        developer: String?,
        systemId: String,
        limit: Int,
    ): Flow<List<Game>>

    // Ids are auto-generated, so the highest ones belong to the games added last.
    @Query("SELECT * FROM games ORDER BY id DESC LIMIT :limit")
    fun selectRecentlyAdded(limit: Int): Flow<List<Game>>

    @Query(
        """
        SELECT * FROM games WHERE lastPlayedAt IS NOT NULL AND lastPlayedAt < :playedBefore
        ORDER BY lastPlayedAt DESC LIMIT :limit
        """,
    )
    fun selectPlayedBefore(
        playedBefore: Long,
        limit: Int,
    ): Flow<List<Game>>

    // The seed changes the order every day while keeping it stable during the day.
    @Query("SELECT * FROM games WHERE systemId = :systemId ORDER BY (id * :seed) % 7919 LIMIT :limit")
    fun selectBySystemShuffled(
        systemId: String,
        seed: Int,
        limit: Int,
    ): Flow<List<Game>>

    @Query(
        """
        SELECT count(*) count, developer developer FROM games
        WHERE developer IS NOT NULL AND developer != '' GROUP BY developer ORDER BY count DESC LIMIT 1
        """,
    )
    fun selectTopDeveloper(): Flow<DeveloperCount?>

    @Query("SELECT * FROM games WHERE developer = :developer ORDER BY COALESCE(customName, title) ASC LIMIT :limit")
    fun selectByDeveloper(
        developer: String,
        limit: Int,
    ): Flow<List<Game>>

    @Insert
    fun insert(games: List<Game>): List<Long>

    @Delete
    fun delete(games: List<Game>)

    @Update
    suspend fun update(game: Game)

    @Update
    fun update(games: List<Game>)
}

data class SystemCount(val systemId: String, val count: Int)

data class DeveloperCount(val developer: String, val count: Int)

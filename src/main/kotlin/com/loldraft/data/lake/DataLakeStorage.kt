package com.loldraft.data.lake

import com.loldraft.data.models.Game

interface DataLakeStorage {
    fun saveGame(game: Game)

    fun saveGames(games: List<Game>) {
        games.forEach { saveGame(it) }
    }

    fun getGame(gameId: String): Game?

    fun getGamesByPatch(patch: String): List<Game>

    fun getAllGames(): List<Game>

    fun count(): Int
}

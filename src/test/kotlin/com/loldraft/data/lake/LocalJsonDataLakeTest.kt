package com.loldraft.data.lake

import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Game
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LocalJsonDataLakeTest {
    @TempDir
    lateinit var tempDir: File

    private fun createGame(
        id: String,
        patch: String,
    ): Game =
        Game(
            id = id,
            gameNumber = 1,
            patch = patch,
            blueTeam = Team("t1", "T1", "T1"),
            redTeam = Team("t2", "Gen.G", "GEN"),
            draftState = DraftState.empty(),
            winner = Side.BLUE,
            durationSeconds = 1800,
        )

    @Test
    fun `should save and retrieve games by ID and patch`() {
        val dataLake = LocalJsonDataLake(tempDir)
        val game1 = createGame("game_1", "14.1")
        val game2 = createGame("game_2", "14.1")
        val game3 = createGame("game_3", "14.2")

        dataLake.saveGames(listOf(game1, game2, game3))

        assertEquals(3, dataLake.count())

        val retrieved1 = dataLake.getGame("game_1")
        assertNotNull(retrieved1)
        assertEquals("game_1", retrieved1.id)
        assertEquals("14.1", retrieved1.patch)

        val patch14_1Games = dataLake.getGamesByPatch("14.1")
        assertEquals(2, patch14_1Games.size)

        val patch14_2Games = dataLake.getGamesByPatch("14.2")
        assertEquals(1, patch14_2Games.size)

        assertNull(dataLake.getGame("non_existent"))
    }
}

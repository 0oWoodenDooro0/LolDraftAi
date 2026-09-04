package com.loldraft.server

import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Game
import com.loldraft.data.models.Team
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProMatchRepositoryPatchTest {
    private val t1 = Team("team-t1", "T1", "T1", "LCK")
    private val gen = Team("team-gen", "Gen.G", "GEN", "LCK")

    private fun createGame(
        id: String,
        patch: String,
        tournament: String = "LCK",
    ): Game =
        Game(
            id = id,
            gameNumber = 1,
            patch = patch,
            blueTeam = t1,
            redTeam = gen,
            draftState = DraftState(),
            tournament = tournament,
        )

    @Test
    fun `test getPatches extracts, normalizes, and sorts patches in chronological order`() {
        val sampleGames =
            listOf(
                createGame("g1", "16.02"),
                createGame("g2", "16.17"),
                createGame("g3", "16.01"),
                createGame("g4", "16.09"),
                createGame("g5", "16.10"),
            )
        val repo = ProMatchRepository(initialGames = sampleGames)
        repo.initialize()

        val patches = repo.getPatches()
        assertEquals(listOf("16.01", "16.02", "16.09", "16.10", "16.17"), patches)
    }

    @Test
    fun `test getDefaultPatch returns latest patch 16_17`() {
        val sampleGames =
            listOf(
                createGame("g1", "16.01"),
                createGame("g2", "16.16"),
                createGame("g3", "16.17"),
                createGame("g4", "16.05"),
            )
        val repo = ProMatchRepository(initialGames = sampleGames)
        repo.initialize()

        assertEquals("16.17", repo.getDefaultPatch())
    }

    @Test
    fun `test real esports dataset contains 16_17 as the default patch`() {
        val repo = ProMatchRepository()
        repo.initialize()

        val patches = repo.getPatches()
        assertTrue(patches.isNotEmpty(), "Patches should not be empty")
        assertTrue(patches.contains("16.17"), "Patches should include 16.17")
        assertEquals("16.17", repo.getDefaultPatch(), "Default patch should be 16.17")
    }

    @Test
    fun `test filter teams by patch`() {
        val sampleGames =
            listOf(
                createGame("g1", "16.01"),
                createGame("g2", "16.17"),
            )
        val repo = ProMatchRepository(initialGames = sampleGames)
        repo.initialize()

        val allTeams = repo.getTeams(patch = null)
        assertTrue(allTeams.isNotEmpty())

        val patch17Teams = repo.getTeams(patch = "16.17")
        assertFalse(patch17Teams.isEmpty())
        assertTrue(patch17Teams.any { it.name == "T1" })
    }

    @Test
    fun `test empty repository handles getPatches and getDefaultPatch gracefully`() {
        val emptyRepo = ProMatchRepository(initialGames = emptyList())
        emptyRepo.initialize()

        assertTrue(emptyRepo.getPatches().isEmpty())
        assertEquals("16.17", emptyRepo.getDefaultPatch()) // Safe fallback to 16.17
    }
}

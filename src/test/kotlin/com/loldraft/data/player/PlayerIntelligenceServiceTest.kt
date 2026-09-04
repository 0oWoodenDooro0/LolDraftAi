package com.loldraft.data.player

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayerIntelligenceServiceTest {
    private val t1 = Team("team-t1", "T1", "T1", "LCK")
    private val gen = Team("team-gen", "Gen.G", "GEN", "LCK")
    private val now = System.currentTimeMillis()
    private val oneDayMs = 86_400_000L

    private fun createProGame(
        id: String,
        role: Role,
        playerId: String,
        champion: String,
        won: Boolean,
    ): Game {
        val winnerSide = if (won) Side.BLUE else Side.RED
        val pick = PickSelection(champion, role, playerId)
        val turn = DraftTurn(1, Side.BLUE, ActionType.PICK, champion, role, playerId)
        return Game(
            id = id,
            gameNumber = 1,
            patch = "16.17",
            blueTeam = t1,
            redTeam = gen,
            draftState =
                DraftState(
                    bluePicks = listOf(pick),
                    turns = listOf(turn),
                ),
            winner = winnerSide,
        )
    }

    private fun createSoloQGame(
        summoner: String,
        champion: String,
        daysAgo: Int,
        won: Boolean,
    ): SoloQGame =
        SoloQGame(
            gameId = "sq-${System.nanoTime()}",
            accountId = summoner,
            server = SoloQServer.KR,
            timestampEpochMs = now - (daysAgo * oneDayMs),
            championId = champion,
            role = Role.MID,
            win = won,
            kills = 6,
            deaths = 2,
            assists = 8,
            durationSeconds = 1800,
        )

    @Test
    fun `test getTeamRosterIntelligence returns 5 standard roles with signature and soloq data`() {
        val proGames = mutableListOf<Game>()
        // Faker MID with Ahri (5 games, 4 wins)
        repeat(5) { i ->
            proGames.add(createProGame("faker-$i", Role.MID, "Faker", "Ahri", i < 4))
        }
        // Zeus TOP with Aatrox (5 games, 3 wins)
        repeat(5) { i ->
            proGames.add(createProGame("zeus-$i", Role.TOP, "Zeus", "Aatrox", i < 3))
        }

        val soloQGames = mutableListOf<SoloQGame>()
        // Faker SoloQ: T1 Faker playing Ahri 8 games in last 3 days (6 wins)
        repeat(8) { i ->
            soloQGames.add(createSoloQGame("T1 Faker", "Ahri", 1, i < 6))
        }

        val service = PlayerIntelligenceService()
        val rosterIntel =
            service.getTeamRosterIntelligence(
                teamId = "team-t1",
                proGames = proGames,
                soloQGames = soloQGames,
                referenceTimeMs = now,
            )

        assertTrue(rosterIntel.containsKey(Role.MID), "Should contain MID intel")
        val midIntel = rosterIntel[Role.MID]
        assertNotNull(midIntel)
        assertEquals("Faker", midIntel?.playerId)
        assertTrue(midIntel!!.signaturePicks.any { it.championId == "Ahri" }, "Ahri should be in signatures")
        assertTrue(midIntel.recentSoloQ7Days.any { it.championId == "Ahri" }, "Ahri should be in recent SoloQ")
        assertEquals(8, midIntel.recentSoloQ7Days.first { it.championId == "Ahri" }.gamesPlayed)
    }

    @Test
    fun `test practice spike is detected and tagged in player intelligence`() {
        val proGames = mutableListOf<Game>()
        // Faker plays Azir in pro (baseline 10 games)
        repeat(10) { i ->
            proGames.add(createProGame("faker-p-$i", Role.MID, "Faker", "Azir", true))
        }

        val soloQGames = mutableListOf<SoloQGame>()
        // Baseline 1 game in past 20 days
        soloQGames.add(createSoloQGame("T1 Faker", "LeBlanc", 20, true))
        // Sudden surge: 12 games of LeBlanc in last 2 days (8 wins)
        repeat(12) { i ->
            soloQGames.add(createSoloQGame("T1 Faker", "LeBlanc", 1, i < 8))
        }

        val service = PlayerIntelligenceService()
        val rosterIntel =
            service.getTeamRosterIntelligence(
                teamId = "team-t1",
                proGames = proGames,
                soloQGames = soloQGames,
                referenceTimeMs = now,
            )

        val midIntel = rosterIntel[Role.MID]
        assertNotNull(midIntel)
        val spikeAlert = midIntel!!.practiceSpikes.find { it.championId.equals("LeBlanc", ignoreCase = true) }
        assertNotNull(spikeAlert, "LeBlanc should have an active practice spike alert")
        assertTrue(spikeAlert!!.frequencyMultiplier > 2.0)
    }

    @Test
    fun `test fallback when no soloq games exist for player`() {
        val proGames =
            listOf(
                createProGame("g1", Role.SUPPORT, "Keria", "Nautilus", true),
            )
        val service = PlayerIntelligenceService()
        val rosterIntel =
            service.getTeamRosterIntelligence(
                teamId = "team-t1",
                proGames = proGames,
                soloQGames = emptyList(),
                referenceTimeMs = now,
            )

        val supIntel = rosterIntel[Role.SUPPORT]
        assertNotNull(supIntel)
        assertEquals("Keria", supIntel?.playerId)
        assertTrue(supIntel!!.recentSoloQ7Days.isEmpty())
        assertTrue(supIntel.practiceSpikes.isEmpty())
    }
}

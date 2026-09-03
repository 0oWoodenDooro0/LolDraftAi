package com.loldraft.data.player

import com.loldraft.data.models.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class SoloQTrackerTest {
    private val tracker = SoloQTracker()

    private val nowMs = 1700000000000L // arbitrary fixed epoch timestamp
    private val oneDayMs = TimeUnit.DAYS.toMillis(1)

    private fun createSoloQGame(
        gameId: String,
        championId: String,
        daysAgo: Double,
        win: Boolean = true,
        kills: Int = 5,
        deaths: Int = 2,
        assists: Int = 5,
        role: Role = Role.MID,
    ): SoloQGame =
        SoloQGame(
            gameId = gameId,
            accountId = "faker_kr",
            server = SoloQServer.KR,
            timestampEpochMs = nowMs - (daysAgo * oneDayMs).toLong(),
            championId = championId,
            role = role,
            win = win,
            kills = kills,
            deaths = deaths,
            assists = assists,
        )

    @Test
    fun `should return empty list when no games available`() {
        val stats = tracker.computeChampionStats(emptyList(), windowDays = 7, referenceTimeMs = nowMs)
        assertTrue(stats.isEmpty())
    }

    @Test
    fun `should filter games strictly within window`() {
        val games =
            listOf(
                createSoloQGame("g1", "azir", daysAgo = 1.0), // in 3d and 7d
                createSoloQGame("g2", "azir", daysAgo = 2.5), // in 3d and 7d
                createSoloQGame("g3", "azir", daysAgo = 5.0), // outside 3d, inside 7d
                createSoloQGame("g4", "azir", daysAgo = 8.0), // outside both
            )

        val stats3d = tracker.computeChampionStats(games, windowDays = 3, referenceTimeMs = nowMs)
        val stats7d = tracker.computeChampionStats(games, windowDays = 7, referenceTimeMs = nowMs)

        assertEquals(1, stats3d.size)
        assertEquals(2, stats3d[0].gamesPlayed)

        assertEquals(1, stats7d.size)
        assertEquals(3, stats7d[0].gamesPlayed)
    }

    @Test
    fun `should compute pick share and win rate accurately`() {
        val games =
            listOf(
                // 3 Azir games: 2 wins, 1 loss
                createSoloQGame("g1", "azir", daysAgo = 1.0, win = true),
                createSoloQGame("g2", "azir", daysAgo = 1.5, win = true),
                createSoloQGame("g3", "azir", daysAgo = 2.0, win = false),
                // 1 Orianna game: 1 win
                createSoloQGame("g4", "orianna", daysAgo = 2.5, win = true),
            )

        val stats = tracker.computeChampionStats(games, windowDays = 3, referenceTimeMs = nowMs)

        assertEquals(2, stats.size)

        val azir = stats.first { it.championId == "azir" }
        assertEquals(3, azir.gamesPlayed)
        assertEquals(2, azir.wins)
        assertEquals(1, azir.losses)
        assertEquals(2.0 / 3.0, azir.winRate, 0.001)
        assertEquals(0.75, azir.pickShare, 0.001) // 3 out of 4 total games
        assertEquals(1.0, azir.gamesPerDay, 0.001) // 3 games / 3 days

        val ori = stats.first { it.championId == "orianna" }
        assertEquals(1, ori.gamesPlayed)
        assertEquals(1, ori.wins)
        assertEquals(1.0, ori.winRate, 0.001)
        assertEquals(0.25, ori.pickShare, 0.001)
    }

    @Test
    fun `should sort champions by games played descending`() {
        val games =
            listOf(
                createSoloQGame("g1", "taliyah", daysAgo = 1.0),
                createSoloQGame("g2", "azir", daysAgo = 1.0),
                createSoloQGame("g3", "azir", daysAgo = 1.2),
                createSoloQGame("g4", "azir", daysAgo = 1.5),
                createSoloQGame("g5", "orianna", daysAgo = 1.0),
                createSoloQGame("g6", "orianna", daysAgo = 1.5),
            )

        val stats = tracker.computeChampionStats(games, windowDays = 3, referenceTimeMs = nowMs)

        assertEquals(3, stats.size)
        assertEquals("azir", stats[0].championId) // 3 games
        assertEquals("orianna", stats[1].championId) // 2 games
        assertEquals("taliyah", stats[2].championId) // 1 game
    }

    @Test
    fun `should calculate average KDA correctly`() {
        val games =
            listOf(
                createSoloQGame("g1", "azir", daysAgo = 1.0, kills = 6, deaths = 2, assists = 4), // (6+4)/2 = 5.0
                createSoloQGame("g2", "azir", daysAgo = 1.5, kills = 4, deaths = 3, assists = 5), // (4+5)/3 = 3.0
            )

        val stats = tracker.computeChampionStats(games, windowDays = 3, referenceTimeMs = nowMs)
        val azir = stats[0]

        // Total K=10, D=5, A=9 -> (10+9)/5 = 3.8
        assertEquals(3.8, azir.avgKda, 0.01)
    }
}

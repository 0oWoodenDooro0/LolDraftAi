package com.loldraft.data.player

import com.loldraft.data.models.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class PracticeSpikeDetectorTest {
    private val detector = PracticeSpikeDetector()

    private val nowMs = 1700000000000L
    private val oneDayMs = TimeUnit.DAYS.toMillis(1)

    private fun createSoloQGame(
        gameId: String,
        championId: String,
        daysAgo: Double,
        win: Boolean = true,
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
        )

    private fun emptyCareerStats(playerId: String = "Faker"): PlayerCareerStats =
        PlayerCareerStats(
            playerId = playerId,
            totalProGames = 0,
            totalWins = 0,
            winRate = 0.0,
            roleDistribution = emptyMap(),
            championRecords = emptyMap(),
            signaturePicks = emptyList(),
        )

    @Test
    fun `should detect off-meta surge when champion has zero pro games and sudden soloQ spam`() {
        val games = mutableListOf<SoloQGame>()
        // 6 Cho'Gath mid games in the last 3 days
        for (i in 1..6) {
            games.add(createSoloQGame("cho_$i", "chogath", daysAgo = 0.5 * i, win = true))
        }

        val alerts = detector.detectSpikes(games, emptyCareerStats(), referenceTimeMs = nowMs)

        assertEquals(1, alerts.size)
        val alert = alerts[0]
        assertEquals("chogath", alert.championId)
        assertEquals(SpikeAlertType.OFF_META_SURGE, alert.type)
        assertEquals(SpikeAlertSeverity.HIGH, alert.severity) // High volume + 100% WR
        assertEquals(6, alert.recentGamesCount)
        assertEquals(1.0, alert.recentWinRate, 0.001)
        assertEquals(0, alert.careerProGames)
        assertTrue(alert.reason.contains("chogath", ignoreCase = true))
    }

    @Test
    fun `should detect practice spike when pick frequency jumps significantly vs baseline`() {
        val games = mutableListOf<SoloQGame>()
        // Baseline (days 4 to 21): 2 games of Syndra across 17 baseline days
        games.add(createSoloQGame("syn_base_1", "syndra", daysAgo = 10.0))
        games.add(createSoloQGame("syn_base_2", "syndra", daysAgo = 15.0))

        // Recent (days 0 to 3): 5 games of Syndra in 3 days
        for (i in 1..5) {
            games.add(createSoloQGame("syn_rec_$i", "syndra", daysAgo = 0.4 * i, win = i % 2 == 0))
        }

        val careerWithSyndra =
            PlayerCareerStats(
                playerId = "Faker",
                totalProGames = 50,
                totalWins = 35,
                winRate = 0.7,
                roleDistribution = mapOf(Role.MID to 50),
                championRecords =
                    mapOf(
                        "syndra" to
                            ChampionCareerRecord(
                                championId = "syndra",
                                gamesPlayed = 15,
                                wins = 11,
                                losses = 4,
                                winRate = 0.733,
                                pickRate = 0.3,
                            ),
                    ),
                signaturePicks = emptyList(),
            )

        val alerts = detector.detectSpikes(games, careerWithSyndra, referenceTimeMs = nowMs)

        assertEquals(1, alerts.size)
        val alert = alerts[0]
        assertEquals("syndra", alert.championId)
        assertEquals(SpikeAlertType.PRACTICE_SPIKE, alert.type)
        assertEquals(5, alert.recentGamesCount)
        assertEquals(2, alert.baselineGamesCount)
        assertTrue(alert.frequencyMultiplier >= 2.5)
    }

    @Test
    fun `should categorize as pocket preparation when sudden practice accompanies very high win rate`() {
        val games = mutableListOf<SoloQGame>()
        // 7 Aurelion Sol games in 3 days with 6 wins (85.7% WR)
        for (i in 1..6) {
            games.add(createSoloQGame("asol_w_$i", "aurelionsol", daysAgo = 0.3 * i, win = true))
        }
        games.add(createSoloQGame("asol_l_1", "aurelionsol", daysAgo = 2.0, win = false))

        val alerts = detector.detectSpikes(games, emptyCareerStats(), referenceTimeMs = nowMs)

        assertEquals(1, alerts.size)
        val alert = alerts[0]
        assertEquals("aurelionsol", alert.championId)
        assertEquals(SpikeAlertSeverity.HIGH, alert.severity)
        assertTrue(alert.recentWinRate >= 0.8)
    }

    @Test
    fun `should not alert when games are within normal baseline frequencies`() {
        val games = mutableListOf<SoloQGame>()
        // 12 games in baseline 21 days (~0.57 games/day)
        for (i in 4..15) {
            games.add(createSoloQGame("azir_base_$i", "azir", daysAgo = i.toDouble()))
        }
        // 2 games in last 3 days (~0.67 games/day) -> ratio < 1.5x
        games.add(createSoloQGame("azir_rec_1", "azir", daysAgo = 1.0))
        games.add(createSoloQGame("azir_rec_2", "azir", daysAgo = 2.0))

        val alerts = detector.detectSpikes(games, emptyCareerStats(), referenceTimeMs = nowMs)

        assertTrue(alerts.none { it.championId == "azir" })
    }

    @Test
    fun `should return empty list when no games present in recent window`() {
        val games =
            listOf(
                createSoloQGame("old_1", "azir", daysAgo = 10.0),
            )
        val alerts = detector.detectSpikes(games, emptyCareerStats(), referenceTimeMs = nowMs)
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `should respect custom synthetic baseline configuration`() {
        val customDetector =
            PracticeSpikeDetector(
                config =
                    SpikeDetectorConfig(
                        recentDays = 2,
                        baselineDays = 20,
                        minRecentGamesForSpike = 4,
                        syntheticBaselineRate = 0.4,
                    ),
            )

        val games = mutableListOf<SoloQGame>()
        for (i in 1..4) {
            games.add(createSoloQGame("cho_$i", "chogath", daysAgo = 0.4 * i, win = true))
        }

        val alerts = customDetector.detectSpikes(games, emptyCareerStats(), referenceTimeMs = nowMs)

        assertEquals(1, alerts.size)
        val alert = alerts[0]
        // recentDailyRate = 4 / 2 = 2.0. With syntheticBaselineRate = 0.4 -> multiplier = 2.0 / 0.4 = 5.0
        assertEquals(5.0, alert.frequencyMultiplier, 0.001)
    }
}

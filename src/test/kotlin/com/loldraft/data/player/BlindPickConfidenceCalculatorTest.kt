package com.loldraft.data.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlindPickConfidenceCalculatorTest {
    private val calculator = BlindPickConfidenceCalculator()

    @Test
    fun `should rate S tier for dominant pro champion with active soloQ practice and strong early pick record`() {
        val proRecord =
            ChampionCareerRecord(
                championId = "azir",
                gamesPlayed = 25,
                wins = 20,
                losses = 5,
                winRate = 0.80,
                pickRate = 0.40,
            )

        val soloQStats =
            SoloQChampionStats(
                championId = "azir",
                gamesPlayed = 15,
                wins = 11,
                losses = 4,
                winRate = 0.733,
                pickShare = 0.35,
                gamesPerDay = 2.1,
            )

        val earlyPicks = listOf(true, true, true, true, false) // 4 wins out of 5 early picks (80%)

        val result = calculator.calculateConfidence("azir", proRecord, soloQStats, earlyPicks)

        assertEquals("azir", result.championId)
        assertTrue(result.confidenceScore >= 85.0, "Score was ${result.confidenceScore}, expected >= 85.0")
        assertEquals(ConfidenceRating.S, result.rating)
        assertTrue(result.reasoning.isNotEmpty())
        assertTrue(result.proMasteryScore > 80.0)
        assertTrue(result.soloQRecentScore > 70.0)
    }

    @Test
    fun `should rate D tier for completely unplayed champion`() {
        val result = calculator.calculateConfidence("urgot", null, null, emptyList())

        assertEquals("urgot", result.championId)
        assertEquals(0.0, result.confidenceScore, 0.001)
        assertEquals(ConfidenceRating.D, result.rating)
        assertEquals(0.0, result.proMasteryScore)
        assertEquals(0.0, result.soloQRecentScore)
        assertEquals(0.0, result.blindPickHistoricalScore)
    }

    @Test
    fun `should rate intermediate tiers B or C for moderate experience`() {
        val proRecord =
            ChampionCareerRecord(
                championId = "syndra",
                gamesPlayed = 6,
                wins = 3,
                losses = 3,
                winRate = 0.50,
                pickRate = 0.10,
            )

        val soloQStats =
            SoloQChampionStats(
                championId = "syndra",
                gamesPlayed = 8,
                wins = 4,
                losses = 4,
                winRate = 0.50,
                pickShare = 0.15,
                gamesPerDay = 1.1,
            )

        val result = calculator.calculateConfidence("syndra", proRecord, soloQStats, listOf(true, false))

        assertTrue(result.confidenceScore in 40.0..70.0, "Score was ${result.confidenceScore}")
        assertTrue(result.rating in listOf(ConfidenceRating.B, ConfidenceRating.C))
    }

    @Test
    fun `should grade ratings accurately against score brackets`() {
        val rS = calculator.calculateConfidence("c1", ChampionCareerRecord("c1", 30, 25, 5, 0.833, 0.5), null, emptyList())
        val rD = calculator.calculateConfidence("c2", ChampionCareerRecord("c2", 1, 0, 1, 0.0, 0.02), null, emptyList())

        assertTrue(rS.confidenceScore >= 40.0)
        assertTrue(rD.confidenceScore < 40.0)
        assertEquals(ConfidenceRating.D, rD.rating)
    }
}

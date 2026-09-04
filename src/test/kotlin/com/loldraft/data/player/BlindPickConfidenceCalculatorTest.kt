package com.loldraft.data.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BlindPickConfidenceCalculatorTest {
    private val calculator = BlindPickConfidenceCalculator()

    @Test
    fun `should rate S tier for dominant pro champion with strong early pick record`() {
        val proRecord =
            ChampionCareerRecord(
                championId = "azir",
                gamesPlayed = 25,
                wins = 20,
                losses = 5,
                winRate = 0.80,
                pickRate = 0.40,
            )

        val earlyPicks = listOf(true, true, true, true, false) // 4 wins out of 5 early picks (80%)

        val result = calculator.calculateConfidence("azir", proRecord, earlyPicks)

        assertEquals("azir", result.championId)
        assertTrue(result.confidenceScore >= 80.0, "Score was ${result.confidenceScore}, expected >= 80.0")
        assertTrue(result.rating in listOf(ConfidenceRating.S, ConfidenceRating.A))
        assertTrue(result.reasoning.isNotEmpty())
        assertTrue(result.proMasteryScore > 80.0)
    }

    @Test
    fun `should rate D tier for completely unplayed champion`() {
        val result = calculator.calculateConfidence("urgot", null, emptyList())

        assertEquals("urgot", result.championId)
        assertEquals(0.0, result.confidenceScore, 0.001)
        assertEquals(ConfidenceRating.D, result.rating)
        assertEquals(0.0, result.proMasteryScore)
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

        val result = calculator.calculateConfidence("syndra", proRecord, listOf(true, false))

        assertTrue(result.confidenceScore in 40.0..70.0, "Score was ${result.confidenceScore}")
        assertTrue(result.rating in listOf(ConfidenceRating.B, ConfidenceRating.C))
    }

    @Test
    fun `should grade ratings accurately against score brackets`() {
        val rS = calculator.calculateConfidence("c1", ChampionCareerRecord("c1", 30, 25, 5, 0.833, 0.5), emptyList())
        val rD = calculator.calculateConfidence("c2", ChampionCareerRecord("c2", 1, 0, 1, 0.0, 0.02), emptyList())

        assertTrue(rS.confidenceScore >= 40.0)
        assertTrue(rD.confidenceScore < 40.0)
        assertEquals(ConfidenceRating.D, rD.rating)
    }
}

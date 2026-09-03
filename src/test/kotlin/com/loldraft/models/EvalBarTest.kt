package com.loldraft.models

import com.loldraft.data.models.Side
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EvalBarTest {
    @Test
    fun `should map 50 percent win rate strictly to zero eval score and EVEN category`() {
        val result = EvalBarCalculator.calculate(0.50)
        assertEquals(0.0, result.score, 0.0001)
        assertEquals("0.0", result.formattedScore)
        assertNull(result.favoredSide)
        assertEquals(50.0, result.blueBarPercentage, 0.01)
        assertEquals(50.0, result.redBarPercentage, 0.01)
        assertEquals(EvalLeadCategory.EVEN, result.leadCategory)
    }

    @Test
    fun `should satisfy sign symmetry for complementary probabilities`() {
        val testProbabilities = listOf(0.51, 0.55, 0.60, 0.70, 0.85, 0.95)
        for (p in testProbabilities) {
            val blueScore = EvalBarCalculator.calculate(p)
            val redScore = EvalBarCalculator.calculate(1.0 - p)

            assertEquals(
                -blueScore.score,
                redScore.score,
                0.0001,
                "Symmetry failed for p=$p: blue=${blueScore.score}, red=${redScore.score}",
            )
            assertEquals(Side.BLUE, blueScore.favoredSide)
            assertEquals(Side.RED, redScore.favoredSide)
            assertEquals(100.0, blueScore.blueBarPercentage + blueScore.redBarPercentage, 0.0001)
            assertEquals(100.0, redScore.blueBarPercentage + redScore.redBarPercentage, 0.0001)
        }
    }

    @Test
    fun `should be strictly monotonic with respect to win rate`() {
        val winRates = (1..99).map { it / 100.0 }
        for (i in 0 until winRates.size - 1) {
            val s1 = EvalBarCalculator.calculate(winRates[i]).score
            val s2 = EvalBarCalculator.calculate(winRates[i + 1]).score
            assertTrue(
                s2 > s1,
                "Monotonicity failed at p1=${winRates[i]} ($s1) vs p2=${winRates[i + 1]} ($s2)",
            )
        }
    }

    @Test
    fun `should cap extreme boundary probabilities within maxEval without NaN or Infinity`() {
        val extremeInputs = listOf(0.0, 1.0, -0.5, 1.5, 0.0000001, 0.9999999)
        for (p in extremeInputs) {
            val result = EvalBarCalculator.calculate(p)
            assertFalse(result.score.isNaN(), "Score should not be NaN for input p=$p")
            assertFalse(result.score.isInfinite(), "Score should not be Infinite for input p=$p")
            assertTrue(
                result.score in -EvalBarCalculator.DEFAULT_MAX_EVAL..EvalBarCalculator.DEFAULT_MAX_EVAL,
                "Score ${result.score} should be bounded within [-10, +10]",
            )
            assertTrue(result.blueBarPercentage in 0.0..100.0)
            assertTrue(result.redBarPercentage in 0.0..100.0)
            assertEquals(100.0, result.blueBarPercentage + result.redBarPercentage, 0.001)
        }
    }

    @Test
    fun `should calculate correct UI bar percentages`() {
        val even = EvalBarCalculator.calculate(0.50)
        assertEquals(50.0, even.blueBarPercentage, 0.01)

        val blueAdv = EvalBarCalculator.calculate(0.75)
        assertEquals(75.0, blueAdv.blueBarPercentage, 0.01)
        assertEquals(25.0, blueAdv.redBarPercentage, 0.01)

        val redAdv = EvalBarCalculator.calculate(0.20)
        assertEquals(20.0, redAdv.blueBarPercentage, 0.01)
        assertEquals(80.0, redAdv.redBarPercentage, 0.01)
    }

    @Test
    fun `should accurately invert eval score back to original win rate`() {
        val testProbabilities = listOf(0.10, 0.25, 0.40, 0.50, 0.65, 0.80, 0.95)
        for (p in testProbabilities) {
            val eval = EvalBarCalculator.calculate(p)
            val invertedP = EvalBarCalculator.evalToWinRate(eval.score)
            assertEquals(p, invertedP, 0.001, "Inversion failed for p=$p (score=${eval.score})")
        }
    }

    @Test
    fun `should format positive negative and zero scores properly`() {
        assertEquals("0.0", EvalBarCalculator.format(0.0))
        assertEquals("+1.5", EvalBarCalculator.format(1.52))
        assertEquals("-1.2", EvalBarCalculator.format(-1.24))
        assertEquals("+0.1", EvalBarCalculator.format(0.08))
        assertEquals("-0.1", EvalBarCalculator.format(-0.08))
    }

    @Test
    fun `should categorize lead severity properly based on threshold brackets`() {
        assertEquals(EvalLeadCategory.EVEN, EvalBarCalculator.calculate(0.50).leadCategory)
        assertEquals(EvalLeadCategory.SLIGHT_BLUE, EvalBarCalculator.calculate(0.53).leadCategory)
        assertEquals(EvalLeadCategory.BLUE_ADVANTAGE, EvalBarCalculator.calculate(0.62).leadCategory)
        assertEquals(EvalLeadCategory.BLUE_DECISIVE, EvalBarCalculator.calculate(0.78).leadCategory)

        assertEquals(EvalLeadCategory.SLIGHT_RED, EvalBarCalculator.calculate(0.47).leadCategory)
        assertEquals(EvalLeadCategory.RED_ADVANTAGE, EvalBarCalculator.calculate(0.38).leadCategory)
        assertEquals(EvalLeadCategory.RED_DECISIVE, EvalBarCalculator.calculate(0.22).leadCategory)
    }

    @Test
    fun `should support custom scale factor`() {
        val defaultScore = EvalBarCalculator.calculate(0.70, scale = 1.0).score
        val doubleScore = EvalBarCalculator.calculate(0.70, scale = 2.0).score

        assertEquals(doubleScore, defaultScore * 2.0, 0.0001)
    }
}

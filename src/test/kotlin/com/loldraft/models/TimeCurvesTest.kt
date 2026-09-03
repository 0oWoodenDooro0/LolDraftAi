package com.loldraft.models

import com.loldraft.data.models.DraftState
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Team
import com.loldraft.data.style.EarlyGameMetrics
import com.loldraft.data.style.FirstPickAnalysis
import com.loldraft.data.style.GamePace
import com.loldraft.data.style.SidePreference
import com.loldraft.data.style.SideRecord
import com.loldraft.data.style.SideTendency
import com.loldraft.data.style.TacticalStyleMetrics
import com.loldraft.data.style.TeamTacticalProfile
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TimeCurvesTest {
    private val featureExtractor = DraftFeatureExtractor()
    private val timeCurveCalculator = TimeCurveCalculator()

    private fun createEarlyGameComp(): List<PickSelection> =
        listOf(
            PickSelection("Renekton", Role.TOP),
            PickSelection("Lee Sin", Role.JUNGLE),
            PickSelection("LeBlanc", Role.MID),
            PickSelection("Kalista", Role.BOT),
            PickSelection("Nautilus", Role.SUPPORT),
        )

    private fun createHyperScalingComp(): List<PickSelection> =
        listOf(
            PickSelection("Jax", Role.TOP),
            PickSelection("Sejuani", Role.JUNGLE),
            PickSelection("Azir", Role.MID),
            PickSelection("Jinx", Role.BOT),
            PickSelection("Lulu", Role.SUPPORT),
        )

    @Test
    fun `should predict early win rate advantage for early bullies and late advantage for scaling comp`() {
        val earlyPicks = createEarlyGameComp()
        val scalingPicks = createHyperScalingComp()

        val draftState = DraftState(bluePicks = earlyPicks, redPicks = scalingPicks)
        val features = featureExtractor.extract(draftState)
        val curve =
            timeCurveCalculator.calculate(
                draftState = draftState,
                features = features,
                baselineBlueWinRate = 0.50,
            )

        // At 10 & 15 minutes, Blue (Early comp) should have clear win rate advantage
        val min10 = curve.points.find { it.minute == 10 }
        val min15 = curve.points.find { it.minute == 15 }
        assertNotNull(min10)
        assertNotNull(min15)
        assertTrue(
            min10.blueWinRate > 0.53,
            "Early comp at 10m should have > 0.53 win rate, got: ${min10.blueWinRate}",
        )
        assertTrue(
            min15.blueWinRate > 0.52,
            "Early comp at 15m should have > 0.52 win rate, got: ${min15.blueWinRate}",
        )

        // At 30, 35, and 40 minutes, Red (Scaling comp) should take over
        val min30 = curve.points.find { it.minute == 30 }
        val min35 = curve.points.find { it.minute == 35 }
        val min40 = curve.points.find { it.minute == 40 }
        assertNotNull(min30)
        assertNotNull(min35)
        assertNotNull(min40)
        assertTrue(
            min35.redWinRate > 0.52,
            "Scaling comp at 35m should have redWinRate > 0.52, got: ${min35.redWinRate}",
        )
        assertTrue(
            min40.redWinRate > min10.redWinRate,
            "Scaling comp should have higher win rate at 40m than at 10m",
        )

        // Summary metrics
        assertTrue(curve.earlyGameWinRate > curve.lateGameWinRate)
        assertTrue(curve.trajectorySummary.isNotBlank())
    }

    @Test
    fun `should guarantee curve smoothness across all consecutive intervals`() {
        val draftState =
            DraftState(
                bluePicks = createEarlyGameComp(),
                redPicks = createHyperScalingComp(),
            )
        val features = featureExtractor.extract(draftState)
        val curve =
            timeCurveCalculator.calculate(
                draftState = draftState,
                features = features,
                baselineBlueWinRate = 0.51,
            )

        // Check continuity: step change between consecutive time points must not exceed 0.08 (8%)
        for (i in 0 until curve.points.size - 1) {
            val p1 = curve.points[i]
            val p2 = curve.points[i + 1]
            val diff = abs(p2.blueWinRate - p1.blueWinRate)
            assertTrue(
                diff <= 0.08,
                "Sudden discontinuous jump between minute ${p1.minute} (${p1.blueWinRate}) and minute ${p2.minute} (${p2.blueWinRate}): diff=$diff",
            )
        }
    }

    @Test
    fun `should invert time curve when compositions are swapped between Blue and Red`() {
        val compA = createEarlyGameComp()
        val compB = createHyperScalingComp()

        val draft1 = DraftState(bluePicks = compA, redPicks = compB)
        val features1 = featureExtractor.extract(draft1)
        val curve1 = timeCurveCalculator.calculate(draft1, features1, baselineBlueWinRate = 0.50)

        val draft2 = DraftState(bluePicks = compB, redPicks = compA)
        val features2 = featureExtractor.extract(draft2)
        val curve2 = timeCurveCalculator.calculate(draft2, features2, baselineBlueWinRate = 0.50)

        // At each time point, Blue win rate in draft1 should closely match Red win rate in draft2
        for (i in curve1.points.indices) {
            val pt1 = curve1.points[i]
            val pt2 = curve2.points[i]
            assertEquals(
                pt1.blueWinRate,
                pt2.redWinRate,
                0.03,
                "Inversion failed at minute ${pt1.minute}: draft1.blue=${pt1.blueWinRate}, draft2.red=${pt2.redWinRate}",
            )
        }
    }

    @Test
    fun `should strictly bound all win rates within 0 to 1 and sum to 1 at all time points`() {
        val draftState =
            DraftState(
                bluePicks = createEarlyGameComp(),
                redPicks = createHyperScalingComp(),
            )
        val features = featureExtractor.extract(draftState)
        val curve = timeCurveCalculator.calculate(draftState, features, baselineBlueWinRate = 0.70)

        for (pt in curve.points) {
            assertTrue(pt.blueWinRate in 0.01..0.99, "Win rate out of bounds at min ${pt.minute}")
            assertTrue(pt.redWinRate in 0.01..0.99, "Red win rate out of bounds at min ${pt.minute}")
            assertEquals(1.0, pt.blueWinRate + pt.redWinRate, 0.0001, "Win rates must sum to 1.0")
            assertNotNull(pt.evalBar)
            assertEquals(pt.blueWinRate > 0.50, pt.evalBar.score > 0.0)
        }
    }

    @Test
    fun `should evaluate gracefully with partial drafts`() {
        // 2 picks on blue, 1 on red
        val partialDraft =
            DraftState(
                bluePicks = listOf(PickSelection("Renekton", Role.TOP), PickSelection("Lee Sin", Role.JUNGLE)),
                redPicks = listOf(PickSelection("Jax", Role.TOP)),
            )
        val features = featureExtractor.extract(partialDraft)
        val curve = timeCurveCalculator.calculate(partialDraft, features, baselineBlueWinRate = 0.52)

        assertTrue(curve.points.isNotEmpty())
        assertEquals(7, curve.points.size) // 10, 15, 20, 25, 30, 35, 40
        for (pt in curve.points) {
            assertTrue(pt.blueWinRate in 0.01..0.99)
        }
    }

    @Test
    fun `should integrate team early dominance metrics into early time curve`() {
        val draftState =
            DraftState(
                bluePicks = createEarlyGameComp(),
                redPicks = createHyperScalingComp(),
            )
        val highDominanceTeam =
            TeamTacticalProfile(
                team = Team("t1", "T1", "T1", "LCK"),
                totalGamesAnalyzed = 30,
                sidePreference =
                    SidePreference(
                        SideRecord(15, 12, 3, 0.8),
                        SideRecord(15, 11, 4, 0.73),
                        SideRecord(30, 23, 7, 0.767),
                        0.07,
                        0.5,
                        0.5,
                        SideTendency.BALANCED,
                    ),
                earlyGameMetrics = EarlyGameMetrics(0.8, 0.75, 1200.0, 30, 9.5),
                tacticalStyleMetrics =
                    TacticalStyleMetrics(
                        0.6,
                        1.2,
                        1800.0,
                        "30:00",
                        GamePace.FAST_PACED,
                        com.loldraft.data.style.AggressionLevel.VERY_AGGRESSIVE,
                    ),
                firstPickAnalysis = FirstPickAnalysis(emptyList(), emptyList(), emptyMap()),
                tags = emptySet(),
            )

        val featuresWithProfile =
            featureExtractor.extract(
                draftState = draftState,
                blueTeamProfile = highDominanceTeam,
            )
        val curveWithProfile =
            timeCurveCalculator.calculate(
                draftState = draftState,
                features = featuresWithProfile,
                baselineBlueWinRate = 0.55,
                blueTeamProfile = highDominanceTeam,
            )

        val featuresNeutral = featureExtractor.extract(draftState)
        val curveNeutral =
            timeCurveCalculator.calculate(
                draftState = draftState,
                features = featuresNeutral,
                baselineBlueWinRate = 0.55,
            )

        val min10Dominant = curveWithProfile.points.find { it.minute == 10 }!!.blueWinRate
        val min10Neutral = curveNeutral.points.find { it.minute == 10 }!!.blueWinRate
        assertTrue(
            min10Dominant >= min10Neutral,
            "Dominant early team should have higher early win rate: dominant=$min10Dominant, neutral=$min10Neutral",
        )
    }
}

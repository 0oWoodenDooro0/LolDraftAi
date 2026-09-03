package com.loldraft.models

import com.loldraft.data.meta.ChampionMetaStats
import com.loldraft.data.meta.ChampionSynergy
import com.loldraft.data.meta.MatchupCounter
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.meta.PatchMetaMatrix
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DraftValueEvaluatorTest {
    private val evaluator = DraftValueEvaluator()

    private fun createStrongBlueDraft(): DraftState {
        // Blue: Aatrox, Sejuani, Orianna, Varus, Nautilus (All tier 0/1, well rounded)
        val bluePicks =
            listOf(
                PickSelection("Aatrox", Role.TOP),
                PickSelection("Sejuani", Role.JUNGLE),
                PickSelection("Orianna", Role.MID),
                PickSelection("Varus", Role.BOT),
                PickSelection("Nautilus", Role.SUPPORT),
            )
        // Red: K'Sante, Vi, Azir, Kai'Sa, Rell
        val redPicks =
            listOf(
                PickSelection("K'Sante", Role.TOP),
                PickSelection("Vi", Role.JUNGLE),
                PickSelection("Azir", Role.MID),
                PickSelection("Kai'Sa", Role.BOT),
                PickSelection("Rell", Role.SUPPORT),
            )
        return DraftState(bluePicks = bluePicks, redPicks = redPicks)
    }

    private fun createSamplePatchMeta(): PatchMetaMatrix =
        PatchMetaMatrix(
            patch = "14.1",
            totalGames = 100,
            championStats =
                mapOf(
                    "aatrox" to ChampionMetaStats("aatrox", "14.1", picks = 50, winRate = 0.58, tier = MetaTier.T0),
                    "sejuani" to ChampionMetaStats("sejuani", "14.1", picks = 40, winRate = 0.53, tier = MetaTier.T1),
                    "orianna" to ChampionMetaStats("orianna", "14.1", picks = 60, winRate = 0.56, tier = MetaTier.T0),
                    "varus" to ChampionMetaStats("varus", "14.1", picks = 30, winRate = 0.52, tier = MetaTier.T1),
                    "nautilus" to ChampionMetaStats("nautilus", "14.1", picks = 45, winRate = 0.54, tier = MetaTier.T1),
                    "ksante" to ChampionMetaStats("ksante", "14.1", picks = 20, winRate = 0.44, tier = MetaTier.T3),
                    "vi" to ChampionMetaStats("vi", "14.1", picks = 15, winRate = 0.46, tier = MetaTier.T3),
                    "azir" to ChampionMetaStats("azir", "14.1", picks = 25, winRate = 0.47, tier = MetaTier.T2),
                    "kaisa" to ChampionMetaStats("kaisa", "14.1", picks = 20, winRate = 0.45, tier = MetaTier.T3),
                    "rell" to ChampionMetaStats("rell", "14.1", picks = 15, winRate = 0.48, tier = MetaTier.T3),
                ),
            synergies =
                listOf(
                    ChampionSynergy("Aatrox", "Sejuani", 20, 14, 0.70, 0.55, 0.15, 1.5),
                    ChampionSynergy("Orianna", "Nautilus", 15, 10, 0.66, 0.54, 0.12, 1.2),
                ),
            matchupCounters =
                listOf(
                    MatchupCounter("Aatrox", "K'Sante", Role.TOP, 15, 11, 4, 0.733, 0.233, 450.0, 2.3),
                ),
        )

    @Test
    fun `should predict win rate strictly bounded in 0 to 1 and sum to 1`() {
        val draft = createStrongBlueDraft()
        val result = evaluator.evaluate(draft)

        assertTrue(result.blueWinRate in 0.0..1.0, "Blue win rate should be in [0, 1]")
        assertTrue(result.redWinRate in 0.0..1.0, "Red win rate should be in [0, 1]")
        assertEquals(1.0, result.blueWinRate + result.redWinRate, 0.0001, "Win rates must sum to 1.0")
        assertNotNull(result.features)
        assertEquals(52, result.features.values.size)
    }

    @Test
    fun `should favor team with dominant patch meta, radar, and synergy`() {
        val draft = createStrongBlueDraft()
        val patchMeta = createSamplePatchMeta()

        val result = evaluator.evaluate(draftState = draft, patchMeta = patchMeta)

        // Blue has superior meta tier (T0/T1 vs T2/T3), positive synergy, and lane counter
        assertTrue(
            result.blueWinRate > 0.52,
            "Expected Blue win rate > 0.52 given meta and synergy advantages, got: ${result.blueWinRate}",
        )
        assertTrue(result.evalScore > 0.0, "Eval score should be positive for Blue")
    }

    @Test
    fun `should reverse win rate advantage when compositions are swapped`() {
        val blueDraft = createStrongBlueDraft()
        val patchMeta = createSamplePatchMeta()

        val result1 = evaluator.evaluate(draftState = blueDraft, patchMeta = patchMeta)

        // Swap Blue and Red teams' champions
        val invertedDraft =
            DraftState(
                bluePicks = blueDraft.redPicks,
                redPicks = blueDraft.bluePicks,
            )

        val result2 = evaluator.evaluate(draftState = invertedDraft, patchMeta = patchMeta)

        // The composition that was strong on Blue is now on Red
        // Taking side bias into account, Red should now be strongly favored in result2
        assertTrue(
            result2.redWinRate > 0.50,
            "Swapped composition should favor Red team, got redWinRate: ${result2.redWinRate}",
        )
        assertTrue(
            result1.blueWinRate > result2.blueWinRate,
            "Blue win rate in original should be higher than in swapped draft",
        )
    }

    @Test
    fun `should provide dominant explanatory factor contributions`() {
        val draft = createStrongBlueDraft()
        val patchMeta = createSamplePatchMeta()

        val result = evaluator.evaluate(draftState = draft, patchMeta = patchMeta)

        assertNotNull(result.dominantFactors)
        assertTrue(result.dominantFactors.isNotEmpty(), "Dominant factors should not be empty")

        val factorNames = result.dominantFactors.map { it.name }
        // Should contain factors like meta tier, synergy, matchup counter, or radar
        assertTrue(
            factorNames.any { it.contains("meta", ignoreCase = true) || it.contains("synergy", ignoreCase = true) },
            "Expected meta or synergy factor in dominant factors, got: $factorNames",
        )
    }

    @Test
    fun `should scale confidence proportionally with draft completion`() {
        // Complete 5v5 draft (10 picks)
        val fullDraft = createStrongBlueDraft()
        val fullResult = evaluator.evaluate(fullDraft)
        assertEquals(1.0, fullResult.confidence, 0.01, "Full draft should have confidence 1.0")

        // Partial draft (e.g. 2 picks on blue, 1 pick on red = 3/10 complete)
        val partialDraft =
            DraftState(
                bluePicks = listOf(PickSelection("Aatrox"), PickSelection("Sejuani")),
                redPicks = listOf(PickSelection("K'Sante")),
            )
        val partialResult = evaluator.evaluate(partialDraft)
        assertTrue(partialResult.confidence < fullResult.confidence, "Partial draft should have lower confidence")
        assertEquals(0.3, partialResult.confidence, 0.05, "Partial draft (3 picks) should have ~0.3 confidence")
    }

    @Test
    fun `should evaluate gracefully with fallback evaluator when ONNX model is absent`() {
        // Instantiating with an explicit nonexistent model path
        val fallbackEvaluator = DraftValueEvaluator(modelPath = "/nonexistent/path/to/model.onnx")
        val draft = createStrongBlueDraft()

        val result = fallbackEvaluator.evaluate(draft)

        assertTrue(result.blueWinRate in 0.0..1.0)
        assertTrue(result.redWinRate in 0.0..1.0)
        assertEquals(1.0, result.blueWinRate + result.redWinRate, 0.0001)
    }

    @Test
    fun `should integrate with custom team profiles and reflect rating disparities`() {
        val draft = createStrongBlueDraft()
        val highEloTeam =
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
                earlyGameMetrics = EarlyGameMetrics(0.7, 0.65, 950.0, 30, 8.0),
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

        val lowEloTeam =
            TeamTacticalProfile(
                team = Team("low", "LowElo", "LOW", "LCK"),
                totalGamesAnalyzed = 30,
                sidePreference =
                    SidePreference(
                        SideRecord(15, 5, 10, 0.33),
                        SideRecord(15, 4, 11, 0.27),
                        SideRecord(30, 9, 21, 0.30),
                        0.06,
                        0.5,
                        0.5,
                        SideTendency.BALANCED,
                    ),
                earlyGameMetrics = EarlyGameMetrics(0.3, 0.35, -700.0, 30, 3.0),
                tacticalStyleMetrics =
                    TacticalStyleMetrics(
                        0.3,
                        0.8,
                        2100.0,
                        "35:00",
                        GamePace.SLOW_CONTROLLED,
                        com.loldraft.data.style.AggressionLevel.CONTROL_ORIENTED,
                    ),
                firstPickAnalysis = FirstPickAnalysis(emptyList(), emptyList(), emptyMap()),
                tags = emptySet(),
            )

        val resultWithStrongBlue =
            evaluator.evaluate(
                draftState = draft,
                blueTeamProfile = highEloTeam,
                redTeamProfile = lowEloTeam,
            )

        val resultWithNeutralTeams = evaluator.evaluate(draftState = draft)

        assertTrue(
            resultWithStrongBlue.blueWinRate > resultWithNeutralTeams.blueWinRate,
            "Team rating advantage should further increase blue win rate",
        )
    }

    @Test
    fun `should implement AutoCloseable and close cleanly without errors`() {
        val closableEvaluator = DraftValueEvaluator()
        // Use Kotlin's .use extension to verify AutoCloseable behavior
        closableEvaluator.use {
            val draft = createStrongBlueDraft()
            val result = it.evaluate(draft)
            assertTrue(result.blueWinRate in 0.0..1.0)
        }
    }

    @Test
    fun `should evaluate batch of multiple draft states efficiently with evaluateBatch`() {
        val draft1 = createStrongBlueDraft()
        val draft2 =
            DraftState(
                bluePicks = draft1.redPicks,
                redPicks = draft1.bluePicks,
            )
        val draftList = listOf(draft1, draft2)

        val batchResults = evaluator.evaluateBatch(draftList)

        assertEquals(2, batchResults.size)
        assertTrue(batchResults[0].blueWinRate in 0.0..1.0)
        assertTrue(batchResults[1].blueWinRate in 0.0..1.0)
        assertTrue(batchResults[0].blueWinRate > batchResults[1].blueWinRate)
    }

    @Test
    fun `should enrich DraftEvaluationResult with evalBar, timeCurve, and compositionRadar`() {
        val draft = createStrongBlueDraft()
        val result = evaluator.evaluate(draft)

        val evalBar = result.evalBar
        assertNotNull(evalBar)
        assertEquals(result.evalScore, evalBar.score, 0.0001)

        val timeCurve = result.timeCurve
        assertNotNull(timeCurve)
        assertEquals(7, timeCurve.points.size)

        val compRadar = result.compositionRadar
        assertNotNull(compRadar)
        assertTrue(compRadar.blueRadar.laning in 0.0..10.0)
    }
}

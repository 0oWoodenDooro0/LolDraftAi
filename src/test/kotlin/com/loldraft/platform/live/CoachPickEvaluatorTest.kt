package com.loldraft.platform.live

import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.models.AnalyticalDraftEvaluator
import com.loldraft.models.CompositionFlawDetector
import com.loldraft.models.DraftFeatureExtractor
import com.loldraft.models.DraftIntentPredictor
import com.loldraft.models.DraftRecommender
import com.loldraft.platform.live.models.CoachGrade
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoachPickEvaluatorTest {
    private val tagRegistry = ChampionTagRegistry.createDefault()
    private val evaluator = AnalyticalDraftEvaluator(featureExtractor = DraftFeatureExtractor(tagRegistry))
    private val flawDetector = CompositionFlawDetector(tagRegistry)
    private val recommender = DraftRecommender(evaluator = evaluator, tagRegistry = tagRegistry)
    private val intentPredictor = DraftIntentPredictor(tagRegistry = tagRegistry)
    private val coachEvaluator =
        CoachPickEvaluator(
            draftEvaluator = evaluator,
            draftRecommender = recommender,
            flawDetector = flawDetector,
            intentPredictor = intentPredictor,
        )

    @Test
    fun testEvaluateOptimalPick() {
        // Base state at Turn 7 (Blue pick 1)
        val turnsBefore =
            listOf(
                DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista"),
                DraftTurn(2, Side.RED, ActionType.BAN, "Rumble"),
                DraftTurn(3, Side.BLUE, ActionType.BAN, "Lucian"),
                DraftTurn(4, Side.RED, ActionType.BAN, "Ashe"),
                DraftTurn(5, Side.BLUE, ActionType.BAN, "Varus"),
                DraftTurn(6, Side.RED, ActionType.BAN, "Caitlyn"),
            )
        val stateBefore = DraftState.fromTurns(turnsBefore)

        // Get AI recommendation for Blue
        val recs = recommender.recommendBestPicks(stateBefore, Side.BLUE, limit = 5)
        assertTrue(recs.isNotEmpty(), "AI should provide recommendations")
        val topPick = recs.first()

        // Coach picks the top recommended champion
        val turn7 = DraftTurn(7, Side.BLUE, ActionType.PICK, topPick.championId, role = topPick.recommendedRole)
        val stateAfter = stateBefore.applyTurn(turn7)

        val feedback = coachEvaluator.evaluate(turn7, stateBefore, stateAfter)

        assertEquals(7, feedback.turnNumber)
        assertEquals(Side.BLUE, feedback.side)
        assertEquals(ActionType.PICK, feedback.actionType)
        assertEquals(topPick.championId, feedback.lockedChampionId)
        assertEquals(1, feedback.aiRank)
        assertEquals(CoachGrade.OPTIMAL_S, feedback.grade)
        assertTrue(feedback.critique.isNotBlank())
        assertTrue(feedback.alternativePicks.isNotEmpty())
    }

    @Test
    fun testEvaluateStrongAlternativePick() {
        val turnsBefore =
            listOf(
                DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista"),
                DraftTurn(2, Side.RED, ActionType.BAN, "Rumble"),
                DraftTurn(3, Side.BLUE, ActionType.BAN, "Lucian"),
                DraftTurn(4, Side.RED, ActionType.BAN, "Ashe"),
                DraftTurn(5, Side.BLUE, ActionType.BAN, "Varus"),
                DraftTurn(6, Side.RED, ActionType.BAN, "Caitlyn"),
            )
        val stateBefore = DraftState.fromTurns(turnsBefore)
        val recs = recommender.recommendBestPicks(stateBefore, Side.BLUE, limit = 5)
        assertTrue(recs.size >= 2)

        val secondPick = recs[1]
        val turn7 = DraftTurn(7, Side.BLUE, ActionType.PICK, secondPick.championId, role = secondPick.recommendedRole)
        val stateAfter = stateBefore.applyTurn(turn7)

        val feedback = coachEvaluator.evaluate(turn7, stateBefore, stateAfter)
        assertEquals(2, feedback.aiRank)
        assertTrue(feedback.grade == CoachGrade.OPTIMAL_S || feedback.grade == CoachGrade.STRONG_A)
        assertTrue(
            feedback.critique.contains("Strong") ||
                feedback.critique.contains("Optimal") ||
                feedback.critique.contains(secondPick.championId),
        )
    }

    @Test
    fun testEvaluateQuestionablePick() {
        val turnsBefore =
            listOf(
                DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista"),
                DraftTurn(2, Side.RED, ActionType.BAN, "Rumble"),
                DraftTurn(3, Side.BLUE, ActionType.BAN, "Lucian"),
                DraftTurn(4, Side.RED, ActionType.BAN, "Ashe"),
                DraftTurn(5, Side.BLUE, ActionType.BAN, "Varus"),
                DraftTurn(6, Side.RED, ActionType.BAN, "Caitlyn"),
            )
        val stateBefore = DraftState.fromTurns(turnsBefore)

        // Coach picks an unrecommended, off-meta pick for Blue turn 7 (e.g. Teemo)
        val turn7 = DraftTurn(7, Side.BLUE, ActionType.PICK, "Teemo", role = Role.TOP)
        val stateAfter = stateBefore.applyTurn(turn7)

        val feedback = coachEvaluator.evaluate(turn7, stateBefore, stateAfter)
        assertEquals("Teemo", feedback.lockedChampionId)
        assertTrue(
            feedback.grade == CoachGrade.QUESTIONABLE_C ||
                feedback.grade == CoachGrade.BLUNDER_D ||
                feedback.grade == CoachGrade.ACCEPTABLE_B,
        )
        assertTrue(feedback.alternativePicks.none { it.championId.equals("Teemo", ignoreCase = true) })
        assertTrue(feedback.critique.isNotBlank())
    }

    @Test
    fun testEvaluateBlunderPickWithStructuralFlaw() {
        // Construct a state where Blue already has extreme squishiness: Jayce, Nidalee, LeBlanc, Ezreal
        val turnsBefore =
            listOf(
                DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista"),
                DraftTurn(2, Side.RED, ActionType.BAN, "Rumble"),
                DraftTurn(3, Side.BLUE, ActionType.BAN, "Lucian"),
                DraftTurn(4, Side.RED, ActionType.BAN, "Ashe"),
                DraftTurn(5, Side.BLUE, ActionType.BAN, "Varus"),
                DraftTurn(6, Side.RED, ActionType.BAN, "Caitlyn"),
                DraftTurn(7, Side.BLUE, ActionType.PICK, "Jayce", Role.TOP),
                DraftTurn(8, Side.RED, ActionType.PICK, "Sion", Role.TOP),
                DraftTurn(9, Side.RED, ActionType.PICK, "Sejuani", Role.JUNGLE),
                DraftTurn(10, Side.BLUE, ActionType.PICK, "Nidalee", Role.JUNGLE),
                DraftTurn(11, Side.BLUE, ActionType.PICK, "LeBlanc", Role.MID),
                DraftTurn(12, Side.RED, ActionType.PICK, "Orianna", Role.MID),
                DraftTurn(13, Side.RED, ActionType.BAN, "Braum"),
                DraftTurn(14, Side.BLUE, ActionType.BAN, "Kai'Sa"),
                DraftTurn(15, Side.RED, ActionType.BAN, "Nautilus"),
                DraftTurn(16, Side.BLUE, ActionType.BAN, "Xayah"),
                DraftTurn(17, Side.RED, ActionType.PICK, "Jinx", Role.BOT),
                DraftTurn(18, Side.BLUE, ActionType.PICK, "Ezreal", Role.BOT),
            )
        val stateBefore = DraftState.fromTurns(turnsBefore)

        // Turn 19: Blue needs support. Coach locks "Yuumi" (zero frontline, zero hard CC), leaving team 100% squishy against Sion + Sejuani
        val turn19 = DraftTurn(19, Side.BLUE, ActionType.PICK, "Yuumi", Role.SUPPORT)
        val stateAfter = stateBefore.applyTurn(turn19)

        val feedback = coachEvaluator.evaluate(turn19, stateBefore, stateAfter)
        assertTrue(feedback.flawsIntroduced.isNotEmpty(), "Should detect introduced composition flaw (e.g. no frontline / lack of hard CC)")
        assertTrue(feedback.grade == CoachGrade.BLUNDER_D || feedback.grade == CoachGrade.QUESTIONABLE_C)
        assertTrue(
            feedback.critique.contains("flaw", ignoreCase = true) ||
                feedback.critique.contains("frontline", ignoreCase = true) ||
                feedback.critique.contains("vulnerability", ignoreCase = true),
        )
    }

    @Test
    fun testEvaluateFlawResolvingPick() {
        val turnsBefore =
            listOf(
                DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista"),
                DraftTurn(2, Side.RED, ActionType.BAN, "Rumble"),
                DraftTurn(3, Side.BLUE, ActionType.BAN, "Lucian"),
                DraftTurn(4, Side.RED, ActionType.BAN, "Ashe"),
                DraftTurn(5, Side.BLUE, ActionType.BAN, "Varus"),
                DraftTurn(6, Side.RED, ActionType.BAN, "Caitlyn"),
                DraftTurn(7, Side.BLUE, ActionType.PICK, "Jayce", Role.TOP),
                DraftTurn(8, Side.RED, ActionType.PICK, "Sion", Role.TOP),
                DraftTurn(9, Side.RED, ActionType.PICK, "Sejuani", Role.JUNGLE),
                DraftTurn(10, Side.BLUE, ActionType.PICK, "Nidalee", Role.JUNGLE),
                DraftTurn(11, Side.BLUE, ActionType.PICK, "LeBlanc", Role.MID),
                DraftTurn(12, Side.RED, ActionType.PICK, "Orianna", Role.MID),
                DraftTurn(13, Side.RED, ActionType.BAN, "Braum"),
                DraftTurn(14, Side.BLUE, ActionType.BAN, "Kai'Sa"),
                DraftTurn(15, Side.RED, ActionType.BAN, "Leona"),
                DraftTurn(16, Side.BLUE, ActionType.BAN, "Xayah"),
                DraftTurn(17, Side.RED, ActionType.PICK, "Jinx", Role.BOT),
                DraftTurn(18, Side.BLUE, ActionType.PICK, "Ezreal", Role.BOT),
            )
        val stateBefore = DraftState.fromTurns(turnsBefore)

        // Turn 19: Blue picks Nautilus (heavy frontline engage and CC to resolve frontline deficiency)
        val turn19 = DraftTurn(19, Side.BLUE, ActionType.PICK, "Nautilus", Role.SUPPORT)
        val stateAfter = stateBefore.applyTurn(turn19)

        val feedback = coachEvaluator.evaluate(turn19, stateBefore, stateAfter)
        assertNotNull(feedback)
        assertTrue(
            feedback.grade == CoachGrade.OPTIMAL_S || feedback.grade == CoachGrade.STRONG_A || feedback.grade == CoachGrade.ACCEPTABLE_B,
        )
        assertTrue(feedback.critique.isNotBlank())
    }

    @Test
    fun testEvaluateBanTurn() {
        val turnsBefore = emptyList<DraftTurn>()
        val stateBefore = DraftState.empty()

        // Turn 1: Blue bans Kalista
        val turn1 = DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista")
        val stateAfter = stateBefore.applyTurn(turn1)

        val feedback = coachEvaluator.evaluate(turn1, stateBefore, stateAfter)
        assertEquals(1, feedback.turnNumber)
        assertEquals(Side.BLUE, feedback.side)
        assertEquals(ActionType.BAN, feedback.actionType)
        assertEquals("Kalista", feedback.lockedChampionId)
        assertTrue(feedback.critique.isNotBlank())
    }
}

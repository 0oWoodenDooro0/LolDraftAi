package com.loldraft.models

import com.loldraft.data.meta.PatchMetaAnalyzer
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DraftIntentPredictorDuoLinkageTest {

    private val predictor = DraftIntentPredictor()
    private val patchMeta = PatchMetaAnalyzer().analyzeGames(emptyList(), "14.10")

    @Test
    fun `predictNextAction should prioritize partner Support when ADC is picked`() {
        // Blue locks Lucian at Turn 7
        val turns = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Lucian", role = Role.BOT),
            DraftTurn(8, Side.RED, ActionType.PICK, "Sejuani", role = Role.JUNGLE),
            DraftTurn(9, Side.RED, ActionType.PICK, "Aatrox", role = Role.TOP),
        )
        // Turn 10 is Blue pick
        val state = DraftState.fromTurns(turns)

        val prediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            topN = 5,
        )

        val topPred = prediction.predictions.firstOrNull()
        assertNotNull(topPred, "Must produce predictions")
        assertEquals("Nami", topPred.championId, "Nami should be top prediction when Lucian is locked")
        assertTrue(topPred.rationale.contains("Bot Duo Synergy with Lucian"), "Rationale must indicate duo synergy: ${topPred.rationale}")
        assertTrue(topPred.intentScore > 0.6, "Intent score must reflect strong duo synergy: ${topPred.intentScore}")
    }

    @Test
    fun `predictNextAction should prioritize Rakan when Xayah is locked`() {
        val turns = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Xayah", role = Role.BOT),
            DraftTurn(8, Side.RED, ActionType.PICK, "Maokai", role = Role.JUNGLE),
            DraftTurn(9, Side.RED, ActionType.PICK, "Rumble", role = Role.TOP),
        )
        val state = DraftState.fromTurns(turns)

        val prediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            topN = 5,
        )

        val topPred = prediction.predictions.firstOrNull()
        assertNotNull(topPred)
        assertEquals("Rakan", topPred.championId, "Rakan should be top prediction for Xayah")
        assertTrue(topPred.rationale.contains("Bot Duo Synergy with Xayah"), "Rationale must indicate duo synergy with Xayah")
    }

    @Test
    fun `predictNextAction should predict Duo Denial ban when enemy locks ADC`() {
        // Red locked Lucian at Turn 8
        val turns = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Ashe", role = Role.BOT),
            DraftTurn(8, Side.RED, ActionType.PICK, "Lucian", role = Role.BOT),
            DraftTurn(9, Side.RED, ActionType.PICK, "Maokai", role = Role.JUNGLE),
            DraftTurn(10, Side.BLUE, ActionType.PICK, "Braum", role = Role.SUPPORT),
            DraftTurn(11, Side.BLUE, ActionType.PICK, "Azir", role = Role.MID),
            DraftTurn(12, Side.RED, ActionType.PICK, "Ahri", role = Role.MID),
            DraftTurn(13, Side.RED, ActionType.BAN, "K'Sante"),
            // Turn 14 is Blue BAN (Phase 2 Ban)
        )
        val state = DraftState.fromTurns(turns)

        val banPrediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            topN = 5,
        )

        val namiBanCandidate = banPrediction.predictions.find { it.championId.equals("Nami", ignoreCase = true) }
        assertNotNull(namiBanCandidate, "Nami should be recommended for Duo Denial ban against enemy Lucian")
        assertTrue(namiBanCandidate.rationale.contains("Duo Denial"), "Rationale should explain duo denial: ${namiBanCandidate.rationale}")
    }

    @Test
    fun `predictNextAction should NOT recommend Duo Denial ban when enemy has locked BOTH Bot and Support`() {
        // Red locked BOTH Lucian (Bot) and Braum (Support)
        val turns = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Ashe", role = Role.BOT),
            DraftTurn(8, Side.RED, ActionType.PICK, "Lucian", role = Role.BOT),
            DraftTurn(9, Side.RED, ActionType.PICK, "Braum", role = Role.SUPPORT),
            DraftTurn(10, Side.BLUE, ActionType.PICK, "Leona", role = Role.SUPPORT),
            DraftTurn(11, Side.BLUE, ActionType.PICK, "Azir", role = Role.MID),
            DraftTurn(12, Side.RED, ActionType.PICK, "Ahri", role = Role.MID),
            DraftTurn(13, Side.RED, ActionType.BAN, "K'Sante"),
            // Turn 14 is Blue BAN (Phase 2 Ban)
        )
        val state = DraftState.fromTurns(turns)

        val banPrediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            topN = 10,
        )

        // Since Red's bot lane is already complete, NO ban should be justified with Duo Denial!
        val duoDenialBan = banPrediction.predictions.find { it.rationale.contains("Duo Denial") }
        assertTrue(
            duoDenialBan == null,
            "No ban candidate should have Duo Denial rationale when enemy duo is complete, but found: ${duoDenialBan?.championId} (${duoDenialBan?.rationale})"
        )
    }

    @Test
    fun `predictNextAction should NOT grant Bot Duo Synergy when ally already has BOTH Bot and Support`() {
        // Blue locked BOTH Lucian (Bot) and Nami (Support)
        val turns = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Lucian", role = Role.BOT),
            DraftTurn(8, Side.RED, ActionType.PICK, "Kai'Sa", role = Role.BOT),
            DraftTurn(9, Side.RED, ActionType.PICK, "Nautilus", role = Role.SUPPORT),
            DraftTurn(10, Side.BLUE, ActionType.PICK, "Nami", role = Role.SUPPORT),
            // Turn 11 is Blue PICK (Vacant: TOP, JUNGLE, MID)
        )
        val state = DraftState.fromTurns(turns)

        val prediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            topN = 10,
        )

        // Neither Top, Jungle, nor Mid picks should claim "Bot Duo Synergy"
        for (candidate in prediction.predictions) {
            assertFalse(
                candidate.rationale.contains("Bot Duo Synergy"),
                "Candidate ${candidate.championId} should not have Bot Duo Synergy when ally bot duo is complete: ${candidate.rationale}"
            )
        }
    }

    @Test
    fun `predictNextAction should evaluate solo lane Blind Pick when enemy opponent is not revealed`() {
        // Turn 7: Blue Pick 1 (Empty draft, only bans done)
        val turns = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            // Turn 7 is Blue pick
        )
        val state = DraftState.fromTurns(turns)

        val prediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            topN = 10,
        )

        // Solo lane candidates (e.g. TOP / MID) should evaluate Blind Pick
        val soloLaneCandidates = prediction.predictions.filter { it.predictedRole == Role.TOP || it.predictedRole == Role.MID }
        assertTrue(soloLaneCandidates.isNotEmpty(), "Must predict solo lane candidates")
        val hasBlindPickRationale = soloLaneCandidates.any {
            it.rationale.contains("Blind Pick") || it.rationale.contains("Flex Pick")
        }
        assertTrue(hasBlindPickRationale, "Solo lane candidates on first pick should have Blind Pick or Flex rationale: ${soloLaneCandidates.map { it.rationale }}")
    }

    @Test
    fun `predictNextAction should evaluate Counter Pick when enemy solo lane opponent is locked`() {
        // Blue locked Aatrox (TOP) at Turn 7
        val turns = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Aatrox", role = Role.TOP),
            // Turn 8 is Red pick
        )
        val state = DraftState.fromTurns(turns)

        val prediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            topN = 10,
        )

        // Red can pick TOP at Turn 8. Any TOP prediction should evaluate Counter Pick vs Aatrox!
        val topLaneCandidate = prediction.predictions.find { it.predictedRole == Role.TOP }
        if (topLaneCandidate != null) {
            assertTrue(
                topLaneCandidate.rationale.contains("Counter Pick") || topLaneCandidate.rationale.contains("vs Aatrox"),
                "TOP pick candidate against Aatrox should contain Counter Pick rationale: ${topLaneCandidate.rationale}"
            )
        }
    }

    @Test
    fun `predictNextAction should return 5 predictions by default and avoid more than 2 options per role`() {
        // Turn 7: Blue Pick 1 (All 5 roles vacant)
        val turns = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
        )
        val state = DraftState.fromTurns(turns)

        val prediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            // default topN should be 5
        )

        assertEquals(5, prediction.predictions.size, "Default predictions count should be 5")

        // Role counts must not exceed 2 for any single role
        val roleCounts = prediction.predictions.groupingBy { it.predictedRole }.eachCount()
        for ((role, count) in roleCounts) {
            assertTrue(
                count <= 2,
                "Role $role should not have more than 2 options in recommendations, but had $count: ${prediction.predictions.map { "${it.championId}(${it.predictedRole})" }}"
            )
        }
    }

    @Test
    fun `predictNextAction should recommend 2v2 Bot Duo Counter when enemy duo is locked`() {
        // Enemy Red locked Kai'Sa + Nautilus
        // Blue locked Caitlyn at Turn 7
        val turns = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Caitlyn", role = Role.BOT),
            DraftTurn(8, Side.RED, ActionType.PICK, "Kai'Sa", role = Role.BOT),
            DraftTurn(9, Side.RED, ActionType.PICK, "Nautilus", role = Role.SUPPORT),
            // Turn 10 is Blue pick
        )
        val state = DraftState.fromTurns(turns)

        val prediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            topN = 5,
        )

        val luxCandidate = prediction.predictions.find { it.championId.equals("Lux", ignoreCase = true) }
        assertNotNull(luxCandidate, "Lux should be top candidate for Caitlyn vs Kai'Sa+Nautilus")
        assertTrue(luxCandidate.rationale.contains("2v2 Bot Duo Counter") || luxCandidate.rationale.contains("Bot Duo Synergy with Caitlyn"),
            "Rationale must reflect duo counter or synergy: ${luxCandidate.rationale}")
    }

    @Test
    fun `predictNextAction should not exceed 3 ban predictions for any single role during ban phase`() {
        val turns = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
        )
        val state = DraftState.fromTurns(turns)
        val prediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            topN = 10,
        )

        val roleCounts = prediction.predictions
            .mapNotNull { it.predictedRole ?: predictor.tagRegistry.getProfile(it.championId)?.primaryRole }
            .groupingBy { it }
            .eachCount()

        for ((role, count) in roleCounts) {
            assertTrue(
                count <= 3,
                "Role $role should not exceed 3 ban predictions, but found $count: ${prediction.predictions.map { it.championId }}"
            )
        }
    }

    @Test
    fun `predictNextAction in Turn 14 must never predict more than 3 bans for any single role`() {
        val turns = listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Ashe", role = Role.BOT),
            DraftTurn(8, Side.RED, ActionType.PICK, "Lucian", role = Role.BOT),
            DraftTurn(9, Side.RED, ActionType.PICK, "Maokai", role = Role.JUNGLE),
            DraftTurn(10, Side.BLUE, ActionType.PICK, "Braum", role = Role.SUPPORT),
            DraftTurn(11, Side.BLUE, ActionType.PICK, "Azir", role = Role.MID),
            DraftTurn(12, Side.RED, ActionType.PICK, "Ahri", role = Role.MID),
            DraftTurn(13, Side.RED, ActionType.BAN, "Sejuani"),
            // Turn 14 is Blue BAN
        )
        val state = DraftState.fromTurns(turns)
        val pred = predictor.predictNextAction(state, patchMeta, topN = 5)
        val roleCounts = pred.predictions
            .mapNotNull { it.predictedRole ?: predictor.tagRegistry.getProfile(it.championId)?.primaryRole }
            .groupingBy { it }
            .eachCount()

        for ((role, count) in roleCounts) {
            assertTrue(
                count <= 3,
                "Role $role should not exceed 3 ban predictions in Turn 14, but found $count: ${pred.predictions.map { it.championId }}"
            )
        }
        assertEquals(5, pred.predictions.size, "Should predict 5 candidates")
    }
}

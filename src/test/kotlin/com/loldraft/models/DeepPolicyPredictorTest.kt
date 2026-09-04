package com.loldraft.models

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeepPolicyPredictorTest {

    private val predictor = DeepPolicyPredictor()

    @Test
    fun `predictNextAction returns top legal candidates for turn 1 ban`() {
        val draftState = DraftState()
        val result = predictor.predictNextAction(
            draftState = draftState,
            league = "LCK",
            targetTeamName = "T1",
            topN = 5,
        )

        assertNotNull(result)
        assertEquals(Side.BLUE, result.actingSide)
        assertEquals(ActionType.BAN, result.actionType)
        assertEquals(5, result.predictions.size)

        // All probabilities should be strictly positive and sum to <= 1.0
        val sumProb = result.predictions.sumOf { it.probability }
        assertTrue(sumProb in 0.01..1.0)

        // Rationale should mention DL Policy
        assertTrue(result.predictions.first().rationale.contains("[Empirical Policy]"))
    }

    @Test
    fun `Action Masking strictly excludes current bans and picks`() {
        // Ban Ashe and Kalista in turns 1 and 2
        val turns = listOf(
            DraftTurn(turnNumber = 1, side = Side.BLUE, actionType = ActionType.BAN, championId = "ashe"),
            DraftTurn(turnNumber = 2, side = Side.RED, actionType = ActionType.BAN, championId = "kalista"),
        )
        val draftState = DraftState.fromTurns(turns)

        val result = predictor.predictNextAction(
            draftState = draftState,
            topN = 10,
        )

        val candidateIds = result.predictions.map { it.championId }
        assertFalse(candidateIds.contains("ashe"), "Ashe must be masked out because she is already banned")
        assertFalse(candidateIds.contains("kalista"), "Kalista must be masked out because she is already banned")
    }

    @Test
    fun `Action Masking strictly excludes standard Fearless Draft champions across games`() {
        // Suppose Ashe and Varus were played in games 1 and 2 of the series
        val fearlessSpent = setOf("ashe", "varus", "corki")
        val draftState = DraftState().withFearlessSpent(fearlessSpent)

        val result = predictor.predictNextAction(
            draftState = draftState,
            league = "LCK",
            topN = 10,
        )

        val candidateIds = result.predictions.map { it.championId }
        assertFalse(candidateIds.contains("ashe"), "Ashe must be excluded under Fearless Draft")
        assertFalse(candidateIds.contains("varus"), "Varus must be excluded under Fearless Draft")
        assertFalse(candidateIds.contains("corki"), "Corki must be excluded under Fearless Draft")

        // Candidates must be valid alternative champions
        assertTrue(result.predictions.isNotEmpty())
    }

    @Test
    fun `Team Conditioned Embedding biases predictions for team identity`() {
        val draftState = DraftState()

        // Predict for T1 in LCK
        val t1Result = predictor.predictNextAction(
            draftState = draftState,
            league = "LCK",
            targetTeamName = "T1",
            topN = 5,
        )

        val t1TopChamps = t1Result.predictions.map { it.championId }
        // T1 signatures should be highly ranked (Azir, Orianna, Varus, Ashe, etc.)
        assertTrue(
            t1TopChamps.any { it in setOf("azir", "orianna", "varus", "ashe") },
            "T1 predictions should reflect team conditioned style, got: $t1TopChamps",
        )
    }

    @Test
    fun `Role vacancy constraint masks out champions when only specific role remains`() {
        // Build full 18 turns leading up to turn 19 (Blue Pick 5)
        // Blue already locked: JUG, MID, BOT, SUP -> only TOP is vacant for Blue!
        val turns = listOf(
            // Phase 1 Bans 1..6
            DraftTurn(1, Side.BLUE, ActionType.BAN, "b1"),
            DraftTurn(2, Side.RED, ActionType.BAN, "b2"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "b3"),
            DraftTurn(4, Side.RED, ActionType.BAN, "b4"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "b5"),
            DraftTurn(6, Side.RED, ActionType.BAN, "b6"),
            // Phase 1 Picks 7..12
            DraftTurn(7, Side.BLUE, ActionType.PICK, "sejuani", role = Role.JUNGLE),
            DraftTurn(8, Side.RED, ActionType.PICK, "maokai", role = Role.JUNGLE),
            DraftTurn(9, Side.RED, ActionType.PICK, "corki", role = Role.MID),
            DraftTurn(10, Side.BLUE, ActionType.PICK, "azir", role = Role.MID),
            DraftTurn(11, Side.BLUE, ActionType.PICK, "ashe", role = Role.BOT),
            DraftTurn(12, Side.RED, ActionType.PICK, "kaisa", role = Role.BOT),
            // Phase 2 Bans 13..16
            DraftTurn(13, Side.RED, ActionType.BAN, "b7"),
            DraftTurn(14, Side.BLUE, ActionType.BAN, "b8"),
            DraftTurn(15, Side.RED, ActionType.BAN, "b9"),
            DraftTurn(16, Side.BLUE, ActionType.BAN, "b10"),
            // Phase 2 Picks 17..18
            DraftTurn(17, Side.RED, ActionType.PICK, "leona", role = Role.SUPPORT),
            DraftTurn(18, Side.BLUE, ActionType.PICK, "braum", role = Role.SUPPORT),
            // Now Turn 19: Blue Pick! Remaining vacant role for Blue is TOP only.
        )
        val draftState = DraftState.fromTurns(turns)
        assertEquals(19, draftState.currentTurnNumber)

        val result = predictor.predictNextAction(
            draftState = draftState,
            firstPickSide = Side.BLUE,
            topN = 5,
        )

        assertEquals(Side.BLUE, result.actingSide)
        assertEquals(ActionType.PICK, result.actionType)

        // All predicted champions for this pick must be viable for TOP
        for (candidate in result.predictions) {
            assertEquals(Role.TOP, candidate.predictedRole, "Candidate ${candidate.championId} must be assigned to vacant TOP role")
        }
    }
}

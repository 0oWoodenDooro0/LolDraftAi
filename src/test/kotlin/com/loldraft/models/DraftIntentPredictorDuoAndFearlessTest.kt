package com.loldraft.models

import com.loldraft.data.meta.PatchMetaAnalyzer
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftIntentPredictorDuoAndFearlessTest {

    private val predictor = DraftIntentPredictor()
    private val patchMeta = PatchMetaAnalyzer().analyzeGames(emptyList(), "14.10")

    @Test
    fun `Fearless mode should strictly exclude previous games spent champions from predictions`() {
        val spentChampions = setOf("Orianna", "Azir", "Aatrox", "Varus", "Nautilus")
        val state = DraftState.empty().withFearlessSpent(spentChampions)

        // Predict Turn 1 (Blue ban)
        val banPrediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            topN = 5,
        )

        for (pred in banPrediction.predictions) {
            assertFalse(
                spentChampions.contains(pred.championId),
                "Spent champion ${pred.championId} must not be predicted in fearless mode"
            )
        }

        // Fast-forward to Turn 7 (Blue pick)
        val turn1to6 = (1..6).map { DraftTurn(it, if (it % 2 == 1) Side.BLUE else Side.RED, com.loldraft.data.models.ActionType.BAN, "Champ$it") }
        val pickState = state.copy(turns = turn1to6)

        val pickPrediction = predictor.predictNextAction(
            draftState = pickState,
            patchMeta = patchMeta,
            topN = 5,
        )

        for (pred in pickPrediction.predictions) {
            assertFalse(
                spentChampions.contains(pred.championId),
                "Spent champion ${pred.championId} must not be predicted in fearless pick"
            )
        }
    }

    @Test
    fun `DraftIntentPredictor should spike Support prediction for Nami when Lucian is picked`() {
        // Assume Blue already locked Lucian as BOT in Turn 7
        // Now at Turn 9 or Turn 10, when predicting a pick where SUPPORT is vacant
        val turns = listOf(
            DraftTurn(1, Side.BLUE, com.loldraft.data.models.ActionType.BAN, "Ban1"),
            DraftTurn(2, Side.RED, com.loldraft.data.models.ActionType.BAN, "Ban2"),
            DraftTurn(3, Side.BLUE, com.loldraft.data.models.ActionType.BAN, "Ban3"),
            DraftTurn(4, Side.RED, com.loldraft.data.models.ActionType.BAN, "Ban4"),
            DraftTurn(5, Side.BLUE, com.loldraft.data.models.ActionType.BAN, "Ban5"),
            DraftTurn(6, Side.RED, com.loldraft.data.models.ActionType.BAN, "Ban6"),
            DraftTurn(7, Side.BLUE, com.loldraft.data.models.ActionType.PICK, "Lucian", role = Role.BOT),
            DraftTurn(8, Side.RED, com.loldraft.data.models.ActionType.PICK, "Sejuani", role = Role.JUNGLE),
            DraftTurn(9, Side.RED, com.loldraft.data.models.ActionType.PICK, "Aatrox", role = Role.TOP),
        )
        // Turn 10 is Blue pick
        val state = DraftState.fromTurns(turns)

        val prediction = predictor.predictNextAction(
            draftState = state,
            patchMeta = patchMeta,
            topN = 5,
        )

        val namiCandidate = prediction.predictions.find { it.championId.equals("Nami", ignoreCase = true) }
        assertTrue(namiCandidate != null, "Nami should be in top predictions when Lucian is locked")
        assertTrue(namiCandidate.rationale.contains("Bot Duo Synergy with Lucian"), "Rationale must reflect Duo Synergy")
    }
}

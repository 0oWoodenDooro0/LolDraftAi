package com.loldraft.models

import com.loldraft.data.meta.SeriesDraftContext
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftStateFearlessTest {

    @Test
    fun `DraftState should incorporate fearless spent champions into allSelectedChampions`() {
        val game1Picked = setOf("Aatrox", "Sejuani", "Orianna", "Varus", "Nautilus", "Renekton", "Vi", "Azir", "Kai'Sa", "Rell")
        
        val context = SeriesDraftContext(
            matchId = "match_bo3_01",
            currentGameNumber = 2,
            spentChampions = game1Picked,
        )

        val game2InitialState = DraftState(seriesContext = context)

        assertEquals(10, game2InitialState.fearlessSpentChampions.size)
        assertTrue(game2InitialState.allBannedChampions.isEmpty())
        assertTrue(game2InitialState.allPickedChampions.isEmpty())
        
        // In Global Fearless, spent champions are immediately unavailable
        assertEquals(10, game2InitialState.allSelectedChampions.size)
        assertTrue(game2InitialState.allSelectedChampions.contains("Orianna"))
        assertTrue(game2InitialState.allSelectedChampions.contains("Azir"))
        assertTrue(game2InitialState.allSelectedChampions.contains("Nautilus"))

        // When a new ban is made in Game 2
        val turn1 = DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista")
        val state1 = game2InitialState.applyTurn(turn1)

        assertEquals(11, state1.allSelectedChampions.size)
        assertTrue(state1.allSelectedChampions.contains("Kalista"))
        assertTrue(state1.allSelectedChampions.contains("Orianna"))
    }

    @Test
    fun `withFearlessSpent helper should properly update draft state`() {
        val baseState = DraftState.empty()
        assertFalse(baseState.allSelectedChampions.contains("Lucian"))

        val updated = baseState.withFearlessSpent(setOf("Lucian", "Nami"))
        assertTrue(updated.fearlessSpentChampions.contains("Lucian"))
        assertTrue(updated.fearlessSpentChampions.contains("Nami"))
        assertTrue(updated.allSelectedChampions.contains("Lucian"))
        assertTrue(updated.allSelectedChampions.contains("Nami"))
    }
}

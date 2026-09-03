package com.loldraft.data.validation

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftValidatorTest {
    private val validator = DraftValidator()

    // Canonical sample 20 champions
    private val canonicalChampions =
        listOf(
            // Turns 1..6: Bans
            "Aatrox",
            "Ahri",
            "Akali",
            "Ashe",
            "Azir",
            "Braum",
            // Turns 7..12: Picks (B1, R1, R2, B2, B3, R3)
            "Caitlyn",
            "Corki",
            "Darius",
            "Ezreal",
            "Fiora",
            "Galio",
            // Turns 13..16: Bans (R4, B4, R5, B5)
            "Gnar",
            "Jax",
            "Jinx",
            "Kaisa",
            // Turns 17..20: Picks (R4, B4, B5, R5)
            "LeeSin",
            "Leona",
            "Lulu",
            "Nautilus",
        )

    private fun buildCanonicalTurns(): List<DraftTurn> {
        val turns = mutableListOf<DraftTurn>()
        for (turnNum in 1..20) {
            val champ = canonicalChampions[turnNum - 1]
            val (side, action) =
                when (turnNum) {
                    1 -> Side.BLUE to ActionType.BAN
                    2 -> Side.RED to ActionType.BAN
                    3 -> Side.BLUE to ActionType.BAN
                    4 -> Side.RED to ActionType.BAN
                    5 -> Side.BLUE to ActionType.BAN
                    6 -> Side.RED to ActionType.BAN
                    7 -> Side.BLUE to ActionType.PICK
                    8 -> Side.RED to ActionType.PICK
                    9 -> Side.RED to ActionType.PICK
                    10 -> Side.BLUE to ActionType.PICK
                    11 -> Side.BLUE to ActionType.PICK
                    12 -> Side.RED to ActionType.PICK
                    13 -> Side.RED to ActionType.BAN
                    14 -> Side.BLUE to ActionType.BAN
                    15 -> Side.RED to ActionType.BAN
                    16 -> Side.BLUE to ActionType.BAN
                    17 -> Side.RED to ActionType.PICK
                    18 -> Side.BLUE to ActionType.PICK
                    19 -> Side.BLUE to ActionType.PICK
                    20 -> Side.RED to ActionType.PICK
                    else -> error("Invalid turn")
                }
            turns.add(DraftTurn(turnNumber = turnNum, side = side, actionType = action, championId = champ))
        }
        return turns
    }

    @Test
    fun `should validate successfully on a complete canonical 20-turn draft sequence`() {
        val turns = buildCanonicalTurns()
        val result = validator.validateDraftSequence(turns)
        assertTrue(result.isValid, "Expected valid sequence but got errors: ${result.errors}")
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `should fail when turn numbers are not strictly consecutive`() {
        val turn1 = DraftTurn(1, Side.BLUE, ActionType.BAN, "Aatrox")
        val turn3 = DraftTurn(3, Side.BLUE, ActionType.BAN, "Akali") // Skipped turn 2!

        val result = validator.validateTurn(turn3, listOf(turn1))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Expected turn 2 but received turn 3") })
    }

    @Test
    fun `should fail when side does not match expected turn order`() {
        // Turn 1 must be BLUE, but RED is provided
        val turn1 = DraftTurn(1, Side.RED, ActionType.BAN, "Aatrox")
        val result = validator.validateTurn(turn1, emptyList())
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Invalid side for turn 1: expected BLUE, got RED") })
    }

    @Test
    fun `should fail when actionType does not match expected phase`() {
        // Turn 1 must be BAN, but PICK is provided
        val turn1 = DraftTurn(1, Side.BLUE, ActionType.PICK, "Aatrox")
        val result = validator.validateTurn(turn1, emptyList())
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Invalid action type for turn 1: expected BAN, got PICK") })
    }

    @Test
    fun `should fail when a champion is banned twice`() {
        val turn1 = DraftTurn(1, Side.BLUE, ActionType.BAN, "Aatrox")
        val turn2 = DraftTurn(2, Side.RED, ActionType.BAN, "Aatrox") // Duplicate!

        val result = validator.validateTurn(turn2, listOf(turn1))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Champion 'Aatrox' has already been selected") })
    }

    @Test
    fun `should fail when a picked champion was already banned`() {
        val turn1 = DraftTurn(1, Side.BLUE, ActionType.BAN, "Caitlyn")
        val turn2 = DraftTurn(2, Side.RED, ActionType.BAN, "Ahri")
        val turn3 = DraftTurn(3, Side.BLUE, ActionType.BAN, "Akali")
        val turn4 = DraftTurn(4, Side.RED, ActionType.BAN, "Ashe")
        val turn5 = DraftTurn(5, Side.BLUE, ActionType.BAN, "Azir")
        val turn6 = DraftTurn(6, Side.RED, ActionType.BAN, "Braum")
        val turn7 = DraftTurn(7, Side.BLUE, ActionType.PICK, "Caitlyn") // Already banned in turn 1!

        val previous = listOf(turn1, turn2, turn3, turn4, turn5, turn6)
        val result = validator.validateTurn(turn7, previous)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Champion 'Caitlyn' has already been selected") })
    }

    @Test
    fun `should fail when championId is blank`() {
        val turn1 = DraftTurn(1, Side.BLUE, ActionType.BAN, "   ")
        val result = validator.validateTurn(turn1, emptyList())
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Champion ID cannot be blank") })
    }

    @Test
    fun `should fail when turnNumber is outside range 1 to 20`() {
        val invalidTurn = DraftTurn(0, Side.BLUE, ActionType.BAN, "Aatrox")
        val result = validator.validateTurn(invalidTurn, emptyList())
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Turn number 0 out of bounds (1..20)") })
    }

    @Test
    fun `should validate complete DraftState successfully`() {
        val turns = buildCanonicalTurns()
        val state = DraftState.fromTurns(turns)

        val result = validator.validateCompleteDraft(state)
        assertTrue(result.isValid, "Expected complete draft to be valid: ${result.errors}")
        assertEquals(5, state.blueBans.size)
        assertEquals(5, state.redBans.size)
        assertEquals(5, state.bluePicks.size)
        assertEquals(5, state.redPicks.size)
        assertTrue(state.isComplete)
    }

    @Test
    fun `should fail complete draft validation if incomplete`() {
        val turns = buildCanonicalTurns().take(19) // only 19 turns
        val state = DraftState.fromTurns(turns)

        val result = validator.validateCompleteDraft(state)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Draft is incomplete: expected 20 turns, but found 19") })
    }

    @Test
    fun `should fail complete draft validation if duplicate champions exist across teams`() {
        val state =
            DraftState(
                blueBans = listOf("Aatrox", "Ahri", "Akali", "Ashe", "Azir"),
                redBans = listOf("Braum", "Corki", "Darius", "Ezreal", "Fiora"),
                bluePicks =
                    listOf(
                        PickSelection("Galio", Role.MID),
                        PickSelection("Gnar", Role.TOP),
                        PickSelection("Jax", Role.JUNGLE),
                        PickSelection("Jinx", Role.BOT),
                        PickSelection("Kaisa", Role.SUPPORT),
                    ),
                redPicks =
                    listOf(
                        // DUPLICATE with blue pick!
                        PickSelection("Galio", Role.MID),
                        PickSelection("LeeSin", Role.JUNGLE),
                        PickSelection("Leona", Role.SUPPORT),
                        PickSelection("Lulu", Role.TOP),
                        PickSelection("Nautilus", Role.BOT),
                    ),
                turns = emptyList(),
            )

        val result = validator.validateCompleteDraft(state)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Duplicate champions found: [Galio]") })
    }
}

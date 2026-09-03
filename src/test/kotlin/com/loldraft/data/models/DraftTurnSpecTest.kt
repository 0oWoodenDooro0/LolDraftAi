package com.loldraft.data.models

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class DraftTurnSpecTest {
    @Test
    fun `should contain exactly 20 canonical turn specifications`() {
        assertEquals(20, DraftTurnSpec.SPECS.size)
        val turnNumbers = DraftTurnSpec.SPECS.map { it.turnNumber }
        assertEquals((1..20).toList(), turnNumbers)
    }

    @Test
    fun `should correctly define Phase 1 Bans (Turns 1 to 6)`() {
        val expected =
            listOf(
                Triple(1, Side.BLUE, ActionType.BAN),
                Triple(2, Side.RED, ActionType.BAN),
                Triple(3, Side.BLUE, ActionType.BAN),
                Triple(4, Side.RED, ActionType.BAN),
                Triple(5, Side.BLUE, ActionType.BAN),
                Triple(6, Side.RED, ActionType.BAN),
            )

        expected.forEach { (turn, side, action) ->
            val spec = DraftTurnSpec.forTurn(turn)
            assertEquals(turn, spec.turnNumber)
            assertEquals(DraftPhase.BAN_PHASE_1, spec.phase)
            assertEquals(side, spec.side)
            assertEquals(action, spec.actionType)
        }
    }

    @Test
    fun `should correctly define Phase 1 Picks (Turns 7 to 12)`() {
        val expected =
            listOf(
                Triple(7, Side.BLUE, ActionType.PICK),
                Triple(8, Side.RED, ActionType.PICK),
                Triple(9, Side.RED, ActionType.PICK),
                Triple(10, Side.BLUE, ActionType.PICK),
                Triple(11, Side.BLUE, ActionType.PICK),
                Triple(12, Side.RED, ActionType.PICK),
            )

        expected.forEach { (turn, side, action) ->
            val spec = DraftTurnSpec.forTurn(turn)
            assertEquals(turn, spec.turnNumber)
            assertEquals(DraftPhase.PICK_PHASE_1, spec.phase)
            assertEquals(side, spec.side)
            assertEquals(action, spec.actionType)
        }
    }

    @Test
    fun `should correctly define Phase 2 Bans with Red side first (Turns 13 to 16)`() {
        val expected =
            listOf(
                Triple(13, Side.RED, ActionType.BAN),
                Triple(14, Side.BLUE, ActionType.BAN),
                Triple(15, Side.RED, ActionType.BAN),
                Triple(16, Side.BLUE, ActionType.BAN),
            )

        expected.forEach { (turn, side, action) ->
            val spec = DraftTurnSpec.forTurn(turn)
            assertEquals(turn, spec.turnNumber)
            assertEquals(DraftPhase.BAN_PHASE_2, spec.phase)
            assertEquals(side, spec.side)
            assertEquals(action, spec.actionType)
        }
    }

    @Test
    fun `should correctly define Phase 2 Picks with Red side first (Turns 17 to 20)`() {
        val expected =
            listOf(
                Triple(17, Side.RED, ActionType.PICK),
                Triple(18, Side.BLUE, ActionType.PICK),
                Triple(19, Side.BLUE, ActionType.PICK),
                Triple(20, Side.RED, ActionType.PICK),
            )

        expected.forEach { (turn, side, action) ->
            val spec = DraftTurnSpec.forTurn(turn)
            assertEquals(turn, spec.turnNumber)
            assertEquals(DraftPhase.PICK_PHASE_2, spec.phase)
            assertEquals(side, spec.side)
            assertEquals(action, spec.actionType)
        }
    }

    @Test
    fun `should throw exception for out-of-bounds turn lookup`() {
        assertThrows<IllegalArgumentException> {
            DraftTurnSpec.forTurn(0)
        }
        assertThrows<IllegalArgumentException> {
            DraftTurnSpec.forTurn(21)
        }
    }
}

package com.loldraft.data.models

import kotlinx.serialization.Serializable

@Serializable
data class DraftTurnSpec(
    val turnNumber: Int,
    val phase: DraftPhase,
    val side: Side,
    val actionType: ActionType,
) {
    companion object {
        val SPECS: List<DraftTurnSpec> =
            listOf(
                // Phase 1 Bans (Turns 1..6): B-R-B-R-B-R
                DraftTurnSpec(1, DraftPhase.BAN_PHASE_1, Side.BLUE, ActionType.BAN),
                DraftTurnSpec(2, DraftPhase.BAN_PHASE_1, Side.RED, ActionType.BAN),
                DraftTurnSpec(3, DraftPhase.BAN_PHASE_1, Side.BLUE, ActionType.BAN),
                DraftTurnSpec(4, DraftPhase.BAN_PHASE_1, Side.RED, ActionType.BAN),
                DraftTurnSpec(5, DraftPhase.BAN_PHASE_1, Side.BLUE, ActionType.BAN),
                DraftTurnSpec(6, DraftPhase.BAN_PHASE_1, Side.RED, ActionType.BAN),
                // Phase 1 Picks (Turns 7..12): B-R-R-B-B-R
                DraftTurnSpec(7, DraftPhase.PICK_PHASE_1, Side.BLUE, ActionType.PICK),
                DraftTurnSpec(8, DraftPhase.PICK_PHASE_1, Side.RED, ActionType.PICK),
                DraftTurnSpec(9, DraftPhase.PICK_PHASE_1, Side.RED, ActionType.PICK),
                DraftTurnSpec(10, DraftPhase.PICK_PHASE_1, Side.BLUE, ActionType.PICK),
                DraftTurnSpec(11, DraftPhase.PICK_PHASE_1, Side.BLUE, ActionType.PICK),
                DraftTurnSpec(12, DraftPhase.PICK_PHASE_1, Side.RED, ActionType.PICK),
                // Phase 2 Bans (Turns 13..16): R-B-R-B (Red side first!)
                DraftTurnSpec(13, DraftPhase.BAN_PHASE_2, Side.RED, ActionType.BAN),
                DraftTurnSpec(14, DraftPhase.BAN_PHASE_2, Side.BLUE, ActionType.BAN),
                DraftTurnSpec(15, DraftPhase.BAN_PHASE_2, Side.RED, ActionType.BAN),
                DraftTurnSpec(16, DraftPhase.BAN_PHASE_2, Side.BLUE, ActionType.BAN),
                // Phase 2 Picks (Turns 17..20): R-B-B-R (Red side first!)
                DraftTurnSpec(17, DraftPhase.PICK_PHASE_2, Side.RED, ActionType.PICK),
                DraftTurnSpec(18, DraftPhase.PICK_PHASE_2, Side.BLUE, ActionType.PICK),
                DraftTurnSpec(19, DraftPhase.PICK_PHASE_2, Side.BLUE, ActionType.PICK),
                DraftTurnSpec(20, DraftPhase.PICK_PHASE_2, Side.RED, ActionType.PICK),
            )

        private val SPECS_MAP: Map<Int, DraftTurnSpec> = SPECS.associateBy { it.turnNumber }

        fun forTurn(turnNumber: Int): DraftTurnSpec =
            SPECS_MAP[turnNumber]
                ?: throw IllegalArgumentException("Turn number $turnNumber out of bounds (1..20)")
    }
}

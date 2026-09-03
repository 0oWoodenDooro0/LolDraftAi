package com.loldraft.data.validation

import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.DraftTurnSpec

class DraftValidator(
    private val allowEmptyBans: Boolean = false
) {

    fun validateTurn(turn: DraftTurn, previousTurns: List<DraftTurn>): ValidationResult {
        val errors = mutableListOf<String>()

        if (turn.turnNumber !in 1..20) {
            errors.add("Turn number ${turn.turnNumber} out of bounds (1..20)")
            return ValidationResult.failure(errors)
        }

        val expectedTurnNumber = previousTurns.size + 1
        if (turn.turnNumber != expectedTurnNumber) {
            errors.add("Expected turn $expectedTurnNumber but received turn ${turn.turnNumber}")
        }

        if (turn.championId.isBlank()) {
            errors.add("Champion ID cannot be blank")
        }

        val spec = DraftTurnSpec.forTurn(turn.turnNumber)
        if (turn.side != spec.side) {
            errors.add("Invalid side for turn ${turn.turnNumber}: expected ${spec.side}, got ${turn.side}")
        }

        if (turn.actionType != spec.actionType) {
            errors.add("Invalid action type for turn ${turn.turnNumber}: expected ${spec.actionType}, got ${turn.actionType}")
        }

        val trimmedChamp = turn.championId.trim().lowercase()
        if (trimmedChamp.isNotBlank()) {
            val previousChamps = previousTurns
                .map { it.championId.trim().lowercase() }
                .toSet()

            if (previousChamps.contains(trimmedChamp)) {
                errors.add("Champion '${turn.championId}' has already been selected in a previous turn")
            }
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    fun validateDraftSequence(turns: List<DraftTurn>): ValidationResult {
        val errors = mutableListOf<String>()
        val history = mutableListOf<DraftTurn>()

        for (turn in turns) {
            val res = validateTurn(turn, history)
            if (!res.isValid) {
                errors.addAll(res.errors)
            }
            history.add(turn)
        }

        if (turns.size > 20) {
            errors.add("Draft sequence exceeds maximum 20 turns: found ${turns.size}")
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }

    fun validateCompleteDraft(state: DraftState): ValidationResult {
        val errors = mutableListOf<String>()

        val totalPicks = state.bluePicks.size + state.redPicks.size
        val totalBans = state.blueBans.size + state.redBans.size
        val totalTurns = if (state.turns.isNotEmpty()) state.turns.size else (totalPicks + totalBans)

        if (totalTurns != 20 || !state.isComplete && state.turns.isNotEmpty() && state.turns.size != 20) {
            errors.add("Draft is incomplete: expected 20 turns, but found $totalTurns")
        }

        if (state.blueBans.size != 5) {
            errors.add("Expected Blue side to have 5 bans, found ${state.blueBans.size}")
        }
        if (state.redBans.size != 5) {
            errors.add("Expected Red side to have 5 bans, found ${state.redBans.size}")
        }
        if (state.bluePicks.size != 5) {
            errors.add("Expected Blue side to have 5 picks, found ${state.bluePicks.size}")
        }
        if (state.redPicks.size != 5) {
            errors.add("Expected Red side to have 5 picks, found ${state.redPicks.size}")
        }

        val allChamps = state.blueBans + state.redBans +
                state.bluePicks.map { it.championId } +
                state.redPicks.map { it.championId }

        val duplicates = allChamps
            .filter { it.isNotBlank() }
            .groupingBy { it.trim().lowercase() }
            .eachCount()
            .filter { it.value > 1 }

        if (duplicates.isNotEmpty()) {
            val dupDisplayNames = allChamps
                .filter { champ -> duplicates.containsKey(champ.trim().lowercase()) }
                .distinct()
            errors.add("Duplicate champions found: $dupDisplayNames")
        }

        if (state.turns.isNotEmpty()) {
            val seqResult = validateDraftSequence(state.turns)
            if (!seqResult.isValid) {
                errors.addAll(seqResult.errors)
            }
        }

        return if (errors.isEmpty()) ValidationResult.success() else ValidationResult.failure(errors)
    }
}

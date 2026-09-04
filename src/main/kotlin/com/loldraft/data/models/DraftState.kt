package com.loldraft.data.models

import com.loldraft.data.meta.SeriesDraftContext
import kotlinx.serialization.Serializable

@Serializable
data class PickSelection(
    val championId: String,
    val role: Role? = null,
    val playerId: String? = null,
)

@Serializable
data class DraftState(
    val blueBans: List<String> = emptyList(),
    val redBans: List<String> = emptyList(),
    val bluePicks: List<PickSelection> = emptyList(),
    val redPicks: List<PickSelection> = emptyList(),
    val turns: List<DraftTurn> = emptyList(),
    val seriesContext: SeriesDraftContext? = null,
) {
    val currentTurnNumber: Int
        get() = turns.size + 1

    val isComplete: Boolean
        get() = turns.size == 20

    val allBannedChampions: Set<String>
        get() = (blueBans + redBans).toSet()

    val allPickedChampions: Set<String>
        get() = (bluePicks.map { it.championId } + redPicks.map { it.championId }).toSet()

    val fearlessSpentChampions: Set<String>
        get() = seriesContext?.spentChampions ?: emptySet()

    val allSelectedChampions: Set<String>
        get() = allBannedChampions + allPickedChampions + fearlessSpentChampions

    fun withFearlessSpent(spent: Set<String>): DraftState =
        copy(seriesContext = (seriesContext ?: SeriesDraftContext()).copy(spentChampions = spent))


    fun applyTurn(turn: DraftTurn): DraftState {
        val updatedTurns = turns + turn
        return when (turn.actionType) {
            ActionType.BAN ->
                when (turn.side) {
                    Side.BLUE ->
                        copy(
                            blueBans = blueBans + turn.championId,
                            turns = updatedTurns,
                        )
                    Side.RED ->
                        copy(
                            redBans = redBans + turn.championId,
                            turns = updatedTurns,
                        )
                }
            ActionType.PICK -> {
                val selection =
                    PickSelection(
                        championId = turn.championId,
                        role = turn.role,
                        playerId = turn.player,
                    )
                when (turn.side) {
                    Side.BLUE ->
                        copy(
                            bluePicks = bluePicks + selection,
                            turns = updatedTurns,
                        )
                    Side.RED ->
                        copy(
                            redPicks = redPicks + selection,
                            turns = updatedTurns,
                        )
                }
            }
        }
    }

    companion object {
        fun empty(): DraftState = DraftState()

        fun fromTurns(turns: List<DraftTurn>): DraftState {
            var state = empty()
            for (turn in turns) {
                state = state.applyTurn(turn)
            }
            return state
        }
    }
}

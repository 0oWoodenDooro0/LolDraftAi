package com.loldraft.client.compose.state

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.player.PlayerRosterIntelligence
import com.loldraft.models.ChampionIntentCandidate
import com.loldraft.models.CompositionFlaw
import com.loldraft.models.PickRecommendation
import com.loldraft.platform.pro.api.ProChampionEntry
import com.loldraft.platform.pro.api.ProTeamSummary

data class BoardSlot(
    val turnNumber: Int,
    val side: Side,
    val actionType: ActionType,
    val role: Role? = null,
    val playerName: String? = null,
    val championId: String? = null,
    val championName: String? = null,
    val isCurrentTurn: Boolean = false,
)

data class EvalBarState(
    val blueWinRate: Double = 0.50,
    val redWinRate: Double = 0.50,
    val evalScore: Double = 0.0,
    val advantageSide: Side? = null,
    val phaseDescription: String = "Even Matchup",
)

data class DraftClientState(
    val selectedLeague: String? = null,
    val availableLeagues: List<String> = emptyList(),
    val selectedPatch: String = "16.17",
    val availablePatches: List<String> = listOf("16.17"),
    val allTeams: List<ProTeamSummary> = emptyList(),
    val blueTeam: ProTeamSummary? = null,
    val redTeam: ProTeamSummary? = null,
    val blueRosterIntelligence: Map<Role, PlayerRosterIntelligence> = emptyMap(),
    val redRosterIntelligence: Map<Role, PlayerRosterIntelligence> = emptyMap(),
    val currentTurnNumber: Int = 1,
    val currentTurnSpec: DraftTurnSpec = DraftTurnSpec.forTurn(1),
    val boardSlots: List<BoardSlot> = emptyList(),
    val evalBar: EvalBarState = EvalBarState(),
    val intentPredictions: List<ChampionIntentCandidate> = emptyList(),
    val counterRecommendations: List<PickRecommendation> = emptyList(),
    val compositionFlaws: List<CompositionFlaw> = emptyList(),
    val allChampions: List<ProChampionEntry> = emptyList(),
    val filteredChampions: List<ProChampionEntry> = emptyList(),
    val searchQuery: String = "",
    val selectedRoleFilter: Role? = null,
    val selectedChampionId: String? = null,
    val bannedChampionIds: Set<String> = emptySet(),
    val pickedChampionIds: Set<String> = emptySet(),
    val isDraftComplete: Boolean = false,
)

package com.loldraft.platform.sandbox.models

import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.player.PlayerIntelligenceDossier
import com.loldraft.data.style.TeamTacticalProfile
import com.loldraft.models.DraftEvaluationResult
import com.loldraft.models.EvalBarScore
import kotlinx.serialization.Serializable

@Serializable
enum class ScenarioPreset {
    META_OPTIMAL,
    TARGETED_COUNTER,
    STYLE_CLASH,
}

@Serializable
enum class PivotType {
    MOMENTUM_SWING,
    CRITICAL_COUNTER,
    FLEX_LOCK,
    COMPOSITION_BLUNDER,
    HIGH_PRIORITY_DENIAL,
}

@Serializable
data class TurnTrajectoryPoint(
    val turnNumber: Int,
    val turnSpec: DraftTurnSpec,
    val championId: String,
    val role: Role? = null,
    val rationale: String,
    val evalBarScore: EvalBarScore,
    val blueWinRate: Double,
    val isPivotPoint: Boolean = false,
)

@Serializable
data class DraftPivotPoint(
    val turnNumber: Int,
    val side: Side,
    val actionType: ActionType,
    val championId: String,
    val role: Role? = null,
    val evalDelta: Double,
    val impactDescription: String,
    val pivotType: PivotType,
)

@Serializable
data class DraftScenario(
    val scenarioId: String,
    val preset: ScenarioPreset,
    val title: String,
    val description: String,
    val likelihood: Double,
    val draftState: DraftState,
    val turnTrajectories: List<TurnTrajectoryPoint>,
    val evaluation: DraftEvaluationResult,
    val pivotPoints: List<DraftPivotPoint>,
)

@Serializable
data class DraftTreeNode(
    val nodeId: String,
    val parentNodeId: String? = null,
    val turnNumber: Int,
    val turn: DraftTurn? = null,
    val draftState: DraftState,
    val evalBarScore: EvalBarScore,
    val blueWinRate: Double,
    val children: List<DraftTreeNode> = emptyList(),
    val isBranchPoint: Boolean = false,
    val branchRationale: String? = null,
)

@Serializable
data class WhatIfBranchRequest(
    val branchTurnNumber: Int,
    val newChampionId: String,
    val newRole: Role? = null,
    val scenarioPreset: ScenarioPreset = ScenarioPreset.META_OPTIMAL,
    val rationale: String? = null,
)

@Serializable
data class BranchComparativeDelta(
    val blueWinRateChange: Double,
    val evalScoreChange: Double,
    val flawsAddedBlue: List<String> = emptyList(),
    val flawsResolvedBlue: List<String> = emptyList(),
    val flawsAddedRed: List<String> = emptyList(),
    val flawsResolvedRed: List<String> = emptyList(),
    val strategicSummary: String,
)

@Serializable
data class WhatIfBranchResult(
    val branchId: String,
    val branchTurnNumber: Int,
    val originalTurn: DraftTurn,
    val replacementTurn: DraftTurn,
    val newScenario: DraftScenario,
    val comparativeDelta: BranchComparativeDelta,
)

@Serializable
data class MatchupSandboxRequest(
    val blueTeam: Team,
    val redTeam: Team,
    val blueTeamProfile: TeamTacticalProfile? = null,
    val redTeamProfile: TeamTacticalProfile? = null,
    val bluePlayerStats: Map<Role, PlayerCareerStats>? = null,
    val redPlayerStats: Map<Role, PlayerCareerStats>? = null,
    val bluePlayerDossiers: List<PlayerIntelligenceDossier>? = null,
    val redPlayerDossiers: List<PlayerIntelligenceDossier>? = null,
    val patchMeta: PatchMetaMatrix? = null,
    val initialTurns: List<DraftTurn> = emptyList(),
)

@Serializable
data class MatchupSandboxResponse(
    val matchupSummary: String,
    val blueTeam: Team,
    val redTeam: Team,
    val scenarios: List<DraftScenario>,
    val rootDraftTree: DraftTreeNode,
)

@Serializable
data class WhatIfBranchApiRequest(
    val baseDraftState: DraftState,
    val branchRequest: WhatIfBranchRequest,
    val context: MatchupSandboxRequest,
)

package com.loldraft.platform.live.models

import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.style.TeamTacticalProfile
import com.loldraft.models.ChampionIntentCandidate
import com.loldraft.models.CompositionFlaw
import com.loldraft.models.EvalBarScore
import com.loldraft.models.FiveDimensionRadarScores
import com.loldraft.models.PickRecommendation
import com.loldraft.models.TimeCurve
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LiveSessionStatus {
    IN_PROGRESS,
    COMPLETED,
}

@Serializable
enum class CoachGrade {
    OPTIMAL_S,
    STRONG_A,
    ACCEPTABLE_B,
    QUESTIONABLE_C,
    BLUNDER_D,
}

@Serializable
data class CoachPickFeedback(
    val turnNumber: Int,
    val side: Side,
    val actionType: ActionType,
    val lockedChampionId: String,
    val role: Role? = null,
    val winRateBefore: Double,
    val winRateAfter: Double,
    val winRateDelta: Double,
    val evalScoreBefore: Double,
    val evalScoreAfter: Double,
    val evalScoreDelta: Double,
    val aiRank: Int? = null,
    val grade: CoachGrade,
    val flawsIntroduced: List<CompositionFlaw> = emptyList(),
    val flawsResolved: List<CompositionFlaw> = emptyList(),
    val critique: String,
    val alternativePicks: List<PickRecommendation> = emptyList(),
)

@Serializable
data class LiveTurnSnapshot(
    val turnNumber: Int,
    val turn: DraftTurn? = null,
    val draftState: DraftState,
    val evalBar: EvalBarScore,
    val timeCurve: TimeCurve,
    val blueRadar: FiveDimensionRadarScores,
    val redRadar: FiveDimensionRadarScores,
    val radarDelta: FiveDimensionRadarScores,
    val blueFlaws: List<CompositionFlaw> = emptyList(),
    val redFlaws: List<CompositionFlaw> = emptyList(),
    val coachPickFeedback: CoachPickFeedback? = null,
    val nextTurnSpec: DraftTurnSpec? = null,
    val aiRecommendations: List<PickRecommendation> = emptyList(),
    val aiIntentPredictions: List<ChampionIntentCandidate> = emptyList(),
)

@Serializable
data class LiveMatchSession(
    val sessionId: String,
    val blueTeam: Team,
    val redTeam: Team,
    val patchMeta: PatchMetaMatrix? = null,
    val blueTeamProfile: TeamTacticalProfile? = null,
    val redTeamProfile: TeamTacticalProfile? = null,
    val playerStatsByRoleBlue: Map<Role, PlayerCareerStats>? = null,
    val playerStatsByRoleRed: Map<Role, PlayerCareerStats>? = null,
    val currentState: DraftState = DraftState.empty(),
    val history: List<LiveTurnSnapshot> = emptyList(),
    val status: LiveSessionStatus = LiveSessionStatus.IN_PROGRESS,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class CreateLiveSessionRequest(
    val sessionId: String? = null,
    val blueTeam: Team,
    val redTeam: Team,
    val patchMeta: PatchMetaMatrix? = null,
    val blueTeamProfile: TeamTacticalProfile? = null,
    val redTeamProfile: TeamTacticalProfile? = null,
    val playerStatsByRoleBlue: Map<Role, PlayerCareerStats>? = null,
    val playerStatsByRoleRed: Map<Role, PlayerCareerStats>? = null,
    val initialTurns: List<DraftTurn> = emptyList(),
)

@Serializable
data class ApplyTurnRequest(
    val championId: String,
    val role: Role? = null,
    val player: String? = null,
)

@Serializable
data class LiveSessionSummaryResponse(
    val sessionId: String,
    val blueTeam: Team,
    val redTeam: Team,
    val status: LiveSessionStatus,
    val currentTurnNumber: Int,
    val isComplete: Boolean,
    val latestSnapshot: LiveTurnSnapshot,
)

@Serializable
sealed class LiveWsClientMessage {
    @Serializable
    @SerialName("apply_turn")
    data class ApplyTurn(
        val championId: String,
        val role: Role? = null,
        val player: String? = null,
    ) : LiveWsClientMessage()

    @Serializable
    @SerialName("undo")
    data object Undo : LiveWsClientMessage()

    @Serializable
    @SerialName("reset")
    data object Reset : LiveWsClientMessage()

    @Serializable
    @SerialName("ping")
    data object Ping : LiveWsClientMessage()
}

@Serializable
sealed class LiveWsServerMessage {
    @Serializable
    @SerialName("session_snapshot")
    data class SessionSnapshot(
        val session: LiveSessionSummaryResponse,
        val latestSnapshot: LiveTurnSnapshot,
    ) : LiveWsServerMessage()

    @Serializable
    @SerialName("turn_applied")
    data class TurnApplied(
        val turn: DraftTurn,
        val snapshot: LiveTurnSnapshot,
    ) : LiveWsServerMessage()

    @Serializable
    @SerialName("turn_undone")
    data class TurnUndone(
        val undoneTurnNumber: Int,
        val snapshot: LiveTurnSnapshot,
    ) : LiveWsServerMessage()

    @Serializable
    @SerialName("session_reset")
    data class SessionReset(
        val snapshot: LiveTurnSnapshot,
    ) : LiveWsServerMessage()

    @Serializable
    @SerialName("error")
    data class Error(
        val code: String,
        val message: String,
    ) : LiveWsServerMessage()

    @Serializable
    @SerialName("pong")
    data object Pong : LiveWsServerMessage()
}

package com.loldraft.platform.debrief.models

import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Game
import com.loldraft.data.models.Match
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.style.TeamTacticalProfile
import com.loldraft.models.CompositionFlaw
import com.loldraft.models.CompositionRadarScore
import com.loldraft.models.PickRecommendation
import com.loldraft.models.TimeCurve
import com.loldraft.platform.live.models.CoachGrade
import kotlinx.serialization.Serializable

@Serializable
enum class AttributionCategory {
    DRAFT_CARRIED,
    EXECUTION_UPSET,
    COMPOSITION_GAP,
    EXECUTION_THROW,
    BALANCED_CONTEST,
}

@Serializable
data class AttributionResult(
    val advantageSide: Side?,
    val actualWinner: Side,
    val category: AttributionCategory,
    val draftInfluencePct: Double,
    val executionInfluencePct: Double,
    val title: String,
    val explanation: String,
    val keyContributingFactors: List<String> = emptyList(),
)

@Serializable
data class TurnDebriefRecord(
    val turnNumber: Int,
    val side: Side,
    val actionType: ActionType,
    val championId: String,
    val role: Role? = null,
    val player: String? = null,
    val winRateBefore: Double,
    val winRateAfter: Double,
    val deltaWinRate: Double,
    val evalScoreBefore: Double,
    val evalScoreAfter: Double,
    val deltaEvalScore: Double,
    val grade: CoachGrade,
    val isMvpTurn: Boolean = false,
    val isBlunderTurn: Boolean = false,
    val critique: String,
    val flawsIntroduced: List<CompositionFlaw> = emptyList(),
    val flawsResolved: List<CompositionFlaw> = emptyList(),
    val alternativePicks: List<PickRecommendation> = emptyList(),
)

@Serializable
data class TeamCoachDebriefSummary(
    val side: Side,
    val team: Team,
    val netDraftDeltaWinRate: Double,
    val coachBpGrade: CoachGrade,
    val coachBpScore: Double,
    val phase1DeltaWinRate: Double,
    val phase2DeltaWinRate: Double,
    val optimalPicksCount: Int,
    val blundersCount: Int,
    val mvpTurn: TurnDebriefRecord? = null,
    val worstTurn: TurnDebriefRecord? = null,
    val unresolvedFlaws: List<CompositionFlaw> = emptyList(),
)

@Serializable
data class TimelineChartPoint(
    val turnNumber: Int,
    val blueWinRate: Double,
    val redWinRate: Double,
    val championId: String? = null,
    val side: Side? = null,
    val actionType: ActionType? = null,
    val deltaWinRate: Double = 0.0,
)

@Serializable
data class TimeCurveChartPoint(
    val minute: Int,
    val blueWinRate: Double,
    val redWinRate: Double,
)

@Serializable
data class RadarDimensionComparison(
    val dimension: String,
    val blueScore: Double,
    val redScore: Double,
    val delta: Double,
    val advantage: Side?,
)

@Serializable
data class AttributionChartData(
    val draftInfluencePct: Double,
    val executionInfluencePct: Double,
)

@Serializable
data class VisualChartData(
    val timelinePoints: List<TimelineChartPoint>,
    val timeCurvePoints: List<TimeCurveChartPoint>,
    val radarComparison: List<RadarDimensionComparison>,
    val attributionBreakdown: AttributionChartData,
)

@Serializable
data class DebriefReport(
    val reportId: String,
    val gameId: String,
    val matchId: String? = null,
    val patch: String,
    val tournament: String? = null,
    val blueTeam: Team,
    val redTeam: Team,
    val actualWinner: Side,
    val durationSeconds: Int? = null,
    val initialBlueWinRate: Double,
    val finalBlueWinRate: Double,
    val finalRedWinRate: Double,
    val attribution: AttributionResult,
    val turns: List<TurnDebriefRecord>,
    val blueCoachSummary: TeamCoachDebriefSummary,
    val redCoachSummary: TeamCoachDebriefSummary,
    val radarComparison: CompositionRadarScore,
    val timeCurve: TimeCurve,
    val charts: VisualChartData,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class MatchDebriefReport(
    val matchId: String,
    val tournament: String,
    val patch: String,
    val bestOf: Int,
    val blueTeam: Team,
    val redTeam: Team,
    val seriesWinnerTeamId: String? = null,
    val gamesPlayed: Int,
    val gameReports: List<DebriefReport>,
    val overallAttributionSummary: String,
    val sideWinRateStats: Map<Side, Double>,
    val frequentBans: List<String>,
    val frequentPicks: List<String>,
    val blueSeriesCoachScore: Double,
    val redSeriesCoachScore: Double,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class DebriefGameRequest(
    val game: Game,
    val patchMeta: PatchMetaMatrix? = null,
    val blueTeamProfile: TeamTacticalProfile? = null,
    val redTeamProfile: TeamTacticalProfile? = null,
    val playerStatsByRoleBlue: Map<Role, PlayerCareerStats>? = null,
    val playerStatsByRoleRed: Map<Role, PlayerCareerStats>? = null,
)

@Serializable
data class DebriefMatchRequest(
    val match: Match,
    val patchMeta: PatchMetaMatrix? = null,
    val blueTeamProfile: TeamTacticalProfile? = null,
    val redTeamProfile: TeamTacticalProfile? = null,
)

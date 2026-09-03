package com.loldraft.data.style

import com.loldraft.data.models.Role
import com.loldraft.data.models.Team
import kotlinx.serialization.Serializable

@Serializable
enum class SideTendency {
    BLUE_FAVORED,
    RED_FAVORED,
    BALANCED,
}

@Serializable
enum class GamePace {
    FAST_PACED,
    AVERAGE,
    SLOW_CONTROLLED,
}

@Serializable
enum class AggressionLevel {
    VERY_AGGRESSIVE,
    BALANCED,
    CONTROL_ORIENTED,
}

@Serializable
enum class TacticalTag {
    EARLY_AGGRESSOR,
    DRAGON_CONTROL,
    BLOODY_SKIRMISHER,
    LATE_GAME_MACRO,
    BLUE_SIDE_SPECIALIST,
    RED_SIDE_SPECIALIST,
    BOT_CENTRIC_DRAFT,
    MID_CENTRIC_DRAFT,
    TOP_CENTRIC_DRAFT,
    FAST_TEMPO,
    SLOW_TEMPO,
}

@Serializable
data class SideRecord(
    val games: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
)

@Serializable
data class SidePreference(
    val blueRecord: SideRecord,
    val redRecord: SideRecord,
    val overallRecord: SideRecord,
    val winRateDelta: Double,
    val blueRate: Double,
    val redRate: Double,
    val tendency: SideTendency,
)

@Serializable
data class EarlyGameMetrics(
    val firstBloodRate: Double,
    val firstDragonRate: Double,
    val avgGoldDiffAt15: Double,
    val gamesSampled: Int,
    val dominanceScore: Double,
)

@Serializable
data class TacticalStyleMetrics(
    val teamKillsPerMinute: Double,
    val combinedKillsPerMinute: Double,
    val avgDurationSeconds: Double,
    val avgDurationFormatted: String,
    val pace: GamePace,
    val aggression: AggressionLevel,
)

@Serializable
data class FirstPickPriority(
    val championId: String,
    val pickCount: Int,
    val totalOpportunities: Int,
    val pickRate: Double,
    val wins: Int,
    val winRate: Double,
    val role: Role? = null,
)

@Serializable
data class FirstPickAnalysis(
    val b1Priorities: List<FirstPickPriority>,
    val teamFirstPickPriorities: List<FirstPickPriority>,
    val roleDistribution: Map<Role, Double>,
)

@Serializable
data class TeamStyleFilter(
    val patch: String? = null,
    val tournament: String? = null,
    val season: String? = null,
    val year: Int? = null,
    val minGames: Int = 1,
)

@Serializable
data class TeamTacticalProfile(
    val team: Team,
    val totalGamesAnalyzed: Int,
    val sidePreference: SidePreference,
    val earlyGameMetrics: EarlyGameMetrics,
    val tacticalStyleMetrics: TacticalStyleMetrics,
    val firstPickAnalysis: FirstPickAnalysis,
    val tags: Set<TacticalTag>,
)

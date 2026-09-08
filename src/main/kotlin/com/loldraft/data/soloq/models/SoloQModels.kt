package com.loldraft.data.soloq.models

import com.loldraft.data.models.Role
import kotlinx.serialization.Serializable

enum class SoloQRoleFilter(val displayName: String) {
    ALL("全部路線"),
    PRIMARY_ONLY("僅本職路線"),
    OFF_ROLE_ONLY("僅副路/補位"),
}

@Serializable
data class PlayerSummonerAccount(
    val playerId: String,
    val teamId: String,
    val primaryRole: Role,
    val gameName: String,
    val tagLine: String,
    val league: String = "",
    val region: String = "asia",
    val platformId: String = "KR",
) {
    val riotId: String get() = "$gameName#$tagLine"
}

@Serializable
data class SoloQMatchRecord(
    val matchId: String,
    val playerId: String,
    val championId: String,
    val championName: String,
    val playedRole: Role,
    val isPrimaryRole: Boolean,
    val win: Boolean,
    val kills: Int,
    val deaths: Int,
    val assists: Int,
    val cs: Int,
    val durationSeconds: Int,
    val timestamp: Long,
    val patch: String? = null,
    val isSecretPick: Boolean = false,
) {
    val kda: Double
        get() = if (deaths == 0) (kills + assists).toDouble() else ((kills + assists).toDouble() / deaths)
}

@Serializable
data class SoloQChampionSummary(
    val championId: String,
    val championName: String,
    val playedRole: Role,
    val isPrimaryRole: Boolean,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val avgKills: Double,
    val avgDeaths: Double,
    val avgAssists: Double,
    val kda: Double,
    val avgCs: Double,
    val lastPlayedTimestamp: Long,
    val playerProGames: Int,
    val tournamentProGames: Int,
    val isSecretPick: Boolean,
    val secretBadgeText: String? = null,
)

@Serializable
data class SoloQPlayerIntelligence(
    val playerId: String,
    val teamId: String,
    val primaryRole: Role,
    val riotId: String,
    val timeRangeDays: Int,
    val totalMatches: Int,
    val overallWinRate: Double,
    val primaryRoleMatches: Int,
    val primaryRoleWinRate: Double,
    val championSummaries: List<SoloQChampionSummary>,
    val secretPicks: List<SoloQChampionSummary>,
    val recentMatches: List<SoloQMatchRecord>,
    val lastSyncedTimestamp: Long = System.currentTimeMillis(),
)

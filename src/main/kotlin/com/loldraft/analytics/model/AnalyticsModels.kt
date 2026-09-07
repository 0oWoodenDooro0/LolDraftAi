package com.loldraft.analytics.model

import com.loldraft.data.models.Role

enum class AnalyticsTab(val title: String) {
    TEAM_ROSTER_MATRIX("戰隊英雄池看板 (Roster & Pool)"),
    PLAYERS_GRID("選手總表 (Players Grid)"),
    TEAMS_GRID("戰隊總表 (Teams Grid)"),
}

enum class SortDirection {
    ASCENDING,
    DESCENDING;

    fun toggle(): SortDirection =
        if (this == ASCENDING) DESCENDING else ASCENDING
}

data class PlayerChampionStats(
    val championId: String,
    val championName: String,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val opponentBans: Int,
    val opponentBanRate: Double,
    val kills: Double,
    val deaths: Double,
    val assists: Double,
    val kda: Double,
    val dpm: Double,
    val cspm: Double,
)

data class RosterRoleSlot(
    val role: Role,
    val currentPlayer: String,
    val availablePlayers: List<String>,
    val totalGames: Int,
    val totalWins: Int,
    val totalLosses: Int,
    val winRate: Double,
    val championPool: List<PlayerChampionStats>,
    val championPoolCount: Int = championPool.size,
)

data class TeamRosterMatrix(
    val teamId: String,
    val teamName: String,
    val tournament: String?,
    val split: String? = null,
    val patch: String?,
    val totalTeamGames: Int,
    val teamWins: Int,
    val teamLosses: Int,
    val teamWinRate: Double,
    val roles: Map<Role, RosterRoleSlot>,
)

data class PlayerAnalyticsRow(
    val playerName: String,
    val teamName: String,
    val league: String,
    val split: String? = null,
    val role: Role,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val kda: Double,
    val avgKills: Double,
    val avgDeaths: Double,
    val avgAssists: Double,
    val avgDpm: Double,
    val avgCspm: Double,
    val avgVspm: Double,
    val avgGoldDiffAt15: Double,
    val championPoolCount: Int,
    val topChampions: String,
)

data class TeamAnalyticsRow(
    val teamId: String,
    val teamName: String,
    val league: String,
    val split: String? = null,
    val games: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val blueGames: Int,
    val blueWins: Int,
    val blueWinRate: Double,
    val redGames: Int,
    val redWins: Int,
    val redWinRate: Double,
    val firstBloodRate: Double,
    val firstDragonRate: Double,
    val avgGoldDiffAt15: Double,
    val avgGameDurationSeconds: Int,
)

data class AnalyticsSummary(
    val totalCount: Int,
    val totalGames: Int,
    val avgWinRate: Double,
    val avgKda: Double? = null,
    val avgDpm: Double? = null,
)

package com.loldraft.data.player

import com.loldraft.data.models.Role
import kotlinx.serialization.Serializable

@Serializable
enum class SoloQServer {
    KR,
    CN_SUPER,
    EUW,
    NA,
}

@Serializable
data class SoloQAccount(
    val accountId: String,
    val summonerName: String,
    val server: SoloQServer,
    val tier: String? = null,
    val rank: String? = null,
    val lp: Int? = null,
    val isActive: Boolean = true,
)

@Serializable
data class SoloQGame(
    val gameId: String,
    val accountId: String,
    val server: SoloQServer,
    val timestampEpochMs: Long,
    val championId: String,
    val role: Role,
    val win: Boolean,
    val kills: Int = 0,
    val deaths: Int = 0,
    val assists: Int = 0,
    val isBlindPick: Boolean = false,
    val durationSeconds: Int? = null,
)

@Serializable
data class PlayerAccountMapping(
    val playerId: String,
    val accounts: List<SoloQAccount> = emptyList(),
)

@Serializable
data class SoloQChampionStats(
    val championId: String,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val pickShare: Double,
    val gamesPerDay: Double,
    val role: Role? = null,
    val avgKda: Double = 0.0,
)

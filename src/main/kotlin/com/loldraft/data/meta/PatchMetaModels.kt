package com.loldraft.data.meta

import com.loldraft.data.models.Role
import com.loldraft.data.normalization.ChampionNormalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class MetaTier {
    T0,
    T1,
    T2,
    T3,
    T4,
}

@Serializable
data class ChampionMetaStats(
    val championId: String,
    val patch: String,
    val picks: Int = 0,
    val bans: Int = 0,
    val presenceCount: Int = 0,
    val presenceRate: Double = 0.0,
    val pickRate: Double = 0.0,
    val banRate: Double = 0.0,
    val wins: Int = 0,
    val losses: Int = 0,
    val winRate: Double = 0.0,
    val roleDistribution: Map<Role, Int> = emptyMap(),
    val tier: MetaTier = MetaTier.T4,
)

@Serializable
data class ChampionSynergy(
    val championA: String,
    val championB: String,
    val gamesTogether: Int,
    val winsTogether: Int,
    val synergyWinRate: Double,
    val expectedWinRate: Double,
    val winRateDelta: Double,
    val synergyScore: Double,
)

@Serializable
data class MatchupCounter(
    val champion: String,
    val opponent: String,
    val role: Role? = null,
    val gamesFaced: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val winRateDelta: Double,
    val avgGoldDiffAt15: Double? = null,
    val counterScore: Double,
)

@Serializable
data class PatchMetaConfig(
    val t0PresenceThreshold: Double = 0.70,
    val t0WinRateThreshold: Double = 0.54,
    val t1PresenceThreshold: Double = 0.35,
    val t2PresenceThreshold: Double = 0.15,
    val t3PresenceThreshold: Double = 0.05,
    val minGamesForSynergy: Int = 2,
    val minGamesForCounter: Int = 2,
)

@Serializable
data class PatchMetaMatrix(
    val patch: String,
    val totalGames: Int,
    val championStats: Map<String, ChampionMetaStats>,
    val synergies: List<ChampionSynergy> = emptyList(),
    val matchupCounters: List<MatchupCounter> = emptyList(),
) {
    fun getStats(championNameOrSlug: String?): ChampionMetaStats? {
        if (championNameOrSlug.isNullOrBlank()) return null
        val slug = ChampionNormalizer.toSlug(championNameOrSlug)
        return championStats[slug] ?: championStats[championNameOrSlug]
    }

    fun getTierList(
        tier: MetaTier? = null,
        role: Role? = null,
    ): List<ChampionMetaStats> =
        championStats.values
            .filter { stats ->
                val matchesTier = tier == null || stats.tier == tier
                val matchesRole =
                    role == null ||
                        (stats.roleDistribution.isNotEmpty() && stats.roleDistribution.maxByOrNull { it.value }?.key == role)
                matchesTier && matchesRole
            }.sortedWith(
                compareBy<ChampionMetaStats> { it.tier }
                    .thenByDescending { it.presenceRate }
                    .thenByDescending { it.winRate },
            )

    fun getTopSynergies(
        championNameOrSlug: String,
        limit: Int = 10,
        minGames: Int = 1,
    ): List<ChampionSynergy> {
        val slug = ChampionNormalizer.toSlug(championNameOrSlug)
        return synergies
            .filter {
                val matchA = ChampionNormalizer.toSlug(it.championA) == slug
                val matchB = ChampionNormalizer.toSlug(it.championB) == slug
                (matchA || matchB) && it.gamesTogether >= minGames
            }.sortedWith(
                compareByDescending<ChampionSynergy> { it.winRateDelta }
                    .thenByDescending { it.synergyWinRate }
                    .thenByDescending { it.gamesTogether },
            ).take(limit)
    }

    fun getMatchup(
        champion: String,
        opponent: String,
        role: Role? = null,
    ): MatchupCounter? {
        val champSlug = ChampionNormalizer.toSlug(champion)
        val oppSlug = ChampionNormalizer.toSlug(opponent)
        return matchupCounters.find {
            ChampionNormalizer.toSlug(it.champion) == champSlug &&
                ChampionNormalizer.toSlug(it.opponent) == oppSlug &&
                (role == null || it.role == role)
        }
    }

    fun getCountersFor(
        targetChampion: String,
        role: Role? = null,
        minGames: Int = 1,
    ): List<MatchupCounter> {
        val targetSlug = ChampionNormalizer.toSlug(targetChampion)
        return matchupCounters
            .filter {
                ChampionNormalizer.toSlug(it.opponent) == targetSlug &&
                    (role == null || it.role == role) &&
                    it.gamesFaced >= minGames &&
                    it.winRate >= 0.50
            }.sortedWith(
                compareByDescending<MatchupCounter> { it.counterScore }
                    .thenByDescending { it.winRate }
                    .thenByDescending { it.gamesFaced },
            )
    }

    fun getCountersAgainst(
        targetOpponent: String,
        role: Role? = null,
        minGames: Int = 1,
    ): List<MatchupCounter> {
        val oppSlug = ChampionNormalizer.toSlug(targetOpponent)
        return matchupCounters
            .filter {
                ChampionNormalizer.toSlug(it.champion) == oppSlug &&
                    (role == null || it.role == role) &&
                    it.gamesFaced >= minGames &&
                    it.winRate >= 0.50
            }.sortedWith(
                compareByDescending<MatchupCounter> { it.counterScore }
                    .thenByDescending { it.winRate }
                    .thenByDescending { it.gamesFaced },
            )
    }

    fun toJson(): String = jsonFormat.encodeToString(this)

    companion object {
        private val jsonFormat =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        fun fromJson(json: String): PatchMetaMatrix = jsonFormat.decodeFromString(json)
    }
}

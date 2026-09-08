package com.loldraft.analytics.service

import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.soloq.models.SoloQChampionSummary
import com.loldraft.data.soloq.models.SoloQMatchRecord
import com.loldraft.data.soloq.models.SoloQPlayerIntelligence
import com.loldraft.data.soloq.models.SoloQRoleFilter
import com.loldraft.data.soloq.repository.SoloQRepository
import com.loldraft.server.ProMatchRepository
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class SoloQIntelligenceService(
    val soloQRepository: SoloQRepository = SoloQRepository(),
    private val proMatchRepository: ProMatchRepository? = null,
    private val gamesSupplier: (() -> List<Game>)? = null,
) {
    private fun getProGames(): List<Game> =
        when {
            gamesSupplier != null -> gamesSupplier.invoke()
            proMatchRepository != null -> proMatchRepository.getAllGames()
            else -> emptyList()
        }

    suspend fun analyzePlayer(
        playerId: String,
        timeRangeDays: Int = 14,
        roleFilter: SoloQRoleFilter = SoloQRoleFilter.ALL,
        forceSync: Boolean = false,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): SoloQPlayerIntelligence? {
        val account = soloQRepository.getAccount(playerId) ?: return null
        val rawMatches = soloQRepository.getRecentMatches(playerId, count = 50, forceSync = forceSync)

        val cutoffTime = referenceTimeMs - TimeUnit.DAYS.toMillis(timeRangeDays.toLong())
        val timeFilteredMatches = rawMatches.filter { it.timestamp >= cutoffTime }

        val proGames = getProGames()
        val playerProChampionCounts = mutableMapOf<String, Int>()
        val tournamentChampionCounts = mutableMapOf<String, Int>()

        for (game in proGames) {
            val allPicks = game.draftState.bluePicks + game.draftState.redPicks
            for (pick in allPicks) {
                val champId = ChampionNormalizer.normalize(pick.championId)
                tournamentChampionCounts[champId] = (tournamentChampionCounts[champId] ?: 0) + 1

                if (pick.playerId != null && pick.playerId.equals(playerId, ignoreCase = true)) {
                    playerProChampionCounts[champId] = (playerProChampionCounts[champId] ?: 0) + 1
                }
            }
        }

        // Group by (championId, playedRole)
        val grouped = timeFilteredMatches.groupBy { Pair(it.championId, it.playedRole) }
        val summaries = mutableListOf<SoloQChampionSummary>()

        for ((key, matches) in grouped) {
            val (champId, role) = key
            val totalGames = matches.size
            val wins = matches.count { it.win }
            val losses = totalGames - wins
            val winRate = if (totalGames > 0) wins.toDouble() / totalGames else 0.0

            val totalKills = matches.sumOf { it.kills }
            val totalDeaths = matches.sumOf { it.deaths }
            val totalAssists = matches.sumOf { it.assists }
            val totalCs = matches.sumOf { it.cs }

            val avgK = roundTo1Decimal(totalKills.toDouble() / totalGames)
            val avgD = roundTo1Decimal(totalDeaths.toDouble() / totalGames)
            val avgA = roundTo1Decimal(totalAssists.toDouble() / totalGames)
            val avgCs = roundTo1Decimal(totalCs.toDouble() / totalGames)
            val kda = if (totalDeaths == 0) (totalKills + totalAssists).toDouble() else roundTo1Decimal((totalKills + totalAssists).toDouble() / totalDeaths)

            val isPrimary = (role == account.primaryRole)
            val lastPlayed = matches.maxOfOrNull { it.timestamp } ?: 0L
            val champName = matches.first().championName

            val playerProCount = playerProChampionCounts[champId] ?: 0
            val tournamentProCount = tournamentChampionCounts[champId] ?: 0

            // Secret pick detection: On primary role, >= 2 games, winRate >= 50%, and player has 0 pro appearances!
            val isSecret = isPrimary && playerProCount == 0 && totalGames >= 2 && winRate >= 0.50
            val badgeText =
                when {
                    isSecret && tournamentProCount == 0 -> "全域未登場黑科技"
                    isSecret -> "選手秘密武器 (賽事 0 選)"
                    !isPrimary && totalGames >= 2 -> "副路/補位練習"
                    else -> null
                }

            summaries.add(
                SoloQChampionSummary(
                    championId = champId,
                    championName = champName,
                    playedRole = role,
                    isPrimaryRole = isPrimary,
                    gamesPlayed = totalGames,
                    wins = wins,
                    losses = losses,
                    winRate = winRate,
                    avgKills = avgK,
                    avgDeaths = avgD,
                    avgAssists = avgA,
                    kda = kda,
                    avgCs = avgCs,
                    lastPlayedTimestamp = lastPlayed,
                    playerProGames = playerProCount,
                    tournamentProGames = tournamentProCount,
                    isSecretPick = isSecret,
                    secretBadgeText = badgeText,
                ),
            )
        }

        val secretPicks =
            summaries
                .filter { it.isSecretPick }
                .sortedWith(compareByDescending<SoloQChampionSummary> { it.gamesPlayed }.thenByDescending { it.winRate })

        val secretChampIds = secretPicks.map { it.championId }.toSet()

        // Annotate recent matches with secret pick flag
        val annotatedMatches =
            timeFilteredMatches.map { match ->
                if (match.isPrimaryRole && secretChampIds.contains(match.championId)) {
                    match.copy(isSecretPick = true)
                } else {
                    match
                }
            }

        // Apply role filter on summaries
        val filteredSummaries =
            when (roleFilter) {
                SoloQRoleFilter.ALL -> summaries
                SoloQRoleFilter.PRIMARY_ONLY -> summaries.filter { it.isPrimaryRole }
                SoloQRoleFilter.OFF_ROLE_ONLY -> summaries.filter { !it.isPrimaryRole }
            }.sortedWith(
                compareByDescending<SoloQChampionSummary> { it.gamesPlayed }
                    .thenByDescending { it.winRate },
            )

        val totalMatches = timeFilteredMatches.size
        val totalWins = timeFilteredMatches.count { it.win }
        val overallWinRate = if (totalMatches > 0) totalWins.toDouble() / totalMatches else 0.0

        val primaryMatches = timeFilteredMatches.filter { it.isPrimaryRole }
        val primaryWins = primaryMatches.count { it.win }
        val primaryWinRate = if (primaryMatches.isNotEmpty()) primaryWins.toDouble() / primaryMatches.size else 0.0

        return SoloQPlayerIntelligence(
            playerId = account.playerId,
            teamId = account.teamId,
            primaryRole = account.primaryRole,
            riotId = account.riotId,
            timeRangeDays = timeRangeDays,
            totalMatches = totalMatches,
            overallWinRate = overallWinRate,
            primaryRoleMatches = primaryMatches.size,
            primaryRoleWinRate = primaryWinRate,
            championSummaries = filteredSummaries,
            secretPicks = secretPicks,
            recentMatches = annotatedMatches,
            lastSyncedTimestamp = System.currentTimeMillis(),
        )
    }

    private fun roundTo1Decimal(value: Double): Double =
        (value * 10.0).roundToInt() / 10.0
}

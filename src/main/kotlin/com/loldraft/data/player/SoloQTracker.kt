package com.loldraft.data.player

import com.loldraft.data.models.Role
import java.util.concurrent.TimeUnit
import kotlin.math.max

data class SoloQTrackerConfig(
    val recentDays: Int = 7,
    val secondaryRecentDays: Int = 3,
    val baselineDays: Int = 21,
)

class SoloQTracker(
    private val config: SoloQTrackerConfig = SoloQTrackerConfig(),
) {
    fun getRecentGames(
        games: List<SoloQGame>,
        windowDays: Int,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): List<SoloQGame> {
        val windowDurationMs = TimeUnit.DAYS.toMillis(windowDays.toLong())
        val cutoffTimeMs = referenceTimeMs - windowDurationMs
        return games.filter { it.timestampEpochMs in cutoffTimeMs..referenceTimeMs }
    }

    fun computeChampionStats(
        games: List<SoloQGame>,
        windowDays: Int,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): List<SoloQChampionStats> {
        val filteredGames = getRecentGames(games, windowDays, referenceTimeMs)
        if (filteredGames.isEmpty()) return emptyList()

        val totalWindowGames = filteredGames.size
        val grouped = filteredGames.groupBy { it.championId }

        val results = mutableListOf<SoloQChampionStats>()

        for ((championId, champGames) in grouped) {
            val gamesPlayed = champGames.size
            val wins = champGames.count { it.win }
            val losses = gamesPlayed - wins
            val winRate = wins.toDouble() / gamesPlayed
            val pickShare = gamesPlayed.toDouble() / totalWindowGames
            val gamesPerDay = gamesPlayed.toDouble() / max(1, windowDays)

            val totalKills = champGames.sumOf { it.kills }
            val totalDeaths = champGames.sumOf { it.deaths }
            val totalAssists = champGames.sumOf { it.assists }
            val avgKda = (totalKills + totalAssists).toDouble() / max(1, totalDeaths)

            val mostFrequentRole: Role? =
                champGames
                    .groupingBy { it.role }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key

            results.add(
                SoloQChampionStats(
                    championId = championId,
                    gamesPlayed = gamesPlayed,
                    wins = wins,
                    losses = losses,
                    winRate = winRate,
                    pickShare = pickShare,
                    gamesPerDay = gamesPerDay,
                    role = mostFrequentRole,
                    avgKda = avgKda,
                ),
            )
        }

        results.sortWith(compareByDescending<SoloQChampionStats> { it.gamesPlayed }.thenByDescending { it.winRate })
        return results
    }
}

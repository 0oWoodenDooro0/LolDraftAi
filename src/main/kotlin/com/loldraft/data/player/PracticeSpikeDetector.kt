package com.loldraft.data.player

import java.util.concurrent.TimeUnit
import kotlin.math.max

data class SpikeDetectorConfig(
    val recentDays: Int = 3,
    val baselineDays: Int = 21,
    val minRecentGamesForSpike: Int = 4,
    val spikeFrequencyMultiplierThreshold: Double = 2.5,
    val offMetaProGamesThreshold: Int = 2,
    val highWinRateThreshold: Double = 0.60,
    val highSeverityRecentGames: Int = 6,
    val syntheticBaselineGames: Double = 0.5,
    val syntheticBaselineRate: Double? = null,
)

class PracticeSpikeDetector(
    private val config: SpikeDetectorConfig = SpikeDetectorConfig(),
    private val tracker: SoloQTracker = SoloQTracker(),
) {
    fun detectSpikes(
        soloQGames: List<SoloQGame>,
        careerStats: PlayerCareerStats? = null,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): List<SpikeAlert> {
        val recentGames = tracker.getRecentGames(soloQGames, config.recentDays, referenceTimeMs)
        if (recentGames.isEmpty()) return emptyList()

        val baselineCutoffMs = referenceTimeMs - TimeUnit.DAYS.toMillis(config.baselineDays.toLong())
        val recentCutoffMs = referenceTimeMs - TimeUnit.DAYS.toMillis(config.recentDays.toLong())
        val baselineGames = soloQGames.filter { it.timestampEpochMs in baselineCutoffMs until recentCutoffMs }
        val baselineDaysEffective = max(1, config.baselineDays - config.recentDays)

        val recentByChampion = recentGames.groupBy { it.championId }
        val alerts = mutableListOf<SpikeAlert>()

        for ((championId, champRecentGames) in recentByChampion) {
            val recentCount = champRecentGames.size
            if (recentCount < config.minRecentGamesForSpike) continue

            val recentWins = champRecentGames.count { it.win }
            val recentWinRate = recentWins.toDouble() / recentCount
            val recentDailyRate = recentCount.toDouble() / config.recentDays

            val champBaselineGames = baselineGames.filter { it.championId == championId }
            val baselineCount = champBaselineGames.size
            val baselineDailyRate = baselineCount.toDouble() / baselineDaysEffective

            val effectiveSyntheticDailyRate =
                config.syntheticBaselineRate ?: (config.syntheticBaselineGames / baselineDaysEffective)

            val frequencyMultiplier =
                if (baselineDailyRate > 0.0) {
                    recentDailyRate / baselineDailyRate
                } else {
                    // Effective synthetic baseline for zero baseline games
                    recentDailyRate / effectiveSyntheticDailyRate
                }

            val careerRecord = careerStats?.championRecords?.get(championId)
            val careerProGames = careerRecord?.gamesPlayed ?: 0
            val isOffMeta = careerProGames <= config.offMetaProGamesThreshold && baselineCount <= 2

            val isSpike = (frequencyMultiplier >= config.spikeFrequencyMultiplierThreshold) || isOffMeta
            if (!isSpike) continue

            val type =
                when {
                    isOffMeta -> SpikeAlertType.OFF_META_SURGE
                    recentWinRate >= config.highWinRateThreshold && recentCount >= config.highSeverityRecentGames ->
                        SpikeAlertType.POCKET_PREPARATION
                    else -> SpikeAlertType.PRACTICE_SPIKE
                }

            val severity =
                when {
                    recentCount >= config.highSeverityRecentGames && recentWinRate >= config.highWinRateThreshold ->
                        SpikeAlertSeverity.HIGH
                    isOffMeta && recentCount >= 5 ->
                        SpikeAlertSeverity.HIGH
                    frequencyMultiplier >= 3.5 || recentWinRate >= 0.70 ->
                        SpikeAlertSeverity.MEDIUM
                    else ->
                        SpikeAlertSeverity.LOW
                }

            val winRatePct = String.format("%.1f", recentWinRate * 100.0)
            val multFormatted = String.format("%.1f", frequencyMultiplier)
            val reason =
                "Sudden surge of $recentCount games in ${config.recentDays} days ($winRatePct% WR) on $championId. " +
                    "Career pro games: $careerProGames, baseline: $baselineCount games in $baselineDaysEffective days " +
                    "(${multFormatted}x frequency surge)."

            alerts.add(
                SpikeAlert(
                    championId = championId,
                    severity = severity,
                    type = type,
                    recentDays = config.recentDays,
                    recentGamesCount = recentCount,
                    recentWinRate = recentWinRate,
                    baselineGamesCount = baselineCount,
                    baselineDays = baselineDaysEffective,
                    frequencyMultiplier = frequencyMultiplier,
                    careerProGames = careerProGames,
                    reason = reason,
                ),
            )
        }

        alerts.sortWith(
            compareByDescending<SpikeAlert> { it.severity.ordinal }
                .thenByDescending { it.recentGamesCount }
                .thenByDescending { it.recentWinRate },
        )

        return alerts
    }
}

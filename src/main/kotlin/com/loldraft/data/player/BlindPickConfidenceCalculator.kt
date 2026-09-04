package com.loldraft.data.player

import kotlin.math.min

data class BlindPickConfidenceConfig(
    val proWeight: Double = 0.60,
    val historicalEarlyPickWeight: Double = 0.40,
    val minProGamesFullConfidence: Int = 15,
)

class BlindPickConfidenceCalculator(
    private val config: BlindPickConfidenceConfig = BlindPickConfidenceConfig(),
) {
    fun calculateConfidence(
        championId: String,
        careerRecord: ChampionCareerRecord?,
        historicalBlindOrEarlyPicks: List<Boolean> = emptyList(),
    ): BlindPickConfidence {
        val hasPro = careerRecord != null && careerRecord.gamesPlayed > 0
        val hasEarly = historicalBlindOrEarlyPicks.isNotEmpty()

        if (!hasPro && !hasEarly) {
            return BlindPickConfidence(
                championId = championId,
                confidenceScore = 0.0,
                rating = ConfidenceRating.D,
                proMasteryScore = 0.0,
                blindPickHistoricalScore = 0.0,
                reasoning = listOf("No pro career or historical early pick data available for $championId."),
            )
        }

        // 1. Pro Career Mastery Score
        val proScore =
            if (hasPro) {
                val games = careerRecord!!.gamesPlayed
                val winRate = careerRecord.winRate
                val volumeFactor = min(1.0, games.toDouble() / config.minProGamesFullConfidence)
                val baseScore = (winRate * 80.0 + 20.0 * volumeFactor)
                val bonus = if (games >= 20 && winRate >= 0.75) 5.0 else 0.0
                (baseScore * (0.6 + 0.4 * volumeFactor) + bonus).coerceIn(0.0, 100.0)
            } else {
                0.0
            }

        // 2. Historical Blind / Early Pick Record
        val earlyScore =
            if (hasEarly) {
                val totalEarly = historicalBlindOrEarlyPicks.size
                val earlyWins = historicalBlindOrEarlyPicks.count { it }
                val earlyWinRate = earlyWins.toDouble() / totalEarly
                val volumeFactor = min(1.0, totalEarly.toDouble() / 5.0)
                val bonus = if (totalEarly >= 4 && earlyWinRate >= 0.75) 10.0 else 0.0
                ((earlyWinRate * 80.0 + 20.0 * volumeFactor) + bonus).coerceIn(0.0, 100.0)
            } else {
                0.0
            }

        // Weight normalization across available dimensions
        var totalWeight = 0.0
        var weightedSum = 0.0

        if (hasPro) {
            totalWeight += config.proWeight
            weightedSum += proScore * config.proWeight
        }
        if (hasEarly) {
            totalWeight += config.historicalEarlyPickWeight
            weightedSum += earlyScore * config.historicalEarlyPickWeight
        }

        val finalScore = if (totalWeight > 0.0) (weightedSum / totalWeight).coerceIn(0.0, 100.0) else 0.0

        val rating =
            when {
                finalScore >= 85.0 -> ConfidenceRating.S
                finalScore >= 70.0 -> ConfidenceRating.A
                finalScore >= 55.0 -> ConfidenceRating.B
                finalScore >= 40.0 -> ConfidenceRating.C
                else -> ConfidenceRating.D
            }

        val reasoning = mutableListOf<String>()
        if (hasPro) {
            val wrPct = String.format("%.1f", careerRecord!!.winRate * 100.0)
            reasoning.add("Pro career: ${careerRecord.gamesPlayed} games with $wrPct% WR.")
        }
        if (hasEarly) {
            val wins = historicalBlindOrEarlyPicks.count { it }
            val total = historicalBlindOrEarlyPicks.size
            val wrPct = String.format("%.1f", (wins.toDouble() / total) * 100.0)
            reasoning.add("Historical early pick performance: $wins/$total wins ($wrPct%).")
        }
        reasoning.add("Confidence rating: $rating (Score: ${String.format("%.1f", finalScore)}).")

        return BlindPickConfidence(
            championId = championId,
            confidenceScore = finalScore,
            rating = rating,
            proMasteryScore = proScore,
            blindPickHistoricalScore = earlyScore,
            reasoning = reasoning,
        )
    }
}

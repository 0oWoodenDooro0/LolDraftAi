package com.loldraft.data.player

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import kotlin.math.max

data class CareerAnalyzerConfig(
    val minGamesForSignature: Int = 3,
    val signatureScoreWeightVolume: Double = 0.4,
    val signatureScoreWeightWinRate: Double = 0.6,
    val pocketMinWinRate: Double = 0.70,
    val pocketMinGames: Int = 3,
    val signatureMinGames: Int = 6,
    val signatureMinWinRate: Double = 0.60,
    val signatureScoreThreshold: Double = 50.0,
)

class PlayerCareerAnalyzer(
    private val config: CareerAnalyzerConfig = CareerAnalyzerConfig(),
) {
    fun analyzePlayer(
        playerId: String,
        games: List<Game>,
        playerRole: Role? = null,
    ): PlayerCareerStats {
        data class PickEvent(
            val championId: String,
            val role: Role?,
            val won: Boolean,
        )

        val pickEvents = mutableListOf<PickEvent>()

        for (game in games) {
            val turns = game.draftState.turns
            for (turn in turns) {
                if (turn.actionType == ActionType.PICK && turn.player.equals(playerId, ignoreCase = true)) {
                    if (playerRole == null || turn.role == null || turn.role == playerRole) {
                        val won = game.winner != null && game.winner == turn.side
                        pickEvents.add(
                            PickEvent(
                                championId = turn.championId,
                                role = turn.role,
                                won = won,
                            ),
                        )
                    }
                }
            }
        }

        if (pickEvents.isEmpty()) {
            return PlayerCareerStats(
                playerId = playerId,
                totalProGames = 0,
                totalWins = 0,
                winRate = 0.0,
                roleDistribution = emptyMap(),
                championRecords = emptyMap(),
                signaturePicks = emptyList(),
            )
        }

        val totalProGames = pickEvents.size
        val totalWins = pickEvents.count { it.won }
        val overallWinRate = totalWins.toDouble() / totalProGames

        val roleDistribution = mutableMapOf<Role, Int>()
        for (event in pickEvents) {
            if (event.role != null) {
                roleDistribution[event.role] = (roleDistribution[event.role] ?: 0) + 1
            }
        }

        val groupedByChampion = pickEvents.groupBy { it.championId }
        val championRecords = mutableMapOf<String, ChampionCareerRecord>()
        var maxChampionGames = 1

        for ((champId, events) in groupedByChampion) {
            val gamesPlayed = events.size
            if (gamesPlayed > maxChampionGames) {
                maxChampionGames = gamesPlayed
            }
            val wins = events.count { it.won }
            val losses = gamesPlayed - wins
            val winRate = wins.toDouble() / gamesPlayed
            val pickRate = gamesPlayed.toDouble() / totalProGames
            val mostFrequentRole =
                events
                    .mapNotNull { it.role }
                    .groupingBy { it }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.key

            championRecords[champId] =
                ChampionCareerRecord(
                    championId = champId,
                    gamesPlayed = gamesPlayed,
                    wins = wins,
                    losses = losses,
                    winRate = winRate,
                    pickRate = pickRate,
                    role = mostFrequentRole,
                )
        }

        val signaturePicks = mutableListOf<SignaturePick>()
        for (record in championRecords.values) {
            if (record.gamesPlayed < config.minGamesForSignature) continue

            val volumeRatio = record.gamesPlayed.toDouble() / max(1, maxChampionGames)
            val score =
                (record.winRate * 100.0 * config.signatureScoreWeightWinRate) +
                    (volumeRatio * 100.0 * config.signatureScoreWeightVolume)

            val tier =
                when {
                    record.gamesPlayed >= config.signatureMinGames && record.winRate >= config.signatureMinWinRate ->
                        SignatureTier.SIGNATURE
                    record.winRate >= config.pocketMinWinRate && record.gamesPlayed < config.signatureMinGames ->
                        SignatureTier.POCKET
                    else ->
                        SignatureTier.COMFORT
                }

            signaturePicks.add(
                SignaturePick(
                    championId = record.championId,
                    gamesPlayed = record.gamesPlayed,
                    wins = record.wins,
                    winRate = record.winRate,
                    pickRate = record.pickRate,
                    signatureScore = score,
                    tier = tier,
                    role = record.role,
                ),
            )
        }

        signaturePicks.sortByDescending { it.signatureScore }

        return PlayerCareerStats(
            playerId = playerId,
            totalProGames = totalProGames,
            totalWins = totalWins,
            winRate = overallWinRate,
            roleDistribution = roleDistribution,
            championRecords = championRecords,
            signaturePicks = signaturePicks,
        )
    }
}

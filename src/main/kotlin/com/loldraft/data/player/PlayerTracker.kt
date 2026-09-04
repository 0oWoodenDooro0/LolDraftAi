package com.loldraft.data.player

import com.loldraft.data.lake.DataLakeStorage
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side

class PlayerTracker(
    val careerAnalyzer: PlayerCareerAnalyzer = PlayerCareerAnalyzer(),
    val confidenceCalculator: BlindPickConfidenceCalculator = BlindPickConfidenceCalculator(),
) {
    fun generateDossier(
        playerId: String,
        proGames: List<Game>,
        playerRole: Role? = null,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): PlayerIntelligenceDossier {
        val careerStats = careerAnalyzer.analyzePlayer(playerId, proGames, playerRole)

        val relevantChampions = mutableSetOf<String>()
        relevantChampions.addAll(careerStats.championRecords.keys)

        val blindPickConfidences = mutableMapOf<String, BlindPickConfidence>()

        for (champId in relevantChampions) {
            val careerRecord = careerStats.championRecords[champId]

            // Extract historical blind or early picks (Phase 1 turns: B1 or early picks)
            val earlyPickOutcomes = mutableListOf<Boolean>()
            for (game in proGames) {
                if (game.draftState.turns.isNotEmpty()) {
                    for (turn in game.draftState.turns) {
                        if (turn.actionType == ActionType.PICK &&
                            turn.championId.equals(champId, ignoreCase = true) &&
                            turn.player.equals(playerId, ignoreCase = true)
                        ) {
                            // Early/Blind pick defined as pick in Phase 1 (turns 7 to 11)
                            if (turn.turnNumber <= 11) {
                                val won = game.winner != null && game.winner == turn.side
                                earlyPickOutcomes.add(won)
                            }
                        }
                    }
                } else {
                    for (pick in game.draftState.bluePicks) {
                        if (pick.championId.equals(champId, ignoreCase = true) &&
                            pick.playerId.equals(playerId, ignoreCase = true)
                        ) {
                            val won = game.winner != null && game.winner == Side.BLUE
                            earlyPickOutcomes.add(won)
                        }
                    }
                    for (pick in game.draftState.redPicks) {
                        if (pick.championId.equals(champId, ignoreCase = true) &&
                            pick.playerId.equals(playerId, ignoreCase = true)
                        ) {
                            val won = game.winner != null && game.winner == Side.RED
                            earlyPickOutcomes.add(won)
                        }
                    }
                }
            }

            val confidence =
                confidenceCalculator.calculateConfidence(
                    championId = champId,
                    careerRecord = careerRecord,
                    historicalBlindOrEarlyPicks = earlyPickOutcomes,
                )
            blindPickConfidences[champId] = confidence
        }

        return PlayerIntelligenceDossier(
            playerId = playerId,
            careerStats = careerStats,
            blindPickConfidences = blindPickConfidences,
        )
    }

    fun generateDossierFromStorage(
        playerId: String,
        storage: DataLakeStorage,
        playerRole: Role? = null,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): PlayerIntelligenceDossier {
        val allGames = storage.getAllGames()
        return generateDossier(playerId, allGames, playerRole, referenceTimeMs)
    }
}

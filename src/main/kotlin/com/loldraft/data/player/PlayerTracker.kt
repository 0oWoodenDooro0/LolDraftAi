package com.loldraft.data.player

import com.loldraft.data.lake.DataLakeStorage
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Game
import com.loldraft.data.models.Role

class PlayerTracker(
    val accountRegistry: PlayerAccountRegistry = PlayerAccountRegistry(),
    val careerAnalyzer: PlayerCareerAnalyzer = PlayerCareerAnalyzer(),
    val soloQTracker: SoloQTracker = SoloQTracker(),
    val spikeDetector: PracticeSpikeDetector = PracticeSpikeDetector(),
    val confidenceCalculator: BlindPickConfidenceCalculator = BlindPickConfidenceCalculator(),
) {
    fun generateDossier(
        playerId: String,
        proGames: List<Game>,
        soloQGames: List<SoloQGame>,
        playerRole: Role? = null,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): PlayerIntelligenceDossier {
        val careerStats = careerAnalyzer.analyzePlayer(playerId, proGames, playerRole)
        val linkedAccounts = accountRegistry.getAccountsForPlayer(playerId)
        val recent3d = soloQTracker.computeChampionStats(soloQGames, 3, referenceTimeMs)
        val recent7d = soloQTracker.computeChampionStats(soloQGames, 7, referenceTimeMs)
        val alerts = spikeDetector.detectSpikes(soloQGames, careerStats, referenceTimeMs)

        val relevantChampions = mutableSetOf<String>()
        relevantChampions.addAll(careerStats.championRecords.keys)
        recent3d.forEach { relevantChampions.add(it.championId) }
        recent7d.forEach { relevantChampions.add(it.championId) }
        alerts.forEach { relevantChampions.add(it.championId) }

        val blindPickConfidences = mutableMapOf<String, BlindPickConfidence>()

        for (champId in relevantChampions) {
            val careerRecord = careerStats.championRecords[champId]
            val soloQRecord = recent7d.find { it.championId == champId } ?: recent3d.find { it.championId == champId }

            // Extract historical blind or early picks (Phase 1 turns: B1 or early picks)
            val earlyPickOutcomes = mutableListOf<Boolean>()
            for (game in proGames) {
                for (turn in game.draftState.turns) {
                    if (turn.actionType == ActionType.PICK &&
                        turn.championId == champId &&
                        turn.player.equals(playerId, ignoreCase = true)
                    ) {
                        // Early/Blind pick defined as pick in Phase 1 (turns 7 to 11)
                        if (turn.turnNumber <= 11) {
                            val won = game.winner != null && game.winner == turn.side
                            earlyPickOutcomes.add(won)
                        }
                    }
                }
            }

            val confidence =
                confidenceCalculator.calculateConfidence(
                    championId = champId,
                    careerRecord = careerRecord,
                    recentSoloQStats = soloQRecord,
                    historicalBlindOrEarlyPicks = earlyPickOutcomes,
                )
            blindPickConfidences[champId] = confidence
        }

        return PlayerIntelligenceDossier(
            playerId = playerId,
            careerStats = careerStats,
            linkedAccounts = linkedAccounts,
            recentSoloQ3Days = recent3d,
            recentSoloQ7Days = recent7d,
            activeSpikeAlerts = alerts,
            blindPickConfidences = blindPickConfidences,
        )
    }

    fun generateDossierFromStorage(
        playerId: String,
        storage: DataLakeStorage,
        soloQGames: List<SoloQGame>,
        playerRole: Role? = null,
        referenceTimeMs: Long = System.currentTimeMillis(),
    ): PlayerIntelligenceDossier {
        val allGames = storage.getAllGames()
        return generateDossier(playerId, allGames, soloQGames, playerRole, referenceTimeMs)
    }
}

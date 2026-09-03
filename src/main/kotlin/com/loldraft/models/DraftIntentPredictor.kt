package com.loldraft.models

import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.DamageType
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.player.SignatureTier
import com.loldraft.data.style.TeamTacticalProfile
import kotlin.math.exp
import kotlin.math.round

class DraftIntentPredictor(
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val flexAnalyzer: FlexPickAnalyzer = FlexPickAnalyzer(tagRegistry),
    val flawDetector: CompositionFlawDetector = CompositionFlawDetector(tagRegistry),
) {
    fun predictNextAction(
        draftState: DraftState,
        patchMeta: PatchMetaMatrix? = null,
        teamProfile: TeamTacticalProfile? = null,
        playerStatsByRole: Map<Role, PlayerCareerStats>? = null,
        topN: Int = 3,
    ): IntentPredictionResult {
        val turnNumber = draftState.currentTurnNumber.coerceIn(1, 20)
        val turnSpec = DraftTurnSpec.forTurn(turnNumber)
        return predictForTurnSpec(
            turnSpec = turnSpec,
            draftState = draftState,
            patchMeta = patchMeta,
            teamProfile = teamProfile,
            playerStatsByRole = playerStatsByRole,
            topN = topN,
        )
    }

    fun predictIntentForSide(
        draftState: DraftState,
        actingSide: Side,
        patchMeta: PatchMetaMatrix? = null,
        teamProfile: TeamTacticalProfile? = null,
        playerStatsByRole: Map<Role, PlayerCareerStats>? = null,
        topN: Int = 3,
    ): IntentPredictionResult {
        val turnNumber = draftState.currentTurnNumber.coerceIn(1, 20)
        val defaultSpec = DraftTurnSpec.forTurn(turnNumber)
        val turnSpec =
            if (defaultSpec.side == actingSide) {
                defaultSpec
            } else {
                DraftTurnSpec(
                    turnNumber = turnNumber,
                    phase = defaultSpec.phase,
                    side = actingSide,
                    actionType = defaultSpec.actionType,
                )
            }
        return predictForTurnSpec(
            turnSpec = turnSpec,
            draftState = draftState,
            patchMeta = patchMeta,
            teamProfile = teamProfile,
            playerStatsByRole = playerStatsByRole,
            topN = topN,
        )
    }

    private fun predictForTurnSpec(
        turnSpec: DraftTurnSpec,
        draftState: DraftState,
        patchMeta: PatchMetaMatrix?,
        teamProfile: TeamTacticalProfile?,
        playerStatsByRole: Map<Role, PlayerCareerStats>?,
        topN: Int,
    ): IntentPredictionResult {
        val unavailable = draftState.allSelectedChampions.map { ChampionNormalizer.toSlug(it) }.toSet()

        // Candidate pool
        val candidateNames = mutableSetOf<String>()
        tagRegistry.getAllProfiles().forEach { candidateNames.add(it.displayName) }
        patchMeta?.championStats?.values?.forEach { candidateNames.add(it.championId) }

        val candidates =
            candidateNames.filterNot {
                ChampionNormalizer.toSlug(it) in unavailable
            }

        val actingSide = turnSpec.side
        val isBan = turnSpec.actionType == ActionType.BAN
        val teamPicks = if (actingSide == Side.BLUE) draftState.bluePicks else draftState.redPicks
        val enemyPicks = if (actingSide == Side.BLUE) draftState.redPicks else draftState.bluePicks

        val lockedRoles = teamPicks.mapNotNull { it.role }.toSet()
        val vacantRoles = Role.entries.filterNot { it in lockedRoles }.toSet()

        val damageSplit = tagRegistry.calculateTeamDamageSplit(teamPicks.map { it.championId })
        val needsAp = teamPicks.size >= 2 && damageSplit.physicalRatio >= 0.75
        val needsAd = teamPicks.size >= 2 && damageSplit.magicRatio >= 0.75

        val scoredList = mutableListOf<ChampionIntentCandidate>()

        for (champ in candidates) {
            val profile = tagRegistry.getProfile(champ)
            val metaStats = patchMeta?.getStats(champ)

            // 1. Meta score
            var metaScore = 0.3
            if (metaStats != null) {
                val tierScore =
                    when (metaStats.tier) {
                        MetaTier.T0 -> 1.0
                        MetaTier.T1 -> 0.8
                        MetaTier.T2 -> 0.6
                        MetaTier.T3 -> 0.4
                        MetaTier.T4 -> 0.2
                    }
                val presenceScore = metaStats.presenceRate.coerceIn(0.0, 1.0)
                val winRateScore = ((metaStats.winRate - 0.50) * 2.0).coerceIn(-0.5, 0.5)
                metaScore = (tierScore * 0.5) + (presenceScore * 0.4) + (winRateScore * 0.1)
            } else if (profile != null) {
                metaScore = 0.5
            }

            // 2. Player mastery score
            var playerMasteryScore = 0.0
            var matchedSignature: String? = null
            if (playerStatsByRole != null) {
                for ((role, pStats) in playerStatsByRole) {
                    val sig = pStats.signaturePicks.firstOrNull { it.championId.equals(champ, ignoreCase = true) }
                    if (sig != null) {
                        val tierBonus =
                            when (sig.tier) {
                                SignatureTier.SIGNATURE -> 0.95
                                SignatureTier.COMFORT -> 0.80
                                SignatureTier.POCKET -> 0.65
                            }
                        if (tierBonus > playerMasteryScore) {
                            playerMasteryScore = tierBonus
                            matchedSignature = sig.tier.name
                        }
                    }
                }
            }

            // 3. Composition fit & role gap score
            var compositionFitScore = 0.0
            val flexAnalysis = flexAnalyzer.analyzeChampion(champ, patchMeta, lockedRoles)
            val bestRole = flexAnalysis.primaryRole

            if (!isBan) {
                // Role gap check
                val canFillVacant = flexAnalysis.roleProbabilities.any { it.key in vacantRoles && it.value >= 0.20 }
                if (canFillVacant) {
                    compositionFitScore += 0.45
                } else if (lockedRoles.isNotEmpty() && flexAnalysis.roleProbabilities[bestRole] ?: 0.0 >= 0.70 && bestRole in lockedRoles) {
                    compositionFitScore -= 0.40 // redundant role penalty
                }

                // Damage type gap check
                val isAp =
                    profile?.damageProfile?.primaryType == DamageType.MAGIC ||
                        (profile?.damageProfile?.magicRatio ?: 0.0) >= 0.65
                val isAd =
                    profile?.damageProfile?.primaryType == DamageType.PHYSICAL ||
                        (profile?.damageProfile?.physicalRatio ?: 0.0) >= 0.65

                if (needsAp && isAp) {
                    compositionFitScore += 0.45
                }
                if (needsAd && isAd) {
                    compositionFitScore += 0.35
                }
            } else {
                // Ban phase targets high presence or opponent signature
                if (metaStats?.tier == MetaTier.T0 || (metaStats?.presenceRate ?: 0.0) >= 0.70) {
                    compositionFitScore += 0.40
                }
            }
            compositionFitScore = compositionFitScore.coerceIn(-1.0, 1.0)

            // 4. Counter / denial score
            var counterDenialScore = 0.0
            if (patchMeta != null && enemyPicks.isNotEmpty()) {
                var totalCounter = 0.0
                for (enemy in enemyPicks) {
                    val matchup = patchMeta.getMatchup(champ, enemy.championId)
                    if (matchup != null && matchup.counterScore > 50.0) {
                        totalCounter += (matchup.counterScore - 50.0) / 50.0
                    }
                }
                counterDenialScore = (totalCounter / enemyPicks.size).coerceIn(0.0, 1.0)
            }

            // Total composite intent score
            val totalIntentScore =
                if (isBan) {
                    (metaScore * 0.50) + (playerMasteryScore * 0.35) + (compositionFitScore * 0.15)
                } else {
                    (metaScore * 0.35) + (playerMasteryScore * 0.30) + (compositionFitScore * 0.25) + (counterDenialScore * 0.10)
                }

            // Construct rationale
            val reasons = mutableListOf<String>()
            if (metaStats?.tier == MetaTier.T0) {
                reasons.add("T0 meta priority (${(metaStats.presenceRate * 100).toInt()}% presence)")
            } else if (metaStats?.tier == MetaTier.T1) {
                reasons.add("T1 meta pick")
            }
            if (matchedSignature != null) reasons.add("$matchedSignature pick for player")
            if (needsAp && (profile?.damageProfile?.magicRatio ?: 0.0) >= 0.65) reasons.add("Fills critical AP damage deficit")
            if (vacantRoles.isNotEmpty() && bestRole in vacantRoles) reasons.add("Fills vacant $bestRole lane")
            if (counterDenialScore > 0.3) reasons.add("Counters enemy composition")
            val rationale = if (reasons.isNotEmpty()) reasons.joinToString("; ") else "Standard draft candidate"

            scoredList.add(
                ChampionIntentCandidate(
                    championId = profile?.displayName ?: champ,
                    probability = 0.0, // calculated after sorting Top N
                    intentScore = roundToFourDecimals(totalIntentScore),
                    predictedRole = bestRole,
                    metaScore = roundToFourDecimals(metaScore),
                    playerMasteryScore = roundToFourDecimals(playerMasteryScore),
                    compositionFitScore = roundToFourDecimals(compositionFitScore),
                    counterDenialScore = roundToFourDecimals(counterDenialScore),
                    rationale = rationale,
                ),
            )
        }

        val topCandidates =
            scoredList
                .sortedByDescending { it.intentScore }
                .take(topN)

        // Calibrate Softmax probabilities over Top N
        val probabilities = calculateSoftmax(topCandidates.map { it.intentScore })
        val calibrated =
            topCandidates.mapIndexed { index, candidate ->
                candidate.copy(probability = probabilities[index])
            }

        return IntentPredictionResult(
            turnSpec = turnSpec,
            actingSide = actingSide,
            actionType = turnSpec.actionType,
            predictions = calibrated,
        )
    }

    private fun calculateSoftmax(scores: List<Double>): List<Double> {
        if (scores.isEmpty()) return emptyList()
        val temperature = 0.5
        val maxScore = scores.maxOrNull() ?: 0.0
        val exps = scores.map { exp((it - maxScore) / temperature) }
        val sumExps = exps.sum()
        if (sumExps == 0.0) return List(scores.size) { 1.0 / scores.size }
        val raw = exps.map { roundToFourDecimals(it / sumExps) }

        // Normalize sum to 1.0 precisely
        val sum = raw.sum()
        val diff = 1.0 - sum
        val result = raw.toMutableList()
        result[0] = roundToFourDecimals(result[0] + diff)
        return result
    }

    private fun roundToFourDecimals(value: Double): Double = round(value * 10000.0) / 10000.0
}

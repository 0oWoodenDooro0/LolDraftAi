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
import com.loldraft.data.player.ProPlayerDetailedProfile
import com.loldraft.data.player.SignatureTier
import com.loldraft.data.player.SoloQChampionStats
import com.loldraft.data.player.SpikeAlert
import com.loldraft.data.player.SpikeAlertSeverity
import com.loldraft.data.style.TeamTacticalProfile
import java.util.Locale
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
        playerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
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
            playerProfilesByRole = playerProfilesByRole,
            topN = topN,
        )
    }

    fun predictIntentForSide(
        draftState: DraftState,
        actingSide: Side,
        patchMeta: PatchMetaMatrix? = null,
        teamProfile: TeamTacticalProfile? = null,
        playerStatsByRole: Map<Role, PlayerCareerStats>? = null,
        playerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
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
            playerProfilesByRole = playerProfilesByRole,
            topN = topN,
        )
    }

    private fun predictForTurnSpec(
        turnSpec: DraftTurnSpec,
        draftState: DraftState,
        patchMeta: PatchMetaMatrix?,
        teamProfile: TeamTacticalProfile?,
        playerStatsByRole: Map<Role, PlayerCareerStats>?,
        playerProfilesByRole: Map<Role, ProPlayerDetailedProfile>?,
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

        val effectiveCareerStats = playerStatsByRole ?: playerProfilesByRole?.mapValues { it.value.careerStats }
        val hasDetailedProfiles = playerProfilesByRole != null && playerProfilesByRole.isNotEmpty()

        val scoredList = mutableListOf<ChampionIntentCandidate>()

        for (champ in candidates) {
            val profile = tagRegistry.getProfile(champ)
            val metaStats = patchMeta?.getStats(champ)

            val flexAnalysis = flexAnalyzer.analyzeChampion(champ, patchMeta, lockedRoles)
            val primaryRole = flexAnalysis.primaryRole

            // Target role & player resolution
            val targetRole =
                if (!isBan) {
                    val candidateVacantRoles = vacantRoles.filter { role -> (flexAnalysis.roleProbabilities[role] ?: 0.0) >= 0.15 }
                    when {
                        primaryRole in candidateVacantRoles -> primaryRole
                        candidateVacantRoles.isNotEmpty() ->
                            candidateVacantRoles.maxByOrNull { flexAnalysis.roleProbabilities[it] ?: 0.0 } ?: primaryRole
                        else -> primaryRole
                    }
                } else {
                    primaryRole
                }

            val targetProfile = playerProfilesByRole?.get(targetRole)
            val targetPlayerName = targetProfile?.playerId
            val targetCareerStats = targetProfile?.careerStats ?: effectiveCareerStats?.get(targetRole)

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

            // 2. Player career mastery score
            var playerMasteryScore = 0.0
            var matchedSignature: String? = null
            if (targetCareerStats != null) {
                val sig = targetCareerStats.signaturePicks.firstOrNull { it.championId.equals(champ, ignoreCase = true) }
                if (sig != null) {
                    val tierBonus =
                        when (sig.tier) {
                            SignatureTier.SIGNATURE -> 0.95
                            SignatureTier.COMFORT -> 0.80
                            SignatureTier.POCKET -> 0.70
                        }
                    playerMasteryScore = tierBonus
                    matchedSignature = sig.tier.name
                } else {
                    val record = targetCareerStats.championRecords.values.firstOrNull { it.championId.equals(champ, ignoreCase = true) }
                    if (record != null && record.gamesPlayed > 0) {
                        playerMasteryScore =
                            (record.winRate * 0.40 + (record.gamesPlayed / 20.0).coerceAtMost(1.0) * 0.30).coerceIn(0.20, 0.65)
                    }
                }
            }

            // Fallback: search all roles if not matched to target role
            if (playerMasteryScore == 0.0 && effectiveCareerStats != null) {
                for ((_, pStats) in effectiveCareerStats) {
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

            // 3. SoloQ practice & spike alert score
            var soloQScore = 0.0
            var matchedSpikeAlert: SpikeAlert? = null
            var matchedSoloQ3d: SoloQChampionStats? = null
            var matchedSoloQ7d: SoloQChampionStats? = null

            if (targetProfile != null) {
                matchedSpikeAlert = targetProfile.activeSpikeAlerts.firstOrNull { it.championId.equals(champ, ignoreCase = true) }
                matchedSoloQ3d = targetProfile.recentSoloQ3Days.firstOrNull { it.championId.equals(champ, ignoreCase = true) }
                matchedSoloQ7d = targetProfile.recentSoloQ7Days.firstOrNull { it.championId.equals(champ, ignoreCase = true) }

                if (matchedSpikeAlert != null) {
                    soloQScore =
                        when (matchedSpikeAlert.severity) {
                            SpikeAlertSeverity.HIGH -> (0.90 + (matchedSpikeAlert.recentWinRate * 0.10)).coerceIn(0.90, 1.0)
                            SpikeAlertSeverity.MEDIUM -> (0.80 + (matchedSpikeAlert.recentWinRate * 0.10)).coerceIn(0.80, 0.90)
                            SpikeAlertSeverity.LOW -> (0.70 + (matchedSpikeAlert.recentWinRate * 0.10)).coerceIn(0.70, 0.80)
                        }
                } else if (matchedSoloQ3d != null && matchedSoloQ3d.gamesPlayed > 0) {
                    val volume = (matchedSoloQ3d.gamesPlayed / 8.0).coerceAtMost(1.0)
                    val wr = matchedSoloQ3d.winRate.coerceIn(0.0, 1.0)
                    soloQScore = ((volume * 0.5 + wr * 0.5) * 0.80).coerceIn(0.20, 0.80)
                } else if (matchedSoloQ7d != null && matchedSoloQ7d.gamesPlayed > 0) {
                    val volume = (matchedSoloQ7d.gamesPlayed / 15.0).coerceAtMost(1.0)
                    val wr = matchedSoloQ7d.winRate.coerceIn(0.0, 1.0)
                    soloQScore = ((volume * 0.5 + wr * 0.5) * 0.65).coerceIn(0.20, 0.65)
                }
            }

            // 4. Composition fit & role gap score
            var compositionFitScore = 0.0
            if (!isBan) {
                val canFillVacant = flexAnalysis.roleProbabilities.any { it.key in vacantRoles && it.value >= 0.20 }
                if (canFillVacant) {
                    compositionFitScore += 0.45
                } else if (lockedRoles.isNotEmpty() &&
                    flexAnalysis.roleProbabilities[targetRole] ?: 0.0 >= 0.70 &&
                    targetRole in lockedRoles
                ) {
                    compositionFitScore -= 0.40 // redundant role penalty
                }

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
                if (metaStats?.tier == MetaTier.T0 || (metaStats?.presenceRate ?: 0.0) >= 0.70) {
                    compositionFitScore += 0.40
                }
            }
            compositionFitScore = compositionFitScore.coerceIn(-1.0, 1.0)

            // 5. Counter / denial score
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

            // 6. Total composite intent score
            val totalIntentScore =
                if (hasDetailedProfiles) {
                    if (isBan) {
                        (metaScore * 0.40) + (playerMasteryScore * 0.30) + (soloQScore * 0.20) + (compositionFitScore * 0.10)
                    } else {
                        (metaScore * 0.25) + (playerMasteryScore * 0.30) + (soloQScore * 0.30) +
                            (compositionFitScore * 0.10) + (counterDenialScore * 0.05)
                    }
                } else {
                    if (isBan) {
                        (metaScore * 0.50) + (playerMasteryScore * 0.35) + (compositionFitScore * 0.15)
                    } else {
                        (metaScore * 0.35) + (playerMasteryScore * 0.30) + (compositionFitScore * 0.25) + (counterDenialScore * 0.10)
                    }
                }

            // 7. Construct transparent rationale
            val reasons = mutableListOf<String>()
            val playerPrefix = if (targetPlayerName != null) "$targetPlayerName ($targetRole)" else null

            val masteryDesc =
                when {
                    matchedSignature != null -> {
                        val record =
                            targetCareerStats?.championRecords?.values?.firstOrNull {
                                it.championId.equals(
                                    champ,
                                    ignoreCase = true,
                                )
                            }
                        if (record != null) {
                            val wrPct = String.format(Locale.US, "%.1f", record.winRate * 100.0)
                            "$matchedSignature pick (${record.gamesPlayed}G, $wrPct% WR)"
                        } else {
                            "$matchedSignature pick for player"
                        }
                    }
                    playerMasteryScore > 0.3 -> {
                        val record =
                            targetCareerStats?.championRecords?.values?.firstOrNull {
                                it.championId.equals(
                                    champ,
                                    ignoreCase = true,
                                )
                            }
                        if (record != null) {
                            val wrPct = String.format(Locale.US, "%.1f", record.winRate * 100.0)
                            "Career experience (${record.gamesPlayed}G, $wrPct% WR)"
                        } else {
                            null
                        }
                    }
                    else -> null
                }

            val soloQDesc =
                when {
                    matchedSpikeAlert != null -> {
                        val wrPct = String.format(Locale.US, "%.1f", matchedSpikeAlert.recentWinRate * 100.0)
                        val multFormatted = String.format(Locale.US, "%.1f", matchedSpikeAlert.frequencyMultiplier)
                        "Recent SoloQ ${matchedSpikeAlert.type.name} (${matchedSpikeAlert.recentGamesCount}G in ${matchedSpikeAlert.recentDays}d, $wrPct% WR, ${multFormatted}x surge)"
                    }
                    matchedSoloQ3d != null && matchedSoloQ3d.gamesPlayed > 0 -> {
                        val wrPct = String.format(Locale.US, "%.1f", matchedSoloQ3d.winRate * 100.0)
                        "Active SoloQ practice (${matchedSoloQ3d.gamesPlayed}G in 3d, $wrPct% WR)"
                    }
                    matchedSoloQ7d != null && matchedSoloQ7d.gamesPlayed > 0 -> {
                        val wrPct = String.format(Locale.US, "%.1f", matchedSoloQ7d.winRate * 100.0)
                        "Active SoloQ practice (${matchedSoloQ7d.gamesPlayed}G in 7d, $wrPct% WR)"
                    }
                    else -> null
                }

            if (playerPrefix != null) {
                val playerDetails = listOfNotNull(masteryDesc, soloQDesc)
                if (playerDetails.isNotEmpty()) {
                    reasons.add("$playerPrefix: ${playerDetails.joinToString("; ")}")
                } else {
                    reasons.add(playerPrefix)
                }
            } else {
                if (masteryDesc != null) reasons.add(masteryDesc)
                if (soloQDesc != null) reasons.add(soloQDesc)
            }

            if (metaStats?.tier == MetaTier.T0) {
                reasons.add("T0 meta priority (${(metaStats.presenceRate * 100).toInt()}% presence)")
            } else if (metaStats?.tier == MetaTier.T1) {
                reasons.add("T1 meta pick")
            }

            if (needsAp && (profile?.damageProfile?.magicRatio ?: 0.0) >= 0.65) reasons.add("Fills critical AP damage deficit")
            if (vacantRoles.isNotEmpty() && targetRole in vacantRoles) reasons.add("Fills vacant $targetRole lane")
            if (counterDenialScore > 0.3) reasons.add("Counters enemy composition")

            val rationale = if (reasons.isNotEmpty()) reasons.joinToString("; ") else "Standard draft candidate"

            scoredList.add(
                ChampionIntentCandidate(
                    championId = profile?.displayName ?: champ,
                    probability = 0.0, // calculated after sorting Top N
                    intentScore = roundToFourDecimals(totalIntentScore),
                    predictedRole = targetRole,
                    metaScore = roundToFourDecimals(metaScore),
                    playerMasteryScore = roundToFourDecimals(playerMasteryScore),
                    soloQScore = roundToFourDecimals(soloQScore),
                    compositionFitScore = roundToFourDecimals(compositionFitScore),
                    counterDenialScore = roundToFourDecimals(counterDenialScore),
                    playerName = targetPlayerName,
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

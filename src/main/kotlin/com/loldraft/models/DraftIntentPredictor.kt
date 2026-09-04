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
import com.loldraft.data.player.PlayerIntelligenceDossier
import com.loldraft.data.meta.ChampionProfile
import com.loldraft.data.player.ChampionCareerRecord
import com.loldraft.data.player.ProPlayerDetailedProfile
import com.loldraft.data.player.SignaturePick
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
    private fun findChampionRecord(careerStats: PlayerCareerStats?, champ: String): ChampionCareerRecord? {
        if (careerStats == null) return null
        val targetSlug = ChampionNormalizer.toSlug(champ)
        return careerStats.championRecords.values.firstOrNull {
            ChampionNormalizer.toSlug(it.championId) == targetSlug
        } ?: careerStats.championRecords[champ]
    }

    private fun findSignaturePick(careerStats: PlayerCareerStats?, champ: String): SignaturePick? {
        if (careerStats == null) return null
        val targetSlug = ChampionNormalizer.toSlug(champ)
        return careerStats.signaturePicks.firstOrNull {
            ChampionNormalizer.toSlug(it.championId) == targetSlug
        }
    }

    private fun isViableForRole(
        champ: String,
        role: Role,
        profile: ChampionProfile?,
        flexAnalysis: FlexAnalysisResult,
        targetCareerStats: PlayerCareerStats?,
    ): Boolean {
        if (profile?.primaryRole == role) return true
        if (profile?.secondaryRoles?.contains(role) == true) return true
        if ((flexAnalysis.roleProbabilities[role] ?: 0.0) >= 0.15) return true
        val rec = findChampionRecord(targetCareerStats, champ)
        if (rec != null && rec.gamesPlayed >= 2 && (rec.role == null || rec.role == role)) return true
        return false
    }
    fun predictNextAction(
        draftState: DraftState,
        patchMeta: PatchMetaMatrix? = null,
        teamProfile: TeamTacticalProfile? = null,
        playerStatsByRole: Map<Role, PlayerCareerStats>? = null,
        playerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
        playerDossiersByRole: Map<Role, PlayerIntelligenceDossier>? = null,
        opponentPlayerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
        opponentBansAgainstTargetTeam: List<com.loldraft.data.style.OpponentBanRecord>? = null,
        targetTeamName: String? = null,
        firstPickSide: Side = Side.BLUE,
        topN: Int = 3,
    ): IntentPredictionResult {
        val effectiveProfiles =
            playerProfilesByRole
                ?: playerDossiersByRole?.mapValues { ProPlayerDetailedProfile.fromDossier(it.key, it.value) }
        val turnNumber = draftState.currentTurnNumber.coerceIn(1, 20)
        val turnSpec = DraftTurnSpec.forTurn(turnNumber, firstPickSide)
        return predictForTurnSpec(
            turnSpec = turnSpec,
            draftState = draftState,
            patchMeta = patchMeta,
            teamProfile = teamProfile,
            playerStatsByRole = playerStatsByRole,
            playerProfilesByRole = effectiveProfiles,
            opponentPlayerProfilesByRole = opponentPlayerProfilesByRole,
            opponentBansAgainstTargetTeam = opponentBansAgainstTargetTeam,
            targetTeamName = targetTeamName,
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
        playerDossiersByRole: Map<Role, PlayerIntelligenceDossier>? = null,
        opponentPlayerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
        opponentBansAgainstTargetTeam: List<com.loldraft.data.style.OpponentBanRecord>? = null,
        targetTeamName: String? = null,
        firstPickSide: Side = Side.BLUE,
        topN: Int = 3,
    ): IntentPredictionResult {
        val effectiveProfiles =
            playerProfilesByRole
                ?: playerDossiersByRole?.mapValues { ProPlayerDetailedProfile.fromDossier(it.key, it.value) }
        val turnNumber = draftState.currentTurnNumber.coerceIn(1, 20)
        val defaultSpec = DraftTurnSpec.forTurn(turnNumber, firstPickSide)
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
            playerProfilesByRole = effectiveProfiles,
            opponentPlayerProfilesByRole = opponentPlayerProfilesByRole,
            opponentBansAgainstTargetTeam = opponentBansAgainstTargetTeam,
            targetTeamName = targetTeamName,
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
        opponentPlayerProfilesByRole: Map<Role, ProPlayerDetailedProfile>? = null,
        opponentBansAgainstTargetTeam: List<com.loldraft.data.style.OpponentBanRecord>? = null,
        targetTeamName: String? = null,
        topN: Int,
    ): IntentPredictionResult {

        val unavailable = draftState.allSelectedChampions.map { ChampionNormalizer.toSlug(it) }.toSet()

        // Candidate pool (deduplicated by canonical champion slug)
        val candidateMap = mutableMapOf<String, String>()
        tagRegistry.getAllProfiles().forEach {
            val slug = ChampionNormalizer.toSlug(it.displayName)
            if (slug.isNotBlank()) {
                candidateMap[slug] = it.displayName
            }
        }
        patchMeta?.championStats?.values?.forEach {
            val norm = ChampionNormalizer.normalize(it.championId)
            val slug = ChampionNormalizer.toSlug(norm)
            if (slug.isNotBlank() && !candidateMap.containsKey(slug)) {
                candidateMap[slug] = norm
            }
        }

        val candidates = candidateMap.filterKeys { it !in unavailable }.values.toList()

        val actingSide = turnSpec.side
        val isBan = turnSpec.actionType == ActionType.BAN
        val teamPicks = if (actingSide == Side.BLUE) draftState.bluePicks else draftState.redPicks
        val enemyPicks = if (actingSide == Side.BLUE) draftState.redPicks else draftState.bluePicks

        val explicitlyLocked = teamPicks.mapNotNull { it.role }.toSet()
        val lockedRoles = explicitlyLocked.toMutableSet()
        for (p in teamPicks) {
            if (p.role == null) {
                val pRole = tagRegistry.getProfile(p.championId)?.primaryRole
                if (pRole != null) {
                    lockedRoles.add(pRole)
                }
            }
        }
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
            val primaryRole = profile?.primaryRole ?: flexAnalysis.primaryRole

            // 1. For pick turns, filter candidates that can play an available vacant role
            val viableVacantRoles =
                if (!isBan && vacantRoles.isNotEmpty()) {
                    vacantRoles.filter { r ->
                        val rStats = playerProfilesByRole?.get(r)?.careerStats ?: effectiveCareerStats?.get(r)
                        isViableForRole(champ, r, profile, flexAnalysis, rStats)
                    }
                } else {
                    emptyList()
                }

            // In pick turns, if the champion cannot play any available vacant role, skip immediately!
            if (!isBan && vacantRoles.isNotEmpty() && viableVacantRoles.isEmpty()) {
                continue
            }

            // Target role resolution:
            val targetRole =
                if (!isBan) {
                    when {
                        primaryRole in viableVacantRoles -> primaryRole
                        viableVacantRoles.isNotEmpty() ->
                            viableVacantRoles.maxByOrNull { flexAnalysis.roleProbabilities[it] ?: 0.0 } ?: viableVacantRoles.first()
                        else -> primaryRole
                    }
                } else {
                    primaryRole
                }

            val targetProfile = playerProfilesByRole?.get(targetRole)
            val targetCareerStats = targetProfile?.careerStats ?: effectiveCareerStats?.get(targetRole)
            val targetPlayerName = targetProfile?.playerId ?: targetCareerStats?.playerId

            // 1. Meta score with robust fallback to prevent identical scores
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
                val radarAvg = (profile.radar.laningStrength + profile.radar.lateGameScaling + profile.radar.engage) / 30.0
                val tagBonus = when {
                    profile.tags.contains(com.loldraft.data.meta.ChampionTag.HYPER_CARRY) -> 0.15
                    profile.tags.contains(com.loldraft.data.meta.ChampionTag.HARD_ENGAGE) -> 0.12
                    profile.tags.contains(com.loldraft.data.meta.ChampionTag.EARLY_BULLY) -> 0.10
                    else -> 0.05
                }
                metaScore = (radarAvg * 0.65 + tagBonus).coerceIn(0.20, 0.85)
            }

            // 2. Opponent respect ban score (what other teams ban against target team)
            val oppBanRecord = opponentBansAgainstTargetTeam?.firstOrNull {
                it.championId.equals(champ, ignoreCase = true) ||
                    ChampionNormalizer.normalize(it.championId).equals(ChampionNormalizer.normalize(champ), ignoreCase = true)
            }
            val opponentRespectBanScore = oppBanRecord?.banRate ?: 0.0

            // 3. Player career mastery score (for pick: acting team; for ban: target opponent)
            var playerMasteryScore = 0.0
            var matchedSignature: String? = null
            var matchedOpponentPlayer: String? = null

            if (isBan) {
                // In BAN turns, check if champion is in opponent's player mastery pool
                if (opponentPlayerProfilesByRole != null && opponentPlayerProfilesByRole.isNotEmpty()) {
                    for ((r, oProfile) in opponentPlayerProfilesByRole) {
                        val sig = findSignaturePick(oProfile.careerStats, champ)
                        if (sig != null) {
                            val bonus = when (sig.tier) {
                                SignatureTier.SIGNATURE -> 0.95
                                SignatureTier.COMFORT -> 0.80
                                SignatureTier.POCKET -> 0.70
                            }
                            if (bonus > playerMasteryScore) {
                                playerMasteryScore = bonus
                                matchedSignature = sig.tier.name
                                matchedOpponentPlayer = "${oProfile.playerId} ($r)"
                            }
                        } else {
                            val rec = findChampionRecord(oProfile.careerStats, champ)
                            if (rec != null && rec.gamesPlayed > 0) {
                                val score = (rec.winRate * 0.40 + (rec.gamesPlayed / 20.0).coerceAtMost(1.0) * 0.30).coerceIn(0.20, 0.65)
                                if (score > playerMasteryScore) {
                                    playerMasteryScore = score
                                    matchedOpponentPlayer = "${oProfile.playerId} ($r)"
                                }
                            }
                        }
                    }
                }
            } else {
                // In PICK turns, check ONLY acting team's player mastery for targetRole!
                if (targetCareerStats != null) {
                    val sig = findSignaturePick(targetCareerStats, champ)
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
                        val record = findChampionRecord(targetCareerStats, champ)
                        if (record != null && record.gamesPlayed > 0) {
                            playerMasteryScore =
                                (record.winRate * 0.40 + (record.gamesPlayed / 20.0).coerceAtMost(1.0) * 0.30).coerceIn(0.20, 0.65)
                        }
                    }
                }
            }

            // 4. SoloQ practice & spike alert score
            var soloQScore = 0.0
            var matchedSpikeAlert: SpikeAlert? = null
            var matchedSoloQ3d: SoloQChampionStats? = null
            var matchedSoloQ7d: SoloQChampionStats? = null

            if (!isBan && targetProfile != null) {
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

            // 5. Composition fit & role gap score
            var compositionFitScore = 0.0
            if (!isBan) {
                val canFillVacant = flexAnalysis.roleProbabilities.any { it.key in vacantRoles && it.value >= 0.20 }
                if (canFillVacant) {
                    compositionFitScore += 0.45
                } else if (lockedRoles.isNotEmpty() &&
                    flexAnalysis.roleProbabilities[targetRole] ?: 0.0 >= 0.70 &&
                    targetRole in lockedRoles
                ) {
                    compositionFitScore -= 0.40
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

            // 6. Counter / denial score
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

            val proRecord = if (!isBan) findChampionRecord(targetCareerStats, champ) else null
            val proGamesPlayed = proRecord?.gamesPlayed ?: 0
            val isProUnplayed = !isBan && targetCareerStats != null && targetCareerStats.totalProGames >= 5 && proGamesPlayed == 0
            val hasSoloQPractice = soloQScore > 0.3
            val isMetaMustPick = metaStats?.tier == MetaTier.T0 || (metaStats?.presenceRate ?: 0.0) >= 0.85

            val unplayedPenalty = when {
                isProUnplayed && !hasSoloQPractice && !isMetaMustPick -> 0.20
                isProUnplayed && !hasSoloQPractice && isMetaMustPick -> 0.08
                else -> 0.0
            }

            val hasTargetSoloQData = targetProfile != null && (
                targetProfile.activeSpikeAlerts.isNotEmpty() ||
                targetProfile.recentSoloQ3Days.isNotEmpty() ||
                targetProfile.recentSoloQ7Days.isNotEmpty()
            )

            // 7. Total composite intent score
            val totalIntentScore =
                if (isBan) {
                    val hasRespectBans = opponentBansAgainstTargetTeam != null && opponentBansAgainstTargetTeam.isNotEmpty()
                    val hasOppMastery = opponentPlayerProfilesByRole != null && opponentPlayerProfilesByRole.isNotEmpty()
                    when {
                        hasRespectBans && hasOppMastery ->
                            (opponentRespectBanScore * 0.35) + (playerMasteryScore * 0.30) + (metaScore * 0.25) + (compositionFitScore * 0.10)
                        hasRespectBans ->
                            (opponentRespectBanScore * 0.45) + (metaScore * 0.40) + (compositionFitScore * 0.15)
                        hasOppMastery ->
                            (playerMasteryScore * 0.45) + (metaScore * 0.40) + (compositionFitScore * 0.15)
                        else ->
                            (metaScore * 0.70) + (compositionFitScore * 0.30)
                    }
                } else {
                    val rawScore = when {
                        hasDetailedProfiles && hasTargetSoloQData ->
                            (metaScore * 0.25) + (playerMasteryScore * 0.30) + (soloQScore * 0.30) +
                                (compositionFitScore * 0.10) + (counterDenialScore * 0.05)
                        hasDetailedProfiles || targetCareerStats != null ->
                            (playerMasteryScore * 0.45) + (metaScore * 0.30) +
                                (compositionFitScore * 0.15) + (counterDenialScore * 0.10)
                        else ->
                            (metaScore * 0.45) + (compositionFitScore * 0.35) + (counterDenialScore * 0.20)
                    }
                    (rawScore - unplayedPenalty).coerceAtLeast(0.01)
                }

            // 8. Construct transparent rationale
            val reasons = mutableListOf<String>()

            if (isBan) {
                if (oppBanRecord != null && oppBanRecord.banCount > 0) {
                    val oppPct = String.format(Locale.US, "%.0f", oppBanRecord.banRate * 100.0)
                    val teamLabel = targetTeamName ?: "opponent"
                    reasons.add("Respect ban: other teams ban vs $teamLabel in $oppPct% of games (${oppBanRecord.banCount}/${oppBanRecord.totalGames}G)")
                }
                if (matchedOpponentPlayer != null && matchedSignature != null) {
                    reasons.add("Target ban vs $matchedOpponentPlayer: $matchedSignature pick")
                } else if (matchedOpponentPlayer != null) {
                    reasons.add("Target ban vs $matchedOpponentPlayer")
                }
                if (metaStats?.tier == MetaTier.T0) {
                    reasons.add("T0 meta ban priority (${(metaStats.presenceRate * 100).toInt()}% presence)")
                } else if (metaStats?.tier == MetaTier.T1) {
                    reasons.add("T1 meta ban")
                }
                if (reasons.isEmpty()) {
                    reasons.add("High priority meta ban")
                }
            } else {
                val playerPrefix = if (targetPlayerName != null) "$targetPlayerName ($targetRole)" else null
                val masteryDesc =
                    when {
                        matchedSignature != null && proRecord != null -> {
                            val wrPct = String.format(Locale.US, "%.1f", proRecord.winRate * 100.0)
                            "$matchedSignature pick (${proRecord.gamesPlayed}G, $wrPct% WR)"
                        }
                        matchedSignature != null -> {
                            "$matchedSignature pick"
                        }
                        proRecord != null && proRecord.gamesPlayed > 0 -> {
                            val wrPct = String.format(Locale.US, "%.1f", proRecord.winRate * 100.0)
                            "Career pick (${proRecord.gamesPlayed}G, $wrPct% WR)"
                        }
                        isProUnplayed -> {
                            "0 pro games on record"
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
            }

            val rationale = if (reasons.isNotEmpty()) reasons.joinToString("; ") else if (isBan) "Strategic ban candidate" else "Standard draft candidate"

            scoredList.add(
                ChampionIntentCandidate(
                    championId = profile?.displayName ?: champ,
                    probability = 0.0, // calculated after sorting Top N
                    intentScore = roundToFourDecimals(totalIntentScore),
                    predictedRole = if (isBan) null else targetRole,
                    metaScore = roundToFourDecimals(metaScore),
                    playerMasteryScore = roundToFourDecimals(playerMasteryScore),
                    soloQScore = roundToFourDecimals(soloQScore),
                    compositionFitScore = roundToFourDecimals(compositionFitScore),
                    counterDenialScore = roundToFourDecimals(counterDenialScore),
                    playerName = if (isBan) matchedOpponentPlayer else targetPlayerName,
                    rationale = rationale,
                ),
            )
        }

        val topCandidates =
            scoredList
                .sortedByDescending { it.intentScore }
                .distinctBy { ChampionNormalizer.toSlug(it.championId) }
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
        val temperature = 0.25
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


package com.loldraft.models

import com.loldraft.data.meta.ChampionProfile
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
import com.loldraft.data.player.ChampionCareerRecord
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.player.PlayerIntelligenceDossier
import com.loldraft.data.player.ProPlayerDetailedProfile
import com.loldraft.data.player.SignaturePick
import com.loldraft.data.player.SignatureTier
import com.loldraft.data.player.SoloQChampionStats
import com.loldraft.data.player.SpikeAlert
import com.loldraft.data.player.SpikeAlertSeverity
import com.loldraft.data.style.TeamTacticalProfile
import java.util.Locale
import kotlin.math.round

class DraftIntentPredictor(
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val flexAnalyzer: FlexPickAnalyzer = FlexPickAnalyzer(tagRegistry),
    val flawDetector: CompositionFlawDetector = CompositionFlawDetector(tagRegistry),
) {
    private fun findChampionRecord(
        careerStats: PlayerCareerStats?,
        champ: String,
    ): ChampionCareerRecord? {
        if (careerStats == null) return null
        val targetSlug = ChampionNormalizer.toSlug(champ)
        return careerStats.championRecords.values.firstOrNull {
            ChampionNormalizer.toSlug(it.championId) == targetSlug
        } ?: careerStats.championRecords[champ]
    }

    private fun findSignaturePick(
        careerStats: PlayerCareerStats?,
        champ: String,
    ): SignaturePick? {
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
            } else {
                metaScore = 0.30
            }

            // 2. Opponent respect ban score (what other teams ban against target team)
            val oppBanRecord =
                opponentBansAgainstTargetTeam?.firstOrNull {
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
                            val bonus =
                                when (sig.tier) {
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

            // 5.5 Bot Duo Synergy gravity bonus
            var botDuoScore = 0.0
            var matchedDuoPartner: String? = null
            var matchedDuoStats: com.loldraft.data.meta.BotDuoSynergy? = null
            if (!isBan) {
                if (targetRole == Role.SUPPORT) {
                    val allyAdc =
                        teamPicks.find { it.role == Role.BOT }
                            ?: teamPicks.find { tagRegistry.getProfile(it.championId)?.primaryRole == Role.BOT }
                    if (allyAdc != null) {
                        val duo =
                            patchMeta?.getDuoSynergy(allyAdc.championId, champ)
                                ?: com.loldraft.data.meta.PatchMetaAnalyzer.CLASSIC_BOT_DUOS.find {
                                    ChampionNormalizer.toSlug(it.botChampion) == ChampionNormalizer.toSlug(allyAdc.championId) &&
                                        ChampionNormalizer.toSlug(it.supportChampion) == ChampionNormalizer.toSlug(champ)
                                }
                        if (duo != null) {
                            botDuoScore = (duo.synergyScore / 100.0).coerceIn(0.2, 1.0)
                            matchedDuoPartner = allyAdc.championId
                            matchedDuoStats = duo
                        }
                    }
                } else if (targetRole == Role.BOT) {
                    val allySup =
                        teamPicks.find { it.role == Role.SUPPORT }
                            ?: teamPicks.find { tagRegistry.getProfile(it.championId)?.primaryRole == Role.SUPPORT }
                    if (allySup != null) {
                        val duo =
                            patchMeta?.getDuoSynergy(champ, allySup.championId)
                                ?: com.loldraft.data.meta.PatchMetaAnalyzer.CLASSIC_BOT_DUOS.find {
                                    ChampionNormalizer.toSlug(it.botChampion) == ChampionNormalizer.toSlug(champ) &&
                                        ChampionNormalizer.toSlug(it.supportChampion) == ChampionNormalizer.toSlug(allySup.championId)
                                }
                        if (duo != null) {
                            botDuoScore = (duo.synergyScore / 100.0).coerceIn(0.2, 1.0)
                            matchedDuoPartner = allySup.championId
                            matchedDuoStats = duo
                        }
                    }
                }
            }

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

            val hasTargetSoloQData =
                targetProfile != null &&
                    (
                        targetProfile.activeSpikeAlerts.isNotEmpty() ||
                            targetProfile.recentSoloQ3Days.isNotEmpty() ||
                            targetProfile.recentSoloQ7Days.isNotEmpty()
                    )

            // 7. Total composite intent score directly from data
            val totalIntentScore =
                if (isBan) {
                    val hasRespectBans = opponentBansAgainstTargetTeam != null && opponentBansAgainstTargetTeam.isNotEmpty()
                    val hasOppMastery = opponentPlayerProfilesByRole != null && opponentPlayerProfilesByRole.isNotEmpty()
                    when {
                        hasRespectBans && hasOppMastery ->
                            (opponentRespectBanScore * 0.35) + (playerMasteryScore * 0.30) + (metaScore * 0.25) +
                                (compositionFitScore * 0.10)
                        hasRespectBans ->
                            (opponentRespectBanScore * 0.45) + (metaScore * 0.40) + (compositionFitScore * 0.15)
                        hasOppMastery ->
                            (playerMasteryScore * 0.45) + (metaScore * 0.40) + (compositionFitScore * 0.15)
                        else ->
                            (metaScore * 0.70) + (compositionFitScore * 0.30)
                    }
                } else {
                    val rawScore =
                        when {
                            matchedDuoStats != null -> {
                                if (hasDetailedProfiles || targetCareerStats != null) {
                                    (botDuoScore * 0.35) + (playerMasteryScore * 0.25) + (metaScore * 0.20) +
                                        (soloQScore * 0.10) + (compositionFitScore * 0.10)
                                } else {
                                    (botDuoScore * 0.45) + (metaScore * 0.35) + (compositionFitScore * 0.20)
                                }
                            }
                            hasDetailedProfiles && hasTargetSoloQData ->
                                (metaScore * 0.25) + (playerMasteryScore * 0.30) + (soloQScore * 0.30) +
                                    (compositionFitScore * 0.10) + (counterDenialScore * 0.05)
                            hasDetailedProfiles || targetCareerStats != null ->
                                (playerMasteryScore * 0.45) + (metaScore * 0.30) +
                                    (compositionFitScore * 0.15) + (counterDenialScore * 0.10)
                            else ->
                                (metaScore * 0.45) + (compositionFitScore * 0.35) + (counterDenialScore * 0.20)
                        }
                    rawScore.coerceAtLeast(0.01)
                }

            // 8. Construct transparent rationale
            val reasons = mutableListOf<String>()

            if (isBan) {
                if (oppBanRecord != null && oppBanRecord.banCount > 0) {
                    val oppPct = String.format(Locale.US, "%.0f", oppBanRecord.banRate * 100.0)
                    val teamLabel = targetTeamName ?: "opponent"
                    reasons.add(
                        "Respect ban: other teams ban vs $teamLabel in $oppPct% of games (${oppBanRecord.banCount}/${oppBanRecord.totalGames}G)",
                    )
                }
                if (matchedOpponentPlayer != null && matchedSignature != null) {
                    reasons.add("Target ban vs $matchedOpponentPlayer: $matchedSignature pick")
                } else if (matchedOpponentPlayer != null) {
                    reasons.add("Target ban vs $matchedOpponentPlayer")
                }
                if (metaStats?.tier == MetaTier.T0) {
                    reasons.add("Tier-0 (OP) meta ban priority (${(metaStats.presenceRate * 100).toInt()}% presence)")
                } else if (metaStats?.tier == MetaTier.T1) {
                    reasons.add("Tier-1 priority meta ban")
                } else if (metaStats?.tier == MetaTier.T2) {
                    reasons.add("Tier-2 meta ban")
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
                    reasons.add("Tier-0 (OP) meta priority (${(metaStats.presenceRate * 100).toInt()}% presence)")
                } else if (metaStats?.tier == MetaTier.T1) {
                    reasons.add("Tier-1 priority meta pick")
                } else if (metaStats?.tier == MetaTier.T2) {
                    reasons.add("Tier-2 meta pick")
                }

                if (needsAp && (profile?.damageProfile?.magicRatio ?: 0.0) >= 0.65) reasons.add("Fills critical AP damage deficit")
                if (vacantRoles.isNotEmpty() && targetRole in vacantRoles) reasons.add("Fills vacant $targetRole lane")
                if (counterDenialScore > 0.3) reasons.add("Counters enemy composition")

                if (matchedDuoStats != null && matchedDuoPartner != null) {
                    val wrPct = String.format(Locale.US, "%.0f", matchedDuoStats.synergyWinRate * 100.0)
                    val gdStr =
                        if (matchedDuoStats.avgGoldDiffAt15 >
                            0
                        ) {
                            "+${matchedDuoStats.avgGoldDiffAt15.toInt()}"
                        } else {
                            "${matchedDuoStats.avgGoldDiffAt15.toInt()}"
                        }
                    reasons.add(0, "Bot Duo Synergy with $matchedDuoPartner ($wrPct% WR, $gdStr GD15)")
                }

                val spentChamps = draftState.seriesContext?.spentChampions ?: emptySet()
                if (spentChamps.isNotEmpty() && targetCareerStats != null) {
                    val spentSignatures = targetCareerStats.signaturePicks.filter { it.championId in spentChamps }
                    if (spentSignatures.isNotEmpty() && isProUnplayed) {
                        reasons.add("Fearless fallback: ${spentSignatures.joinToString { it.championId }} spent in earlier games")
                    }
                }
            }

            val rationale =
                if (reasons.isNotEmpty()) {
                    reasons.joinToString("; ")
                } else if (isBan) {
                    "Strategic ban candidate"
                } else {
                    "Standard draft candidate"
                }

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

        val dedupedCandidates =
            scoredList
                .distinctBy { ChampionNormalizer.toSlug(it.championId) }

        val totalPoolScore = dedupedCandidates.sumOf { it.intentScore }
        val candidatesWithGlobalProb =
            if (totalPoolScore > 0.0) {
                dedupedCandidates.map { candidate ->
                    candidate.copy(
                        probability = roundToFourDecimals(candidate.intentScore / totalPoolScore),
                    )
                }
            } else {
                val uniform = if (dedupedCandidates.isNotEmpty()) 1.0 / dedupedCandidates.size else 0.0
                dedupedCandidates.map { candidate ->
                    candidate.copy(probability = roundToFourDecimals(uniform))
                }
            }

        val topCandidates =
            candidatesWithGlobalProb
                .sortedByDescending { it.probability }
                .take(topN)

        return IntentPredictionResult(
            turnSpec = turnSpec,
            actingSide = actingSide,
            actionType = turnSpec.actionType,
            predictions = topCandidates,
        )
    }

    private fun roundToFourDecimals(value: Double): Double = round(value * 10000.0) / 10000.0
}

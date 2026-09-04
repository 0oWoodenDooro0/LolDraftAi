package com.loldraft.models

import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.meta.TankinessTier
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.style.TeamTacticalProfile
import kotlin.math.round
import kotlin.system.measureTimeMillis

class DraftRecommender(
    val evaluator: DraftEvaluator = AnalyticalDraftEvaluator(),
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val flawDetector: CompositionFlawDetector = CompositionFlawDetector(tagRegistry),
    val flexAnalyzer: FlexPickAnalyzer = FlexPickAnalyzer(tagRegistry),
) {
    fun recommendBestPicks(
        draftState: DraftState,
        targetSide: Side,
        patchMeta: PatchMetaMatrix? = null,
        limit: Int = 5,
    ): List<PickRecommendation> = recommend(draftState, targetSide, patchMeta = patchMeta, limit = limit).recommendations

    fun recommendBestBans(
        draftState: DraftState,
        targetSide: Side,
        patchMeta: PatchMetaMatrix? = null,
        opponentBansAgainstTargetTeam: List<com.loldraft.data.style.OpponentBanRecord>? = null,
        opponentPlayerProfilesByRole: Map<Role, com.loldraft.data.player.ProPlayerDetailedProfile>? = null,
        opponentTeamName: String? = null,
        limit: Int = 3,
    ): List<PickRecommendation> {
        val unavailable = draftState.allSelectedChampions.map { ChampionNormalizer.toSlug(it) }.toSet()
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
        val available = candidateMap.filterKeys { it !in unavailable }.values.toList()

        val banRecommendations = mutableListOf<PickRecommendation>()

        for (champ in available) {
            val profile = tagRegistry.getProfile(champ)
            val metaStats = patchMeta?.getStats(champ)
            val primaryRole = profile?.primaryRole ?: Role.MID

            val oppBan =
                opponentBansAgainstTargetTeam?.firstOrNull {
                    it.championId.equals(champ, ignoreCase = true) ||
                        ChampionNormalizer.normalize(it.championId).equals(ChampionNormalizer.normalize(champ), ignoreCase = true)
                }

            var oppMasteryScore = 0.0
            var matchedOppPlayer: String? = null
            var matchedTierName: String? = null
            if (opponentPlayerProfilesByRole != null) {
                for ((r, oProfile) in opponentPlayerProfilesByRole) {
                    val sig = oProfile.careerStats.signaturePicks.firstOrNull { it.championId.equals(champ, ignoreCase = true) }
                    if (sig != null) {
                        val b =
                            when (sig.tier) {
                                com.loldraft.data.player.SignatureTier.SIGNATURE -> 0.95
                                com.loldraft.data.player.SignatureTier.COMFORT -> 0.80
                                com.loldraft.data.player.SignatureTier.POCKET -> 0.70
                            }
                        if (b > oppMasteryScore) {
                            oppMasteryScore = b
                            matchedOppPlayer = "${oProfile.playerId} ($r)"
                            matchedTierName = sig.tier.name
                        }
                    }
                }
            }

            val metaScore =
                if (metaStats != null) {
                    val tierScore =
                        when (metaStats.tier) {
                            com.loldraft.data.meta.MetaTier.T0 -> 1.0
                            com.loldraft.data.meta.MetaTier.T1 -> 0.8
                            com.loldraft.data.meta.MetaTier.T2 -> 0.6
                            com.loldraft.data.meta.MetaTier.T3 -> 0.4
                            com.loldraft.data.meta.MetaTier.T4 -> 0.2
                        }
                    tierScore * 0.6 + metaStats.presenceRate * 0.4
                } else if (profile != null) {
                    val radarAvg = (profile.radar.laningStrength + profile.radar.lateGameScaling + profile.radar.engage) / 30.0
                    (radarAvg * 0.6 + 0.1).coerceIn(0.2, 0.8)
                } else {
                    0.5
                }

            val oppBanRate = oppBan?.banRate ?: 0.0
            val banImpact = (oppBanRate * 0.45) + (oppMasteryScore * 0.35) + (metaScore * 0.20)
            val winRateGain = roundToFourDecimals((banImpact * 0.05).coerceIn(0.01, 0.08))

            val reasons = mutableListOf<String>()
            if (oppBan != null && oppBan.banCount > 0) {
                val pct = String.format(java.util.Locale.US, "%.0f", oppBan.banRate * 100)
                val tName = opponentTeamName ?: "opponent"
                reasons.add("Respect ban: other teams ban vs $tName in $pct% of games (${oppBan.banCount}/${oppBan.totalGames}G)")
            }
            if (matchedOppPlayer != null && matchedTierName != null) {
                reasons.add("Denies $matchedOppPlayer's $matchedTierName pick")
            }
            if (metaStats?.tier == com.loldraft.data.meta.MetaTier.T0) {
                reasons.add("Removes T0 high-presence power pick (${(metaStats.presenceRate * 100).toInt()}% presence)")
            } else if (metaStats?.tier == com.loldraft.data.meta.MetaTier.T1) {
                reasons.add("Removes T1 priority meta threat")
            }
            if (reasons.isEmpty()) {
                reasons.add("Neutralizes high threat meta candidate")
            }

            banRecommendations.add(
                PickRecommendation(
                    championId = profile?.displayName ?: champ,
                    recommendedRole = primaryRole,
                    winRateGain = winRateGain,
                    predictedWinRate = 0.50 + winRateGain,
                    baseWinRate = 0.50,
                    reasons = reasons,
                ),
            )
        }

        return banRecommendations
            .sortedWith(
                compareByDescending<PickRecommendation> { it.winRateGain }
                    .thenBy { it.championId },
            )
            .distinctBy { ChampionNormalizer.toSlug(it.championId) }
            .take(limit)
    }


    fun recommend(
        draftState: DraftState,
        targetSide: Side,
        patchMeta: PatchMetaMatrix? = null,
        blueTeamProfile: TeamTacticalProfile? = null,
        redTeamProfile: TeamTacticalProfile? = null,
        playerStatsByRole: Map<Role, PlayerCareerStats>? = null,
        targetRole: Role? = null,
        limit: Int = 5,
    ): RecommendationReport {
        var report: RecommendationReport
        val latency =
            measureTimeMillis {
                report =
                    computeRecommendations(
                        draftState = draftState,
                        targetSide = targetSide,
                        patchMeta = patchMeta,
                        blueTeamProfile = blueTeamProfile,
                        redTeamProfile = redTeamProfile,
                        playerStatsByRole = playerStatsByRole,
                        targetRole = targetRole,
                        limit = limit,
                    )
            }
        return report.copy(latencyMs = latency)
    }

    private fun computeRecommendations(
        draftState: DraftState,
        targetSide: Side,
        patchMeta: PatchMetaMatrix?,
        blueTeamProfile: TeamTacticalProfile?,
        redTeamProfile: TeamTacticalProfile?,
        playerStatsByRole: Map<Role, PlayerCareerStats>?,
        targetRole: Role?,
        limit: Int,
    ): RecommendationReport {
        val baseEval = evaluator.evaluate(draftState, patchMeta, blueTeamProfile, redTeamProfile)
        val baseWinRate = if (targetSide == Side.BLUE) baseEval.blueWinRate else baseEval.redWinRate

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

        val availableChampions = candidateMap.filterKeys { it !in unavailable }.values.toList()

        val targetTeamPicks = if (targetSide == Side.BLUE) draftState.bluePicks else draftState.redPicks
        val enemyPicks = if (targetSide == Side.BLUE) draftState.redPicks else draftState.bluePicks

        val lockedRoles = targetTeamPicks.mapNotNull { it.role }.toSet()
        val vacantRoles = Role.entries.filterNot { it in lockedRoles }

        // Filter by targetRole if specified
        val eligibleCandidates =
            if (targetRole != null) {
                availableChampions.filter { champ ->
                    val profile = tagRegistry.getProfile(champ)
                    val flex = flexAnalyzer.analyzeChampion(champ, patchMeta)
                    profile?.primaryRole == targetRole ||
                        targetRole in (profile?.secondaryRoles ?: emptySet()) ||
                        (flex.roleProbabilities[targetRole] ?: 0.0) >= 0.15
                }
            } else {
                availableChampions
            }

        // Extract base composition flaws
        val baseFlaws =
            if (targetSide == Side.BLUE) {
                baseEval.flaws?.blueReport?.flaws ?: emptyList()
            } else {
                baseEval.flaws?.redReport?.flaws ?: emptyList()
            }
        val baseFlawIds = baseFlaws.map { it.id }.toSet()

        val enemyDamage = tagRegistry.calculateTeamDamageSplit(enemyPicks.map { it.championId })
        val enemyIsFullAd = enemyPicks.size >= 2 && enemyDamage.physicalRatio >= 0.80

        val recommendations = mutableListOf<PickRecommendation>()

        for (champ in eligibleCandidates) {
            val profile = tagRegistry.getProfile(champ)
            val assignedRole =
                targetRole
                    ?: run {
                        val flex = flexAnalyzer.analyzeChampion(champ, patchMeta, lockedRoles)
                        val viableOpen = vacantRoles.filter { (flex.roleProbabilities[it] ?: 0.0) >= 0.15 }
                        viableOpen.maxByOrNull { flex.roleProbabilities[it] ?: 0.0 }
                            ?: flex.primaryRole
                    }

            // Simulate draft with candidate pick
            val simulatedPick =
                PickSelection(
                    championId = profile?.displayName ?: champ,
                    role = assignedRole,
                )
            val simulatedDraft =
                if (targetSide == Side.BLUE) {
                    draftState.copy(bluePicks = draftState.bluePicks + simulatedPick)
                } else {
                    draftState.copy(redPicks = draftState.redPicks + simulatedPick)
                }

            val simEval = evaluator.evaluate(simulatedDraft, patchMeta, blueTeamProfile, redTeamProfile)
            val simWinRate = if (targetSide == Side.BLUE) simEval.blueWinRate else simEval.redWinRate
            val rawGain = simWinRate - baseWinRate

            // Check flaw resolution
            val simFlaws =
                if (targetSide == Side.BLUE) {
                    simEval.flaws?.blueReport?.flaws ?: emptyList()
                } else {
                    simEval.flaws?.redReport?.flaws ?: emptyList()
                }
            val simFlawIds = simFlaws.map { it.id }.toSet()

            val resolvedFlaws = (baseFlawIds - simFlawIds).toList()
            val introducedFlaws = (simFlawIds - baseFlawIds).toList()

            // Counter calculation
            var counterScore = 0.0
            val counterReasons = mutableListOf<String>()
            if (patchMeta != null && enemyPicks.isNotEmpty()) {
                for (enemy in enemyPicks) {
                    val counter =
                        patchMeta.getMatchup(champ, enemy.championId, assignedRole)
                            ?: patchMeta.getMatchup(champ, enemy.championId)
                    if (counter != null && counter.counterScore >= 60.0) {
                        counterScore += counter.counterScore
                        counterReasons.add("Hard counter against ${enemy.championId} (${(counter.winRate * 100).toInt()}% WR)")
                    }
                }
            }

            // Anti-AD heavy tank counter bonus against full AD teams
            if (enemyIsFullAd) {
                val isArmorTank =
                    profile?.durability?.tankinessTier == TankinessTier.FRONTLINE_TANK ||
                        champ.equals("Malphite", ignoreCase = true) ||
                        champ.equals("K'Sante", ignoreCase = true) ||
                        champ.equals("Rammus", ignoreCase = true)
                if (isArmorTank) {
                    counterScore += 45.0
                    counterReasons.add("Frontline armor counter against Full AD enemy composition")
                }
            }

            // Synergy calculation
            var synergyScore = 0.0
            val synergyReasons = mutableListOf<String>()
            if (patchMeta != null && targetTeamPicks.isNotEmpty()) {
                for (ally in targetTeamPicks) {
                    val synergies = patchMeta.getTopSynergies(ally.championId, limit = 10)
                    val match =
                        synergies.firstOrNull {
                            it.championA.equals(champ, ignoreCase = true) ||
                                it.championB.equals(champ, ignoreCase = true)
                        }
                    if (match != null && match.synergyScore >= 60.0) {
                        synergyScore += match.synergyScore
                        synergyReasons.add("High synergy with ${ally.championId} (${(match.synergyWinRate * 100).toInt()}% WR)")
                    }
                }
            }

            // Composite gain adjustment
            val flawBonus = (resolvedFlaws.size * 0.015) - (introducedFlaws.size * 0.010)
            val counterBonus = (counterScore / 2500.0)
            val synergyBonus = (synergyScore / 2500.0)

            val totalGain = rawGain + flawBonus + counterBonus + synergyBonus
            val roundedGain = roundToFourDecimals(totalGain)
            val predictedWinRate = roundToFourDecimals((baseWinRate + roundedGain).coerceIn(0.01, 0.99))
            val finalGain = roundToFourDecimals(predictedWinRate - baseWinRate)

            // Construct explainable reasons
            val reasons = mutableListOf<String>()
            if (resolvedFlaws.isNotEmpty()) {
                val flawDescriptions = resolvedFlaws.joinToString(", ")
                reasons.add("Resolves structural flaws ($flawDescriptions)")
            }
            reasons.addAll(counterReasons)
            reasons.addAll(synergyReasons)
            if (reasons.isEmpty()) {
                if (finalGain > 0.0) {
                    reasons.add("Improves composition balance and lane scaling (+${(finalGain * 1000).toInt() / 10.0}%)")
                } else {
                    reasons.add("Viable standard flex pick")
                }
            }

            recommendations.add(
                PickRecommendation(
                    championId = profile?.displayName ?: champ,
                    recommendedRole = assignedRole,
                    winRateGain = finalGain,
                    predictedWinRate = predictedWinRate,
                    baseWinRate = roundToFourDecimals(baseWinRate),
                    synergyScore = roundToFourDecimals(synergyScore),
                    counterScore = roundToFourDecimals(counterScore),
                    flawsResolved = resolvedFlaws,
                    flawsIntroduced = introducedFlaws,
                    reasons = reasons,
                ),
            )
        }

        val topRecommendations =
            recommendations
                .sortedWith(
                    compareByDescending<PickRecommendation> { it.winRateGain }
                        .thenByDescending { it.counterScore + it.synergyScore },
                )
                .distinctBy { ChampionNormalizer.toSlug(it.championId) }
                .take(limit)

        return RecommendationReport(
            targetSide = targetSide,
            turnNumber = draftState.currentTurnNumber,
            baseWinRate = roundToFourDecimals(baseWinRate),
            recommendations = topRecommendations,
            evaluatedCandidateCount = eligibleCandidates.size,
            latencyMs = 0L,
        )
    }

    private fun roundToFourDecimals(value: Double): Double = round(value * 10000.0) / 10000.0
}

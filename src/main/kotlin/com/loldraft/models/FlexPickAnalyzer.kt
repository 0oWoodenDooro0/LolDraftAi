package com.loldraft.models

import com.loldraft.data.meta.ChampionRoleDictionary
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.normalization.ChampionNormalizer
import kotlin.math.ln
import kotlin.math.round

class FlexPickAnalyzer(
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val flexProbabilityThreshold: Double = 0.15,
) {
    companion object {
        private val DEFAULT_FLEX_PRIORS: Map<String, Map<Role, Double>> =
            mapOf(
                "rumble" to mapOf(Role.TOP to 0.45, Role.MID to 0.45, Role.JUNGLE to 0.10),
                "poppy" to mapOf(Role.SUPPORT to 0.45, Role.JUNGLE to 0.35, Role.TOP to 0.20),
                "corki" to mapOf(Role.BOT to 0.60, Role.MID to 0.40),
                "ambessa" to mapOf(Role.TOP to 0.75, Role.JUNGLE to 0.25),
                "tristana" to mapOf(Role.MID to 0.55, Role.BOT to 0.45),
                "maokai" to mapOf(Role.SUPPORT to 0.50, Role.JUNGLE to 0.35, Role.TOP to 0.15),
                "gragas" to mapOf(Role.TOP to 0.45, Role.JUNGLE to 0.30, Role.MID to 0.25),
                "nautilus" to mapOf(Role.SUPPORT to 0.85, Role.MID to 0.15),
                "jayce" to mapOf(Role.TOP to 0.65, Role.MID to 0.35),
                "lucian" to mapOf(Role.BOT to 0.75, Role.MID to 0.25),
                "k'sante" to mapOf(Role.TOP to 0.85, Role.MID to 0.15),
                "karma" to mapOf(Role.SUPPORT to 0.70, Role.MID to 0.20, Role.TOP to 0.10),
                "seraphine" to mapOf(Role.SUPPORT to 0.50, Role.BOT to 0.30, Role.MID to 0.20),
                "neeko" to mapOf(Role.MID to 0.55, Role.SUPPORT to 0.45),
                "yasuo" to mapOf(Role.MID to 0.65, Role.TOP to 0.20, Role.BOT to 0.15),
                "vayne" to mapOf(Role.BOT to 0.75, Role.TOP to 0.25),
                "renekton" to mapOf(Role.TOP to 0.85, Role.MID to 0.15),
                "smolder" to mapOf(Role.BOT to 0.70, Role.MID to 0.30),
                "galio" to mapOf(Role.MID to 0.65, Role.SUPPORT to 0.35),
                "pantheon" to mapOf(Role.SUPPORT to 0.45, Role.MID to 0.35, Role.TOP to 0.20),
            )

        private val KNOWN_DUAL_COUNTERS: Map<String, List<String>> =
            mapOf(
                "rumble" to listOf("Galio", "Renekton", "K'Sante", "Jayce"),
                "poppy" to listOf("Olaf", "Gwen", "Trundle", "Morgana"),
                "corki" to listOf("Lucian", "Tristana", "Jayce", "Syndra"),
                "tristana" to listOf("Corki", "Yasuo", "Ashe", "Syndra"),
                "maokai" to listOf("Olaf", "Sylas", "Trundle", "Braum"),
                "gragas" to listOf("Aatrox", "Camille", "Sylas", "Olaf"),
            )

        private val KNOWN_PRIMARY_ROLES: Map<String, Role> =
            mapOf(
                "blitzcrank" to Role.SUPPORT,
                "thresh" to Role.SUPPORT,
                "darius" to Role.TOP,
                "garen" to Role.TOP,
                "jinx" to Role.BOT,
                "vayne" to Role.BOT,
                "ashe" to Role.BOT,
                "caitlyn" to Role.BOT,
                "ezreal" to Role.BOT,
                "lucian" to Role.BOT,
                "kai'sa" to Role.BOT,
                "varus" to Role.BOT,
                "leona" to Role.SUPPORT,
                "nami" to Role.SUPPORT,
                "lulu" to Role.SUPPORT,
                "alistar" to Role.SUPPORT,
                "braum" to Role.SUPPORT,
                "rakan" to Role.SUPPORT,
                "pyke" to Role.SUPPORT,
                "senna" to Role.SUPPORT,
                "tahm kench" to Role.SUPPORT,
                "lee sin" to Role.JUNGLE,
                "viego" to Role.JUNGLE,
                "xin zhao" to Role.JUNGLE,
                "sejuani" to Role.JUNGLE,
                "jarvan iv" to Role.JUNGLE,
                "orianna" to Role.MID,
                "syndra" to Role.MID,
                "ahri" to Role.MID,
                "leblanc" to Role.MID,
                "azir" to Role.MID,
                "aatrox" to Role.TOP,
                "renekton" to Role.TOP,
                "k'sante" to Role.TOP,
                "jax" to Role.TOP,
                "fiora" to Role.TOP,
                "camille" to Role.TOP,
                "malphite" to Role.TOP,
                "gnar" to Role.TOP,
                "ambessa" to Role.TOP,
                "corki" to Role.BOT,
                "smolder" to Role.BOT,
                "jayce" to Role.TOP,
                "poppy" to Role.SUPPORT,
            )
    }

    fun isFlexPick(
        championId: String,
        patchMeta: PatchMetaMatrix? = null,
    ): Boolean = analyzeChampion(championId, patchMeta).isFlex

    fun getRoleProbabilities(
        championId: String,
        patchMeta: PatchMetaMatrix? = null,
        teamExistingRoles: Set<Role> = emptySet(),
    ): Map<Role, Double> = analyzeChampion(championId, patchMeta, teamExistingRoles).roleProbabilities

    fun analyzeChampion(
        championId: String,
        patchMeta: PatchMetaMatrix? = null,
        teamExistingRoles: Set<Role> = emptySet(),
    ): FlexAnalysisResult {
        val slug = ChampionNormalizer.toSlug(championId)
        val profile = tagRegistry.getProfile(championId)
        val baseline = ChampionRoleDictionary.getBaselineRole(championId)
        val primaryRole = profile?.primaryRole ?: KNOWN_PRIMARY_ROLES[slug] ?: baseline.first

        // 1. Determine prior distribution
        val basePriors = mutableMapOf<Role, Double>()
        val defaultFlex = DEFAULT_FLEX_PRIORS[slug]

        if (defaultFlex != null) {
            basePriors.putAll(defaultFlex)
        } else if (profile != null) {
            if (profile.secondaryRoles.isNotEmpty()) {
                val secShare = 0.35 / profile.secondaryRoles.size
                basePriors[profile.primaryRole] = 0.65
                profile.secondaryRoles.forEach { basePriors[it] = secShare }
            } else {
                basePriors[profile.primaryRole] = 1.0
            }
        } else if (baseline.second.isNotEmpty()) {
            val secShare = 0.35 / baseline.second.size
            basePriors[baseline.first] = 0.65
            baseline.second.forEach { basePriors[it] = secShare }
        } else {
            basePriors[primaryRole] = 1.0
        }

        // 2. Blend with empirical PatchMetaMatrix if available
        val metaStats = patchMeta?.getStats(championId)
        if (metaStats != null && metaStats.roleDistribution.isNotEmpty()) {
            val totalCount = metaStats.roleDistribution.values.sum()
            val totalGames = totalCount.toDouble()
            if (totalGames > 0) {
                val empiricalDist = metaStats.roleDistribution.mapValues { it.value / totalGames }
                val allRoles = (basePriors.keys + empiricalDist.keys).toSet()
                val blended = mutableMapOf<Role, Double>()
                val empiricalWeight = (totalGames / (totalGames + 20.0)).coerceIn(0.5, 0.9)
                val priorWeight = 1.0 - empiricalWeight

                for (role in allRoles) {
                    val pEmp = empiricalDist[role] ?: 0.0
                    val pPrior = basePriors[role] ?: 0.0
                    blended[role] = (pEmp * empiricalWeight) + (pPrior * priorWeight)
                }
                basePriors.clear()
                basePriors.putAll(blended)
            }
        }

        // Ensure all 5 roles have an entry in the map
        for (r in Role.entries) {
            basePriors.putIfAbsent(r, 0.0)
        }

        // 3. Contextual Bayesian conditioning (clamp already taken roles to 0)
        val conditionalMap = basePriors.toMutableMap()
        for (role in teamExistingRoles) {
            conditionalMap[role] = 0.0
        }

        val remainingSum = conditionalMap.values.sum()
        val normalizedProbabilities =
            if (remainingSum > 0.0001) {
                conditionalMap.mapValues { roundToFourDecimals(it.value / remainingSum) }
            } else {
                // If all probable roles are claimed, conditional probabilities for remaining roles are 0.0
                Role.entries.associateWith { 0.0 }
            }

        // Fix tiny rounding error so sum is precisely 1.0
        val finalProbabilities = forceSumToOne(normalizedProbabilities)

        // 4. Evaluate flex status and entropy
        val viableRoles = finalProbabilities.filter { it.value >= flexProbabilityThreshold }
        val isFlex = viableRoles.size >= 2

        // Normalized Shannon entropy
        var entropy = 0.0
        for ((_, p) in finalProbabilities) {
            if (p > 0.0001) {
                entropy -= p * ln(p)
            }
        }
        val maxEntropy = ln(5.0)
        val normalizedEntropy = roundToFourDecimals((entropy / maxEntropy).coerceIn(0.0, 1.0))

        val secondaryRoles =
            finalProbabilities
                .filter { it.key != primaryRole && it.value >= flexProbabilityThreshold }
                .keys
                .toList()

        return FlexAnalysisResult(
            championId = profile?.displayName ?: championId,
            isFlex = isFlex,
            roleProbabilities = finalProbabilities,
            primaryRole = primaryRole,
            secondaryRoles = secondaryRoles,
            flexEntropy = normalizedEntropy,
            confidence = if (profile != null) 1.0 else 0.5,
        )
    }

    fun analyzeTeamDraft(
        draftState: DraftState,
        side: Side,
        patchMeta: PatchMetaMatrix? = null,
    ): List<FlexAnalysisResult> {
        val picks = if (side == Side.BLUE) draftState.bluePicks else draftState.redPicks
        val lockedRoles = picks.mapNotNull { it.role }.toSet()

        return picks.map { pick ->
            val otherRoles = lockedRoles - listOfNotNull(pick.role).toSet()
            analyzeChampion(
                championId = pick.championId,
                patchMeta = patchMeta,
                teamExistingRoles = otherRoles,
            )
        }
    }

    fun generateDefenseAdvice(
        draftState: DraftState,
        opponentSide: Side,
        patchMeta: PatchMetaMatrix? = null,
    ): List<FlexDefenseAdvice> {
        val opponentPicks = if (opponentSide == Side.BLUE) draftState.bluePicks else draftState.redPicks
        val lockedRoles = opponentPicks.mapNotNull { it.role }.toSet()
        val adviceList = mutableListOf<FlexDefenseAdvice>()

        for (pick in opponentPicks) {
            val otherRoles = lockedRoles - listOfNotNull(pick.role).toSet()
            val analysis = analyzeChampion(pick.championId, patchMeta, otherRoles)

            if (!analysis.isFlex) continue

            val viableRoles =
                analysis.roleProbabilities
                    .filter { it.value >= flexProbabilityThreshold }
                    .entries
                    .sortedByDescending { it.value }
                    .map { RoleProbability(it.key, it.value) }

            val threatLevel =
                when {
                    analysis.flexEntropy >= 0.6 || viableRoles.size >= 3 -> FlexThreatLevel.CRITICAL
                    analysis.flexEntropy >= 0.4 || viableRoles.size >= 2 -> FlexThreatLevel.HIGH
                    else -> FlexThreatLevel.MEDIUM
                }

            val roleNames = viableRoles.joinToString("/") { it.role.name }
            val tacticalWarnings =
                listOf(
                    "Opponent locked ${analysis.championId} with multi-lane flex potential ($roleNames).",
                    "Do not commit to a rigid lane counter until opponent's final lane assignments are revealed.",
                )

            val counterStrategies =
                listOf(
                    "Draft versatile multi-lane neutralizers or retain your own flex pick in response.",
                    "Defer vulnerable single-lane counter-picks to Phase 2.",
                )

            val slug = ChampionNormalizer.toSlug(analysis.championId)
            val dualCounters = KNOWN_DUAL_COUNTERS[slug] ?: listOf("Galio", "Renekton", "K'Sante")

            adviceList.add(
                FlexDefenseAdvice(
                    targetChampion = analysis.championId,
                    threatLevel = threatLevel,
                    candidateRoles = viableRoles,
                    tacticalWarnings = tacticalWarnings,
                    counterStrategies = counterStrategies,
                    recommendedDualCounters = dualCounters,
                ),
            )
        }

        return adviceList
    }

    private fun forceSumToOne(probMap: Map<Role, Double>): Map<Role, Double> {
        val sum = probMap.values.sum()
        if (sum == 0.0) return probMap
        val maxEntry = probMap.maxByOrNull { it.value } ?: return probMap
        val diff = 1.0 - sum
        val adjusted = probMap.toMutableMap()
        adjusted[maxEntry.key] = roundToFourDecimals(maxEntry.value + diff)
        return adjusted
    }

    private fun roundToFourDecimals(value: Double): Double = round(value * 10000.0) / 10000.0
}

package com.loldraft.models

import com.loldraft.data.meta.ChampionProfile
import com.loldraft.data.meta.ChampionTag
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.FiveDimensionRadar
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.meta.TankinessTier
import com.loldraft.data.meta.DamageProfile
import com.loldraft.data.meta.DamageType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.style.TeamTacticalProfile

import com.loldraft.data.meta.ChampionEmpiricalRegistry
import com.loldraft.data.meta.ChampionEmpiricalStats

data class RolePrior(
    val durability: Double,
    val cc: Double,
    val radar: FiveDimensionRadar,
    val physDmg: Double,
    val magicDmg: Double,
    val trueDmg: Double = 0.0,
)

class DraftFeatureExtractor(
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val blueSideAdvantageBias: Double = 0.03,
    val empiricalRegistry: ChampionEmpiricalRegistry = ChampionEmpiricalRegistry.createDefault(),
) {
    companion object {
        const val TEAM_SIZE = 5

        val ROLE_PRIORS: Map<Role, RolePrior> =
            mapOf(
                Role.TOP to RolePrior(durability = 7.5, cc = 2.0, radar = FiveDimensionRadar(7.8, 6.8, 5.0, 6.8, 7.2), physDmg = 0.75, magicDmg = 0.25),
                Role.JUNGLE to RolePrior(durability = 7.2, cc = 2.8, radar = FiveDimensionRadar(6.8, 8.2, 5.5, 6.2, 7.0), physDmg = 0.65, magicDmg = 0.35),
                Role.MID to RolePrior(durability = 4.5, cc = 2.0, radar = FiveDimensionRadar(7.5, 6.8, 6.5, 8.2, 8.2), physDmg = 0.20, magicDmg = 0.80),
                Role.BOT to RolePrior(durability = 3.8, cc = 1.0, radar = FiveDimensionRadar(7.2, 5.5, 5.5, 7.8, 8.8), physDmg = 0.85, magicDmg = 0.15),
                Role.SUPPORT to RolePrior(durability = 6.0, cc = 3.2, radar = FiveDimensionRadar(6.8, 7.8, 7.8, 4.5, 6.8), physDmg = 0.15, magicDmg = 0.85),
            )

        val DEFAULT_PRIOR =
            RolePrior(
                durability = 5.8,
                cc = 2.2,
                radar = FiveDimensionRadar(7.22, 7.02, 6.06, 6.70, 7.60),
                physDmg = 0.52,
                magicDmg = 0.48,
            )
    }

    private fun getMissingPriors(selections: List<PickSelection>): List<RolePrior> {
        val missingCount = (TEAM_SIZE - selections.size).coerceAtLeast(0)
        if (missingCount == 0) return emptyList()

        val takenRoles = selections.mapNotNull { it.role }.toSet()
        val untakenRoles = Role.entries.filter { it !in takenRoles }.toMutableList()

        val priors = mutableListOf<RolePrior>()
        for (i in 0 until missingCount) {
            if (untakenRoles.isNotEmpty()) {
                val role = untakenRoles.removeAt(0)
                priors.add(ROLE_PRIORS[role] ?: DEFAULT_PRIOR)
            } else {
                priors.add(DEFAULT_PRIOR)
            }
        }
        return priors
    }

    fun extract(
        draftState: DraftState,
        patchMeta: PatchMetaMatrix? = null,
        blueTeamProfile: TeamTacticalProfile? = null,
        redTeamProfile: TeamTacticalProfile? = null,
    ): DraftFeatures =
        extractFromSelections(
            blueSelections = draftState.bluePicks,
            redSelections = draftState.redPicks,
            patchMeta = patchMeta,
            blueTeamProfile = blueTeamProfile,
            redTeamProfile = redTeamProfile,
        )

    fun extractFromChampions(
        blueChampions: List<String>,
        redChampions: List<String>,
        patchMeta: PatchMetaMatrix? = null,
        blueTeamProfile: TeamTacticalProfile? = null,
        redTeamProfile: TeamTacticalProfile? = null,
    ): DraftFeatures =
        extractFromSelections(
            blueSelections = blueChampions.map { PickSelection(it) },
            redSelections = redChampions.map { PickSelection(it) },
            patchMeta = patchMeta,
            blueTeamProfile = blueTeamProfile,
            redTeamProfile = redTeamProfile,
        )

    fun extractFromSelections(
        blueSelections: List<PickSelection>,
        redSelections: List<PickSelection>,
        patchMeta: PatchMetaMatrix? = null,
        blueTeamProfile: TeamTacticalProfile? = null,
        redTeamProfile: TeamTacticalProfile? = null,
    ): DraftFeatures {
        val blueChamps = blueSelections.map { it.championId }
        val redChamps = redSelections.map { it.championId }

        val blueProfiles = blueChamps.mapNotNull { tagRegistry.getProfile(it) }
        val redProfiles = redChamps.mapNotNull { tagRegistry.getProfile(it) }

        val bluePriors = getMissingPriors(blueSelections)
        val redPriors = getMissingPriors(redSelections)

        // 1. Five Dimension Radar (0..4, 5..9, 10..14)
        val blueRadarList = blueProfiles.map { it.radar } + bluePriors.map { it.radar }
        val blueRadar = if (blueRadarList.isEmpty()) FiveDimensionRadar.average(emptyList()) else FiveDimensionRadar.average(blueRadarList)
        val redRadarList = redProfiles.map { it.radar } + redPriors.map { it.radar }
        val redRadar = if (redRadarList.isEmpty()) FiveDimensionRadar.average(emptyList()) else FiveDimensionRadar.average(redRadarList)
        val radarDelta =
            FiveDimensionRadar(
                laningStrength = blueRadar.laningStrength - redRadar.laningStrength,
                engage = blueRadar.engage - redRadar.engage,
                disengage = blueRadar.disengage - redRadar.disengage,
                waveclear = blueRadar.waveclear - redRadar.waveclear,
                lateGameScaling = blueRadar.lateGameScaling - redRadar.lateGameScaling,
            )

        // 2. Damage Profiles (15..17, 18..20)
        val blueSlots = (blueProfiles.size + bluePriors.size).coerceAtLeast(1)
        val bluePhys = (blueProfiles.sumOf { it.damageProfile.physicalRatio } + bluePriors.sumOf { it.physDmg }) / blueSlots
        val blueMagic = (blueProfiles.sumOf { it.damageProfile.magicRatio } + bluePriors.sumOf { it.magicDmg }) / blueSlots
        val blueTrue = (blueProfiles.sumOf { it.damageProfile.trueRatio } + bluePriors.sumOf { it.trueDmg }) / blueSlots
        val blueDamage = DamageProfile(bluePhys, blueMagic, blueTrue, DamageType.MIXED)

        val redSlots = (redProfiles.size + redPriors.size).coerceAtLeast(1)
        val redPhys = (redProfiles.sumOf { it.damageProfile.physicalRatio } + redPriors.sumOf { it.physDmg }) / redSlots
        val redMagic = (redProfiles.sumOf { it.damageProfile.magicRatio } + redPriors.sumOf { it.magicDmg }) / redSlots
        val redTrue = (redProfiles.sumOf { it.damageProfile.trueRatio } + redPriors.sumOf { it.trueDmg }) / redSlots
        val redDamage = DamageProfile(redPhys, redMagic, redTrue, DamageType.MIXED)

        // 3. Durability (21..23)
        val blueDurability = (blueProfiles.sumOf { it.durability.durabilityScore } + bluePriors.sumOf { it.durability }) / blueSlots
        val redDurability = (redProfiles.sumOf { it.durability.durabilityScore } + redPriors.sumOf { it.durability }) / redSlots
        val deltaDurability = blueDurability - redDurability

        // 4. CC Score (24..26)
        val blueCcScore =
            blueProfiles.sumOf {
                it.ccRating.hardCcDurationSeconds + if (it.ccRating.hasReliableHardCc) 1.0 else 0.0
            } + bluePriors.sumOf { it.cc }
        val redCcScore =
            redProfiles.sumOf {
                it.ccRating.hardCcDurationSeconds + if (it.ccRating.hasReliableHardCc) 1.0 else 0.0
            } + redPriors.sumOf { it.cc }
        val deltaCcScore = blueCcScore - redCcScore

        // 5. Patch Meta Tiers & Win Rates (27..29, 30..32)
        val (blueTierScore, blueMetaWinRate) = calculateMetaStats(blueChamps, patchMeta)
        val (redTierScore, redMetaWinRate) = calculateMetaStats(redChamps, patchMeta)
        val deltaTierScore = blueTierScore - redTierScore
        val deltaMetaWinRate = blueMetaWinRate - redMetaWinRate

        // 6. Synergies (33..35)
        val blueSynergy = calculateSynergyScore(blueChamps, patchMeta)
        val redSynergy = calculateSynergyScore(redChamps, patchMeta)
        val deltaSynergy = blueSynergy - redSynergy

        // 7. Lane Matchup Counters (36)
        val deltaMatchup = calculateMatchupDelta(blueSelections, redSelections, patchMeta)

        // 8. Team Historical Rating & Dominance (37..38)
        val blueTeamWinRate = blueTeamProfile?.sidePreference?.overallRecord?.winRate ?: 0.50
        val redTeamWinRate = redTeamProfile?.sidePreference?.overallRecord?.winRate ?: 0.50
        val deltaTeamRating = blueTeamWinRate - redTeamWinRate

        val blueDominance = blueTeamProfile?.earlyGameMetrics?.dominanceScore ?: 5.0
        val redDominance = redTeamProfile?.earlyGameMetrics?.dominanceScore ?: 5.0
        val deltaDominance = blueDominance - redDominance

        // 9. Side Advantage & Side Preferences (39..41)
        val sideAdvantage = blueSideAdvantageBias
        val blueSidePref = blueTeamProfile?.sidePreference?.winRateDelta ?: 0.0
        val redSidePref = redTeamProfile?.sidePreference?.winRateDelta ?: 0.0

        // 10. Archetypes (42..46, 47..51)
        val blueArchetypes = countArchetypes(blueProfiles)
        val redArchetypes = countArchetypes(redProfiles)

        // Assemble 52-dimensional FloatArray
        val values = FloatArray(DraftFeatures.FEATURE_COUNT)
        // 0..4
        values[0] = blueRadar.laningStrength.toFloat()
        values[1] = blueRadar.engage.toFloat()
        values[2] = blueRadar.disengage.toFloat()
        values[3] = blueRadar.waveclear.toFloat()
        values[4] = blueRadar.lateGameScaling.toFloat()
        // 5..9
        values[5] = redRadar.laningStrength.toFloat()
        values[6] = redRadar.engage.toFloat()
        values[7] = redRadar.disengage.toFloat()
        values[8] = redRadar.waveclear.toFloat()
        values[9] = redRadar.lateGameScaling.toFloat()
        // 10..14
        values[10] = radarDelta.laningStrength.toFloat()
        values[11] = radarDelta.engage.toFloat()
        values[12] = radarDelta.disengage.toFloat()
        values[13] = radarDelta.waveclear.toFloat()
        values[14] = radarDelta.lateGameScaling.toFloat()
        // 15..17
        values[15] = blueDamage.physicalRatio.toFloat()
        values[16] = blueDamage.magicRatio.toFloat()
        values[17] = blueDamage.trueRatio.toFloat()
        // 18..20
        values[18] = redDamage.physicalRatio.toFloat()
        values[19] = redDamage.magicRatio.toFloat()
        values[20] = redDamage.trueRatio.toFloat()
        // 21..23
        values[21] = blueDurability.toFloat()
        values[22] = redDurability.toFloat()
        values[23] = deltaDurability.toFloat()
        // 24..26
        values[24] = blueCcScore.toFloat()
        values[25] = redCcScore.toFloat()
        values[26] = deltaCcScore.toFloat()
        // 27..29
        values[27] = blueTierScore.toFloat()
        values[28] = redTierScore.toFloat()
        values[29] = deltaTierScore.toFloat()
        // 30..32
        values[30] = blueMetaWinRate.toFloat()
        values[31] = redMetaWinRate.toFloat()
        values[32] = deltaMetaWinRate.toFloat()
        // 33..35
        values[33] = blueSynergy.toFloat()
        values[34] = redSynergy.toFloat()
        values[35] = deltaSynergy.toFloat()
        // 36
        values[36] = deltaMatchup.toFloat()
        // 37..38
        values[37] = deltaTeamRating.toFloat()
        values[38] = deltaDominance.toFloat()
        // 39..41
        values[39] = sideAdvantage.toFloat()
        values[40] = blueSidePref.toFloat()
        values[41] = redSidePref.toFloat()
        // 42..46 Blue archetypes
        values[42] = (blueArchetypes["tank"] ?: 0).toFloat()
        values[43] = (blueArchetypes["marksman"] ?: 0).toFloat()
        values[44] = (blueArchetypes["mage"] ?: 0).toFloat()
        values[45] = (blueArchetypes["assassin"] ?: 0).toFloat()
        values[46] = (blueArchetypes["enchanter"] ?: 0).toFloat()
        // 47..51 Red archetypes
        values[47] = (redArchetypes["tank"] ?: 0).toFloat()
        values[48] = (redArchetypes["marksman"] ?: 0).toFloat()
        values[49] = (redArchetypes["mage"] ?: 0).toFloat()
        values[50] = (redArchetypes["assassin"] ?: 0).toFloat()
        values[51] = (redArchetypes["enchanter"] ?: 0).toFloat()

        // Compute 21-dimensional objective empirical vector for Hybrid ONNX model
        val empiricalValues = FloatArray(DraftFeatures.EMPIRICAL_FEATURE_COUNT)
        for (i in 0 until 5) {
            val c = blueChamps.getOrNull(i)
            empiricalValues[i] = empiricalRegistry.getChampId(c).toFloat()
        }
        for (i in 0 until 5) {
            val c = redChamps.getOrNull(i)
            empiricalValues[5 + i] = empiricalRegistry.getChampId(c).toFloat()
        }

        val blueEmpStats = blueChamps.mapNotNull { empiricalRegistry.getStats(it) }
        val redEmpStats = redChamps.mapNotNull { empiricalRegistry.getStats(it) }

        fun avgEmp(list: List<ChampionEmpiricalStats>, default: Double, selector: (ChampionEmpiricalStats) -> Double): Double =
            if (list.isEmpty()) default else list.map(selector).average()

        val bWr = avgEmp(blueEmpStats, 0.5) { it.smoothedWinRate }
        val rWr = avgEmp(redEmpStats, 0.5) { it.smoothedWinRate }
        empiricalValues[10] = (bWr - rWr).toFloat()

        val bGd = avgEmp(blueEmpStats, 0.0) { it.smoothedGd15 }
        val rGd = avgEmp(redEmpStats, 0.0) { it.smoothedGd15 }
        empiricalValues[11] = (bGd - rGd).toFloat()

        val bCsd = avgEmp(blueEmpStats, 0.0) { it.smoothedCsd15 }
        val rCsd = avgEmp(redEmpStats, 0.0) { it.smoothedCsd15 }
        empiricalValues[12] = (bCsd - rCsd).toFloat()

        val bDpm = avgEmp(blueEmpStats, 500.0) { it.smoothedDpm }
        val rDpm = avgEmp(redEmpStats, 500.0) { it.smoothedDpm }
        empiricalValues[13] = (bDpm - rDpm).toFloat()

        val bDtpm = avgEmp(blueEmpStats, 600.0) { it.smoothedDtpm }
        val rDtpm = avgEmp(redEmpStats, 600.0) { it.smoothedDtpm }
        empiricalValues[14] = (bDtpm - rDtpm).toFloat()

        val bDmpm = avgEmp(blueEmpStats, 600.0) { it.smoothedDmpm }
        val rDmpm = avgEmp(redEmpStats, 600.0) { it.smoothedDmpm }
        empiricalValues[15] = (bDmpm - rDmpm).toFloat()

        val bFt = avgEmp(blueEmpStats, 0.5) { it.firstTowerRate }
        val rFt = avgEmp(redEmpStats, 0.5) { it.firstTowerRate }
        empiricalValues[16] = (bFt - rFt).toFloat()

        val bFd = avgEmp(blueEmpStats, 0.5) { it.firstDragonRate }
        val rFd = avgEmp(redEmpStats, 0.5) { it.firstDragonRate }
        empiricalValues[17] = (bFd - rFd).toFloat()

        fun calcEmpSynergy(picks: List<String>): Double {
            if (picks.size < 2) return 0.5
            val scores = mutableListOf<Double>()
            for (i in 0 until picks.size) {
                for (j in i + 1 until picks.size) {
                    scores.add(empiricalRegistry.getSynergy(picks[i], picks[j]))
                }
            }
            return if (scores.isEmpty()) 0.5 else scores.average()
        }
        empiricalValues[18] = (calcEmpSynergy(blueChamps) - calcEmpSynergy(redChamps)).toFloat()

        val minEmpPicks = minOf(blueChamps.size, redChamps.size)
        val counterWrs = mutableListOf<Double>()
        val counterGds = mutableListOf<Double>()
        for (i in 0 until minEmpPicks) {
            val info = empiricalRegistry.getCounter(blueChamps[i], redChamps[i])
            counterWrs.add(info.winRateAdvantage)
            counterGds.add(info.gd15Advantage)
        }
        empiricalValues[19] = (if (counterWrs.isEmpty()) 0.0 else counterWrs.average()).toFloat()
        empiricalValues[20] = (if (counterGds.isEmpty()) 0.0 else counterGds.average()).toFloat()

        return DraftFeatures(
            values = values,
            blueRadar = blueRadar,
            redRadar = redRadar,
            radarDelta = radarDelta,
            blueDamageProfile = blueDamage,
            redDamageProfile = redDamage,
            blueDurability = blueDurability,
            redDurability = redDurability,
            blueCcScore = blueCcScore,
            redCcScore = redCcScore,
            blueMetaTierScore = blueTierScore,
            redMetaTierScore = redTierScore,
            blueMetaWinRate = blueMetaWinRate,
            redMetaWinRate = redMetaWinRate,
            blueSynergyScore = blueSynergy,
            redSynergyScore = redSynergy,
            synergyDelta = deltaSynergy,
            matchupDelta = deltaMatchup,
            teamRatingDelta = deltaTeamRating,
            earlyDominanceDelta = deltaDominance,
            sideAdvantage = sideAdvantage,
            blueSidePreferenceDelta = blueSidePref,
            redSidePreferenceDelta = redSidePref,
            blueArchetypes = blueArchetypes,
            redArchetypes = redArchetypes,
            empiricalValues = empiricalValues,
        )
    }

    private fun calculateMetaStats(
        champions: List<String>,
        patchMeta: PatchMetaMatrix?,
    ): Pair<Double, Double> {
        if (champions.isEmpty()) return 2.0 to 0.50
        if (patchMeta == null) return 2.0 to 0.50

        var tierSum = 0.0
        var winRateSum = 0.0
        var count = 0

        for (champ in champions) {
            val stats = patchMeta.getStats(champ)
            val tierWeight =
                when (stats?.tier) {
                    MetaTier.T0 -> 4.0
                    MetaTier.T1 -> 3.0
                    MetaTier.T2 -> 2.0
                    MetaTier.T3 -> 1.0
                    MetaTier.T4 -> 0.0
                    null -> 2.0
                }
            val winRate = stats?.winRate ?: 0.50
            tierSum += tierWeight
            winRateSum += winRate
            count++
        }

        return if (count > 0) (tierSum / count) to (winRateSum / count) else 2.0 to 0.50
    }

    private fun calculateSynergyScore(
        champions: List<String>,
        patchMeta: PatchMetaMatrix?,
    ): Double {
        if (patchMeta == null || champions.size < 2) return 0.0
        var totalSynergy = 0.0
        val slugs = champions.map { ChampionNormalizer.toSlug(it) }

        for (i in 0 until slugs.size) {
            for (j in i + 1 until slugs.size) {
                val s1 = slugs[i]
                val s2 = slugs[j]
                val syn =
                    patchMeta.synergies.find {
                        (ChampionNormalizer.toSlug(it.championA) == s1 && ChampionNormalizer.toSlug(it.championB) == s2) ||
                            (ChampionNormalizer.toSlug(it.championA) == s2 && ChampionNormalizer.toSlug(it.championB) == s1)
                    }
                if (syn != null) {
                    totalSynergy += syn.synergyScore
                }
            }
        }
        return totalSynergy
    }

    private fun calculateMatchupDelta(
        blueSelections: List<PickSelection>,
        redSelections: List<PickSelection>,
        patchMeta: PatchMetaMatrix?,
    ): Double {
        if (patchMeta == null) return 0.0
        var delta = 0.0

        for (blue in blueSelections) {
            val matchedRed =
                if (blue.role != null) {
                    redSelections.find { it.role == blue.role }
                } else {
                    null
                }

            if (matchedRed != null) {
                val blueCounter = patchMeta.getMatchup(blue.championId, matchedRed.championId, blue.role)
                val redCounter = patchMeta.getMatchup(matchedRed.championId, blue.championId, matchedRed.role)

                if (blueCounter != null) {
                    delta += blueCounter.counterScore
                } else if (redCounter != null) {
                    delta -= redCounter.counterScore
                }
            }
        }
        return delta
    }

    private fun countArchetypes(profiles: List<ChampionProfile>): Map<String, Int> {
        var tanks = 0
        var marksmen = 0
        var mages = 0
        var assassins = 0
        var enchanters = 0

        for (p in profiles) {
            if (p.tags.contains(ChampionTag.VANGUARD_TANK) ||
                p.tags.contains(ChampionTag.WARDEN_TANK) ||
                p.tags.contains(ChampionTag.JUGGERNAUT) ||
                p.durability.tankinessTier == TankinessTier.FRONTLINE_TANK
            ) {
                tanks++
            }
            if (p.tags.contains(ChampionTag.MARKSMAN) || p.tags.contains(ChampionTag.HYPER_CARRY)) {
                marksmen++
            }
            if (p.tags.contains(ChampionTag.BURST_MAGE) ||
                p.tags.contains(ChampionTag.BATTLEMAGE) ||
                p.tags.contains(ChampionTag.ARTILLERY_MAGE)
            ) {
                mages++
            }
            if (p.tags.contains(ChampionTag.ASSASSIN) || p.tags.contains(ChampionTag.SKIRMISHER)) {
                assassins++
            }
            if (p.tags.contains(ChampionTag.ENCHANTER) || p.tags.contains(ChampionTag.DISENGAGE_PEEL)) {
                enchanters++
            }
        }

        return mapOf(
            "tank" to tanks,
            "marksman" to marksmen,
            "mage" to mages,
            "assassin" to assassins,
            "enchanter" to enchanters,
        )
    }
}

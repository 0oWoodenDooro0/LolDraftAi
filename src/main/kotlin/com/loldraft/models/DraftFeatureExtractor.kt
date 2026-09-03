package com.loldraft.models

import com.loldraft.data.meta.ChampionProfile
import com.loldraft.data.meta.ChampionTag
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.FiveDimensionRadar
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.meta.TankinessTier
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.PickSelection
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.style.TeamTacticalProfile

class DraftFeatureExtractor(
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val blueSideAdvantageBias: Double = 0.03,
) {
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

        // 1. Five Dimension Radar (0..4, 5..9, 10..14)
        val blueRadar = tagRegistry.calculateTeamRadar(blueChamps)
        val redRadar = tagRegistry.calculateTeamRadar(redChamps)
        val radarDelta =
            FiveDimensionRadar(
                laningStrength = blueRadar.laningStrength - redRadar.laningStrength,
                engage = blueRadar.engage - redRadar.engage,
                disengage = blueRadar.disengage - redRadar.disengage,
                waveclear = blueRadar.waveclear - redRadar.waveclear,
                lateGameScaling = blueRadar.lateGameScaling - redRadar.lateGameScaling,
            )

        // 2. Damage Profiles (15..17, 18..20)
        val blueDamage = tagRegistry.calculateTeamDamageSplit(blueChamps)
        val redDamage = tagRegistry.calculateTeamDamageSplit(redChamps)

        // 3. Durability (21..23)
        val blueDurability =
            if (blueProfiles.isEmpty()) 5.0 else blueProfiles.sumOf { it.durability.durabilityScore } / blueProfiles.size
        val redDurability =
            if (redProfiles.isEmpty()) 5.0 else redProfiles.sumOf { it.durability.durabilityScore } / redProfiles.size
        val deltaDurability = blueDurability - redDurability

        // 4. CC Score (24..26)
        val blueCcScore =
            blueProfiles.sumOf {
                it.ccRating.hardCcDurationSeconds + if (it.ccRating.hasReliableHardCc) 1.0 else 0.0
            }
        val redCcScore =
            redProfiles.sumOf {
                it.ccRating.hardCcDurationSeconds + if (it.ccRating.hasReliableHardCc) 1.0 else 0.0
            }
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

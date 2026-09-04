package com.loldraft.models

import com.loldraft.data.meta.ChampionMetaStats
import com.loldraft.data.meta.ChampionSynergy
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.MatchupCounter
import com.loldraft.data.meta.MetaTier
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Team
import com.loldraft.data.style.EarlyGameMetrics
import com.loldraft.data.style.FirstPickAnalysis
import com.loldraft.data.style.GamePace
import com.loldraft.data.style.SidePreference
import com.loldraft.data.style.SideRecord
import com.loldraft.data.style.SideTendency
import com.loldraft.data.style.TacticalStyleMetrics
import com.loldraft.data.style.TeamTacticalProfile
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DraftFeatureExtractorTest {
    private val extractor = DraftFeatureExtractor()

    private fun createStandardDraft(): DraftState {
        // Blue: Aatrox (Top), Sejuani (Jng), Orianna (Mid), Varus (Adc), Nautilus (Sup)
        val bluePicks =
            listOf(
                PickSelection("Aatrox", Role.TOP),
                PickSelection("Sejuani", Role.JUNGLE),
                PickSelection("Orianna", Role.MID),
                PickSelection("Varus", Role.BOT),
                PickSelection("Nautilus", Role.SUPPORT),
            )
        // Red: K'Sante (Top), Vi (Jng), Azir (Mid), Kai'Sa (Adc), Rell (Sup)
        val redPicks =
            listOf(
                PickSelection("K'Sante", Role.TOP),
                PickSelection("Vi", Role.JUNGLE),
                PickSelection("Azir", Role.MID),
                PickSelection("Kai'Sa", Role.BOT),
                PickSelection("Rell", Role.SUPPORT),
            )
        return DraftState(
            bluePicks = bluePicks,
            redPicks = redPicks,
        )
    }

    @Test
    fun `should extract exactly 52 feature dimensions and matching feature names schema`() {
        val draft = createStandardDraft()
        val features = extractor.extract(draft)

        assertEquals(52, DraftFeatures.FEATURE_COUNT)
        assertEquals(52, DraftFeatures.FEATURE_NAMES.size)
        assertEquals(52, features.values.size)
        assertEquals(52, features.toFloatArray().size)
        assertEquals(52, features.toDoubleArray().size)
        assertEquals(52, features.toMap().size)

        // Check feature naming indices
        assertEquals("blue_laning", DraftFeatures.FEATURE_NAMES[0])
        assertEquals("red_laning", DraftFeatures.FEATURE_NAMES[5])
        assertEquals("delta_laning", DraftFeatures.FEATURE_NAMES[10])
        assertEquals("blue_dmg_phys", DraftFeatures.FEATURE_NAMES[15])
        assertEquals("delta_durability", DraftFeatures.FEATURE_NAMES[23])
        assertEquals("delta_meta_tier", DraftFeatures.FEATURE_NAMES[29])
        assertEquals("delta_synergy", DraftFeatures.FEATURE_NAMES[35])
        assertEquals("delta_matchup_counter", DraftFeatures.FEATURE_NAMES[36])
        assertEquals("delta_team_rating", DraftFeatures.FEATURE_NAMES[37])
        assertEquals("side_advantage_bias", DraftFeatures.FEATURE_NAMES[39])
    }

    @Test
    fun `should correctly aggregate five dimension radar and calculate radar delta`() {
        val draft = createStandardDraft()
        val features = extractor.extract(draft)

        val registry = ChampionTagRegistry.createDefault()
        val expectedBlueRadar = registry.calculateTeamRadar(listOf("Aatrox", "Sejuani", "Orianna", "Varus", "Nautilus"))
        val expectedRedRadar = registry.calculateTeamRadar(listOf("K'Sante", "Vi", "Azir", "Kai'Sa", "Rell"))

        assertEquals(expectedBlueRadar.laningStrength, features.blueRadar.laningStrength, 0.01)
        assertEquals(expectedBlueRadar.engage, features.blueRadar.engage, 0.01)
        assertEquals(expectedRedRadar.lateGameScaling, features.redRadar.lateGameScaling, 0.01)

        // Delta = Blue - Red
        val expectedLaningDelta = expectedBlueRadar.laningStrength - expectedRedRadar.laningStrength
        assertEquals(expectedLaningDelta, features.radarDelta.laningStrength, 0.01)
        assertEquals(expectedLaningDelta.toFloat(), features.values[10], 0.01f)
    }

    @Test
    fun `should compute team damage profile, durability, and crowd control metrics`() {
        val draft = createStandardDraft()
        val features = extractor.extract(draft)

        // Blue has Varus, Aatrox (physical) and Orianna, Sejuani (magic) -> mixed damage
        assertTrue(features.blueDamageProfile.physicalRatio in 0.2..0.8)
        assertTrue(features.blueDamageProfile.magicRatio in 0.2..0.8)

        // Durability should be positive
        assertTrue(features.blueDurability > 0.0)
        assertTrue(features.redDurability > 0.0)

        // Both teams have Sejuani/Nautilus or Rell/Vi (heavy CC)
        assertTrue(features.blueCcScore > 0.0)
        assertTrue(features.redCcScore > 0.0)
    }

    @Test
    fun `should incorporate patch meta tiers, win rates, and synergies`() {
        val draft = createStandardDraft()
        val patchMeta =
            PatchMetaMatrix(
                patch = "14.1",
                totalGames = 100,
                championStats =
                    mapOf(
                        "aatrox" to ChampionMetaStats("aatrox", "14.1", picks = 50, winRate = 0.58, tier = MetaTier.T0),
                        "sejuani" to ChampionMetaStats("sejuani", "14.1", picks = 40, winRate = 0.52, tier = MetaTier.T1),
                        "orianna" to ChampionMetaStats("orianna", "14.1", picks = 60, winRate = 0.55, tier = MetaTier.T0),
                        "varus" to ChampionMetaStats("varus", "14.1", picks = 30, winRate = 0.50, tier = MetaTier.T1),
                        "nautilus" to ChampionMetaStats("nautilus", "14.1", picks = 45, winRate = 0.54, tier = MetaTier.T1),
                        "ksante" to ChampionMetaStats("ksante", "14.1", picks = 20, winRate = 0.44, tier = MetaTier.T3),
                        "vi" to ChampionMetaStats("vi", "14.1", picks = 15, winRate = 0.46, tier = MetaTier.T3),
                        "azir" to ChampionMetaStats("azir", "14.1", picks = 25, winRate = 0.47, tier = MetaTier.T2),
                        "kaisa" to ChampionMetaStats("kaisa", "14.1", picks = 20, winRate = 0.45, tier = MetaTier.T3),
                        "rell" to ChampionMetaStats("rell", "14.1", picks = 15, winRate = 0.48, tier = MetaTier.T3),
                    ),
                synergies =
                    listOf(
                        ChampionSynergy(
                            championA = "Aatrox",
                            championB = "Sejuani",
                            gamesTogether = 20,
                            winsTogether = 14,
                            synergyWinRate = 0.70,
                            expectedWinRate = 0.55,
                            winRateDelta = 0.15,
                            synergyScore = 1.5,
                        ),
                        ChampionSynergy(
                            championA = "Orianna",
                            championB = "Nautilus",
                            gamesTogether = 15,
                            winsTogether = 10,
                            synergyWinRate = 0.66,
                            expectedWinRate = 0.54,
                            winRateDelta = 0.12,
                            synergyScore = 1.2,
                        ),
                    ),
            )

        val features = extractor.extract(draft, patchMeta = patchMeta)

        // Blue has T0 and T1 champions -> high tier score and win rate
        assertTrue(features.blueMetaTierScore > features.redMetaTierScore)
        assertTrue(features.blueMetaWinRate > features.redMetaWinRate)
        assertTrue(features.blueSynergyScore > features.redSynergyScore)
        assertTrue(features.synergyDelta > 0.0)
    }

    @Test
    fun `should incorporate lane matchup counter deltas from patch meta`() {
        val draft = createStandardDraft()
        val patchMeta =
            PatchMetaMatrix(
                patch = "14.1",
                totalGames = 50,
                championStats = emptyMap(),
                matchupCounters =
                    listOf(
                        // Aatrox counters K'Sante in top lane
                        MatchupCounter(
                            champion = "Aatrox",
                            opponent = "K'Sante",
                            role = Role.TOP,
                            gamesFaced = 15,
                            wins = 11,
                            losses = 4,
                            winRate = 0.733,
                            winRateDelta = 0.233,
                            avgGoldDiffAt15 = 450.0,
                            counterScore = 2.3,
                        ),
                    ),
            )

        val features = extractor.extract(draft, patchMeta = patchMeta)
        assertTrue(features.matchupDelta > 0.0)
    }

    @Test
    fun `should incorporate historical team win rate delta, dominance delta, and side preference`() {
        val draft = createStandardDraft()
        val blueProfile =
            TeamTacticalProfile(
                team = Team("t1", "T1", "T1", "LCK"),
                totalGamesAnalyzed = 40,
                sidePreference =
                    SidePreference(
                        blueRecord = SideRecord(20, 16, 4, 0.80),
                        redRecord = SideRecord(20, 12, 8, 0.60),
                        overallRecord = SideRecord(40, 28, 12, 0.70),
                        winRateDelta = 0.20,
                        blueRate = 0.5,
                        redRate = 0.5,
                        tendency = SideTendency.BLUE_FAVORED,
                    ),
                earlyGameMetrics =
                    EarlyGameMetrics(
                        firstBloodRate = 0.65,
                        firstDragonRate = 0.60,
                        avgGoldDiffAt15 = 850.0,
                        gamesSampled = 40,
                        dominanceScore = 7.5,
                    ),
                tacticalStyleMetrics =
                    TacticalStyleMetrics(
                        teamKillsPerMinute = 0.6,
                        combinedKillsPerMinute = 1.1,
                        avgDurationSeconds = 1850.0,
                        avgDurationFormatted = "30:50",
                        pace = GamePace.FAST_PACED,
                        aggression = com.loldraft.data.style.AggressionLevel.VERY_AGGRESSIVE,
                    ),
                firstPickAnalysis = FirstPickAnalysis(emptyList(), emptyList(), emptyMap()),
                tags = emptySet(),
            )

        val redProfile =
            TeamTacticalProfile(
                team = Team("kdf", "KDF", "KDF", "LCK"),
                totalGamesAnalyzed = 40,
                sidePreference =
                    SidePreference(
                        blueRecord = SideRecord(20, 8, 12, 0.40),
                        redRecord = SideRecord(20, 6, 14, 0.30),
                        overallRecord = SideRecord(40, 14, 26, 0.35),
                        winRateDelta = 0.10,
                        blueRate = 0.5,
                        redRate = 0.5,
                        tendency = SideTendency.BALANCED,
                    ),
                earlyGameMetrics =
                    EarlyGameMetrics(
                        firstBloodRate = 0.35,
                        firstDragonRate = 0.40,
                        avgGoldDiffAt15 = -600.0,
                        gamesSampled = 40,
                        dominanceScore = 3.5,
                    ),
                tacticalStyleMetrics =
                    TacticalStyleMetrics(
                        teamKillsPerMinute = 0.3,
                        combinedKillsPerMinute = 0.8,
                        avgDurationSeconds = 2100.0,
                        avgDurationFormatted = "35:00",
                        pace = GamePace.SLOW_CONTROLLED,
                        aggression = com.loldraft.data.style.AggressionLevel.CONTROL_ORIENTED,
                    ),
                firstPickAnalysis = FirstPickAnalysis(emptyList(), emptyList(), emptyMap()),
                tags = emptySet(),
            )

        val features =
            extractor.extract(
                draftState = draft,
                blueTeamProfile = blueProfile,
                redTeamProfile = redProfile,
            )

        // T1 (0.70) vs KDF (0.35) -> teamRatingDelta = +0.35
        assertEquals(0.35, features.teamRatingDelta, 0.01)
        // Dominance: 7.5 - 3.5 = +4.0
        assertEquals(4.0, features.earlyDominanceDelta, 0.01)
        // Blue side advantage bias is present
        assertEquals(0.03, features.sideAdvantage, 0.001)
    }

    @Test
    fun `should guarantee symmetry when draft compositions are mirrored`() {
        val champs = listOf("Aatrox", "Sejuani", "Orianna", "Varus", "Nautilus")
        val mirroredDraft =
            DraftState(
                bluePicks = champs.map { PickSelection(it) },
                redPicks = champs.map { PickSelection(it) },
            )

        val features = extractor.extract(mirroredDraft)

        // Team-specific features should be identical
        assertEquals(features.blueRadar.laningStrength, features.redRadar.laningStrength, 0.001)
        assertEquals(features.blueRadar.lateGameScaling, features.redRadar.lateGameScaling, 0.001)
        assertEquals(features.blueDurability, features.redDurability, 0.001)
        assertEquals(features.blueCcScore, features.redCcScore, 0.001)

        // Delta features must be zero
        assertEquals(0.0, features.radarDelta.laningStrength, 0.001)
        assertEquals(0.0, features.radarDelta.engage, 0.001)
        assertEquals(0.0, features.synergyDelta, 0.001)
        assertEquals(0.0, features.matchupDelta, 0.001)
        assertEquals(0.0, features.teamRatingDelta, 0.001)

        // But side advantage bias remains for blue side
        assertEquals(0.03, features.sideAdvantage, 0.001)
    }

    @Test
    fun `should gracefully handle unknown champions and partial draft picks`() {
        val partialDraft =
            DraftState(
                bluePicks = listOf(PickSelection("UnknownChampion123"), PickSelection("Aatrox")),
                redPicks = listOf(PickSelection("NonExistentHero")),
            )

        val features = extractor.extract(partialDraft)

        assertNotNull(features)
        assertEquals(52, features.values.size)
        // Values must not contain NaN or Infinite
        assertTrue(features.values.all { !it.isNaN() && !it.isInfinite() })
    }

    @Test
    fun `should correctly count champion archetypes across both teams`() {
        // Blue: Aatrox (Juggernaut), Sejuani (Tank), Orianna (Mage), Varus (Marksman), Nautilus (Tank)
        val draft = createStandardDraft()
        val features = extractor.extract(draft)

        assertTrue(features.blueArchetypes["tank"] ?: 0 >= 1)
        assertTrue(features.blueArchetypes["marksman"] ?: 0 >= 1)
        assertTrue(features.redArchetypes["marksman"] ?: 0 >= 1)
    }

    @Test
    fun `partial draft should use role prior imputation to prevent stat distortion`() {
        // Poppy is an extreme tank (durability 8.8). In a 1-pick partial draft without imputation,
        // team durability was 8.8. With prior imputation (4 unpicked slots imputed with ~5.8 prior),
        // the team durability should stay balanced between 6.0 and 7.0.
        val singleTankDraft = DraftState(
            bluePicks = listOf(PickSelection("Poppy", Role.TOP)),
            redPicks = emptyList(),
        )

        val features = extractor.extract(singleTankDraft)

        // Durability of 1 tank + 4 priors should not blow up to 8.8
        assertTrue(
            features.blueDurability in 6.0..7.2,
            "Blue durability for 1 pick should be mitigated by prior imputation (expected 6.0..7.2), was: ${features.blueDurability}"
        )
        // Red with 0 picks should sit at prior baseline (~5.8)
        assertTrue(
            features.redDurability in 5.5..6.2,
            "Red durability with 0 picks should sit near baseline prior (expected 5.5..6.2), was: ${features.redDurability}"
        )
        // Delta durability should be reasonable (~0.6), not massive (> 3.0)
        assertTrue(
            features.values[23] < 1.5f,
            "Delta durability for 1 tank should be moderated by remaining slots, was: ${features.values[23]}"
        )
    }

    @Test
    fun `empirical profile resolution should extract objective features without champion tags`() {
        val empiricalExtractor = DraftFeatureExtractor(useEmpiricalProfiles = true)
        val draft = createStandardDraft()
        val features = empiricalExtractor.extract(draft)

        assertEquals(52, features.values.size)
        assertTrue(features.blueDamageProfile.physicalRatio > 0.1)
        assertTrue(features.blueDamageProfile.magicRatio > 0.1)
        assertTrue(features.blueDurability > 0.0)
        assertTrue(features.blueCcScore > 0.0)
    }
}


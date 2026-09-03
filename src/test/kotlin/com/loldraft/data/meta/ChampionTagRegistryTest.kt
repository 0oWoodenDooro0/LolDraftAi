package com.loldraft.data.meta

import com.loldraft.data.models.Role
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ChampionTagRegistryTest {
    private lateinit var registry: ChampionTagRegistry

    @BeforeEach
    fun setUp() {
        registry = ChampionTagRegistry.createDefault()
    }

    @Test
    fun `test baseline champions exist with valid multi-dimensional attributes`() {
        val championsToCheck = listOf("Aatrox", "Orianna", "Jinx", "Nautilus", "Sejuani", "Renekton", "Azir")

        for (champName in championsToCheck) {
            val profile = registry.getProfile(champName)
            assertNotNull(profile, "Profile for $champName should exist in baseline registry")
            profile!!

            // Damage split sum should equal approximately 1.0 (allowing small floating point delta)
            val damageSum = profile.damageProfile.physicalRatio + profile.damageProfile.magicRatio + profile.damageProfile.trueRatio
            assertEquals(1.0, damageSum, 0.05, "Damage split for $champName should sum to ~1.0")

            // Durability score between 0.0 and 10.0
            assertTrue(profile.durability.durabilityScore in 0.0..10.0, "Durability score for $champName must be within 0..10")

            // 5D radar values in 0.0..10.0
            val radar = profile.radar
            assertTrue(radar.laningStrength in 0.0..10.0)
            assertTrue(radar.engage in 0.0..10.0)
            assertTrue(radar.disengage in 0.0..10.0)
            assertTrue(radar.waveclear in 0.0..10.0)
            assertTrue(radar.lateGameScaling in 0.0..10.0)

            // Tags should not be empty
            assertFalse(profile.tags.isEmpty(), "$champName should have assigned tags")
        }
    }

    @Test
    fun `test name normalization resolves aliases and accents`() {
        val kaisaLower = registry.getProfile("kaisa")
        val kaisaProper = registry.getProfile("Kai'Sa")
        assertNotNull(kaisaLower)
        assertNotNull(kaisaProper)
        assertEquals("Kai'Sa", kaisaLower?.displayName)
        assertEquals(kaisaProper?.championId, kaisaLower?.championId)

        val wukongAlias = registry.getProfile("monkeyking")
        assertNotNull(wukongAlias)
        assertEquals("Wukong", wukongAlias?.displayName)

        val nunuAlias = registry.getProfile("nunu")
        assertNotNull(nunuAlias)
        assertEquals("Nunu & Willump", nunuAlias?.displayName)

        val leblanc = registry.getProfile("leblanc")
        assertNotNull(leblanc)
        assertEquals("LeBlanc", leblanc?.displayName)
    }

    @Test
    fun `test custom champion registration and override`() {
        val custom =
            ChampionProfile(
                championId = "custom_hero",
                displayName = "Custom Hero",
                primaryRole = Role.MID,
                secondaryRoles = setOf(Role.TOP),
                damageProfile = DamageProfile(0.8, 0.2, 0.0, DamageType.PHYSICAL),
                ccRating = CrowdControlRating(2.5, true, CcTier.MODERATE),
                durability = DurabilityProfile(6.0, TankinessTier.BRUISER),
                radar =
                    FiveDimensionRadar(
                        laningStrength = 8.0,
                        engage = 7.0,
                        disengage = 4.0,
                        waveclear = 7.5,
                        lateGameScaling = 6.5,
                    ),
                powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                tags = setOf(ChampionTag.DIVER, ChampionTag.EARLY_BULLY),
            )

        registry.registerProfile(custom)
        val retrieved = registry.getProfile("custom_hero")
        assertNotNull(retrieved)
        assertEquals("Custom Hero", retrieved?.displayName)
        assertEquals(DamageType.PHYSICAL, retrieved?.damageProfile?.primaryType)
    }

    @Test
    fun `test calculate team composition 5D radar`() {
        val teamChamps = listOf("Renekton", "Sejuani", "Orianna", "Jinx", "Nautilus")
        val teamRadar = registry.calculateTeamRadar(teamChamps)

        assertNotNull(teamRadar)
        // Check reasonable aggregated radar values
        assertTrue(teamRadar.laningStrength in 5.0..9.0)
        assertTrue(teamRadar.engage in 6.0..10.0) // Sejuani + Nautilus + Renekton + Orianna have high engage
        assertTrue(teamRadar.waveclear in 5.0..10.0) // Orianna + Jinx have strong waveclear
        assertTrue(teamRadar.lateGameScaling in 5.0..9.0) // Jinx scales hard
    }

    @Test
    fun `test calculate team damage split`() {
        val teamChamps = listOf("Renekton", "Sejuani", "Orianna", "Jinx", "Nautilus")
        val split = registry.calculateTeamDamageSplit(teamChamps)

        assertTrue(split.physicalRatio > 0.3, "Team with Jinx and Renekton has significant physical damage")
        assertTrue(split.magicRatio > 0.3, "Team with Orianna and Sejuani has significant magic damage")
        val sum = split.physicalRatio + split.magicRatio + split.trueRatio
        assertEquals(1.0, sum, 0.05)
    }

    @Test
    fun `test find champions by tag or role`() {
        val engageChamps = registry.findByTag(ChampionTag.HARD_ENGAGE)
        assertTrue(engageChamps.any { it.displayName == "Nautilus" || it.displayName == "Malphite" || it.displayName == "Leona" })

        val hyperCarries = registry.findByTag(ChampionTag.HYPER_CARRY)
        assertTrue(hyperCarries.any { it.displayName == "Jinx" || it.displayName == "Kassadin" || it.displayName == "Kayle" })

        val midChamps = registry.findByRole(Role.MID)
        assertTrue(midChamps.any { it.displayName == "Orianna" || it.displayName == "Azir" || it.displayName == "Ahri" })
    }

    @Test
    fun `test JSON serialization and deserialization`() {
        val json = registry.exportToJson()
        assertTrue(json.isNotBlank())
        assertTrue(json.contains("Orianna"))

        val restored = ChampionTagRegistry.fromJson(json)
        assertNotNull(restored.getProfile("Orianna"))
        assertEquals(registry.getAllProfiles().size, restored.getAllProfiles().size)
    }

    @Test
    fun `test unknown champion returns null or fallback`() {
        assertNull(registry.getProfile("NonExistentChampion999"))
        assertNull(registry.getProfile(""))
    }
}

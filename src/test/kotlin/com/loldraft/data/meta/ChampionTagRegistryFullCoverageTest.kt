package com.loldraft.data.meta

import com.loldraft.data.normalization.ChampionNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChampionTagRegistryFullCoverageTest {

    @Test
    fun `ChampionTagRegistry createDefault should cover all 168 canonical champions without missing any`() {
        val registry = ChampionTagRegistry.createDefault()
        val canonicalNames = ChampionNormalizer.getCanonicalNames()

        assertTrue(canonicalNames.size >= 168, "ChampionNormalizer canonical list should have at least 168 champions")
        assertTrue(
            registry.getAllProfiles().size >= canonicalNames.size,
            "Registry should contain at least ${canonicalNames.size} unique profiles, but had ${registry.getAllProfiles().size}"
        )

        for (name in canonicalNames) {
            val profile = registry.getProfile(name)
            assertNotNull(profile, "Profile for canonical champion '$name' must exist in registry")
            profile!!

            // Verify Damage split sum ~= 1.0
            val dmg = profile.damageProfile
            val sum = dmg.physicalRatio + dmg.magicRatio + dmg.trueRatio
            assertEquals(1.0, sum, 0.05, "Damage ratios for '$name' must sum to ~1.0")

            // Verify Radar values
            assertTrue(profile.radar.laningStrength in 1.0..10.0, "laningStrength in 1..10 for $name")
            assertTrue(profile.radar.engage in 1.0..10.0, "engage in 1..10 for $name")
            assertTrue(profile.radar.disengage in 1.0..10.0, "disengage in 1..10 for $name")
            assertTrue(profile.radar.waveclear in 1.0..10.0, "waveclear in 1..10 for $name")
            assertTrue(profile.radar.lateGameScaling in 1.0..10.0, "lateGameScaling in 1..10 for $name")

            // Verify Durability and CC
            assertTrue(profile.durability.durabilityScore in 1.0..10.0, "durabilityScore in 1..10 for $name")
            assertTrue(profile.ccRating.hardCcDurationSeconds >= 0.0, "hardCcDurationSeconds >= 0 for $name")

            // Verify Tags
            assertFalse(profile.tags.isEmpty(), "Champion '$name' must have at least one assigned tag")
        }
    }

    @Test
    fun `Spot check specific previously missing champions across all positions`() {
        val registry = ChampionTagRegistry.createDefault()
        val spotChecks = listOf(
            "Smolder", "Hwei", "Briar", "Aurora", "Naafiri", "Karthus",
            "Vladimir", "Zed", "Twisted Fate", "Darius", "Fiora", "Camille",
            "Irelia", "Viego", "Alistar", "Braum", "Sona", "Soraka",
            "Janna", "Zac", "Rammus", "Vex", "Zoe", "Kha'Zix", "Rengar",
            "Ornn", "Cho'Gath", "Garen", "Sion", "Urgot", "Teemo"
        )

        for (champ in spotChecks) {
            val profile = registry.getProfile(champ)
            assertNotNull(profile, "Spot-checked champion '$champ' must exist in registry")
            assertTrue(profile!!.tags.isNotEmpty())
        }
    }

    @Test
    fun `JSON export and import roundtrip preserves all 168 champions`() {
        val original = ChampionTagRegistry.createDefault()
        val json = original.exportToJson()
        val restored = ChampionTagRegistry.fromJson(json)

        assertEquals(original.getAllProfiles().size, restored.getAllProfiles().size)
        for (name in ChampionNormalizer.getCanonicalNames()) {
            val orig = original.getProfile(name)
            val rest = restored.getProfile(name)
            assertNotNull(orig)
            assertNotNull(rest)
            assertEquals(orig!!.championId, rest!!.championId)
            assertEquals(orig.radar, rest.radar)
            assertEquals(orig.damageProfile, rest.damageProfile)
        }
    }

    @Test
    fun `verify exportToJson serialization in memory without disk file dependency`() {
        val registry = ChampionTagRegistry.createDefault()
        val json = registry.exportToJson()
        assertTrue(json.isNotBlank())
        assertTrue(json.length > 1000)
        val restored = ChampionTagRegistry.fromJson(json)
        assertEquals(registry.getAllProfiles().size, restored.getAllProfiles().size)
    }
}

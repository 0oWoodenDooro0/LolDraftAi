package com.loldraft.data.meta

import com.loldraft.data.models.Role
import com.loldraft.data.normalization.ChampionNormalizer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class ChampionTagRegistry(
    initialProfiles: List<ChampionProfile> = emptyList(),
) {
    private val profilesBySlug = ConcurrentHashMap<String, ChampionProfile>()

    init {
        for (profile in initialProfiles) {
            registerProfile(profile)
        }
    }

    fun registerProfile(profile: ChampionProfile) {
        val slug = ChampionNormalizer.toSlug(profile.championId)
        val nameSlug = ChampionNormalizer.toSlug(profile.displayName)
        profilesBySlug[slug] = profile
        if (nameSlug.isNotBlank() && nameSlug != slug) {
            profilesBySlug[nameSlug] = profile
        }
    }

    fun registerAll(profiles: List<ChampionProfile>) {
        profiles.forEach { registerProfile(it) }
    }

    fun getProfile(championNameOrSlug: String?): ChampionProfile? {
        if (championNameOrSlug.isNullOrBlank()) return null
        val slug = ChampionNormalizer.toSlug(championNameOrSlug)
        profilesBySlug[slug]?.let { return it }

        val normalized = ChampionNormalizer.normalize(championNameOrSlug)
        val normalizedSlug = ChampionNormalizer.toSlug(normalized)
        return profilesBySlug[normalizedSlug]
    }

    fun getAllProfiles(): List<ChampionProfile> = profilesBySlug.values.distinctBy { it.championId }

    fun findByTag(tag: ChampionTag): List<ChampionProfile> = getAllProfiles().filter { it.tags.contains(tag) }

    fun findByRole(role: Role): List<ChampionProfile> =
        getAllProfiles().filter { it.primaryRole == role || it.secondaryRoles.contains(role) }

    fun calculateTeamRadar(championNames: List<String>): FiveDimensionRadar {
        val radars = championNames.mapNotNull { getProfile(it)?.radar }
        return FiveDimensionRadar.average(radars)
    }

    fun calculateTeamDamageSplit(championNames: List<String>): DamageProfile {
        val profiles = championNames.mapNotNull { getProfile(it) }
        if (profiles.isEmpty()) {
            return DamageProfile(0.5, 0.5, 0.0, DamageType.MIXED)
        }

        val count = profiles.size.toDouble()
        val totalPhys = profiles.sumOf { it.damageProfile.physicalRatio } / count
        val totalMagic = profiles.sumOf { it.damageProfile.magicRatio } / count
        val totalTrue = profiles.sumOf { it.damageProfile.trueRatio } / count

        val primary =
            when {
                totalPhys >= 0.65 -> DamageType.PHYSICAL
                totalMagic >= 0.65 -> DamageType.MAGIC
                totalTrue >= 0.50 -> DamageType.TRUE_DAMAGE
                else -> DamageType.MIXED
            }

        return DamageProfile(
            physicalRatio = totalPhys,
            magicRatio = totalMagic,
            trueRatio = totalTrue,
            primaryType = primary,
        )
    }

    fun exportToJson(): String = jsonFormat.encodeToString(getAllProfiles())

    companion object {
        private val jsonFormat =
            Json {
                prettyPrint = true
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

        fun fromJson(json: String): ChampionTagRegistry {
            val profiles = jsonFormat.decodeFromString<List<ChampionProfile>>(json)
            return ChampionTagRegistry(profiles)
        }

        fun createDefault(): ChampionTagRegistry {
            val resourceStream = ChampionTagRegistry::class.java.getResourceAsStream("/data/champion_tags.json")
            if (resourceStream != null) {
                try {
                    val json = resourceStream.bufferedReader().use { it.readText() }
                    return fromJson(json)
                } catch (_: Exception) {
                    // Fall back to ChampionDatabaseBuilder
                }
            }
            return ChampionTagRegistry(ChampionDatabaseBuilder.buildAll())
        }
    }
}

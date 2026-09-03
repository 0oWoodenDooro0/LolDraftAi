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
            val baseline =
                listOf(
                    // --- TOP LANERS ---
                    ChampionProfile(
                        championId = "Aatrox",
                        displayName = "Aatrox",
                        primaryRole = Role.TOP,
                        damageProfile = DamageProfile(0.95, 0.05, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(1.8, true, CcTier.MODERATE),
                        durability = DurabilityProfile(7.5, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(8.0, 7.0, 5.0, 7.5, 7.0),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.JUGGERNAUT, ChampionTag.EARLY_BULLY),
                    ),
                    ChampionProfile(
                        championId = "Renekton",
                        displayName = "Renekton",
                        primaryRole = Role.TOP,
                        damageProfile = DamageProfile(0.90, 0.10, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                        durability = DurabilityProfile(7.8, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(8.8, 6.8, 4.5, 7.5, 5.5),
                        powerSpike = PowerSpikeCurve.EARLY_SPIKE,
                        tags = setOf(ChampionTag.DIVER, ChampionTag.EARLY_BULLY),
                    ),
                    ChampionProfile(
                        championId = "Jax",
                        displayName = "Jax",
                        primaryRole = Role.TOP,
                        secondaryRoles = setOf(Role.JUNGLE),
                        damageProfile = DamageProfile(0.70, 0.30, 0.0, DamageType.MIXED),
                        ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                        durability = DurabilityProfile(7.2, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(7.0, 6.5, 5.0, 5.5, 9.0),
                        powerSpike = PowerSpikeCurve.HYPER_SCALING,
                        tags = setOf(ChampionTag.SKIRMISHER, ChampionTag.SPLIT_PUSHER, ChampionTag.HYPER_CARRY),
                    ),
                    ChampionProfile(
                        championId = "K'Sante",
                        displayName = "K'Sante",
                        primaryRole = Role.TOP,
                        damageProfile = DamageProfile(0.75, 0.15, 0.10, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(2.5, true, CcTier.HEAVY),
                        durability = DurabilityProfile(9.0, TankinessTier.FRONTLINE_TANK),
                        radar = FiveDimensionRadar(7.5, 7.8, 7.2, 6.8, 8.2),
                        powerSpike = PowerSpikeCurve.LATE_GAME_SPIKE,
                        tags = setOf(ChampionTag.WARDEN_TANK, ChampionTag.SKIRMISHER),
                    ),
                    ChampionProfile(
                        championId = "Gnar",
                        displayName = "Gnar",
                        primaryRole = Role.TOP,
                        damageProfile = DamageProfile(0.85, 0.15, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(2.2, true, CcTier.HEAVY),
                        durability = DurabilityProfile(6.8, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(7.5, 8.5, 6.5, 6.5, 7.0),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.HARD_ENGAGE, ChampionTag.EARLY_BULLY),
                    ),
                    ChampionProfile(
                        championId = "Malphite",
                        displayName = "Malphite",
                        primaryRole = Role.TOP,
                        damageProfile = DamageProfile(0.20, 0.80, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(2.2, true, CcTier.HEAVY),
                        durability = DurabilityProfile(9.2, TankinessTier.FRONTLINE_TANK),
                        radar = FiveDimensionRadar(5.5, 9.5, 3.5, 6.0, 7.5),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE),
                    ),
                    ChampionProfile(
                        championId = "Poppy",
                        displayName = "Poppy",
                        primaryRole = Role.TOP,
                        secondaryRoles = setOf(Role.JUNGLE, Role.SUPPORT),
                        damageProfile = DamageProfile(0.80, 0.20, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(2.5, true, CcTier.HEAVY),
                        durability = DurabilityProfile(8.8, TankinessTier.FRONTLINE_TANK),
                        radar = FiveDimensionRadar(6.8, 6.5, 8.8, 5.5, 6.5),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.WARDEN_TANK, ChampionTag.DISENGAGE_PEEL),
                    ),
                    // --- JUNGLE ---
                    ChampionProfile(
                        championId = "Sejuani",
                        displayName = "Sejuani",
                        primaryRole = Role.JUNGLE,
                        secondaryRoles = setOf(Role.TOP),
                        damageProfile = DamageProfile(0.20, 0.80, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(3.0, true, CcTier.HEAVY),
                        durability = DurabilityProfile(9.0, TankinessTier.FRONTLINE_TANK),
                        radar = FiveDimensionRadar(6.0, 8.8, 6.8, 6.0, 7.2),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE),
                    ),
                    ChampionProfile(
                        championId = "Maokai",
                        displayName = "Maokai",
                        primaryRole = Role.JUNGLE,
                        secondaryRoles = setOf(Role.SUPPORT, Role.TOP),
                        damageProfile = DamageProfile(0.15, 0.85, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(3.2, true, CcTier.HEAVY),
                        durability = DurabilityProfile(8.8, TankinessTier.FRONTLINE_TANK),
                        radar = FiveDimensionRadar(6.0, 9.0, 7.5, 6.5, 7.5),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE, ChampionTag.DISENGAGE_PEEL),
                    ),
                    ChampionProfile(
                        championId = "Lee Sin",
                        displayName = "Lee Sin",
                        primaryRole = Role.JUNGLE,
                        damageProfile = DamageProfile(0.90, 0.10, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                        durability = DurabilityProfile(6.2, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(7.8, 8.0, 7.0, 5.5, 5.2),
                        powerSpike = PowerSpikeCurve.EARLY_SPIKE,
                        tags = setOf(ChampionTag.DIVER, ChampionTag.PICK_POTENTIAL, ChampionTag.EARLY_BULLY),
                    ),
                    ChampionProfile(
                        championId = "Vi",
                        displayName = "Vi",
                        primaryRole = Role.JUNGLE,
                        damageProfile = DamageProfile(0.90, 0.10, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(2.5, true, CcTier.HEAVY),
                        durability = DurabilityProfile(7.0, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(6.8, 9.2, 3.8, 6.0, 6.5),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.DIVER, ChampionTag.HARD_ENGAGE, ChampionTag.PICK_POTENTIAL),
                    ),
                    ChampionProfile(
                        championId = "Jarvan IV",
                        displayName = "Jarvan IV",
                        primaryRole = Role.JUNGLE,
                        damageProfile = DamageProfile(0.85, 0.15, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(2.2, true, CcTier.HEAVY),
                        durability = DurabilityProfile(7.5, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(7.2, 9.2, 4.0, 6.2, 6.2),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.DIVER, ChampionTag.HARD_ENGAGE),
                    ),
                    ChampionProfile(
                        championId = "Wukong",
                        displayName = "Wukong",
                        primaryRole = Role.JUNGLE,
                        secondaryRoles = setOf(Role.TOP),
                        damageProfile = DamageProfile(0.90, 0.10, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(2.0, true, CcTier.HEAVY),
                        durability = DurabilityProfile(7.2, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(6.5, 8.8, 5.0, 5.8, 7.0),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.DIVER, ChampionTag.HARD_ENGAGE),
                    ),
                    ChampionProfile(
                        championId = "Nunu & Willump",
                        displayName = "Nunu & Willump",
                        primaryRole = Role.JUNGLE,
                        damageProfile = DamageProfile(0.20, 0.80, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(2.2, true, CcTier.HEAVY),
                        durability = DurabilityProfile(8.0, TankinessTier.FRONTLINE_TANK),
                        radar = FiveDimensionRadar(5.5, 8.0, 5.5, 7.5, 5.5),
                        powerSpike = PowerSpikeCurve.EARLY_SPIKE,
                        tags = setOf(ChampionTag.VANGUARD_TANK),
                    ),
                    // --- MID LANERS ---
                    ChampionProfile(
                        championId = "Orianna",
                        displayName = "Orianna",
                        primaryRole = Role.MID,
                        damageProfile = DamageProfile(0.10, 0.90, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(1.8, true, CcTier.MODERATE),
                        durability = DurabilityProfile(4.0, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(7.5, 8.0, 7.5, 8.8, 8.5),
                        powerSpike = PowerSpikeCurve.LATE_GAME_SPIKE,
                        tags = setOf(ChampionTag.BURST_MAGE, ChampionTag.WAVECLEAR_STALL, ChampionTag.DISENGAGE_PEEL),
                    ),
                    ChampionProfile(
                        championId = "Azir",
                        displayName = "Azir",
                        primaryRole = Role.MID,
                        damageProfile = DamageProfile(0.05, 0.95, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(1.8, true, CcTier.MODERATE),
                        durability = DurabilityProfile(4.2, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(7.2, 8.0, 7.5, 8.5, 9.2),
                        powerSpike = PowerSpikeCurve.HYPER_SCALING,
                        tags = setOf(ChampionTag.BATTLEMAGE, ChampionTag.HYPER_CARRY, ChampionTag.WAVECLEAR_STALL),
                    ),
                    ChampionProfile(
                        championId = "Ahri",
                        displayName = "Ahri",
                        primaryRole = Role.MID,
                        damageProfile = DamageProfile(0.05, 0.80, 0.15, DamageType.MAGIC),
                        ccRating = CrowdControlRating(1.8, true, CcTier.MODERATE),
                        durability = DurabilityProfile(4.0, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(7.5, 7.8, 7.0, 8.0, 7.0),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.BURST_MAGE, ChampionTag.PICK_POTENTIAL),
                    ),
                    ChampionProfile(
                        championId = "Syndra",
                        displayName = "Syndra",
                        primaryRole = Role.MID,
                        damageProfile = DamageProfile(0.05, 0.90, 0.05, DamageType.MAGIC),
                        ccRating = CrowdControlRating(1.8, true, CcTier.MODERATE),
                        durability = DurabilityProfile(3.8, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(8.2, 7.0, 7.2, 8.5, 8.2),
                        powerSpike = PowerSpikeCurve.LATE_GAME_SPIKE,
                        tags = setOf(ChampionTag.BURST_MAGE, ChampionTag.PICK_POTENTIAL),
                    ),
                    ChampionProfile(
                        championId = "Sylas",
                        displayName = "Sylas",
                        primaryRole = Role.MID,
                        secondaryRoles = setOf(Role.TOP, Role.JUNGLE),
                        damageProfile = DamageProfile(0.10, 0.90, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                        durability = DurabilityProfile(6.5, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(6.8, 7.5, 5.0, 6.5, 8.2),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.SKIRMISHER, ChampionTag.DIVER),
                    ),
                    ChampionProfile(
                        championId = "Jayce",
                        displayName = "Jayce",
                        primaryRole = Role.MID,
                        secondaryRoles = setOf(Role.TOP),
                        damageProfile = DamageProfile(0.90, 0.10, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(0.8, true, CcTier.LIGHT),
                        durability = DurabilityProfile(5.0, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(8.0, 5.5, 5.5, 8.5, 7.5),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.POKE, ChampionTag.EARLY_BULLY),
                    ),
                    ChampionProfile(
                        championId = "LeBlanc",
                        displayName = "LeBlanc",
                        primaryRole = Role.MID,
                        damageProfile = DamageProfile(0.05, 0.95, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(1.5, true, CcTier.MODERATE),
                        durability = DurabilityProfile(3.5, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(8.2, 7.2, 6.5, 6.0, 6.8),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.ASSASSIN, ChampionTag.PICK_POTENTIAL),
                    ),
                    ChampionProfile(
                        championId = "Kassadin",
                        displayName = "Kassadin",
                        primaryRole = Role.MID,
                        damageProfile = DamageProfile(0.05, 0.95, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(1.0, true, CcTier.LIGHT),
                        durability = DurabilityProfile(5.5, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(4.5, 7.5, 6.5, 6.0, 9.8),
                        powerSpike = PowerSpikeCurve.HYPER_SCALING,
                        tags = setOf(ChampionTag.ASSASSIN, ChampionTag.HYPER_CARRY),
                    ),
                    // --- BOT LANERS (ADCS) ---
                    ChampionProfile(
                        championId = "Jinx",
                        displayName = "Jinx",
                        primaryRole = Role.BOT,
                        damageProfile = DamageProfile(0.95, 0.05, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(1.5, true, CcTier.LIGHT),
                        durability = DurabilityProfile(3.5, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(6.2, 4.0, 4.5, 8.5, 9.5),
                        powerSpike = PowerSpikeCurve.HYPER_SCALING,
                        tags = setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY, ChampionTag.WAVECLEAR_STALL),
                    ),
                    ChampionProfile(
                        championId = "Kai'Sa",
                        displayName = "Kai'Sa",
                        primaryRole = Role.BOT,
                        damageProfile = DamageProfile(0.55, 0.40, 0.05, DamageType.MIXED),
                        ccRating = CrowdControlRating(0.0, false, CcTier.NONE),
                        durability = DurabilityProfile(4.0, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(6.5, 6.5, 6.0, 7.2, 9.0),
                        powerSpike = PowerSpikeCurve.HYPER_SCALING,
                        tags = setOf(ChampionTag.MARKSMAN, ChampionTag.HYPER_CARRY, ChampionTag.DIVER),
                    ),
                    ChampionProfile(
                        championId = "Varus",
                        displayName = "Varus",
                        primaryRole = Role.BOT,
                        secondaryRoles = setOf(Role.MID),
                        damageProfile = DamageProfile(0.65, 0.35, 0.0, DamageType.MIXED),
                        ccRating = CrowdControlRating(2.0, true, CcTier.HEAVY),
                        durability = DurabilityProfile(3.5, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(8.5, 7.5, 5.0, 8.2, 7.2),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.MARKSMAN, ChampionTag.POKE, ChampionTag.EARLY_BULLY),
                    ),
                    ChampionProfile(
                        championId = "Ashe",
                        displayName = "Ashe",
                        primaryRole = Role.BOT,
                        secondaryRoles = setOf(Role.SUPPORT),
                        damageProfile = DamageProfile(0.85, 0.15, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(3.0, true, CcTier.HEAVY),
                        durability = DurabilityProfile(3.5, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(7.8, 8.5, 6.0, 7.0, 7.5),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.MARKSMAN, ChampionTag.HARD_ENGAGE, ChampionTag.PICK_POTENTIAL),
                    ),
                    ChampionProfile(
                        championId = "Kalista",
                        displayName = "Kalista",
                        primaryRole = Role.BOT,
                        damageProfile = DamageProfile(0.95, 0.05, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(2.0, true, CcTier.HEAVY),
                        durability = DurabilityProfile(3.8, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(9.0, 7.5, 6.0, 6.5, 6.0),
                        powerSpike = PowerSpikeCurve.EARLY_SPIKE,
                        tags = setOf(ChampionTag.MARKSMAN, ChampionTag.EARLY_BULLY),
                    ),
                    ChampionProfile(
                        championId = "Lucian",
                        displayName = "Lucian",
                        primaryRole = Role.BOT,
                        secondaryRoles = setOf(Role.MID),
                        damageProfile = DamageProfile(0.85, 0.15, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(0.0, false, CcTier.NONE),
                        durability = DurabilityProfile(4.0, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(8.5, 5.5, 6.0, 7.0, 7.0),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.MARKSMAN, ChampionTag.EARLY_BULLY),
                    ),
                    ChampionProfile(
                        championId = "Caitlyn",
                        displayName = "Caitlyn",
                        primaryRole = Role.BOT,
                        damageProfile = DamageProfile(0.95, 0.05, 0.0, DamageType.PHYSICAL),
                        ccRating = CrowdControlRating(1.5, true, CcTier.LIGHT),
                        durability = DurabilityProfile(3.5, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(8.8, 4.0, 5.5, 8.0, 8.0),
                        powerSpike = PowerSpikeCurve.LATE_GAME_SPIKE,
                        tags = setOf(ChampionTag.MARKSMAN, ChampionTag.EARLY_BULLY, ChampionTag.POKE),
                    ),
                    // --- SUPPORTS ---
                    ChampionProfile(
                        championId = "Nautilus",
                        displayName = "Nautilus",
                        primaryRole = Role.SUPPORT,
                        damageProfile = DamageProfile(0.15, 0.85, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(3.5, true, CcTier.HEAVY),
                        durability = DurabilityProfile(8.8, TankinessTier.FRONTLINE_TANK),
                        radar = FiveDimensionRadar(7.5, 9.5, 4.5, 4.0, 6.0),
                        powerSpike = PowerSpikeCurve.EARLY_SPIKE,
                        tags = setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE, ChampionTag.PICK_POTENTIAL),
                    ),
                    ChampionProfile(
                        championId = "Leona",
                        displayName = "Leona",
                        primaryRole = Role.SUPPORT,
                        damageProfile = DamageProfile(0.10, 0.90, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(3.5, true, CcTier.HEAVY),
                        durability = DurabilityProfile(9.0, TankinessTier.FRONTLINE_TANK),
                        radar = FiveDimensionRadar(7.2, 9.8, 3.5, 3.5, 6.0),
                        powerSpike = PowerSpikeCurve.EARLY_SPIKE,
                        tags = setOf(ChampionTag.VANGUARD_TANK, ChampionTag.HARD_ENGAGE),
                    ),
                    ChampionProfile(
                        championId = "Rakan",
                        displayName = "Rakan",
                        primaryRole = Role.SUPPORT,
                        damageProfile = DamageProfile(0.10, 0.90, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(2.8, true, CcTier.HEAVY),
                        durability = DurabilityProfile(6.0, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(6.8, 9.5, 8.0, 4.5, 7.5),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.CATCHER, ChampionTag.HARD_ENGAGE, ChampionTag.DISENGAGE_PEEL),
                    ),
                    ChampionProfile(
                        championId = "Thresh",
                        displayName = "Thresh",
                        primaryRole = Role.SUPPORT,
                        damageProfile = DamageProfile(0.20, 0.80, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(2.8, true, CcTier.HEAVY),
                        durability = DurabilityProfile(7.2, TankinessTier.BRUISER),
                        radar = FiveDimensionRadar(7.2, 8.5, 8.2, 4.0, 6.8),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.CATCHER, ChampionTag.PICK_POTENTIAL, ChampionTag.DISENGAGE_PEEL),
                    ),
                    ChampionProfile(
                        championId = "Nami",
                        displayName = "Nami",
                        primaryRole = Role.SUPPORT,
                        damageProfile = DamageProfile(0.05, 0.95, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(2.5, true, CcTier.HEAVY),
                        durability = DurabilityProfile(3.5, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(7.8, 7.2, 8.5, 4.0, 6.5),
                        powerSpike = PowerSpikeCurve.MID_GAME_SPIKE,
                        tags = setOf(ChampionTag.ENCHANTER, ChampionTag.DISENGAGE_PEEL),
                    ),
                    ChampionProfile(
                        championId = "Lulu",
                        displayName = "Lulu",
                        primaryRole = Role.SUPPORT,
                        damageProfile = DamageProfile(0.05, 0.95, 0.0, DamageType.MAGIC),
                        ccRating = CrowdControlRating(2.5, true, CcTier.HEAVY),
                        durability = DurabilityProfile(3.5, TankinessTier.SQUISHY),
                        radar = FiveDimensionRadar(7.5, 5.0, 9.5, 4.0, 8.0),
                        powerSpike = PowerSpikeCurve.LATE_GAME_SPIKE,
                        tags = setOf(ChampionTag.ENCHANTER, ChampionTag.DISENGAGE_PEEL),
                    ),
                )
            return ChampionTagRegistry(baseline)
        }
    }
}

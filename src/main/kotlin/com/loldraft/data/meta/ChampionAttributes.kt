package com.loldraft.data.meta

import com.loldraft.data.models.Role
import kotlinx.serialization.Serializable

@Serializable
enum class DamageType {
    PHYSICAL,
    MAGIC,
    TRUE_DAMAGE,
    MIXED,
}

@Serializable
data class DamageProfile(
    val physicalRatio: Double = 0.0,
    val magicRatio: Double = 0.0,
    val trueRatio: Double = 0.0,
    val primaryType: DamageType = DamageType.PHYSICAL,
)

@Serializable
enum class CcTier {
    HEAVY,
    MODERATE,
    LIGHT,
    NONE,
}

@Serializable
data class CrowdControlRating(
    val hardCcDurationSeconds: Double = 0.0,
    val hasReliableHardCc: Boolean = false,
    val tier: CcTier = CcTier.NONE,
)

@Serializable
enum class TankinessTier {
    FRONTLINE_TANK,
    BRUISER,
    SQUISHY,
}

@Serializable
data class DurabilityProfile(
    val durabilityScore: Double = 5.0,
    val tankinessTier: TankinessTier = TankinessTier.SQUISHY,
)

@Serializable
data class FiveDimensionRadar(
    val laningStrength: Double = 5.0,
    val engage: Double = 5.0,
    val disengage: Double = 5.0,
    val waveclear: Double = 5.0,
    val lateGameScaling: Double = 5.0,
) {
    fun averageWith(others: List<FiveDimensionRadar>): FiveDimensionRadar {
        val all = listOf(this) + others
        return average(all)
    }

    companion object {
        val ZERO = FiveDimensionRadar(0.0, 0.0, 0.0, 0.0, 0.0)

        fun average(radars: Collection<FiveDimensionRadar>): FiveDimensionRadar {
            if (radars.isEmpty()) return ZERO
            val count = radars.size.toDouble()
            return FiveDimensionRadar(
                laningStrength = (radars.sumOf { it.laningStrength } / count).coerceIn(0.0, 10.0),
                engage = (radars.sumOf { it.engage } / count).coerceIn(0.0, 10.0),
                disengage = (radars.sumOf { it.disengage } / count).coerceIn(0.0, 10.0),
                waveclear = (radars.sumOf { it.waveclear } / count).coerceIn(0.0, 10.0),
                lateGameScaling = (radars.sumOf { it.lateGameScaling } / count).coerceIn(0.0, 10.0),
            )
        }
    }
}

@Serializable
enum class PowerSpikeCurve {
    EARLY_SPIKE,
    MID_GAME_SPIKE,
    LATE_GAME_SPIKE,
    HYPER_SCALING,
    BALANCED,
}

@Serializable
enum class ChampionTag {
    // Archetypes
    ASSASSIN,
    SKIRMISHER,
    BURST_MAGE,
    BATTLEMAGE,
    ARTILLERY_MAGE,
    MARKSMAN,
    ENCHANTER,
    CATCHER,
    VANGUARD_TANK,
    WARDEN_TANK,
    JUGGERNAUT,
    DIVER,

    // Tactical traits
    HYPER_CARRY,
    EARLY_BULLY,
    POKE,
    SPLIT_PUSHER,
    GLOBAL_PRESENCE,
    HARD_ENGAGE,
    DISENGAGE_PEEL,
    PICK_POTENTIAL,
    WAVECLEAR_STALL,
}

@Serializable
data class ChampionProfile(
    val championId: String,
    val displayName: String,
    val primaryRole: Role,
    val secondaryRoles: Set<Role> = emptySet(),
    val damageProfile: DamageProfile,
    val ccRating: CrowdControlRating,
    val durability: DurabilityProfile,
    val radar: FiveDimensionRadar,
    val powerSpike: PowerSpikeCurve = PowerSpikeCurve.BALANCED,
    val tags: Set<ChampionTag> = emptySet(),
)

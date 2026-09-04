package com.loldraft.models

import com.loldraft.data.meta.DamageProfile
import com.loldraft.data.meta.FiveDimensionRadar
import kotlinx.serialization.Serializable

@Serializable
data class DraftFeatures(
    val values: FloatArray,
    val blueRadar: FiveDimensionRadar,
    val redRadar: FiveDimensionRadar,
    val radarDelta: FiveDimensionRadar,
    val blueDamageProfile: DamageProfile,
    val redDamageProfile: DamageProfile,
    val blueDurability: Double,
    val redDurability: Double,
    val blueCcScore: Double,
    val redCcScore: Double,
    val blueMetaTierScore: Double,
    val redMetaTierScore: Double,
    val blueMetaWinRate: Double,
    val redMetaWinRate: Double,
    val blueSynergyScore: Double,
    val redSynergyScore: Double,
    val synergyDelta: Double,
    val matchupDelta: Double,
    val teamRatingDelta: Double,
    val earlyDominanceDelta: Double,
    val sideAdvantage: Double,
    val blueSidePreferenceDelta: Double,
    val redSidePreferenceDelta: Double,
    val blueArchetypes: Map<String, Int> = emptyMap(),
    val redArchetypes: Map<String, Int> = emptyMap(),
    val empiricalValues: FloatArray = FloatArray(EMPIRICAL_FEATURE_COUNT),
) {
    val durabilityDelta: Double get() = blueDurability - redDurability
    val ccDelta: Double get() = blueCcScore - redCcScore
    val metaTierDelta: Double get() = blueMetaTierScore - redMetaTierScore
    val metaWinRateDelta: Double get() = blueMetaWinRate - redMetaWinRate

    fun toFloatArray(): FloatArray = values.copyOf()

    fun toDoubleArray(): DoubleArray = DoubleArray(values.size) { values[it].toDouble() }

    fun toMap(): Map<String, Float> =
        FEATURE_NAMES.indices.associate { i ->
            val name = FEATURE_NAMES.getOrElse(i) { "feature_$i" }
            val v = if (i < values.size) values[i] else 0f
            name to v
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DraftFeatures
        return values.contentEquals(other.values) && empiricalValues.contentEquals(other.empiricalValues)
    }

    override fun hashCode(): Int {
        var result = values.contentHashCode()
        result = 31 * result + empiricalValues.contentHashCode()
        return result
    }

    companion object {
        const val FEATURE_COUNT: Int = 52
        const val EMPIRICAL_FEATURE_COUNT: Int = 21

        val EMPIRICAL_FEATURE_NAMES: List<String> =
            listOf(
                "blue_champ_1", "blue_champ_2", "blue_champ_3", "blue_champ_4", "blue_champ_5",
                "red_champ_1", "red_champ_2", "red_champ_3", "red_champ_4", "red_champ_5",
                "delta_win_rate",
                "delta_gd15",
                "delta_csd15",
                "delta_dpm",
                "delta_dtpm",
                "delta_dmpm",
                "delta_first_tower",
                "delta_first_dragon",
                "delta_synergy",
                "delta_counter_winrate",
                "delta_counter_gd15",
            )


        val FEATURE_NAMES: List<String> =
            listOf(
                // 0..4: Blue Radar
                "blue_laning",
                "blue_engage",
                "blue_disengage",
                "blue_waveclear",
                "blue_late_game",
                // 5..9: Red Radar
                "red_laning",
                "red_engage",
                "red_disengage",
                "red_waveclear",
                "red_late_game",
                // 10..14: Radar Delta (Blue - Red)
                "delta_laning",
                "delta_engage",
                "delta_disengage",
                "delta_waveclear",
                "delta_late_game",
                // 15..17: Blue Damage Profile
                "blue_dmg_phys",
                "blue_dmg_magic",
                "blue_dmg_true",
                // 18..20: Red Damage Profile
                "red_dmg_phys",
                "red_dmg_magic",
                "red_dmg_true",
                // 21..23: Durability
                "blue_durability",
                "red_durability",
                "delta_durability",
                // 24..26: Crowd Control
                "blue_cc_score",
                "red_cc_score",
                "delta_cc_score",
                // 27..29: Patch Meta Tier (T0=4, T1=3, T2=2, T3=1, T4=0)
                "blue_meta_tier",
                "red_meta_tier",
                "delta_meta_tier",
                // 30..32: Patch Meta Win Rate
                "blue_meta_winrate",
                "red_meta_winrate",
                "delta_meta_winrate",
                // 33..35: Synergy
                "blue_synergy",
                "red_synergy",
                "delta_synergy",
                // 36: Lane Matchup Counter Delta
                "delta_matchup_counter",
                // 37..38: Team Historical Rating & Early Dominance Delta
                "delta_team_rating",
                "delta_early_dominance",
                // 39..41: Side Advantage & Tendencies
                "side_advantage_bias",
                "blue_side_preference",
                "red_side_preference",
                // 42..46: Blue Archetype counts (Tank, Marksman, Mage, Assassin, Enchanter)
                "blue_count_tank",
                "blue_count_marksman",
                "blue_count_mage",
                "blue_count_assassin",
                "blue_count_enchanter",
                // 47..51: Red Archetype counts
                "red_count_tank",
                "red_count_marksman",
                "red_count_mage",
                "red_count_assassin",
                "red_count_enchanter",
            )
    }
}

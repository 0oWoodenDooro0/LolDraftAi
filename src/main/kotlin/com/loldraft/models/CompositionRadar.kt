package com.loldraft.models

import com.loldraft.data.meta.DamageProfile
import com.loldraft.data.meta.FiveDimensionRadar
import com.loldraft.data.models.Side
import kotlinx.serialization.Serializable
import kotlin.math.max
import kotlin.math.round

@Serializable
enum class RadarDimension {
    LANING,
    ENGAGE,
    WAVECLEAR,
    DAMAGE_BALANCE,
    LATE_SCALING,
}

@Serializable
data class FiveDimensionRadarScores(
    val laning: Double,
    val engage: Double,
    val waveclear: Double,
    val damageBalance: Double,
    val lateScaling: Double,
) {
    fun getScore(dimension: RadarDimension): Double =
        when (dimension) {
            RadarDimension.LANING -> laning
            RadarDimension.ENGAGE -> engage
            RadarDimension.WAVECLEAR -> waveclear
            RadarDimension.DAMAGE_BALANCE -> damageBalance
            RadarDimension.LATE_SCALING -> lateScaling
        }
}

@Serializable
data class CompositionRadarScore(
    val blueRadar: FiveDimensionRadarScores,
    val redRadar: FiveDimensionRadarScores,
    val deltaRadar: FiveDimensionRadarScores,
    val dimensionAdvantages: Map<RadarDimension, Side?>,
)

object CompositionRadarCalculator {
    private const val ADVANTAGE_THRESHOLD: Double = 0.1

    fun calculate(
        blueRadar: FiveDimensionRadar,
        redRadar: FiveDimensionRadar,
        blueDamageProfile: DamageProfile,
        redDamageProfile: DamageProfile,
    ): CompositionRadarScore {
        val blueDmgBalance = calculateDamageBalance(blueDamageProfile)
        val redDmgBalance = calculateDamageBalance(redDamageProfile)

        val blueScores =
            FiveDimensionRadarScores(
                laning = roundToTwoDecimals(blueRadar.laningStrength.coerceIn(0.0, 10.0)),
                engage = roundToTwoDecimals(blueRadar.engage.coerceIn(0.0, 10.0)),
                waveclear = roundToTwoDecimals(blueRadar.waveclear.coerceIn(0.0, 10.0)),
                damageBalance = blueDmgBalance,
                lateScaling = roundToTwoDecimals(blueRadar.lateGameScaling.coerceIn(0.0, 10.0)),
            )

        val redScores =
            FiveDimensionRadarScores(
                laning = roundToTwoDecimals(redRadar.laningStrength.coerceIn(0.0, 10.0)),
                engage = roundToTwoDecimals(redRadar.engage.coerceIn(0.0, 10.0)),
                waveclear = roundToTwoDecimals(redRadar.waveclear.coerceIn(0.0, 10.0)),
                damageBalance = redDmgBalance,
                lateScaling = roundToTwoDecimals(redRadar.lateGameScaling.coerceIn(0.0, 10.0)),
            )

        val deltaScores =
            FiveDimensionRadarScores(
                laning = roundToTwoDecimals(blueScores.laning - redScores.laning),
                engage = roundToTwoDecimals(blueScores.engage - redScores.engage),
                waveclear = roundToTwoDecimals(blueScores.waveclear - redScores.waveclear),
                damageBalance = roundToTwoDecimals(blueScores.damageBalance - redScores.damageBalance),
                lateScaling = roundToTwoDecimals(blueScores.lateScaling - redScores.lateScaling),
            )

        val advantages =
            mapOf(
                RadarDimension.LANING to resolveAdvantage(deltaScores.laning),
                RadarDimension.ENGAGE to resolveAdvantage(deltaScores.engage),
                RadarDimension.WAVECLEAR to resolveAdvantage(deltaScores.waveclear),
                RadarDimension.DAMAGE_BALANCE to resolveAdvantage(deltaScores.damageBalance),
                RadarDimension.LATE_SCALING to resolveAdvantage(deltaScores.lateScaling),
            )

        return CompositionRadarScore(
            blueRadar = blueScores,
            redRadar = redScores,
            deltaRadar = deltaScores,
            dimensionAdvantages = advantages,
        )
    }

    fun calculateDamageBalance(profile: DamageProfile): Double {
        val maxRatio = max(profile.physicalRatio, profile.magicRatio)
        val imbalance = (maxRatio - 0.50).coerceAtLeast(0.0)
        val raw = 10.0 - (imbalance / 0.50) * 7.5 + (profile.trueRatio * 5.0)
        return roundToTwoDecimals(raw.coerceIn(0.0, 10.0))
    }

    private fun resolveAdvantage(delta: Double): Side? =
        when {
            delta > ADVANTAGE_THRESHOLD -> Side.BLUE
            delta < -ADVANTAGE_THRESHOLD -> Side.RED
            else -> null
        }

    private fun roundToTwoDecimals(value: Double): Double = round(value * 100.0) / 100.0
}

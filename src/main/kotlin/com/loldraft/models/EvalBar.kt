package com.loldraft.models

import com.loldraft.data.models.Side
import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.round

@Serializable
enum class EvalLeadCategory {
    EVEN,
    SLIGHT_BLUE,
    BLUE_ADVANTAGE,
    BLUE_DECISIVE,
    SLIGHT_RED,
    RED_ADVANTAGE,
    RED_DECISIVE,
}

@Serializable
data class EvalBarScore(
    val score: Double,
    val formattedScore: String,
    val favoredSide: Side?,
    val blueBarPercentage: Double,
    val redBarPercentage: Double,
    val leadCategory: EvalLeadCategory,
)

object EvalBarCalculator {
    const val DEFAULT_SCALE: Double = 1.5
    const val DEFAULT_MAX_EVAL: Double = 10.0

    private const val EPSILON: Double = 1e-7

    fun calculate(
        blueWinRate: Double,
        scale: Double = DEFAULT_SCALE,
        maxEval: Double = DEFAULT_MAX_EVAL,
    ): EvalBarScore {
        val clampedP = blueWinRate.coerceIn(0.0, 1.0)
        val blueBarPercentage = clampedP * 100.0
        val redBarPercentage = 100.0 - blueBarPercentage

        val safeP = blueWinRate.coerceIn(EPSILON, 1.0 - EPSILON)
        val score =
            if (abs(clampedP - 0.50) < 1e-9) {
                0.0
            } else {
                val logit = ln(safeP / (1.0 - safeP))
                (scale * logit).coerceIn(-maxEval, maxEval)
            }

        val formatted = format(score)

        val favoredSide =
            when {
                score > 1e-6 -> Side.BLUE
                score < -1e-6 -> Side.RED
                else -> null
            }

        val leadCategory =
            when {
                abs(score) < 0.05 -> EvalLeadCategory.EVEN
                score > 1.5 -> EvalLeadCategory.BLUE_DECISIVE
                score > 0.4 -> EvalLeadCategory.BLUE_ADVANTAGE
                score > 0.0 -> EvalLeadCategory.SLIGHT_BLUE
                score < -1.5 -> EvalLeadCategory.RED_DECISIVE
                score < -0.4 -> EvalLeadCategory.RED_ADVANTAGE
                else -> EvalLeadCategory.SLIGHT_RED
            }

        return EvalBarScore(
            score = score,
            formattedScore = formatted,
            favoredSide = favoredSide,
            blueBarPercentage = blueBarPercentage,
            redBarPercentage = redBarPercentage,
            leadCategory = leadCategory,
        )
    }

    fun evalToWinRate(
        score: Double,
        scale: Double = DEFAULT_SCALE,
    ): Double {
        val logit = score / scale
        return 1.0 / (1.0 + exp(-logit))
    }

    fun format(score: Double): String {
        val rounded = round(score * 10.0) / 10.0
        return when {
            abs(rounded) < 1e-9 -> "0.0"
            rounded > 0.0 -> "+${String.format(Locale.US, "%.1f", rounded)}"
            else -> "-${String.format(Locale.US, "%.1f", abs(rounded))}"
        }
    }
}

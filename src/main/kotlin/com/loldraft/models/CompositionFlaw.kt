package com.loldraft.models

import com.loldraft.data.models.Side
import kotlinx.serialization.Serializable

@Serializable
enum class FlawCategory {
    DAMAGE_PROFILE,
    ENGAGE_FRONTLINE,
    WAVECLEAR,
    TEMPO_DISCONNECT,
}

@Serializable
enum class FlawSeverity {
    INFO,
    WARNING,
    CRITICAL,
}

@Serializable
data class CompositionFlaw(
    val id: String,
    val category: FlawCategory,
    val severity: FlawSeverity,
    val title: String,
    val description: String,
    val affectedSide: Side,
    val currentPicksCount: Int,
    val suggestion: String,
    val metrics: Map<String, Double> = emptyMap(),
)

@Serializable
data class CompositionFlawReport(
    val side: Side,
    val picks: List<String>,
    val flaws: List<CompositionFlaw> = emptyList(),
    val hasCriticalFlaws: Boolean = false,
    val flawCountByCategory: Map<FlawCategory, Int> = emptyMap(),
    val overallHealthScore: Double = 100.0,
) {
    fun getFlawsByCategory(category: FlawCategory): List<CompositionFlaw> = flaws.filter { it.category == category }

    fun getFlawsBySeverity(severity: FlawSeverity): List<CompositionFlaw> = flaws.filter { it.severity == severity }
}

@Serializable
data class DraftFlawAnalysisResult(
    val blueReport: CompositionFlawReport,
    val redReport: CompositionFlawReport,
    val allFlaws: List<CompositionFlaw> = emptyList(),
)

@Serializable
data class FlawDetectionConfig(
    val minPicksForDamageImbalance: Int = 3,
    val physicalRatioWarningThreshold: Double = 0.80,
    val physicalRatioCriticalThreshold: Double = 0.88,
    val magicRatioDeficitThreshold: Double = 0.15,
    val magicRatioWarningThreshold: Double = 0.80,
    val magicRatioCriticalThreshold: Double = 0.88,
    val minPicksForFrontlineCc: Int = 3,
    val minHardCcDurationSeconds: Double = 2.0,
    val minDurabilityScore: Double = 4.5,
    val minEngageScore: Double = 4.5,
    val minWaveclearScore: Double = 5.0,
    val minLaningScoreForLateComp: Double = 4.5,
    val minLateScalingScoreForEarlyComp: Double = 4.5,
)

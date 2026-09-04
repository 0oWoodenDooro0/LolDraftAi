package com.loldraft.models

import kotlinx.serialization.Serializable

/**
 * Supported algorithms for next-step Ban/Pick intent prediction.
 */
@Serializable
enum class BpPredictionAlgorithm(
    val displayName: String,
    val shortName: String,
    val description: String,
) {
    HEURISTIC_EXPERT(
        displayName = "啟發式多因子 (Heuristic)",
        shortName = "Heuristic",
        description = "專家啟發式多因子規則引擎，結合版本強度、選手熟練度、下路組合與克制矩陣",
    ),
    EMPIRICAL_BEHAVIORAL(
        displayName = "經驗行為統計 (Empirical)",
        shortName = "Empirical",
        description = "純經驗行為統計預測，結合賽區/戰隊客觀頻率、選手時間指數衰減歷史、嚴格分路約束與全局 BP 動態動作遮罩 (CSP Action Masking)",
    );

    companion object {
        @JvmField
        val DEEP_LEARNING_POLICY = EMPIRICAL_BEHAVIORAL
    }
}

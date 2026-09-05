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
    );
}

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
        description = "專家啟發式多因子規則引擎，結合版本強度、選手熟練度、下路組合與剋制矩陣",
    ),
    DEEP_LEARNING_POLICY(
        displayName = "深度學習網絡 (Deep Learning)",
        shortName = "Deep Learning",
        description = "條件實體嵌入策略神經網絡，支援賽區/戰隊風格條件注入、嚴格分路約束與標準全局 BP 動態動作遮罩 (Action Masking)",
    ),
}

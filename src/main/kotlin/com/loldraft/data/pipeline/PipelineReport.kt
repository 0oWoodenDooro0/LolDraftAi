package com.loldraft.data.pipeline

import com.loldraft.data.cleaning.AnomalyReason

data class PipelineReport(
    val totalProcessed: Int,
    val validIngested: Int,
    val rejectedCount: Int,
    val rejectionBreakdown: Map<AnomalyReason, Int> = emptyMap(),
    val patches: Set<String> = emptySet(),
    val tournamentCount: Int = 0,
) {
    val successRate: Double
        get() = if (totalProcessed == 0) 0.0 else validIngested.toDouble() / totalProcessed.toDouble()
}

package com.loldraft.models

import kotlinx.serialization.Serializable

@Serializable
data class EvaluationFactor(
    val name: String,
    val category: String,
    val impact: Double,
    val description: String,
)

@Serializable
data class DraftEvaluationResult(
    val blueWinRate: Double,
    val redWinRate: Double,
    val evalScore: Double,
    val confidence: Double,
    val dominantFactors: List<EvaluationFactor> = emptyList(),
    val features: DraftFeatures,
    val flaws: DraftFlawAnalysisResult? = null,
)

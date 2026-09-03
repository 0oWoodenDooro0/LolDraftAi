package com.loldraft.models

import com.loldraft.data.models.Role
import kotlinx.serialization.Serializable

@Serializable
data class RoleProbability(
    val role: Role,
    val probability: Double,
    val proGames: Int = 0,
    val sampleShare: Double = 0.0,
)

@Serializable
data class FlexAnalysisResult(
    val championId: String,
    val isFlex: Boolean,
    val roleProbabilities: Map<Role, Double>,
    val primaryRole: Role,
    val secondaryRoles: List<Role> = emptyList(),
    val flexEntropy: Double = 0.0,
    val confidence: Double = 1.0,
)

@Serializable
enum class FlexThreatLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

@Serializable
data class FlexDefenseAdvice(
    val targetChampion: String,
    val threatLevel: FlexThreatLevel,
    val candidateRoles: List<RoleProbability>,
    val tacticalWarnings: List<String>,
    val counterStrategies: List<String>,
    val recommendedDualCounters: List<String> = emptyList(),
)

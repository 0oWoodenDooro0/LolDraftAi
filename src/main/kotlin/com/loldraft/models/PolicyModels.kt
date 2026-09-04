package com.loldraft.models

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import kotlinx.serialization.Serializable

@Serializable
data class ChampionIntentCandidate(
    val championId: String,
    val probability: Double,
    val intentScore: Double,
    val predictedRole: Role? = null,
    val metaScore: Double = 0.0,
    val playerMasteryScore: Double = 0.0,
    val compositionFitScore: Double = 0.0,
    val counterDenialScore: Double = 0.0,
    val playerName: String? = null,
    val rationale: String = "",
)

@Serializable
data class IntentPredictionResult(
    val turnSpec: DraftTurnSpec,
    val actingSide: Side,
    val actionType: ActionType,
    val predictions: List<ChampionIntentCandidate>,
)

@Serializable
data class PickRecommendation(
    val championId: String,
    val recommendedRole: Role,
    val winRateGain: Double,
    val predictedWinRate: Double,
    val baseWinRate: Double,
    val synergyScore: Double = 0.0,
    val counterScore: Double = 0.0,
    val flawsResolved: List<String> = emptyList(),
    val flawsIntroduced: List<String> = emptyList(),
    val reasons: List<String> = emptyList(),
)

@Serializable
data class RecommendationReport(
    val targetSide: Side,
    val turnNumber: Int,
    val baseWinRate: Double,
    val recommendations: List<PickRecommendation>,
    val evaluatedCandidateCount: Int = 0,
    val latencyMs: Long = 0L,
)

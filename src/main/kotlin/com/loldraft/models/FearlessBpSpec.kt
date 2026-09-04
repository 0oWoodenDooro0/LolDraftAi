package com.loldraft.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 全局 BP (Fearless Draft) AI 預測模型系統設計規格書 - 資料結構定義
 * 依據規格書第 3 節 JSON Schema 規範定義。
 */
@Serializable
data class FearlessTurnInfo(
    val side: String, // "blue" or "red"
    val phase: String, // "ban" or "pick"
    @SerialName("step_index")
    val stepIndex: Int, // 1 to 20
)

@Serializable
data class FearlessDraftContext(
    val patch: String,
    val region: String, // "LPL", "LCK", "LEC", "LCS", etc.
    @SerialName("series_type")
    val seriesType: String = "Bo5",
    @SerialName("game_number")
    val gameNumber: Int, // 1 to 5
    @SerialName("current_turn")
    val currentTurn: FearlessTurnInfo,
)

@Serializable
data class FearlessRoster(
    val top: String? = null,
    val jng: String? = null,
    val mid: String? = null,
    val bot: String? = null,
    val sup: String? = null,
) {
    fun toList(): List<Pair<String, String>> = listOfNotNull(
        top?.let { "top" to it },
        jng?.let { "jng" to it },
        mid?.let { "mid" to it },
        bot?.let { "bot" to it },
        sup?.let { "sup" to it },
    )
}

@Serializable
data class FearlessTeamInfo(
    @SerialName("team_id")
    val teamId: String,
    val roster: FearlessRoster,
)

@Serializable
data class FearlessTeams(
    val blue: FearlessTeamInfo,
    val red: FearlessTeamInfo,
)

@Serializable
data class FearlessCurrentPicks(
    val blue: List<String> = emptyList(),
    val red: List<String> = emptyList(),
)

@Serializable
data class FearlessConstraints(
    @SerialName("fearless_locked")
    val fearlessLocked: List<String> = emptyList(),
    @SerialName("current_bans")
    val currentBans: List<String> = emptyList(),
    @SerialName("current_picks")
    val currentPicks: FearlessCurrentPicks = FearlessCurrentPicks(),
)

@Serializable
data class FearlessHistory(
    @SerialName("player_pick_counts")
    val playerPickCounts: Map<String, Map<String, Int>> = emptyMap(),
)

@Serializable
data class FearlessPredictionRequest(
    val context: FearlessDraftContext,
    val teams: FearlessTeams,
    val constraints: FearlessConstraints,
    val history: FearlessHistory = FearlessHistory(),
)

@Serializable
data class FearlessCandidate(
    val champion: String,
    val probability: Double,
    val percentage: String,
    val logit: Double,
    val rationale: String,
)

@Serializable
data class FearlessPredictionResponse(
    val targetTurn: FearlessTurnInfo,
    val actingTeam: String,
    val actingPlayer: String?,
    val targetRole: String?,
    val candidates: List<FearlessCandidate>,
    val maskedChampionsCount: Int,
    val totalLegalCandidates: Int,
)

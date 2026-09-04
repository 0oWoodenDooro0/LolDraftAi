package com.loldraft.data.player

import com.loldraft.data.models.Role
import kotlinx.serialization.Serializable

@Serializable
enum class SignatureTier {
    SIGNATURE,
    COMFORT,
    POCKET,
}

@Serializable
data class ChampionCareerRecord(
    val championId: String,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val winRate: Double,
    val pickRate: Double,
    val role: Role? = null,
)

@Serializable
data class SignaturePick(
    val championId: String,
    val gamesPlayed: Int,
    val wins: Int,
    val winRate: Double,
    val pickRate: Double,
    val signatureScore: Double,
    val tier: SignatureTier,
    val role: Role? = null,
)

@Serializable
data class PlayerCareerStats(
    val playerId: String,
    val totalProGames: Int,
    val totalWins: Int,
    val winRate: Double,
    val roleDistribution: Map<Role, Int>,
    val championRecords: Map<String, ChampionCareerRecord>,
    val signaturePicks: List<SignaturePick>,
)

@Serializable
enum class SpikeAlertType {
    OFF_META_SURGE,
    PRACTICE_SPIKE,
    POCKET_PREPARATION,
}

@Serializable
enum class SpikeAlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
}

@Serializable
data class SpikeAlert(
    val championId: String,
    val severity: SpikeAlertSeverity,
    val type: SpikeAlertType,
    val recentDays: Int,
    val recentGamesCount: Int,
    val recentWinRate: Double,
    val baselineGamesCount: Int,
    val baselineDays: Int,
    val frequencyMultiplier: Double,
    val careerProGames: Int,
    val reason: String,
)

@Serializable
enum class ConfidenceRating {
    S,
    A,
    B,
    C,
    D,
}

@Serializable
data class BlindPickConfidence(
    val championId: String,
    val confidenceScore: Double,
    val rating: ConfidenceRating,
    val proMasteryScore: Double,
    val soloQRecentScore: Double,
    val blindPickHistoricalScore: Double,
    val reasoning: List<String>,
)

@Serializable
data class PlayerIntelligenceDossier(
    val playerId: String,
    val careerStats: PlayerCareerStats,
    val linkedAccounts: List<SoloQAccount>,
    val recentSoloQ3Days: List<SoloQChampionStats>,
    val recentSoloQ7Days: List<SoloQChampionStats>,
    val activeSpikeAlerts: List<SpikeAlert>,
    val blindPickConfidences: Map<String, BlindPickConfidence>,
)

@Serializable
data class ProPlayerDetailedProfile(
    val playerId: String,
    val role: Role,
    val totalProGames: Int,
    val proWinRate: Double,
    val careerStats: PlayerCareerStats,
    val signaturePicks: List<SignaturePick>,
    val recentSoloQ3Days: List<SoloQChampionStats>,
    val recentSoloQ7Days: List<SoloQChampionStats>,
    val activeSpikeAlerts: List<SpikeAlert>,
    val linkedAccounts: List<SoloQAccount> = emptyList(),
    val dossier: PlayerIntelligenceDossier? = null,
) {
    companion object {
        fun fromDossier(
            role: Role,
            dossier: PlayerIntelligenceDossier,
        ): ProPlayerDetailedProfile =
            ProPlayerDetailedProfile(
                playerId = dossier.playerId,
                role = role,
                totalProGames = dossier.careerStats.totalProGames,
                proWinRate = dossier.careerStats.winRate,
                careerStats = dossier.careerStats,
                signaturePicks = dossier.careerStats.signaturePicks,
                recentSoloQ3Days = dossier.recentSoloQ3Days,
                recentSoloQ7Days = dossier.recentSoloQ7Days,
                activeSpikeAlerts = dossier.activeSpikeAlerts,
                linkedAccounts = dossier.linkedAccounts,
                dossier = dossier,
            )
    }
}

package com.loldraft.data.meta

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ChampionEmpiricalStats(
    val id: Int,
    val picks: Int = 0,
    val wins: Int = 0,
    @SerialName("win_rate") val winRate: Double = 0.5,
    @SerialName("smoothed_win_rate") val smoothedWinRate: Double = 0.5,
    val gd15: Double = 0.0,
    @SerialName("smoothed_gd15") val smoothedGd15: Double = 0.0,
    val csd15: Double = 0.0,
    @SerialName("smoothed_csd15") val smoothedCsd15: Double = 0.0,
    val dpm: Double = 500.0,
    @SerialName("smoothed_dpm") val smoothedDpm: Double = 500.0,
    val dtpm: Double = 600.0,
    @SerialName("smoothed_dtpm") val smoothedDtpm: Double = 600.0,
    val dmpm: Double = 600.0,
    @SerialName("smoothed_dmpm") val smoothedDmpm: Double = 600.0,
    @SerialName("first_tower_rate") val firstTowerRate: Double = 0.5,
    @SerialName("first_dragon_rate") val firstDragonRate: Double = 0.5,
)

@Serializable
data class EmpiricalSynergyRecord(
    val games: Int = 0,
    val wins: Int = 0,
    @SerialName("win_rate") val winRate: Double = 0.5,
    @SerialName("smoothed_win_rate") val smoothedWinRate: Double = 0.5,
)

@Serializable
data class EmpiricalCounterRecord(
    val games: Int = 0,
    val wins: Int = 0,
    @SerialName("win_rate") val winRate: Double = 0.5,
    @SerialName("smoothed_win_rate") val smoothedWinRate: Double = 0.5,
    @SerialName("avg_gd15") val avgGd15: Double = 0.0,
    @SerialName("smoothed_gd15") val smoothedGd15: Double = 0.0,
)

@Serializable
data class EmpiricalGlobalStats(
    val dpm: Double = 500.0,
    val dtpm: Double = 600.0,
    val dmpm: Double = 600.0,
    val firsttower: Double = 0.5,
    val firstdragon: Double = 0.5,
)

@Serializable
data class EmpiricalStatsRoot(
    @SerialName("num_champions") val numChampions: Int = 0,
    @SerialName("champ_to_id") val champToId: Map<String, Int> = emptyMap(),
    val champions: Map<String, ChampionEmpiricalStats> = emptyMap(),
    val synergy: Map<String, EmpiricalSynergyRecord> = emptyMap(),
    val counters: Map<String, EmpiricalCounterRecord> = emptyMap(),
    @SerialName("global_stats") val globalStats: EmpiricalGlobalStats = EmpiricalGlobalStats(),
)

data class LaneCounterInfo(
    val winRateAdvantage: Double,
    val gd15Advantage: Double,
)

class ChampionEmpiricalRegistry(
    val data: EmpiricalStatsRoot
) {
    val numChampions: Int get() = data.champToId.size

    fun getChampId(name: String?): Int {
        if (name.isNullOrBlank()) return 0
        return data.champToId[name] ?: data.champToId[name.trim()] ?: 0
    }

    fun getStats(name: String?): ChampionEmpiricalStats? {
        if (name.isNullOrBlank()) return null
        return data.champions[name] ?: data.champions[name.trim()]
    }

    fun getSynergy(c1: String, c2: String): Double {
        val sorted = listOf(c1.trim(), c2.trim()).sorted()
        val key = "${sorted[0]}|${sorted[1]}"
        return data.synergy[key]?.smoothedWinRate ?: 0.50
    }

    fun getCounter(c1: String, c2: String): LaneCounterInfo {
        val key = "${c1.trim()}|${c2.trim()}"
        val record = data.counters[key]
        return if (record != null) {
            LaneCounterInfo(
                winRateAdvantage = record.smoothedWinRate - 0.50,
                gd15Advantage = record.smoothedGd15
            )
        } else {
            LaneCounterInfo(winRateAdvantage = 0.0, gd15Advantage = 0.0)
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun createDefault(): ChampionEmpiricalRegistry {
            val resourceStream = ChampionEmpiricalRegistry::class.java.getResourceAsStream("/data/champion_empirical_stats.json")
            if (resourceStream != null) {
                try {
                    val root = resourceStream.bufferedReader().use { json.decodeFromString<EmpiricalStatsRoot>(it.readText()) }
                    return ChampionEmpiricalRegistry(root)
                } catch (_: Exception) {}
            }
            val localFile = File("data/champion_empirical_stats.json")
            if (localFile.exists()) {
                val root = json.decodeFromString<EmpiricalStatsRoot>(localFile.readText())
                return ChampionEmpiricalRegistry(root)
            }
            return ChampionEmpiricalRegistry(EmpiricalStatsRoot())
        }
    }
}

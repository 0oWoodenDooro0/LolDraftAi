package com.loldraft.data.meta

import kotlinx.serialization.Serializable

@Serializable
enum class BotDuoStyleTag {
    ALL_IN_KILL,
    POKE_SIEGE,
    HYPER_CARRY_PEEL,
    CROWD_CONTROL_CHAIN,
    STANDARD,
}

@Serializable
data class BotDuoSynergy(
    val botChampion: String,
    val supportChampion: String,
    val gamesTogether: Int,
    val winsTogether: Int,
    val synergyWinRate: Double,
    val avgGoldDiffAt15: Double = 0.0,
    val synergyScore: Double = 50.0,
    val styleTags: List<BotDuoStyleTag> = emptyList(),
)

@Serializable
data class BotDuoMatchup(
    val blueDuo: Pair<String, String>,
    val redDuo: Pair<String, String>,
    val gamesFaced: Int,
    val blueWins: Int,
    val blueWinRate: Double,
    val avgGoldDiffAt15: Double = 0.0,
    val counterScore: Double = 50.0,
)

@Serializable
data class SeriesDraftContext(
    val matchId: String = "series",
    val currentGameNumber: Int = 1,
    val blueScore: Int = 0,
    val redScore: Int = 0,
    val spentChampions: Set<String> = emptySet(),
) {
    fun withSpentChampion(championId: String): SeriesDraftContext =
        copy(spentChampions = spentChampions + championId)

    fun withSpentChampions(champions: Collection<String>): SeriesDraftContext =
        copy(spentChampions = spentChampions + champions)
}

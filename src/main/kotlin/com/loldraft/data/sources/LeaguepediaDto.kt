package com.loldraft.data.sources

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CargoWrapper<T>(
    val title: T,
)

@Serializable
data class CargoResponse<T>(
    val cargoquery: List<CargoWrapper<T>> = emptyList(),
)

@Serializable
data class PicksAndBansCargoRow(
    @SerialName("Tournament") val tournament: String? = null,
    @SerialName("Team1") val team1: String? = null,
    @SerialName("Team2") val team2: String? = null,
    @SerialName("WinTeam") val winTeam: String? = null,
    @SerialName("Team1Score") val team1Score: String? = null,
    @SerialName("Team2Score") val team2Score: String? = null,
    @SerialName("DateTime_UTC") val dateTimeUtc: String? = null,
    @SerialName("MatchId") val matchId: String? = null,
    @SerialName("GameId") val gameId: String? = null,
    @SerialName("Patch") val patch: String? = null,
    // Bans Phase 1 (Turns 1..6)
    @SerialName("Team1Ban1") val team1Ban1: String? = null,
    @SerialName("Team2Ban1") val team2Ban1: String? = null,
    @SerialName("Team1Ban2") val team1Ban2: String? = null,
    @SerialName("Team2Ban2") val team2Ban2: String? = null,
    @SerialName("Team1Ban3") val team1Ban3: String? = null,
    @SerialName("Team2Ban3") val team2Ban3: String? = null,
    // Picks Phase 1 (Turns 7..12)
    @SerialName("Team1Pick1") val team1Pick1: String? = null,
    @SerialName("Team2Pick1") val team2Pick1: String? = null,
    @SerialName("Team2Pick2") val team2Pick2: String? = null,
    @SerialName("Team1Pick2") val team1Pick2: String? = null,
    @SerialName("Team1Pick3") val team1Pick3: String? = null,
    @SerialName("Team2Pick3") val team2Pick3: String? = null,
    // Bans Phase 2 (Turns 13..16)
    @SerialName("Team2Ban4") val team2Ban4: String? = null,
    @SerialName("Team1Ban4") val team1Ban4: String? = null,
    @SerialName("Team2Ban5") val team2Ban5: String? = null,
    @SerialName("Team1Ban5") val team1Ban5: String? = null,
    // Picks Phase 2 (Turns 17..20)
    @SerialName("Team2Pick4") val team2Pick4: String? = null,
    @SerialName("Team1Pick4") val team1Pick4: String? = null,
    @SerialName("Team1Pick5") val team1Pick5: String? = null,
    @SerialName("Team2Pick5") val team2Pick5: String? = null,
)

@Serializable
data class ScoreboardGameCargoRow(
    @SerialName("GameId") val gameId: String? = null,
    @SerialName("MatchId") val matchId: String? = null,
    @SerialName("Tournament") val tournament: String? = null,
    @SerialName("Team1") val team1: String? = null,
    @SerialName("Team2") val team2: String? = null,
    @SerialName("WinTeam") val winTeam: String? = null,
    @SerialName("DateTime_UTC") val dateTimeUtc: String? = null,
    @SerialName("Gamelength_Number") val gamelengthNumber: Double? = null,
    @SerialName("Patch") val patch: String? = null,
)

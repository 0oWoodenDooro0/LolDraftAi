package com.loldraft.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Match(
    val id: String,
    val tournament: String,
    val patch: String,
    val bestOf: Int,
    val blueTeam: Team,
    val redTeam: Team,
    val games: List<Game> = emptyList(),
    val winnerTeamId: String? = null
)

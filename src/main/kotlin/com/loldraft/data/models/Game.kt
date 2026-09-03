package com.loldraft.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Game(
    val id: String,
    val gameNumber: Int,
    val patch: String,
    val blueTeam: Team,
    val redTeam: Team,
    val draftState: DraftState,
    val winner: Side? = null,
    val durationSeconds: Int? = null
)

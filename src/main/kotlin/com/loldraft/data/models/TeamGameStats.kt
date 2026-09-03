package com.loldraft.data.models

import kotlinx.serialization.Serializable

@Serializable
data class TeamGameStats(
    val teamId: String? = null,
    val firstBlood: Boolean? = null,
    val firstDragon: Boolean? = null,
    val goldDiffAt15: Double? = null,
    val kills: Int? = null,
    val deaths: Int? = null,
    val towers: Int? = null,
)

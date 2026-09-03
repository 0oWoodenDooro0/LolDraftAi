package com.loldraft.data.models

import kotlinx.serialization.Serializable

@Serializable
data class DraftTurn(
    val turnNumber: Int,
    val side: Side,
    val actionType: ActionType,
    val championId: String,
    val role: Role? = null,
    val player: String? = null
)

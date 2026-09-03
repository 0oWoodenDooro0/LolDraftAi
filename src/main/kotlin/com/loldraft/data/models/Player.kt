package com.loldraft.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Player(
    val id: String,
    val name: String,
    val role: Role,
    val teamId: String? = null
)

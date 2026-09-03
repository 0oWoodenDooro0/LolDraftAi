package com.loldraft.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val id: String,
    val name: String,
    val code: String,
    val region: String? = null
)

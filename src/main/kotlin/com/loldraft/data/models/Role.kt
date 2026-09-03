package com.loldraft.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class Role {
    TOP,
    JUNGLE,
    MID,
    BOT,
    SUPPORT
}

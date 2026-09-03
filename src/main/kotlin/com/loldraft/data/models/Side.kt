package com.loldraft.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class Side {
    BLUE,
    RED;

    val opposite: Side
        get() = when (this) {
            BLUE -> RED
            RED -> BLUE
        }
}

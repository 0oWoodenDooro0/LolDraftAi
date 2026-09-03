package com.loldraft.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class DraftPhase {
    BAN_PHASE_1,
    PICK_PHASE_1,
    BAN_PHASE_2,
    PICK_PHASE_2,
}

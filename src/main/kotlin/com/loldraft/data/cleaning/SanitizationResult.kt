package com.loldraft.data.cleaning

import com.loldraft.data.models.Game

sealed interface SanitizationResult {
    val isValid: Boolean

    data class Valid(
        val game: Game,
    ) : SanitizationResult {
        override val isValid: Boolean get() = true
    }

    data class Rejected(
        val gameId: String,
        val reasons: List<AnomalyReason>,
        val details: List<String> = emptyList(),
    ) : SanitizationResult {
        override val isValid: Boolean get() = false
    }
}

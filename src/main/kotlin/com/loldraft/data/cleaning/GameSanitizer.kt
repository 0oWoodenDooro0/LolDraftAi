package com.loldraft.data.cleaning

import com.loldraft.data.models.Game
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.normalization.PatchNormalizer
import com.loldraft.data.validation.DraftValidator

class GameSanitizer(
    val minDurationSeconds: Int = 300,
    val draftValidator: DraftValidator = DraftValidator(),
    val patchNormalizer: PatchNormalizer = PatchNormalizer,
    val championNormalizer: ChampionNormalizer = ChampionNormalizer,
) {
    fun sanitize(game: Game): SanitizationResult {
        val reasons = mutableListOf<AnomalyReason>()
        val details = mutableListOf<String>()

        // 1. Remake / Short duration check
        val duration = game.durationSeconds
        if (duration != null && duration < minDurationSeconds) {
            reasons.add(AnomalyReason.REMAKE)
            details.add("Game duration $duration seconds is below minimum threshold of $minDurationSeconds seconds")
        }

        // 2. Patch validation
        if (!patchNormalizer.isValid(game.patch)) {
            reasons.add(AnomalyReason.INVALID_PATCH)
            details.add("Invalid patch format: '${game.patch}'")
        }

        // 3. Draft completeness and validity
        val draft = game.draftState
        val totalPicks = draft.bluePicks.size + draft.redPicks.size
        if (totalPicks < 10) {
            reasons.add(AnomalyReason.INSUFFICIENT_PICKS)
            details.add("Insufficient champion picks: found $totalPicks (expected 10)")
        }

        // 4. Duplicate champions (pre-normalized via championNormalizer to catch alias mismatches like monkeyking vs Wukong)
        val allChamps =
            (
                draft.blueBans + draft.redBans +
                    draft.bluePicks.map { it.championId } +
                    draft.redPicks.map { it.championId }
            ).map { championNormalizer.normalize(it) }
                .filter { it.isNotBlank() }

        val duplicates =
            allChamps
                .groupBy { it.trim().lowercase() }
                .filter { it.value.size > 1 }

        if (duplicates.isNotEmpty()) {
            reasons.add(AnomalyReason.DUPLICATE_CHAMPION)
            details.add("Duplicate champions found: ${duplicates.keys}")
        }

        // 5. Sequence validation if turns are present (pre-normalizing champion IDs)
        if (draft.turns.isNotEmpty()) {
            val normalizedTurns =
                draft.turns.map {
                    it.copy(championId = championNormalizer.normalize(it.championId))
                }
            val seqResult = draftValidator.validateDraftSequence(normalizedTurns)
            if (!seqResult.isValid) {
                reasons.add(AnomalyReason.CORRUPT_TURNS)
                details.addAll(seqResult.errors)
            }
        }

        return if (reasons.isEmpty()) {
            val normalizedPatch = patchNormalizer.normalize(game.patch)
            val normalizedBluePicks =
                draft.bluePicks.map {
                    it.copy(championId = ChampionNormalizer.normalize(it.championId))
                }
            val normalizedRedPicks =
                draft.redPicks.map {
                    it.copy(championId = ChampionNormalizer.normalize(it.championId))
                }
            val normalizedBlueBans = draft.blueBans.map { ChampionNormalizer.normalize(it) }
            val normalizedRedBans = draft.redBans.map { ChampionNormalizer.normalize(it) }
            val normalizedTurns =
                draft.turns.map {
                    it.copy(championId = ChampionNormalizer.normalize(it.championId))
                }

            val cleanedDraft =
                draft.copy(
                    blueBans = normalizedBlueBans,
                    redBans = normalizedRedBans,
                    bluePicks = normalizedBluePicks,
                    redPicks = normalizedRedPicks,
                    turns = normalizedTurns,
                )

            SanitizationResult.Valid(
                game.copy(
                    patch = normalizedPatch,
                    draftState = cleanedDraft,
                ),
            )
        } else {
            SanitizationResult.Rejected(game.id, reasons, details)
        }
    }

    fun sanitizeAll(games: List<Game>): List<SanitizationResult> = games.map { sanitize(it) }
}

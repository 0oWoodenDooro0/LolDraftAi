package com.loldraft.data.pipeline

import com.loldraft.data.cleaning.AnomalyReason
import com.loldraft.data.cleaning.GameSanitizer
import com.loldraft.data.cleaning.SanitizationResult
import com.loldraft.data.lake.DataLakeStorage
import com.loldraft.data.models.Game
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class ProMatchPipeline(
    val sanitizer: GameSanitizer = GameSanitizer(),
    val storage: DataLakeStorage,
) {
    fun process(rawGames: List<Game>): PipelineReport {
        var validCount = 0
        var rejectedCount = 0
        val breakdown = mutableMapOf<AnomalyReason, Int>()
        val patches = mutableSetOf<String>()

        for (game in rawGames) {
            when (val result = sanitizer.sanitize(game)) {
                is SanitizationResult.Valid -> {
                    validCount++
                    patches.add(result.game.patch)
                    storage.saveGame(result.game)
                }
                is SanitizationResult.Rejected -> {
                    rejectedCount++
                    for (reason in result.reasons) {
                        breakdown[reason] = (breakdown[reason] ?: 0) + 1
                    }
                }
            }
        }

        return PipelineReport(
            totalProcessed = rawGames.size,
            validIngested = validCount,
            rejectedCount = rejectedCount,
            rejectionBreakdown = breakdown,
            patches = patches,
            tournamentCount = 0,
        )
    }

    suspend fun ingestFromSources(sources: List<suspend () -> List<Game>>): PipelineReport =
        coroutineScope {
            val deferredGames =
                sources.map { source ->
                    async {
                        try {
                            source()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
            val allGames = deferredGames.awaitAll().flatten()
            process(allGames)
        }
}

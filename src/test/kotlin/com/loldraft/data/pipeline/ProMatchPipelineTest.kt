package com.loldraft.data.pipeline

import com.loldraft.data.cleaning.AnomalyReason
import com.loldraft.data.cleaning.GameSanitizer
import com.loldraft.data.lake.LocalJsonDataLake
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProMatchPipelineTest {
    @TempDir
    lateinit var tempDir: File

    private fun createGame(
        id: String,
        patch: String,
        duration: Int = 1800,
        isRemake: Boolean = false,
    ): Game {
        val champs = (1..20).map { "Champ_${id}_$it" }
        val turns =
            DraftTurnSpec.SPECS.mapIndexed { idx, spec ->
                DraftTurn(spec.turnNumber, spec.side, spec.actionType, champs[idx])
            }
        val blueBans = turns.filter { it.side == Side.BLUE && it.actionType == ActionType.BAN }.map { it.championId }
        val redBans = turns.filter { it.side == Side.RED && it.actionType == ActionType.BAN }.map { it.championId }
        val bluePicks = turns.filter { it.side == Side.BLUE && it.actionType == ActionType.PICK }.map { PickSelection(it.championId) }
        val redPicks = turns.filter { it.side == Side.RED && it.actionType == ActionType.PICK }.map { PickSelection(it.championId) }

        return Game(
            id = id,
            gameNumber = 1,
            patch = patch,
            blueTeam = Team("t1", "T1", "T1"),
            redTeam = Team("t2", "Gen.G", "GEN"),
            draftState = DraftState(blueBans, redBans, bluePicks, redPicks, turns),
            winner = Side.BLUE,
            durationSeconds = if (isRemake) 120 else duration,
        )
    }

    @Test
    fun `should process batch games and output comprehensive report`() {
        val storage = LocalJsonDataLake(tempDir)
        val pipeline = ProMatchPipeline(sanitizer = GameSanitizer(), storage = storage)

        val game1 = createGame("g1", "14.1")
        val game2 = createGame("g2", "14.1")
        val remakeGame = createGame("g_remake", "14.1", isRemake = true)
        val invalidPatchGame = createGame("g_invalid_patch", "invalid_patch")

        val report = pipeline.process(listOf(game1, game2, remakeGame, invalidPatchGame))

        assertEquals(4, report.totalProcessed)
        assertEquals(2, report.validIngested)
        assertEquals(2, report.rejectedCount)
        assertEquals(2, storage.count())
        assertTrue(report.patches.contains("14.1"))

        assertEquals(1, report.rejectionBreakdown[AnomalyReason.REMAKE])
        assertEquals(1, report.rejectionBreakdown[AnomalyReason.INVALID_PATCH])
    }

    @Test
    fun `should ingest from multiple asynchronous sources`() =
        runBlocking {
            val storage = LocalJsonDataLake(tempDir)
            val pipeline = ProMatchPipeline(sanitizer = GameSanitizer(), storage = storage)

            val source1: suspend () -> List<Game> = {
                listOf(createGame("src1_g1", "14.1"))
            }
            val source2: suspend () -> List<Game> = {
                listOf(createGame("src2_g1", "14.2"), createGame("src2_g2", "14.2"))
            }

            val report = pipeline.ingestFromSources(listOf(source1, source2))

            assertEquals(3, report.totalProcessed)
            assertEquals(3, report.validIngested)
            assertEquals(0, report.rejectedCount)
            assertEquals(3, storage.count())
            assertEquals(setOf("14.1", "14.2"), report.patches)
        }
}

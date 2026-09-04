package com.loldraft.data.player

import com.loldraft.data.lake.LocalJsonDataLake
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class PlayerTrackerIntegrationTest {
    private val nowMs = 1700000000000L
    private val oneDayMs = TimeUnit.DAYS.toMillis(1)

    private val t1 = Team("t1", "T1", "T1")
    private val geng = Team("geng", "Gen.G", "GEN")

    private fun createProGame(
        gameId: String,
        player: String,
        championId: String,
        winner: Side = Side.BLUE,
        side: Side = Side.BLUE,
        turnNumber: Int = 7,
    ): Game {
        val turns =
            listOf(
                DraftTurn(1, Side.BLUE, ActionType.BAN, "lucian"),
                DraftTurn(2, Side.RED, ActionType.BAN, "rumble"),
                DraftTurn(3, Side.BLUE, ActionType.BAN, "ashe"),
                DraftTurn(4, Side.RED, ActionType.BAN, "poppy"),
                DraftTurn(5, Side.BLUE, ActionType.BAN, "vi"),
                DraftTurn(6, Side.RED, ActionType.BAN, "kalista"),
                DraftTurn(
                    turnNumber = turnNumber,
                    side = side,
                    actionType = ActionType.PICK,
                    championId = championId,
                    role = Role.MID,
                    player = player,
                ),
            )
        return Game(
            id = gameId,
            gameNumber = 1,
            patch = "14.1",
            blueTeam = if (side == Side.BLUE) t1 else geng,
            redTeam = if (side == Side.BLUE) geng else t1,
            draftState = DraftState.fromTurns(turns),
            winner = winner,
        )
    }

    @Test
    fun `should generate complete player intelligence dossier from pro matches`() {
        val tracker =
            PlayerTracker(
                careerAnalyzer = PlayerCareerAnalyzer(),
                confidenceCalculator = BlindPickConfidenceCalculator(),
            )

        // 1. Pro Games: 8 Azir games (6 wins), 4 Orianna games (2 wins)
        val proGames = mutableListOf<Game>()
        for (i in 1..6) {
            proGames.add(createProGame("p_azir_w_$i", "Faker", "azir", winner = Side.BLUE, side = Side.BLUE))
        }
        for (i in 1..2) {
            proGames.add(createProGame("p_azir_l_$i", "Faker", "azir", winner = Side.RED, side = Side.BLUE))
        }
        for (i in 1..2) {
            proGames.add(createProGame("p_ori_w_$i", "Faker", "orianna", winner = Side.BLUE, side = Side.BLUE))
        }
        for (i in 1..2) {
            proGames.add(createProGame("p_ori_l_$i", "Faker", "orianna", winner = Side.RED, side = Side.BLUE))
        }

        val dossier = tracker.generateDossier("Faker", proGames, Role.MID, referenceTimeMs = nowMs)

        // Verify dossier components
        assertEquals("Faker", dossier.playerId)

        // Career stats
        assertEquals(12, dossier.careerStats.totalProGames)
        assertEquals(8, dossier.careerStats.totalWins)
        assertTrue(dossier.careerStats.signaturePicks.any { it.championId == "azir" })

        // Blind pick confidence
        val azirConfidence = dossier.blindPickConfidences["azir"]
        assertNotNull(azirConfidence)
        assertTrue(azirConfidence!!.confidenceScore > 60.0)
    }

    @Test
    fun `should generate dossier from data lake storage`(
        @TempDir tempDir: Path,
    ) {
        val storage = LocalJsonDataLake(tempDir.toFile())
        val game = createProGame("g_lake_1", "Faker", "azir", winner = Side.BLUE)
        storage.saveGame(game)

        val tracker = PlayerTracker()
        val dossier = tracker.generateDossierFromStorage("Faker", storage, Role.MID, referenceTimeMs = nowMs)

        assertEquals("Faker", dossier.playerId)
        assertEquals(1, dossier.careerStats.totalProGames)
        assertEquals(1, dossier.careerStats.totalWins)
    }
}

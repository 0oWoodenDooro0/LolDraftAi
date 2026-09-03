package com.loldraft.data.player

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

class PlayerCareerAnalyzerTest {
    private val analyzer = PlayerCareerAnalyzer()

    private val t1 = Team(id = "t1", name = "T1", code = "T1")
    private val geng = Team(id = "geng", name = "Gen.G", code = "GEN")

    private fun createGameWithPlayerPick(
        gameId: String,
        player: String,
        championId: String,
        role: Role = Role.MID,
        side: Side = Side.BLUE,
        winner: Side = Side.BLUE,
        patch: String = "14.1",
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
                    turnNumber = if (side == Side.BLUE) 7 else 8,
                    side = side,
                    actionType = ActionType.PICK,
                    championId = championId,
                    role = role,
                    player = player,
                ),
            )
        return Game(
            id = gameId,
            gameNumber = 1,
            patch = patch,
            blueTeam = if (side == Side.BLUE) t1 else geng,
            redTeam = if (side == Side.BLUE) geng else t1,
            draftState = DraftState.fromTurns(turns),
            winner = winner,
        )
    }

    @Test
    fun `should return empty stats when no games provided`() {
        val stats = analyzer.analyzePlayer("Faker", emptyList())

        assertEquals("Faker", stats.playerId)
        assertEquals(0, stats.totalProGames)
        assertEquals(0, stats.totalWins)
        assertEquals(0.0, stats.winRate)
        assertTrue(stats.championRecords.isEmpty())
        assertTrue(stats.signaturePicks.isEmpty())
    }

    @Test
    fun `should return empty stats when player not in any games`() {
        val games =
            listOf(
                createGameWithPlayerPick("g1", "Chovy", "azir", winner = Side.BLUE),
            )
        val stats = analyzer.analyzePlayer("Faker", games)

        assertEquals("Faker", stats.playerId)
        assertEquals(0, stats.totalProGames)
    }

    @Test
    fun `should compute overall career stats and win rate accurately`() {
        val games =
            listOf(
                createGameWithPlayerPick("g1", "Faker", "azir", side = Side.BLUE, winner = Side.BLUE),
                createGameWithPlayerPick("g2", "Faker", "azir", side = Side.BLUE, winner = Side.BLUE),
                createGameWithPlayerPick("g3", "Faker", "azir", side = Side.RED, winner = Side.BLUE), // lost
                createGameWithPlayerPick("g4", "Faker", "orianna", side = Side.BLUE, winner = Side.BLUE),
            )

        val stats = analyzer.analyzePlayer("Faker", games)

        assertEquals(4, stats.totalProGames)
        assertEquals(3, stats.totalWins)
        assertEquals(0.75, stats.winRate, 0.001)
        assertEquals(4, stats.roleDistribution[Role.MID])
    }

    @Test
    fun `should compute per champion career record accurately`() {
        val games =
            listOf(
                createGameWithPlayerPick("g1", "Faker", "azir", winner = Side.BLUE),
                createGameWithPlayerPick("g2", "Faker", "azir", winner = Side.BLUE),
                createGameWithPlayerPick("g3", "Faker", "azir", side = Side.RED, winner = Side.BLUE), // loss
                createGameWithPlayerPick("g4", "Faker", "orianna", winner = Side.BLUE),
            )

        val stats = analyzer.analyzePlayer("Faker", games)
        val azirRecord = stats.championRecords["azir"]

        assertNotNull(azirRecord)
        assertEquals("azir", azirRecord?.championId)
        assertEquals(3, azirRecord?.gamesPlayed)
        assertEquals(2, azirRecord?.wins)
        assertEquals(1, azirRecord?.losses)
        assertEquals(2.0 / 3.0, azirRecord!!.winRate, 0.001)
        assertEquals(0.75, azirRecord.pickRate, 0.001) // 3 out of 4 games
    }

    @Test
    fun `should identify signature picks and categorize tiers correctly`() {
        val games = mutableListOf<Game>()
        // 10 Azir games: 8 wins (80% WR) -> High Volume + High WR -> SIGNATURE
        for (i in 1..8) {
            games.add(createGameWithPlayerPick("azir_w_$i", "Faker", "azir", side = Side.BLUE, winner = Side.BLUE))
        }
        for (i in 1..2) {
            games.add(createGameWithPlayerPick("azir_l_$i", "Faker", "azir", side = Side.BLUE, winner = Side.RED))
        }

        // 8 Orianna games: 4 wins (50% WR) -> High Volume + Balanced WR -> COMFORT
        for (i in 1..4) {
            games.add(createGameWithPlayerPick("ori_w_$i", "Faker", "orianna", side = Side.BLUE, winner = Side.BLUE))
        }
        for (i in 1..4) {
            games.add(createGameWithPlayerPick("ori_l_$i", "Faker", "orianna", side = Side.BLUE, winner = Side.RED))
        }

        // 4 LeBlanc games: 4 wins (100% WR) -> Moderate Volume + Very High WR -> POCKET
        for (i in 1..4) {
            games.add(createGameWithPlayerPick("lb_w_$i", "Faker", "leblanc", side = Side.BLUE, winner = Side.BLUE))
        }

        // 1 Ryze game: 1 win -> Below min threshold (default 3) -> should not be signature
        games.add(createGameWithPlayerPick("ryze_w_1", "Faker", "ryze", side = Side.BLUE, winner = Side.BLUE))

        val stats = analyzer.analyzePlayer("Faker", games)
        val signatures = stats.signaturePicks

        assertTrue(signatures.any { it.championId == "azir" && it.tier == SignatureTier.SIGNATURE })
        assertTrue(signatures.any { it.championId == "orianna" && it.tier == SignatureTier.COMFORT })
        assertTrue(signatures.any { it.championId == "leblanc" && it.tier == SignatureTier.POCKET })
        assertTrue(signatures.none { it.championId == "ryze" })

        // Check ranking order: highest signature score first
        assertTrue(signatures.size >= 3)
        for (i in 0 until signatures.size - 1) {
            assertTrue(signatures[i].signatureScore >= signatures[i + 1].signatureScore)
        }
    }
}

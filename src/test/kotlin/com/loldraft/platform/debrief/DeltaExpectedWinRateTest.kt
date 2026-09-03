package com.loldraft.platform.debrief

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.platform.debrief.models.DebriefGameRequest
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DeltaExpectedWinRateTest {
    private val blueTeam = Team("team-t1", "T1", "T1", "LCK")
    private val redTeam = Team("team-gen", "Gen.G", "GEN", "LCK")

    private fun createStandardDraftTurns(): List<DraftTurn> =
        listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Rumble"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Lucian"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ashe"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Varus"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Caitlyn"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Jayce", Role.TOP, "Zeus"),
            DraftTurn(8, Side.RED, ActionType.PICK, "Sion", Role.TOP, "Kiin"),
            DraftTurn(9, Side.RED, ActionType.PICK, "Sejuani", Role.JUNGLE, "Canyon"),
            DraftTurn(10, Side.BLUE, ActionType.PICK, "Viego", Role.JUNGLE, "Oner"),
            DraftTurn(11, Side.BLUE, ActionType.PICK, "Azir", Role.MID, "Faker"),
            DraftTurn(12, Side.RED, ActionType.PICK, "Orianna", Role.MID, "Chovy"),
            DraftTurn(13, Side.RED, ActionType.BAN, "Braum"),
            DraftTurn(14, Side.BLUE, ActionType.BAN, "Kai'Sa"),
            DraftTurn(15, Side.RED, ActionType.BAN, "Leona"),
            DraftTurn(16, Side.BLUE, ActionType.BAN, "Xayah"),
            DraftTurn(17, Side.RED, ActionType.PICK, "Jinx", Role.BOT, "Peyz"),
            DraftTurn(18, Side.BLUE, ActionType.PICK, "Ezreal", Role.BOT, "Gumayusi"),
            DraftTurn(19, Side.BLUE, ActionType.PICK, "Nautilus", Role.SUPPORT, "Keria"),
            DraftTurn(20, Side.RED, ActionType.PICK, "Thresh", Role.SUPPORT, "Delight"),
        )

    private fun createSampleGame(
        turns: List<DraftTurn> = createStandardDraftTurns(),
        winner: Side = Side.BLUE,
    ): Game =
        Game(
            id = "game-101",
            gameNumber = 1,
            patch = "14.10",
            blueTeam = blueTeam,
            redTeam = redTeam,
            draftState = DraftState.fromTurns(turns),
            winner = winner,
            durationSeconds = 1850,
            tournament = "LCK 2024 Summer",
        )

    @Test
    fun testTurnByTurnDeltaWinRateCalculation() {
        val engine = PostMatchDebriefEngine()
        val game = createSampleGame()
        val report = engine.generateGameDebrief(DebriefGameRequest(game))

        assertNotNull(report)
        assertEquals(20, report.turns.size)

        for (record in report.turns) {
            assertTrue(record.turnNumber in 1..20)
            assertTrue(record.winRateBefore in 0.0..1.0)
            assertTrue(record.winRateAfter in 0.0..1.0)

            // Delta should match (after - before) within rounding tolerance
            val expectedDelta = record.winRateAfter - record.winRateBefore
            assertTrue(
                abs(record.deltaWinRate - expectedDelta) < 0.001,
                "Turn ${record.turnNumber} deltaWinRate ${record.deltaWinRate} should match difference $expectedDelta",
            )
            assertTrue(record.critique.isNotBlank())
        }
    }

    @Test
    fun testNetDraftContributionSum() {
        val engine = PostMatchDebriefEngine()
        val game = createSampleGame()
        val report = engine.generateGameDebrief(DebriefGameRequest(game))

        val blueTurns = report.turns.filter { it.side == Side.BLUE }
        val redTurns = report.turns.filter { it.side == Side.RED }

        assertEquals(10, blueTurns.size)
        assertEquals(10, redTurns.size)

        val sumBlueDeltas = blueTurns.sumOf { it.deltaWinRate }
        val sumRedDeltas = redTurns.sumOf { it.deltaWinRate }

        assertTrue(
            abs(report.blueCoachSummary.netDraftDeltaWinRate - sumBlueDeltas) < 0.002,
            "Blue netDraftDeltaWinRate should equal sum of Blue turn deltas",
        )
        assertTrue(
            abs(report.redCoachSummary.netDraftDeltaWinRate - sumRedDeltas) < 0.002,
            "Red netDraftDeltaWinRate should equal sum of Red turn deltas",
        )
    }

    @Test
    fun testPhase1AndPhase2Splits() {
        val engine = PostMatchDebriefEngine()
        val game = createSampleGame()
        val report = engine.generateGameDebrief(DebriefGameRequest(game))

        val bluePhase1 = report.turns.filter { it.side == Side.BLUE && it.turnNumber <= 12 }.sumOf { it.deltaWinRate }
        val bluePhase2 = report.turns.filter { it.side == Side.BLUE && it.turnNumber > 12 }.sumOf { it.deltaWinRate }

        assertTrue(abs(report.blueCoachSummary.phase1DeltaWinRate - bluePhase1) < 0.002)
        assertTrue(abs(report.blueCoachSummary.phase2DeltaWinRate - bluePhase2) < 0.002)

        val redPhase1 = report.turns.filter { it.side == Side.RED && it.turnNumber <= 12 }.sumOf { it.deltaWinRate }
        val redPhase2 = report.turns.filter { it.side == Side.RED && it.turnNumber > 12 }.sumOf { it.deltaWinRate }

        assertTrue(abs(report.redCoachSummary.phase1DeltaWinRate - redPhase1) < 0.002)
        assertTrue(abs(report.redCoachSummary.phase2DeltaWinRate - redPhase2) < 0.002)
    }

    @Test
    fun testPartialDraftHandling() {
        val engine = PostMatchDebriefEngine()
        // Only 10 turns played
        val partialTurns = createStandardDraftTurns().take(10)
        val game = createSampleGame(turns = partialTurns)
        val report = engine.generateGameDebrief(DebriefGameRequest(game))

        assertEquals(10, report.turns.size)
        assertTrue(report.turns.all { it.turnNumber <= 10 })
        assertNotNull(report.blueCoachSummary)
        assertNotNull(report.redCoachSummary)
    }
}

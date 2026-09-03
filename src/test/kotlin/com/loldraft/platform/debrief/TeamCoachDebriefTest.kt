package com.loldraft.platform.debrief

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.platform.debrief.models.DebriefGameRequest
import com.loldraft.platform.live.models.CoachGrade
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TeamCoachDebriefTest {
    private val blueTeam = Team("team-t1", "T1", "T1", "LCK")
    private val redTeam = Team("team-gen", "Gen.G", "GEN", "LCK")

    private fun createStandardGame(): Game {
        val turns =
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
        return Game(
            id = "game-coach-eval",
            gameNumber = 1,
            patch = "14.10",
            blueTeam = blueTeam,
            redTeam = redTeam,
            draftState = DraftState.fromTurns(turns),
            winner = Side.BLUE,
            durationSeconds = 1920,
        )
    }

    @Test
    fun testCoachScoreAndGradeRange() {
        val engine = PostMatchDebriefEngine()
        val report = engine.generateGameDebrief(DebriefGameRequest(createStandardGame()))

        val blueSummary = report.blueCoachSummary
        val redSummary = report.redCoachSummary

        assertTrue(blueSummary.coachBpScore in 0.0..100.0, "Coach score must be within 0..100")
        assertTrue(redSummary.coachBpScore in 0.0..100.0, "Coach score must be within 0..100")

        assertNotNull(blueSummary.coachBpGrade)
        assertNotNull(redSummary.coachBpGrade)
    }

    @Test
    fun testMvpAndBlunderTurnDetection() {
        val engine = PostMatchDebriefEngine()
        val report = engine.generateGameDebrief(DebriefGameRequest(createStandardGame()))

        val blueMvp = report.blueCoachSummary.mvpTurn
        val blueWorst = report.blueCoachSummary.worstTurn

        assertNotNull(blueMvp, "Blue team must have an MVP turn identified")
        assertNotNull(blueWorst, "Blue team must have a worst/blunder turn identified")

        val blueTurns = report.turns.filter { it.side == Side.BLUE }
        val maxDelta = blueTurns.maxOf { it.deltaWinRate }
        val minDelta = blueTurns.minOf { it.deltaWinRate }

        assertEquals(maxDelta, blueMvp.deltaWinRate, "MVP turn must have the highest deltaWinRate")
        assertEquals(minDelta, blueWorst.deltaWinRate, "Worst turn must have the lowest deltaWinRate")
        assertTrue(blueMvp.isMvpTurn)
    }

    @Test
    fun testOptimalAndBlunderCounts() {
        val engine = PostMatchDebriefEngine()
        val report = engine.generateGameDebrief(DebriefGameRequest(createStandardGame()))

        val blueTurns = report.turns.filter { it.side == Side.BLUE }
        val blueOptimalCount =
            blueTurns.count { it.grade == CoachGrade.OPTIMAL_S || it.grade == CoachGrade.STRONG_A }
        val blueBlunderCount = blueTurns.count { it.grade == CoachGrade.BLUNDER_D }

        assertEquals(blueOptimalCount, report.blueCoachSummary.optimalPicksCount)
        assertEquals(blueBlunderCount, report.blueCoachSummary.blundersCount)
    }

    @Test
    fun testChartDataGeneration() {
        val engine = PostMatchDebriefEngine()
        val report = engine.generateGameDebrief(DebriefGameRequest(createStandardGame()))

        val charts = report.charts
        assertNotNull(charts)

        // Timeline has 21 points (turn 0 initial + 20 turns)
        assertEquals(21, charts.timelinePoints.size)
        assertEquals(0, charts.timelinePoints.first().turnNumber)
        assertEquals(20, charts.timelinePoints.last().turnNumber)

        // Time curve has 7 standard minute checkpoints (10, 15, 20, 25, 30, 35, 40)
        assertEquals(7, charts.timeCurvePoints.size)
        assertEquals(10, charts.timeCurvePoints.first().minute)
        assertEquals(40, charts.timeCurvePoints.last().minute)

        // Radar has 5 dimensions
        assertEquals(5, charts.radarComparison.size)

        // Attribution breakdown
        assertTrue(charts.attributionBreakdown.draftInfluencePct in 0.0..1.0)
        assertTrue(charts.attributionBreakdown.executionInfluencePct in 0.0..1.0)
    }
}

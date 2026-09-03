package com.loldraft.platform.debrief

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.Match
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.platform.debrief.export.DebriefMarkdownExporter
import com.loldraft.platform.debrief.models.DebriefGameRequest
import com.loldraft.platform.debrief.models.DebriefMatchRequest
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DebriefMarkdownExporterTest {
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

    private fun createGame(
        id: String,
        winner: Side = Side.BLUE,
    ): Game =
        Game(
            id = id,
            gameNumber = 1,
            patch = "14.10",
            blueTeam = blueTeam,
            redTeam = redTeam,
            draftState = DraftState.fromTurns(createStandardDraftTurns()),
            winner = winner,
            durationSeconds = 1860,
            tournament = "LCK 2024 Summer",
        )

    @Test
    fun testExportSingleGameDebriefMarkdown() {
        val engine = PostMatchDebriefEngine()
        val game = createGame("game-md-1", Side.BLUE)
        val report = engine.generateGameDebrief(DebriefGameRequest(game))

        val md = DebriefMarkdownExporter.exportGameDebrief(report)
        assertNotNull(md)

        // Check essential markdown headers and sections
        assertTrue(md.contains("Post-Match BP Debrief Report"), "Must contain main title")
        assertTrue(md.contains("T1") && md.contains("Gen.G"), "Must contain team names")
        assertTrue(md.contains("Attribution Verdict") || md.contains("Attribution Analysis"), "Must contain attribution section")
        assertTrue(md.contains("Coach BP Performance Scorecard"), "Must contain coach scorecard")
        assertTrue(md.contains("Turn-by-Turn Draft Timeline"), "Must contain timeline table")
        assertTrue(md.contains("Composition 5-Dimension Radar"), "Must contain radar analysis")
        assertTrue(md.contains("Time-Horizon Win Probability Curve"), "Must contain time curve")
    }

    @Test
    fun testExportMatchSeriesDebriefMarkdown() {
        val engine = PostMatchDebriefEngine()
        val g1 = createGame("match-g1", Side.BLUE)
        val g2 = createGame("match-g2", Side.RED)
        val g3 = createGame("match-g3", Side.BLUE)

        val match =
            Match(
                id = "match-bo3-1",
                tournament = "LCK Finals",
                patch = "14.10",
                bestOf = 3,
                blueTeam = blueTeam,
                redTeam = redTeam,
                games = listOf(g1, g2, g3),
                winnerTeamId = "team-t1",
            )

        val matchReport = engine.generateMatchDebrief(DebriefMatchRequest(match))
        val md = DebriefMarkdownExporter.exportMatchDebrief(matchReport)

        assertNotNull(md)
        assertTrue(md.contains("Series Match Debrief Report"))
        assertTrue(md.contains("LCK Finals"))
        assertTrue(md.contains("Game 1") && md.contains("Game 2") && md.contains("Game 3"))
        assertTrue(md.contains("Series Coaching Performance"))
    }
}

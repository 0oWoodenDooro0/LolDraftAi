package com.loldraft.platform.debrief

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.platform.debrief.models.AttributionCategory
import com.loldraft.platform.debrief.models.DebriefGameRequest
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DraftAttributionTest {
    private val blueTeam = Team("team-t1", "T1", "T1", "LCK")
    private val redTeam = Team("team-gen", "Gen.G", "GEN", "LCK")

    private fun createEngine(): PostMatchDebriefEngine = PostMatchDebriefEngine()

    private fun createMockGame(
        blueTurnsList: List<String>,
        redTurnsList: List<String>,
        winner: Side,
        durationSeconds: Int = 1800,
    ): Game {
        val turns = mutableListOf<DraftTurn>()
        var turnNum = 1

        // 6 bans
        turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.BAN, "Kalista"))
        turns.add(DraftTurn(turnNum++, Side.RED, ActionType.BAN, "Rumble"))
        turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.BAN, "Lucian"))
        turns.add(DraftTurn(turnNum++, Side.RED, ActionType.BAN, "Ashe"))
        turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.BAN, "Varus"))
        turns.add(DraftTurn(turnNum++, Side.RED, ActionType.BAN, "Caitlyn"))

        // Phase 1 picks (B, R, R, B, B, R)
        turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.PICK, blueTurnsList[0], Role.TOP))
        turns.add(DraftTurn(turnNum++, Side.RED, ActionType.PICK, redTurnsList[0], Role.TOP))
        turns.add(DraftTurn(turnNum++, Side.RED, ActionType.PICK, redTurnsList[1], Role.JUNGLE))
        turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.PICK, blueTurnsList[1], Role.JUNGLE))
        turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.PICK, blueTurnsList[2], Role.MID))
        turns.add(DraftTurn(turnNum++, Side.RED, ActionType.PICK, redTurnsList[2], Role.MID))

        // Phase 2 bans (R, B, R, B)
        turns.add(DraftTurn(turnNum++, Side.RED, ActionType.BAN, "Braum"))
        turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.BAN, "Kai'Sa"))
        turns.add(DraftTurn(turnNum++, Side.RED, ActionType.BAN, "Leona"))
        turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.BAN, "Xayah"))

        // Phase 2 picks (R, B, B, R)
        turns.add(DraftTurn(turnNum++, Side.RED, ActionType.PICK, redTurnsList[3], Role.BOT))
        turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.PICK, blueTurnsList[3], Role.BOT))
        turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.PICK, blueTurnsList[4], Role.SUPPORT))
        turns.add(DraftTurn(turnNum++, Side.RED, ActionType.PICK, redTurnsList[4], Role.SUPPORT))

        return Game(
            id = "game-attr",
            gameNumber = 1,
            patch = "14.10",
            blueTeam = blueTeam,
            redTeam = redTeam,
            draftState = DraftState.fromTurns(turns),
            winner = winner,
            durationSeconds = durationSeconds,
            tournament = "Worlds 2024",
        )
    }

    @Test
    fun testDraftCarriedScenario() {
        val engine = createEngine()
        // Strong blue draft (meta power picks), weak red draft
        val game =
            createMockGame(
                blueTurnsList = listOf("Aatrox", "Sejuani", "Azir", "Varus", "Nautilus"),
                redTurnsList = listOf("Teemo", "Master Yi", "Katarina", "Vayne", "Yuumi"),
                winner = Side.BLUE,
                durationSeconds = 1450, // fast stomp under 25 mins
            )

        val report = engine.generateGameDebrief(DebriefGameRequest(game))
        val attribution = report.attribution

        assertEquals(Side.BLUE, attribution.advantageSide)
        assertEquals(Side.BLUE, attribution.actualWinner)
        assertTrue(
            attribution.category == AttributionCategory.DRAFT_CARRIED ||
                attribution.category == AttributionCategory.COMPOSITION_GAP,
        )
        assertTrue(attribution.draftInfluencePct >= 0.55, "Draft influence should be majority for draft carried")
        assertTrue(attribution.draftInfluencePct + attribution.executionInfluencePct in 0.99..1.01)
        assertTrue(attribution.explanation.isNotBlank())
    }

    @Test
    fun testExecutionThrowScenario() {
        val engine = createEngine()
        // Strong blue draft, but RED unexpectedly won!
        val game =
            createMockGame(
                blueTurnsList = listOf("Aatrox", "Sejuani", "Azir", "Varus", "Nautilus"),
                redTurnsList = listOf("Teemo", "Master Yi", "Katarina", "Vayne", "Yuumi"),
                winner = Side.RED,
                durationSeconds = 2700, // 45 minute slugfest throw
            )

        val report = engine.generateGameDebrief(DebriefGameRequest(game))
        val attribution = report.attribution

        assertEquals(Side.BLUE, attribution.advantageSide)
        assertEquals(Side.RED, attribution.actualWinner)
        assertEquals(AttributionCategory.EXECUTION_THROW, attribution.category)
        assertTrue(attribution.executionInfluencePct >= 0.65, "Execution influence should dominate in a throw")
        assertTrue(
            attribution.explanation.contains("throw", ignoreCase = true) || attribution.explanation.contains("in-game", ignoreCase = true),
        )
    }

    @Test
    fun testExecutionUpsetScenario() {
        val engine = createEngine()
        // Strong red draft, but BLUE overcame and won!
        val game =
            createMockGame(
                blueTurnsList = listOf("Teemo", "Master Yi", "Katarina", "Vayne", "Yuumi"),
                redTurnsList = listOf("Aatrox", "Sejuani", "Azir", "Varus", "Nautilus"),
                winner = Side.BLUE,
                durationSeconds = 2100,
            )

        val report = engine.generateGameDebrief(DebriefGameRequest(game))
        val attribution = report.attribution

        assertEquals(Side.RED, attribution.advantageSide)
        assertEquals(Side.BLUE, attribution.actualWinner)
        assertEquals(AttributionCategory.EXECUTION_UPSET, attribution.category)
        assertTrue(attribution.executionInfluencePct >= 0.65, "Execution influence should dominate in an upset")
        assertTrue(attribution.explanation.isNotBlank())
    }

    @Test
    fun testBalancedContestScenario() {
        val engine = createEngine()
        // Symmetrical balanced draft
        val game =
            createMockGame(
                blueTurnsList = listOf("Aatrox", "Sejuani", "Orianna", "Varus", "Nautilus"),
                redTurnsList = listOf("Renekton", "Maokai", "Azir", "Ezreal", "Leona"),
                winner = Side.BLUE,
                durationSeconds = 1950,
            )

        val report = engine.generateGameDebrief(DebriefGameRequest(game))
        val attribution = report.attribution

        if (attribution.advantageSide == null) {
            assertEquals(AttributionCategory.BALANCED_CONTEST, attribution.category)
            assertNull(attribution.advantageSide)
            assertTrue(attribution.executionInfluencePct >= 0.75)
        } else {
            assertTrue(attribution.category == AttributionCategory.EXECUTION_UPSET || attribution.category == AttributionCategory.DRAFT_CARRIED)
        }
    }

    @Test
    fun testAttributionWeightsSumToOne() {
        val engine = createEngine()
        val game =
            createMockGame(
                blueTurnsList = listOf("Jayce", "Viego", "Azir", "Ezreal", "Nautilus"),
                redTurnsList = listOf("Sion", "Sejuani", "Orianna", "Jinx", "Thresh"),
                winner = Side.RED,
            )

        val report = engine.generateGameDebrief(DebriefGameRequest(game))
        val sum = report.attribution.draftInfluencePct + report.attribution.executionInfluencePct
        assertTrue(abs(sum - 1.0) < 0.001, "Attribution weights must sum to 1.0 (100%)")
    }
}

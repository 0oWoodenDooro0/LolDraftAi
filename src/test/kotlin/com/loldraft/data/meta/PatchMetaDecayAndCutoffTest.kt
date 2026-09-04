package com.loldraft.data.meta

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.server.ProMatchRepository
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PatchMetaDecayAndCutoffTest {

    private val analyzer = PatchMetaAnalyzer()

    private fun createDummyGame(
        id: String,
        patch: String,
        date: String?,
        bluePicks: List<String>,
        redPicks: List<String>,
        winner: Side = Side.BLUE,
    ): Game {
        val blueTurns = bluePicks.mapIndexed { idx, champ ->
            DraftTurn(7 + idx * 2, Side.BLUE, ActionType.PICK, champ, role = Role.entries[idx % Role.entries.size])
        }
        val redTurns = redPicks.mapIndexed { idx, champ ->
            DraftTurn(8 + idx * 2, Side.RED, ActionType.PICK, champ, role = Role.entries[idx % Role.entries.size])
        }
        return Game(
            id = id,
            gameNumber = 1,
            patch = patch,
            blueTeam = Team("T1", "T1", "T1"),
            redTeam = Team("GEN", "Gen.G", "GEN"),
            draftState = DraftState(
                bluePicks = bluePicks.mapIndexed { idx, c -> PickSelection(c, Role.entries[idx % Role.entries.size]) },
                redPicks = redPicks.mapIndexed { idx, c -> PickSelection(c, Role.entries[idx % Role.entries.size]) },
                turns = blueTurns + redTurns,
            ),
            winner = winner,
            date = date,
        )
    }

    @Test
    fun `parseDate should accurately parse multiple date formats`() {
        assertEquals(LocalDate.of(2026, 1, 8), PatchMetaAnalyzer.parseDate("2026-01-08 17:08:27"))
        assertEquals(LocalDate.of(2026, 1, 8), PatchMetaAnalyzer.parseDate("2026-01-08"))
        assertEquals(LocalDate.of(2024, 5, 1), PatchMetaAnalyzer.parseDate("2024-05-01 12:00:00"))
        assertNull(PatchMetaAnalyzer.parseDate(null))
        assertNull(PatchMetaAnalyzer.parseDate(""))
    }

    @Test
    fun `calculateGameWeight should exclude matches older than 30 days`() {
        val refDate = LocalDate.of(2026, 2, 1)
        val gameRecent = createDummyGame("g1", "16.02", "2026-01-25 12:00:00", listOf("Ashe"), listOf("Varus"))
        val gameOld = createDummyGame("g2", "16.02", "2025-12-20 12:00:00", listOf("Jinx"), listOf("Lucian"))

        val weightRecent = PatchMetaAnalyzer.calculateGameWeight(gameRecent, targetPatch = "16.02", referenceDate = refDate, maxAgeDays = 30)
        val weightOld = PatchMetaAnalyzer.calculateGameWeight(gameOld, targetPatch = "16.02", referenceDate = refDate, maxAgeDays = 30)

        assertNotNull(weightRecent, "Recent game within 30 days must be included")
        assertTrue(weightRecent > 0.8, "Recent same-patch game should have high weight")
        assertNull(weightOld, "Game older than 30 days must be excluded (returned null)")
    }

    @Test
    fun `calculateGameWeight should give same patch highest weight and decay for older patches`() {
        val refDate = LocalDate.of(2026, 2, 1)
        val gameSamePatch = createDummyGame("g1", "16.03", "2026-01-30", listOf("Ashe"), listOf("Varus"))
        val gameOnePatchPrior = createDummyGame("g2", "16.02", "2026-01-30", listOf("Ashe"), listOf("Varus"))
        val gameTwoPatchesPrior = createDummyGame("g3", "16.01", "2026-01-30", listOf("Ashe"), listOf("Varus"))

        val wSame = PatchMetaAnalyzer.calculateGameWeight(gameSamePatch, targetPatch = "16.03", referenceDate = refDate)!!
        val wPrior1 = PatchMetaAnalyzer.calculateGameWeight(gameOnePatchPrior, targetPatch = "16.03", referenceDate = refDate)!!
        val wPrior2 = PatchMetaAnalyzer.calculateGameWeight(gameTwoPatchesPrior, targetPatch = "16.03", referenceDate = refDate)!!

        assertTrue(wSame > wPrior1, "Same patch weight ($wSame) must be greater than 1 patch prior ($wPrior1)")
        assertTrue(wPrior1 > wPrior2, "1 patch prior ($wPrior1) must be greater than 2 patches prior ($wPrior2)")
        assertEquals(1.0, Math.round(wSame * 10.0) / 10.0, "Same patch recent game should be ~1.0")
    }

    @Test
    fun `analyzeGamesForPrediction should filter out expired games and weight remaining properly`() {
        val refDate = LocalDate.of(2026, 2, 1)
        val g1 = createDummyGame("g1", "16.02", "2026-01-28", listOf("Lucian", "Nami"), listOf("Zeri", "Lulu"))
        val g2 = createDummyGame("g2", "16.01", "2026-01-15", listOf("Lucian", "Nami"), listOf("Zeri", "Lulu"))
        // g3 is older than 30 days (> 40 days ago) and features exclusive champion "Kalista"
        val g3 = createDummyGame("g3", "15.24", "2025-12-15", listOf("Kalista", "Renata Glasc"), listOf("Varus", "Ashe"))

        val matrix = analyzer.analyzeGamesForPrediction(
            games = listOf(g1, g2, g3),
            targetPatch = "16.02",
            referenceDate = refDate,
            maxAgeDays = 30,
        )

        assertNull(matrix.getStats("Kalista"), "Kalista from expired match (> 30 days) must not be in prediction meta matrix")
        assertNotNull(matrix.getStats("Lucian"), "Lucian from recent matches must be present")

        val lucianStats = matrix.getStats("Lucian")!!
        assertTrue(lucianStats.presenceCount > 0)
        assertTrue(lucianStats.presenceRate > 0.0)
    }

    @Test
    fun `ProMatchRepository getPatchMetaForPrediction should produce weighted recency meta`() {
        val g1 = createDummyGame("g1", "16.02", "2026-01-28", listOf("Lucian"), listOf("Caitlyn"))
        val g2 = createDummyGame("g2", "16.02", "2025-11-01", listOf("Teemo"), listOf("Singed")) // very old

        val repo = ProMatchRepository(initialGames = listOf(g1, g2))
        repo.initialize()

        val predMeta = repo.getPatchMetaForPrediction(patch = "16.02", referenceDate = LocalDate.of(2026, 1, 29))
        assertNotNull(predMeta.getStats("Lucian"))
        assertNull(predMeta.getStats("Teemo"), "Teemo from 3-month-old match must be filtered out")
    }
}

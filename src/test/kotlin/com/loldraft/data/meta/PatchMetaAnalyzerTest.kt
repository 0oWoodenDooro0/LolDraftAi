package com.loldraft.data.meta

import com.loldraft.data.lake.LocalJsonDataLake
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.models.TeamGameStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class PatchMetaAnalyzerTest {
    private val analyzer = PatchMetaAnalyzer()

    private val teamBlue = Team(id = "team_blue", name = "Team Blue", code = "BLU")
    private val teamRed = Team(id = "team_red", name = "Team Red", code = "RED")

    @TempDir
    lateinit var tempDir: Path

    private fun createTestGame(
        id: String,
        patch: String = "14.1",
        winner: Side = Side.BLUE,
        bluePicks: List<Pair<String, Role>>,
        redPicks: List<Pair<String, Role>>,
        blueBans: List<String> = emptyList(),
        redBans: List<String> = emptyList(),
        blueGd15: Double = 0.0,
        redGd15: Double = 0.0,
    ): Game {
        val turns = mutableListOf<DraftTurn>()
        var turnNum = 1

        // 3 bans each
        for (i in 0 until 3) {
            if (i < blueBans.size) turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.BAN, blueBans[i]))
            if (i < redBans.size) turns.add(DraftTurn(turnNum++, Side.RED, ActionType.BAN, redBans[i]))
        }

        // 3 picks each
        for (i in 0 until 3) {
            if (i <
                bluePicks.size
            ) {
                turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.PICK, bluePicks[i].first, role = bluePicks[i].second))
            }
            if (i < redPicks.size) turns.add(DraftTurn(turnNum++, Side.RED, ActionType.PICK, redPicks[i].first, role = redPicks[i].second))
        }

        // 2 bans each
        for (i in 3 until 5) {
            if (i < blueBans.size) turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.BAN, blueBans[i]))
            if (i < redBans.size) turns.add(DraftTurn(turnNum++, Side.RED, ActionType.BAN, redBans[i]))
        }

        // Remaining 2 picks each
        for (i in 3 until 5) {
            if (i <
                bluePicks.size
            ) {
                turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.PICK, bluePicks[i].first, role = bluePicks[i].second))
            }
            if (i < redPicks.size) turns.add(DraftTurn(turnNum++, Side.RED, ActionType.PICK, redPicks[i].first, role = redPicks[i].second))
        }

        val draftState = DraftState.fromTurns(turns)

        return Game(
            id = id,
            gameNumber = 1,
            patch = patch,
            blueTeam = teamBlue,
            redTeam = teamRed,
            draftState = draftState,
            winner = winner,
            durationSeconds = 1800,
            blueStats = TeamGameStats(firstBlood = winner == Side.BLUE, goldDiffAt15 = blueGd15),
            redStats = TeamGameStats(firstBlood = winner == Side.RED, goldDiffAt15 = redGd15),
        )
    }

    private fun buildStandardRoster(
        top: String,
        jungle: String,
        mid: String,
        bot: String,
        support: String,
    ): List<Pair<String, Role>> =
        listOf(
            top to Role.TOP,
            jungle to Role.JUNGLE,
            mid to Role.MID,
            bot to Role.BOT,
            support to Role.SUPPORT,
        )

    @Test
    fun `test presence and pick ban rate calculation`() {
        val games = mutableListOf<Game>()

        // 10 games:
        // Kalista is banned 8 times and picked 2 times -> presence = 10 (100%), banRate = 80%, pickRate = 20%
        // Orianna is picked 8 times, banned 0 times -> presence = 8 (80%), pickRate = 80%
        for (i in 1..10) {
            val isKalistaBanned = i <= 8
            val blueBans = if (isKalistaBanned) listOf("kalista", "rumble", "ashe") else listOf("rumble", "ashe", "vi")
            val redBans = listOf("lucian", "poppy", "varus")

            val blueRoster =
                if (!isKalistaBanned) {
                    buildStandardRoster("renekton", "sejuani", "orianna", "kalista", "nautilus")
                } else {
                    buildStandardRoster("renekton", "sejuani", "orianna", "jinx", "nautilus")
                }

            val redRoster = buildStandardRoster("jax", "vi", "azir", "varus", "rakan")

            games.add(
                createTestGame(
                    id = "game_$i",
                    patch = "14.1",
                    winner = if (i % 2 == 1) Side.BLUE else Side.RED,
                    bluePicks = blueRoster,
                    redPicks = redRoster,
                    blueBans = blueBans,
                    redBans = redBans,
                ),
            )
        }

        val matrix = analyzer.analyzePatch("14.1", games)

        assertEquals("14.1", matrix.patch)
        assertEquals(10, matrix.totalGames)

        val kalistaStats = matrix.getStats("kalista")
        assertNotNull(kalistaStats)
        assertEquals(2, kalistaStats?.picks)
        assertEquals(8, kalistaStats?.bans)
        assertEquals(10, kalistaStats?.presenceCount)
        assertEquals(1.0, kalistaStats?.presenceRate)
        assertEquals(0.2, kalistaStats?.pickRate)
        assertEquals(0.8, kalistaStats?.banRate)

        val oriannaStats = matrix.getStats("orianna")
        assertNotNull(oriannaStats)
        assertEquals(10, oriannaStats?.picks) // picked in all 10 games
        assertEquals(0, oriannaStats?.bans)
        assertEquals(1.0, oriannaStats?.presenceRate)
    }

    @Test
    fun `test tier classification based on presence and winrate`() {
        val games = mutableListOf<Game>()
        for (i in 1..10) {
            games.add(
                createTestGame(
                    id = "game_$i",
                    patch = "14.1",
                    winner = Side.BLUE,
                    bluePicks = buildStandardRoster("renekton", "sejuani", "orianna", "kalista", "nautilus"),
                    redPicks = buildStandardRoster("jax", "vi", "azir", "varus", "rakan"),
                    blueBans = listOf("lucian", "poppy", "rumble"),
                    redBans = listOf("ashe", "senna", "corki"),
                ),
            )
        }

        val matrix = analyzer.analyzePatch("14.1", games)

        // Kalista: 10/10 presence (100%) -> T0
        assertEquals(MetaTier.T0, matrix.getStats("kalista")?.tier)
        assertEquals(MetaTier.T0, matrix.getStats("orianna")?.tier)

        // Ban-only champions with 100% presence
        assertEquals(MetaTier.T0, matrix.getStats("lucian")?.tier)

        val t0List = matrix.getTierList(tier = MetaTier.T0)
        assertTrue(t0List.any { it.championId == "kalista" })
        assertTrue(t0List.any { it.championId == "orianna" })
    }

    @Test
    fun `test combo synergy calculation`() {
        val games = mutableListOf<Game>()

        // 6 games where Lucian + Nami are paired together on Blue team and win 5 out of 6
        for (i in 1..6) {
            games.add(
                createTestGame(
                    id = "game_$i",
                    patch = "14.1",
                    winner = if (i <= 5) Side.BLUE else Side.RED,
                    bluePicks = buildStandardRoster("gnar", "maokai", "jayce", "lucian", "nami"),
                    redPicks = buildStandardRoster("ksante", "sejuani", "azir", "jinx", "tahm kench"),
                ),
            )
        }

        // 4 games where Lucian is paired with Thresh and wins 1 out of 4
        for (i in 7..10) {
            games.add(
                createTestGame(
                    id = "game_$i",
                    patch = "14.1",
                    winner = if (i == 7) Side.BLUE else Side.RED,
                    bluePicks = buildStandardRoster("gnar", "maokai", "jayce", "lucian", "thresh"),
                    redPicks = buildStandardRoster("ksante", "sejuani", "azir", "jinx", "tahm kench"),
                ),
            )
        }

        val matrix = analyzer.analyzePatch("14.1", games)

        val topSynergies = matrix.getTopSynergies("lucian", minGames = 3)
        assertTrue(topSynergies.isNotEmpty())

        val namiSynergy = topSynergies.find { it.championA == "nami" || it.championB == "nami" }
        assertNotNull(namiSynergy)
        assertEquals(6, namiSynergy?.gamesTogether)
        assertEquals(5, namiSynergy?.winsTogether)
        assertEquals(5.0 / 6.0, namiSynergy?.synergyWinRate ?: 0.0, 0.001)

        // Win rate delta should be positive since Lucian + Nami win 83.3% while Lucian overall is 6/10 (60%)
        assertTrue((namiSynergy?.winRateDelta ?: 0.0) > 0.1)
    }

    @Test
    fun `test lane matchup counter analysis`() {
        val games = mutableListOf<Game>()

        // 6 games: Renekton (Blue TOP) vs Jax (Red TOP)
        // Renekton wins 5 out of 6 games, with positive gold diff at 15
        for (i in 1..6) {
            games.add(
                createTestGame(
                    id = "game_$i",
                    patch = "14.1",
                    winner = if (i <= 5) Side.BLUE else Side.RED,
                    bluePicks = buildStandardRoster("renekton", "sejuani", "ahri", "varus", "nautilus"),
                    redPicks = buildStandardRoster("jax", "vi", "azir", "jinx", "lulu"),
                    blueGd15 = 850.0,
                    redGd15 = -850.0,
                ),
            )
        }

        val matrix = analyzer.analyzePatch("14.1", games)

        val renektonVsJax = matrix.getMatchup("renekton", "jax", role = Role.TOP)
        assertNotNull(renektonVsJax)
        assertEquals(6, renektonVsJax?.gamesFaced)
        assertEquals(5, renektonVsJax?.wins)
        assertEquals(1, renektonVsJax?.losses)
        assertEquals(5.0 / 6.0, renektonVsJax?.winRate ?: 0.0, 0.001)
        assertEquals(850.0, renektonVsJax?.avgGoldDiffAt15 ?: 0.0, 1.0)
        assertTrue((renektonVsJax?.counterScore ?: 0.0) > 0.0)

        val countersForJax = matrix.getCountersFor("jax", role = Role.TOP)
        assertTrue(countersForJax.any { it.champion == "renekton" })
    }

    @Test
    fun `test query APIs and role filtering`() {
        val games =
            listOf(
                createTestGame(
                    id = "game_1",
                    patch = "14.1",
                    winner = Side.BLUE,
                    bluePicks = buildStandardRoster("renekton", "sejuani", "orianna", "jinx", "nautilus"),
                    redPicks = buildStandardRoster("jax", "vi", "azir", "varus", "rakan"),
                ),
            )

        val matrix = analyzer.analyzePatch("14.1", games)

        val midTierList = matrix.getTierList(role = Role.MID)
        assertTrue(midTierList.any { it.championId == "orianna" })
        assertTrue(midTierList.any { it.championId == "azir" })
        assertFalse(midTierList.any { it.championId == "renekton" }) // Renekton is TOP, not MID
    }

    @Test
    fun `test patch filter ignores other patches`() {
        val games =
            listOf(
                createTestGame(
                    id = "game_14_1",
                    patch = "14.1",
                    bluePicks = buildStandardRoster("renekton", "sejuani", "orianna", "jinx", "nautilus"),
                    redPicks = buildStandardRoster("jax", "vi", "azir", "varus", "rakan"),
                ),
                createTestGame(
                    id = "game_14_2",
                    patch = "14.2",
                    bluePicks = buildStandardRoster("aatrox", "lee sin", "sylas", "kaisa", "alistar"),
                    redPicks = buildStandardRoster("ksante", "nocturne", "taliyah", "caitlyn", "lux"),
                ),
            )

        val matrix141 = analyzer.analyzePatch("14.1", games)
        assertEquals(1, matrix141.totalGames)
        assertNotNull(matrix141.getStats("renekton"))
        assertNull(matrix141.getStats("aatrox"))

        val matrix142 = analyzer.analyzePatch("14.2", games)
        assertEquals(1, matrix142.totalGames)
        assertNotNull(matrix142.getStats("aatrox"))
        assertNull(matrix142.getStats("renekton"))
    }

    @Test
    fun `test storage integration with DataLakeStorage`() {
        val storage = LocalJsonDataLake(tempDir.resolve("datalake").toString())
        val game =
            createTestGame(
                id = "storage_game_1",
                patch = "14.1",
                bluePicks = buildStandardRoster("renekton", "sejuani", "orianna", "jinx", "nautilus"),
                redPicks = buildStandardRoster("jax", "vi", "azir", "varus", "rakan"),
            )
        storage.saveGame(game)

        val matrix = analyzer.analyzeFromStorage(storage, "14.1")
        assertNotNull(matrix)
        assertEquals(1, matrix?.totalGames)
        assertNotNull(matrix?.getStats("orianna"))
    }

    @Test
    fun `test JSON serialization and deserialization of PatchMetaMatrix`() {
        val games =
            listOf(
                createTestGame(
                    id = "game_1",
                    patch = "14.1",
                    bluePicks = buildStandardRoster("renekton", "sejuani", "orianna", "jinx", "nautilus"),
                    redPicks = buildStandardRoster("jax", "vi", "azir", "varus", "rakan"),
                ),
            )

        val matrix = analyzer.analyzePatch("14.1", games)
        val json = matrix.toJson()
        assertTrue(json.isNotBlank())
        assertTrue(json.contains("14.1"))

        val restored = PatchMetaMatrix.fromJson(json)
        assertEquals(matrix.patch, restored.patch)
        assertEquals(matrix.totalGames, restored.totalGames)
        assertEquals(matrix.championStats.size, restored.championStats.size)
        assertEquals(matrix.getStats("renekton")?.picks, restored.getStats("renekton")?.picks)
    }

    @Test
    fun `test empty games list edge case`() {
        val matrix = analyzer.analyzePatch("14.1", emptyList())
        assertEquals(0, matrix.totalGames)
        assertTrue(matrix.championStats.isEmpty())
        assertTrue(matrix.synergies.isEmpty())
        assertTrue(matrix.matchupCounters.isEmpty())
        assertNull(matrix.getStats("orianna"))
    }
}

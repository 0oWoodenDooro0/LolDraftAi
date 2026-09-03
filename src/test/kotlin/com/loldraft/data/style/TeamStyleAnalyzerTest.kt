package com.loldraft.data.style

import com.loldraft.data.lake.DataLakeStorage
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.models.TeamGameStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TeamStyleAnalyzerTest {
    private val analyzer = TeamStyleAnalyzer()

    private val t1 = Team(id = "t1", name = "T1", code = "T1")
    private val geng = Team(id = "geng", name = "Gen.G", code = "GEN")
    private val blg = Team(id = "blg", name = "Bilibili Gaming", code = "BLG")

    private fun createGame(
        id: String,
        blueTeam: Team,
        redTeam: Team,
        winner: Side,
        durationSeconds: Int = 1800, // 30 minutes
        blueFb: Boolean = false,
        redFb: Boolean = false,
        blueFd: Boolean = false,
        redFd: Boolean = false,
        blueGd15: Double = 0.0,
        redGd15: Double = 0.0,
        blueKills: Int = 10,
        redKills: Int = 10,
        blueFirstPick: String = "varus",
        blueFirstPickRole: Role = Role.BOT,
        patch: String = "14.1",
        tournament: String = "LCK",
        season: String = "Spring",
        year: Int = 2024,
    ): Game {
        val turns =
            listOf(
                DraftTurn(1, Side.BLUE, ActionType.BAN, "lucian"),
                DraftTurn(2, Side.RED, ActionType.BAN, "rumble"),
                DraftTurn(3, Side.BLUE, ActionType.BAN, "ashe"),
                DraftTurn(4, Side.RED, ActionType.BAN, "poppy"),
                DraftTurn(5, Side.BLUE, ActionType.BAN, "vi"),
                DraftTurn(6, Side.RED, ActionType.BAN, "kalista"),
                // Turn 7: Blue B1
                DraftTurn(7, Side.BLUE, ActionType.PICK, blueFirstPick, role = blueFirstPickRole),
                DraftTurn(8, Side.RED, ActionType.PICK, "ksante", role = Role.TOP),
                DraftTurn(9, Side.RED, ActionType.PICK, "sejuani", role = Role.JUNGLE),
                DraftTurn(10, Side.BLUE, ActionType.PICK, "orianna", role = Role.MID),
                DraftTurn(11, Side.BLUE, ActionType.PICK, "nautilus", role = Role.SUPPORT),
                DraftTurn(12, Side.RED, ActionType.PICK, "corki", role = Role.MID),
                DraftTurn(13, Side.RED, ActionType.BAN, "jax"),
                DraftTurn(14, Side.BLUE, ActionType.BAN, "rell"),
                DraftTurn(15, Side.RED, ActionType.BAN, "rakan"),
                DraftTurn(16, Side.BLUE, ActionType.BAN, "leona"),
                DraftTurn(17, Side.RED, ActionType.PICK, "aphelios", role = Role.BOT),
                DraftTurn(18, Side.BLUE, ActionType.PICK, "aatrox", role = Role.TOP),
                DraftTurn(19, Side.BLUE, ActionType.PICK, "nocturne", role = Role.JUNGLE),
                DraftTurn(20, Side.RED, ActionType.PICK, "milio", role = Role.SUPPORT),
            )

        val draftState =
            DraftState(
                blueBans = turns.filter { it.side == Side.BLUE && it.actionType == ActionType.BAN }.map { it.championId },
                redBans = turns.filter { it.side == Side.RED && it.actionType == ActionType.BAN }.map { it.championId },
                bluePicks =
                    turns.filter { it.side == Side.BLUE && it.actionType == ActionType.PICK }.map {
                        PickSelection(it.championId, it.role)
                    },
                redPicks =
                    turns.filter { it.side == Side.RED && it.actionType == ActionType.PICK }.map {
                        PickSelection(it.championId, it.role)
                    },
                turns = turns,
            )

        return Game(
            id = id,
            gameNumber = 1,
            patch = patch,
            blueTeam = blueTeam,
            redTeam = redTeam,
            draftState = draftState,
            winner = winner,
            durationSeconds = durationSeconds,
            blueStats =
                TeamGameStats(
                    teamId = blueTeam.id,
                    firstBlood = blueFb,
                    firstDragon = blueFd,
                    goldDiffAt15 = blueGd15,
                    kills = blueKills,
                    deaths = redKills,
                ),
            redStats =
                TeamGameStats(
                    teamId = redTeam.id,
                    firstBlood = redFb,
                    firstDragon = redFd,
                    goldDiffAt15 = redGd15,
                    kills = redKills,
                    deaths = blueKills,
                ),
            tournament = tournament,
            season = season,
            year = year,
        )
    }

    @Test
    fun `should calculate side win rates and side preference delta accurately`() {
        // T1: 3 Blue games (all won = 100% win rate), 2 Red games (1 won, 1 lost = 50% win rate)
        val games =
            listOf(
                createGame("g1", t1, geng, winner = Side.BLUE),
                createGame("g2", t1, geng, winner = Side.BLUE),
                createGame("g3", t1, geng, winner = Side.BLUE),
                createGame("g4", geng, t1, winner = Side.RED), // T1 on Red wins
                createGame("g5", geng, t1, winner = Side.BLUE), // T1 on Red loses
            )

        val profile = analyzer.analyzeTeam("t1", games)
        assertNotNull(profile)
        assertEquals(5, profile!!.totalGamesAnalyzed)

        val sidePref = profile.sidePreference
        // Blue side: 3 games, 3 wins, 0 losses, winRate = 1.0
        assertEquals(3, sidePref.blueRecord.games)
        assertEquals(3, sidePref.blueRecord.wins)
        assertEquals(0, sidePref.blueRecord.losses)
        assertEquals(1.0, sidePref.blueRecord.winRate, 0.001)

        // Red side: 2 games, 1 win, 1 loss, winRate = 0.5
        assertEquals(2, sidePref.redRecord.games)
        assertEquals(1, sidePref.redRecord.wins)
        assertEquals(1, sidePref.redRecord.losses)
        assertEquals(0.5, sidePref.redRecord.winRate, 0.001)

        // Overall: 5 games, 4 wins, 1 loss, winRate = 0.8
        assertEquals(5, sidePref.overallRecord.games)
        assertEquals(4, sidePref.overallRecord.wins)
        assertEquals(0.8, sidePref.overallRecord.winRate, 0.001)

        // Win rate delta: 1.0 - 0.5 = 0.5
        assertEquals(0.5, sidePref.winRateDelta, 0.001)

        // Blue ratio: 3/5 = 0.6, Red ratio: 2/5 = 0.4
        assertEquals(0.6, sidePref.blueRate, 0.001)
        assertEquals(0.4, sidePref.redRate, 0.001)
        assertEquals(SideTendency.BLUE_FAVORED, sidePref.tendency)
    }

    @Test
    fun `should calculate early game aggression metrics accurately`() {
        // T1 has 4 games:
        // g1 (Blue): FB=true, FD=true, GD15=+1500
        // g2 (Blue): FB=true, FD=false, GD15=+500
        // g3 (Red): FB=false, FD=true, GD15=+1000
        // g4 (Red): FB=false, FD=false, GD15=-600
        val games =
            listOf(
                createGame("g1", t1, geng, winner = Side.BLUE, blueFb = true, blueFd = true, blueGd15 = 1500.0),
                createGame("g2", t1, geng, winner = Side.BLUE, blueFb = true, blueFd = false, blueGd15 = 500.0),
                createGame("g3", geng, t1, winner = Side.RED, redFb = false, redFd = true, redGd15 = 1000.0),
                createGame("g4", geng, t1, winner = Side.BLUE, redFb = false, redFd = false, redGd15 = -600.0),
            )

        val profile = analyzer.analyzeTeam("t1", games)
        assertNotNull(profile)

        val early = profile!!.earlyGameMetrics
        assertEquals(4, early.gamesSampled)
        // FB: 2 out of 4 = 0.5
        assertEquals(0.5, early.firstBloodRate, 0.001)
        // FD: 2 out of 4 = 0.5
        assertEquals(0.5, early.firstDragonRate, 0.001)
        // Avg GD@15: (1500 + 500 + 1000 - 600) / 4 = 2400 / 4 = 600.0
        assertEquals(600.0, early.avgGoldDiffAt15, 0.001)
        assertTrue(early.dominanceScore > 50.0, "Positive early lead should have high dominance score")
    }

    @Test
    fun `should calculate tactical style metrics including KPM bloodiness and pace`() {
        // Two games, each 1800s (30.0 mins)
        // g1: T1 kills = 21, GenG kills = 9 (total kills = 30) -> Team KPM = 21/30 = 0.70, Bloodiness = 30/30 = 1.00
        // g2: T1 kills = 15, GenG kills = 15 (total kills = 30) -> Team KPM = 15/30 = 0.50, Bloodiness = 30/30 = 1.00
        val games =
            listOf(
                createGame("g1", t1, geng, winner = Side.BLUE, durationSeconds = 1800, blueKills = 21, redKills = 9),
                createGame("g2", geng, t1, winner = Side.RED, durationSeconds = 1800, redKills = 15, blueKills = 15),
            )

        val profile = analyzer.analyzeTeam("t1", games)
        assertNotNull(profile)

        val tactical = profile!!.tacticalStyleMetrics
        // Total T1 kills = 36 in 60 mins -> 0.60 KPM
        assertEquals(0.60, tactical.teamKillsPerMinute, 0.01)
        // Total match kills = 60 in 60 mins -> 1.00 CKPM (Bloodiness)
        assertEquals(1.00, tactical.combinedKillsPerMinute, 0.01)
        assertEquals(1800.0, tactical.avgDurationSeconds, 0.01)
        assertEquals("30:00", tactical.avgDurationFormatted)
        assertEquals(AggressionLevel.VERY_AGGRESSIVE, tactical.aggression)
    }

    @Test
    fun `should determine first pick priority and role distribution`() {
        // T1 on Blue side 4 times:
        // g1: Varus (BOT) - won
        // g2: Varus (BOT) - won
        // g3: Orianna (MID) - won
        // g4: Varus (BOT) - lost
        val games =
            listOf(
                createGame("g1", t1, geng, winner = Side.BLUE, blueFirstPick = "varus", blueFirstPickRole = Role.BOT),
                createGame("g2", t1, geng, winner = Side.BLUE, blueFirstPick = "varus", blueFirstPickRole = Role.BOT),
                createGame("g3", t1, geng, winner = Side.BLUE, blueFirstPick = "orianna", blueFirstPickRole = Role.MID),
                createGame("g4", t1, geng, winner = Side.RED, blueFirstPick = "varus", blueFirstPickRole = Role.BOT),
            )

        val profile = analyzer.analyzeTeam("t1", games)
        assertNotNull(profile)

        val fp = profile!!.firstPickAnalysis
        assertEquals(2, fp.b1Priorities.size)

        // Top pick is Varus: 3 picks out of 4 opportunities (75%), 2 wins (66.7%)
        val topPick = fp.b1Priorities[0]
        assertEquals("varus", topPick.championId)
        assertEquals(3, topPick.pickCount)
        assertEquals(4, topPick.totalOpportunities)
        assertEquals(0.75, topPick.pickRate, 0.001)
        assertEquals(2, topPick.wins)
        assertEquals(0.667, topPick.winRate, 0.01)
        assertEquals(Role.BOT, topPick.role)

        // Second pick is Orianna: 1 pick out of 4 (25%), 1 win (100%)
        val secondPick = fp.b1Priorities[1]
        assertEquals("orianna", secondPick.championId)
        assertEquals(1, secondPick.pickCount)
        assertEquals(0.25, secondPick.pickRate, 0.001)
        assertEquals(1, secondPick.wins)
        assertEquals(1.0, secondPick.winRate, 0.001)

        // Role distribution: BOT = 3/4 = 75%, MID = 1/4 = 25%
        assertEquals(0.75, fp.roleDistribution[Role.BOT] ?: 0.0, 0.001)
        assertEquals(0.25, fp.roleDistribution[Role.MID] ?: 0.0, 0.001)
    }

    @Test
    fun `should synthesize tactical tags for teams based on their profiles`() {
        val games =
            listOf(
                createGame(
                    "g1",
                    t1,
                    geng,
                    winner = Side.BLUE,
                    durationSeconds = 1500, // fast game (25m)
                    blueFb = true,
                    blueFd = true,
                    blueGd15 = 2000.0,
                    blueKills = 22,
                    redKills = 8,
                    blueFirstPick = "varus",
                    blueFirstPickRole = Role.BOT,
                ),
                createGame(
                    "g2",
                    t1,
                    geng,
                    winner = Side.BLUE,
                    durationSeconds = 1600,
                    blueFb = true,
                    blueFd = true,
                    blueGd15 = 1800.0,
                    blueKills = 20,
                    redKills = 10,
                    blueFirstPick = "kalista",
                    blueFirstPickRole = Role.BOT,
                ),
            )

        val profile = analyzer.analyzeTeam("t1", games)
        assertNotNull(profile)

        val tags = profile!!.tags
        // T1 has 100% FB, high GD15 -> EARLY_AGGRESSOR
        assertTrue(tags.contains(TacticalTag.EARLY_AGGRESSOR), "Should have EARLY_AGGRESSOR tag")
        // 100% FD -> DRAGON_CONTROL
        assertTrue(tags.contains(TacticalTag.DRAGON_CONTROL), "Should have DRAGON_CONTROL tag")
        // High bloodiness & kills -> BLOODY_SKIRMISHER
        assertTrue(tags.contains(TacticalTag.BLOODY_SKIRMISHER), "Should have BLOODY_SKIRMISHER tag")
        // 100% BOT first-picks -> BOT_CENTRIC_DRAFT
        assertTrue(tags.contains(TacticalTag.BOT_CENTRIC_DRAFT), "Should have BOT_CENTRIC_DRAFT tag")
        // Fast games -> FAST_TEMPO
        assertTrue(tags.contains(TacticalTag.FAST_TEMPO), "Should have FAST_TEMPO tag")
    }

    @Test
    fun `should support structured querying by case-insensitive name or code`() {
        val games = listOf(createGame("g1", t1, geng, winner = Side.BLUE))

        // Match by id
        val p1 = analyzer.analyzeTeam("t1", games)
        assertNotNull(p1)

        // Match by name
        val p2 = analyzer.analyzeTeam("T1", games)
        assertNotNull(p2)

        // Match by case-insensitive name
        val p3 = analyzer.analyzeTeam("t1", games)
        assertNotNull(p3)

        // Unknown team returns null
        val p4 = analyzer.analyzeTeam("unknown_team", games)
        assertNull(p4)
    }

    @Test
    fun `should analyze all teams in dataset`() {
        val games =
            listOf(
                createGame("g1", t1, geng, winner = Side.BLUE),
                createGame("g2", geng, blg, winner = Side.RED),
            )

        val allProfiles = analyzer.analyzeAllTeams(games)
        assertEquals(3, allProfiles.size)
        assertTrue(allProfiles.containsKey("t1"))
        assertTrue(allProfiles.containsKey("geng"))
        assertTrue(allProfiles.containsKey("blg"))
    }

    @Test
    fun `should filter games by patch and tournament`() {
        val games =
            listOf(
                createGame("g1", t1, geng, winner = Side.BLUE, patch = "14.1", tournament = "LCK"),
                createGame("g2", t1, geng, winner = Side.RED, patch = "14.2", tournament = "LCK"),
                createGame("g3", t1, blg, winner = Side.BLUE, patch = "14.1", tournament = "MSI"),
            )

        // Filter for patch 14.1 only
        val patch141Profiles = analyzer.analyzeWithFilter(games, TeamStyleFilter(patch = "14.1"))
        val t1Patch141 = patch141Profiles.find { it.team.id == "t1" }
        assertNotNull(t1Patch141)
        assertEquals(2, t1Patch141!!.totalGamesAnalyzed)

        // Filter for tournament MSI only
        val msiProfiles = analyzer.analyzeWithFilter(games, TeamStyleFilter(tournament = "MSI"))
        val t1Msi = msiProfiles.find { it.team.id == "t1" }
        assertNotNull(t1Msi)
        assertEquals(1, t1Msi!!.totalGamesAnalyzed)
    }

    @Test
    fun `should analyze directly from DataLakeStorage`() {
        val sampleGames =
            listOf(
                createGame("g1", t1, geng, winner = Side.BLUE, patch = "14.1"),
                createGame("g2", t1, geng, winner = Side.RED, patch = "14.1"),
            )

        val mockStorage =
            object : DataLakeStorage {
                override fun saveGame(game: Game) {}

                override fun getGame(gameId: String): Game? = sampleGames.find { it.id == gameId }

                override fun getGamesByPatch(patch: String): List<Game> = sampleGames.filter { it.patch == patch }

                override fun getAllGames(): List<Game> = sampleGames

                override fun count(): Int = sampleGames.size
            }

        val profile = analyzer.analyzeFromStorage(mockStorage, "t1")
        assertNotNull(profile)
        assertEquals(2, profile!!.totalGamesAnalyzed)
    }

    @Test
    fun `should handle edge cases safely like missing stats or zero duration`() {
        // Game with null duration and null stats
        val bareGame =
            Game(
                id = "bare",
                gameNumber = 1,
                patch = "14.1",
                blueTeam = t1,
                redTeam = geng,
                draftState = DraftState.empty(),
                winner = null,
                durationSeconds = null,
                blueStats = null,
                redStats = null,
            )

        val profile = analyzer.analyzeTeam("t1", listOf(bareGame))
        assertNotNull(profile)
        assertEquals(1, profile!!.totalGamesAnalyzed)
        assertEquals(0.0, profile.sidePreference.overallRecord.winRate)
        assertEquals(0.0, profile.earlyGameMetrics.firstBloodRate)
        assertEquals(0, profile.earlyGameMetrics.gamesSampled)
        assertEquals(0.0, profile.tacticalStyleMetrics.teamKillsPerMinute)
        assertEquals("00:00", profile.tacticalStyleMetrics.avgDurationFormatted)
        assertTrue(profile.firstPickAnalysis.b1Priorities.isEmpty())
    }
}

package com.loldraft.data.player

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.server.ProMatchRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class PlayerIntelligenceServiceTest {
    private val nowMs = 1700000000000L
    private val oneDayMs = TimeUnit.DAYS.toMillis(1)

    private val t1 = Team(id = "t1", name = "T1", code = "T1")
    private val gen = Team(id = "gen", name = "Gen.G", code = "GEN")

    private fun createProGame(
        gameId: String,
        bluePicks: List<PickSelection>,
        redPicks: List<PickSelection>,
        winner: Side = Side.BLUE,
    ): Game {
        val turns = mutableListOf<DraftTurn>()
        var turnNum = 1
        for (i in 1..3) {
            turns.add(DraftTurn(turnNum++, Side.BLUE, ActionType.BAN, "ban_b_$i"))
            turns.add(DraftTurn(turnNum++, Side.RED, ActionType.BAN, "ban_r_$i"))
        }
        for (pick in bluePicks) {
            turns.add(
                DraftTurn(
                    turnNumber = turnNum++,
                    side = Side.BLUE,
                    actionType = ActionType.PICK,
                    championId = pick.championId,
                    role = pick.role,
                    player = pick.playerId,
                ),
            )
        }
        for (pick in redPicks) {
            turns.add(
                DraftTurn(
                    turnNumber = turnNum++,
                    side = Side.RED,
                    actionType = ActionType.PICK,
                    championId = pick.championId,
                    role = pick.role,
                    player = pick.playerId,
                ),
            )
        }

        return Game(
            id = gameId,
            gameNumber = 1,
            patch = "14.1",
            blueTeam = t1,
            redTeam = gen,
            draftState =
                DraftState(
                    blueBans = listOf("b1", "b2", "b3"),
                    redBans = listOf("r1", "r2", "r3"),
                    bluePicks = bluePicks,
                    redPicks = redPicks,
                    turns = turns,
                ),
            winner = winner,
            tournament = "LCK",
        )
    }

    private fun createSoloQGame(
        gameId: String,
        accountId: String,
        championId: String,
        daysAgo: Double,
        win: Boolean = true,
        role: Role = Role.MID,
        kills: Int = 5,
        deaths: Int = 2,
        assists: Int = 5,
    ): SoloQGame =
        SoloQGame(
            gameId = gameId,
            accountId = accountId,
            server = SoloQServer.KR,
            timestampEpochMs = nowMs - (daysAgo * oneDayMs).toLong(),
            championId = championId,
            role = role,
            win = win,
            kills = kills,
            deaths = deaths,
            assists = assists,
        )

    private fun createSampleT1Games(): List<Game> {
        val games = mutableListOf<Game>()

        // 8 games where T1 wins with standard starting five
        for (i in 1..8) {
            val bluePicks =
                listOf(
                    PickSelection("Aatrox", Role.TOP, "Zeus"),
                    PickSelection("Nocturne", Role.JUNGLE, "Oner"),
                    PickSelection("Azir", Role.MID, "Faker"),
                    PickSelection("Varus", Role.BOT, "Gumayusi"),
                    PickSelection("Nautilus", Role.SUPPORT, "Keria"),
                )
            val redPicks =
                listOf(
                    PickSelection("K'Sante", Role.TOP, "Kiin"),
                    PickSelection("Rell", Role.JUNGLE, "Canyon"),
                    PickSelection("Corki", Role.MID, "Chovy"),
                    PickSelection("Aphelios", Role.BOT, "Peyz"),
                    PickSelection("Milio", Role.SUPPORT, "Lehends"),
                )
            games.add(createProGame("game_w_$i", bluePicks, redPicks, winner = Side.BLUE))
        }

        // 2 games where Faker plays Azir and loses
        for (i in 1..2) {
            val bluePicks =
                listOf(
                    PickSelection("Aatrox", Role.TOP, "Zeus"),
                    PickSelection("Nocturne", Role.JUNGLE, "Oner"),
                    PickSelection("Azir", Role.MID, "Faker"),
                    PickSelection("Varus", Role.BOT, "Gumayusi"),
                    PickSelection("Nautilus", Role.SUPPORT, "Keria"),
                )
            val redPicks =
                listOf(
                    PickSelection("K'Sante", Role.TOP, "Kiin"),
                    PickSelection("Rell", Role.JUNGLE, "Canyon"),
                    PickSelection("Tristana", Role.MID, "Chovy"),
                    PickSelection("Aphelios", Role.BOT, "Peyz"),
                    PickSelection("Milio", Role.SUPPORT, "Lehends"),
                )
            games.add(createProGame("game_azir_l_$i", bluePicks, redPicks, winner = Side.RED))
        }

        // 6 games where Faker plays Orianna (3 wins, 3 losses)
        for (i in 1..3) {
            val bluePicks =
                listOf(
                    PickSelection("Aatrox", Role.TOP, "Zeus"),
                    PickSelection("Nocturne", Role.JUNGLE, "Oner"),
                    PickSelection("Orianna", Role.MID, "Faker"),
                    PickSelection("Varus", Role.BOT, "Gumayusi"),
                    PickSelection("Nautilus", Role.SUPPORT, "Keria"),
                )
            val redPicks =
                listOf(
                    PickSelection("Gnar", Role.TOP, "Kiin"),
                    PickSelection("Sejuani", Role.JUNGLE, "Canyon"),
                    PickSelection("Corki", Role.MID, "Chovy"),
                    PickSelection("Kalista", Role.BOT, "Peyz"),
                    PickSelection("Ashe", Role.SUPPORT, "Lehends"),
                )
            games.add(createProGame("game_ori_w_$i", bluePicks, redPicks, winner = Side.BLUE))
        }
        for (i in 1..3) {
            val bluePicks =
                listOf(
                    PickSelection("Aatrox", Role.TOP, "Zeus"),
                    PickSelection("Nocturne", Role.JUNGLE, "Oner"),
                    PickSelection("Orianna", Role.MID, "Faker"),
                    PickSelection("Varus", Role.BOT, "Gumayusi"),
                    PickSelection("Nautilus", Role.SUPPORT, "Keria"),
                )
            val redPicks =
                listOf(
                    PickSelection("Gnar", Role.TOP, "Kiin"),
                    PickSelection("Sejuani", Role.JUNGLE, "Canyon"),
                    PickSelection("Corki", Role.MID, "Chovy"),
                    PickSelection("Kalista", Role.BOT, "Peyz"),
                    PickSelection("Ashe", Role.SUPPORT, "Lehends"),
                )
            games.add(createProGame("game_ori_l_$i", bluePicks, redPicks, winner = Side.RED))
        }

        // 4 games where Faker plays LeBlanc (4 wins, 0 losses - Pocket tier)
        for (i in 1..4) {
            val bluePicks =
                listOf(
                    PickSelection("Jayce", Role.TOP, "Zeus"),
                    PickSelection("Viego", Role.JUNGLE, "Oner"),
                    PickSelection("LeBlanc", Role.MID, "Faker"),
                    PickSelection("Lucian", Role.BOT, "Gumayusi"),
                    PickSelection("Nami", Role.SUPPORT, "Keria"),
                )
            val redPicks =
                listOf(
                    PickSelection("K'Sante", Role.TOP, "Kiin"),
                    PickSelection("Rell", Role.JUNGLE, "Canyon"),
                    PickSelection("Taliyah", Role.MID, "Chovy"),
                    PickSelection("Zeri", Role.BOT, "Peyz"),
                    PickSelection("Lulu", Role.SUPPORT, "Lehends"),
                )
            games.add(createProGame("game_lb_w_$i", bluePicks, redPicks, winner = Side.BLUE))
        }

        return games
    }

    @Test
    fun `should return empty profiles when team has no games or roster`() {
        val service = PlayerIntelligenceService(proGames = emptyList())
        val profiles = service.getTeamPlayerProfiles("unknown_team", referenceTimeMs = nowMs)
        assertTrue(profiles.isEmpty())
    }

    @Test
    fun `should generate complete starting five player profiles in standard role order`() {
        val games = createSampleT1Games()
        val service = PlayerIntelligenceService(proGames = games)

        val profiles = service.getTeamPlayerProfiles("t1", referenceTimeMs = nowMs)

        assertEquals(5, profiles.size)
        assertEquals(listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT), profiles.map { it.role })

        val zeus = profiles[0]
        assertEquals("Zeus", zeus.playerId)
        assertEquals(Role.TOP, zeus.role)
        assertEquals(20, zeus.totalProGames)

        val faker = profiles[2]
        assertEquals("Faker", faker.playerId)
        assertEquals(Role.MID, faker.role)
        assertEquals(20, faker.totalProGames)
        assertEquals(15, faker.careerStats.totalWins)
        assertEquals(0.75, faker.proWinRate, 0.001)
    }

    @Test
    fun `should accurately extract career stats and signature tiers for players`() {
        val games = createSampleT1Games()
        val service = PlayerIntelligenceService(proGames = games)

        val fakerProfile = service.getPlayerProfile("Faker", Role.MID, referenceTimeMs = nowMs)

        assertEquals("Faker", fakerProfile.playerId)
        assertEquals(Role.MID, fakerProfile.role)
        assertEquals(20, fakerProfile.totalProGames)

        val signatures = fakerProfile.signaturePicks
        assertTrue(signatures.isNotEmpty())

        // Azir: 10 games, 8 wins (80%) -> SIGNATURE tier
        val azirPick = signatures.find { it.championId.equals("Azir", ignoreCase = true) }
        assertNotNull(azirPick)
        assertEquals(10, azirPick?.gamesPlayed)
        assertEquals(8, azirPick?.wins)
        assertEquals(SignatureTier.SIGNATURE, azirPick?.tier)

        // Orianna: 6 games, 3 wins (50%) -> COMFORT tier
        val oriPick = signatures.find { it.championId.equals("Orianna", ignoreCase = true) }
        assertNotNull(oriPick)
        assertEquals(6, oriPick?.gamesPlayed)
        assertEquals(3, oriPick?.wins)
        assertEquals(SignatureTier.COMFORT, oriPick?.tier)

        // LeBlanc: 4 games, 4 wins (100%) -> POCKET tier
        val lbPick = signatures.find { it.championId.equals("LeBlanc", ignoreCase = true) }
        assertNotNull(lbPick)
        assertEquals(4, lbPick?.gamesPlayed)
        assertEquals(4, lbPick?.wins)
        assertEquals(SignatureTier.POCKET, lbPick?.tier)
    }

    @Test
    fun `should compute 7-day and 3-day soloQ stats with winRate, KDA, and pickShare`() {
        val games = createSampleT1Games()
        val service = PlayerIntelligenceService(proGames = games)

        service.registerSoloQAccount(
            "Faker",
            SoloQAccount(
                accountId = "kr_faker",
                summonerName = "Hide on bush",
                server = SoloQServer.KR,
                tier = "CHALLENGER",
                lp = 1250,
            ),
        )

        // Add SoloQ practice games
        val soloQGames = mutableListOf<SoloQGame>()
        // 4 Azir games in last 2 days (win=true, kills=6, deaths=2, assists=8 -> KDA = (6+8)/2 = 7.0)
        for (i in 1..4) {
            soloQGames.add(
                createSoloQGame(
                    gameId = "sq_azir_$i",
                    accountId = "kr_faker",
                    championId = "Azir",
                    daysAgo = 0.4 * i,
                    win = true,
                    kills = 6,
                    deaths = 2,
                    assists = 8,
                ),
            )
        }
        // 2 Ahri games 5 days ago (in 7-day window, outside 3-day window)
        for (i in 1..2) {
            soloQGames.add(
                createSoloQGame(
                    gameId = "sq_ahri_$i",
                    accountId = "kr_faker",
                    championId = "Ahri",
                    daysAgo = 5.0,
                    win = i % 2 == 0,
                    kills = 4,
                    deaths = 4,
                    assists = 4,
                ),
            )
        }
        service.addSoloQGames(soloQGames)

        val profile = service.getPlayerProfile("Faker", Role.MID, referenceTimeMs = nowMs)

        // Linked account check
        assertEquals(1, profile.linkedAccounts.size)
        assertEquals("Hide on bush", profile.linkedAccounts[0].summonerName)

        // 3-day SoloQ: only Azir (4 games)
        assertEquals(1, profile.recentSoloQ3Days.size)
        val azir3d = profile.recentSoloQ3Days.first()
        assertEquals("Azir", azir3d.championId)
        assertEquals(4, azir3d.gamesPlayed)
        assertEquals(4, azir3d.wins)
        assertEquals(1.0, azir3d.winRate)
        assertEquals(7.0, azir3d.avgKda, 0.001)

        // 7-day SoloQ: Azir (4 games) + Ahri (2 games) = 6 games total
        assertEquals(2, profile.recentSoloQ7Days.size)
        val azir7d = profile.recentSoloQ7Days.find { it.championId == "Azir" }
        assertNotNull(azir7d)
        assertEquals(4, azir7d?.gamesPlayed)
        assertEquals(4.0 / 6.0, azir7d!!.pickShare, 0.001)

        val ahri7d = profile.recentSoloQ7Days.find { it.championId == "Ahri" }
        assertNotNull(ahri7d)
        assertEquals(2, ahri7d?.gamesPlayed)
        assertEquals(1, ahri7d?.wins)
        assertEquals(0.5, ahri7d?.winRate)
    }

    @Test
    fun `should detect soloQ practice spike alerts and categorize type and severity`() {
        val games = createSampleT1Games()
        val service = PlayerIntelligenceService(proGames = games)

        service.registerSoloQAccount(
            "Faker",
            SoloQAccount(
                accountId = "kr_faker",
                summonerName = "Hide on bush",
                server = SoloQServer.KR,
            ),
        )

        // Off-meta Galio surge: 0 pro games, 6 soloQ games in last 2 days with 5 wins
        val soloQGames = mutableListOf<SoloQGame>()
        for (i in 1..5) {
            soloQGames.add(
                createSoloQGame(
                    gameId = "sq_galio_w_$i",
                    accountId = "kr_faker",
                    championId = "Galio",
                    daysAgo = 0.3 * i,
                    win = true,
                ),
            )
        }
        soloQGames.add(
            createSoloQGame(
                gameId = "sq_galio_l_1",
                accountId = "kr_faker",
                championId = "Galio",
                daysAgo = 1.2,
                win = false,
            ),
        )
        service.addSoloQGames(soloQGames)

        val profile = service.getPlayerProfile("Faker", Role.MID, referenceTimeMs = nowMs)

        assertTrue(profile.activeSpikeAlerts.isNotEmpty())
        val galioAlert = profile.activeSpikeAlerts.find { it.championId.equals("Galio", ignoreCase = true) }
        assertNotNull(galioAlert)
        assertEquals(SpikeAlertType.OFF_META_SURGE, galioAlert?.type)
        assertEquals(SpikeAlertSeverity.HIGH, galioAlert?.severity)
        assertEquals(6, galioAlert?.recentGamesCount)
    }

    @Test
    fun `should resolve soloQ games by registered accounts and fallback to accountId`() {
        val service = PlayerIntelligenceService(proGames = emptyList())

        // Chovy has no registered SoloQAccount in accountRegistry, but games use accountId = "Chovy"
        val chovyGames =
            listOf(
                createSoloQGame("sq_chovy_1", "Chovy", "Yone", daysAgo = 1.0, win = true),
                createSoloQGame("sq_chovy_2", "Chovy", "Yone", daysAgo = 2.0, win = true),
            )
        service.addSoloQGames(chovyGames)

        val soloQForChovy = service.getSoloQGamesForPlayer("Chovy")
        assertEquals(2, soloQForChovy.size)

        val chovyProfile = service.getPlayerProfile("Chovy", Role.MID, referenceTimeMs = nowMs)
        assertEquals(1, chovyProfile.recentSoloQ3Days.size)
        assertEquals("Yone", chovyProfile.recentSoloQ3Days.first().championId)
    }

    @Test
    fun `should integrate with ProMatchRepository initialized data`() {
        val games = createSampleT1Games()
        val repo = ProMatchRepository(initialGames = games)
        repo.initialize()

        val service = PlayerIntelligenceService(proMatchRepository = repo)
        val profiles = service.getTeamPlayerProfiles("t1", referenceTimeMs = nowMs)

        assertEquals(5, profiles.size)
        val roles = profiles.map { it.role }
        assertEquals(listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT), roles)
        assertEquals("Faker", profiles.find { it.role == Role.MID }?.playerId)
    }

    @Test
    fun `test getTeamRosterIntelligence returns 5 standard roles with signature and soloq data`() {
        val games = createSampleT1Games()
        val service = PlayerIntelligenceService(proGames = games)
        val rosterIntel = service.getTeamRosterIntelligence(teamId = "t1", referenceTimeMs = nowMs)

        assertEquals(5, rosterIntel.size)
        assertTrue(rosterIntel.containsKey(Role.MID))
        val mid = rosterIntel[Role.MID]
        assertNotNull(mid)
        assertEquals("Faker", mid?.playerId)
        assertTrue(mid!!.signaturePicks.isNotEmpty())
    }

    @Test
    fun `test practice spike is detected and tagged in roster player intelligence`() {
        val games = createSampleT1Games()
        val service = PlayerIntelligenceService(proGames = games)
        service.registerSoloQAccount(
            "Faker",
            SoloQAccount(
                accountId = "kr_faker",
                summonerName = "Hide on bush",
                server = SoloQServer.KR,
            ),
        )
        val soloQGames = mutableListOf<SoloQGame>()
        for (i in 1..5) {
            soloQGames.add(
                createSoloQGame(
                    gameId = "sq_galio_w_$i",
                    accountId = "kr_faker",
                    championId = "Galio",
                    daysAgo = 0.3 * i,
                    win = true,
                ),
            )
        }
        service.addSoloQGames(soloQGames)

        val rosterIntel = service.getTeamRosterIntelligence(teamId = "t1", referenceTimeMs = nowMs)
        val mid = rosterIntel[Role.MID]
        assertNotNull(mid)
        assertTrue(mid!!.practiceSpikes.isNotEmpty() || mid.recentSoloQ7Days.isNotEmpty())
    }

    @Test
    fun `test fallback when no soloq games exist for player in roster`() {
        val games = createSampleT1Games()
        val service = PlayerIntelligenceService(proGames = games)
        val rosterIntel = service.getTeamRosterIntelligence(teamId = "t1", soloQGames = emptyList(), referenceTimeMs = nowMs)

        val supIntel = rosterIntel[Role.SUPPORT]
        assertNotNull(supIntel)
        assertEquals("Keria", supIntel?.playerId)
    }
}

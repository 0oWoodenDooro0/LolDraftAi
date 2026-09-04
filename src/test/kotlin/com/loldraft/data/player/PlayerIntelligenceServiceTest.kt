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
    fun `test getTeamRosterIntelligence returns 5 standard roles with signature picks`() {
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
}

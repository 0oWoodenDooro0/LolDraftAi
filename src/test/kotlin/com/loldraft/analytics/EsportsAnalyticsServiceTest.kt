package com.loldraft.analytics

import com.loldraft.analytics.model.SortDirection
import com.loldraft.analytics.service.EsportsAnalyticsService
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.models.TeamGameStats
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EsportsAnalyticsServiceTest {
    private fun createTestGames(): List<Game> {
        val t1 = Team("t1", "T1", "T1")
        val geng = Team("gen-g", "Gen.G", "GEN")

        val game1 =
            Game(
                id = "game-1",
                gameNumber = 1,
                patch = "14.2",
                season = "Spring",
                blueTeam = t1,
                redTeam = geng,
                winner = Side.BLUE,
                tournament = "LCK",
                draftState =
                    DraftState(
                        blueBans = listOf("Kalista", "Lucian", "Ashe"),
                        redBans = listOf("Azir", "Orianna", "Rumble"),
                        bluePicks =
                            listOf(
                                PickSelection(championId = "Aatrox", role = Role.TOP, playerId = "Zeus", kills = 4, deaths = 1, assists = 5, dpm = 500.0, cspm = 8.5),
                                PickSelection(championId = "Olaf", role = Role.JUNGLE, playerId = "Oner", kills = 2, deaths = 2, assists = 7, dpm = 350.0, cspm = 6.0),
                                PickSelection(championId = "Azir", role = Role.MID, playerId = "Faker", kills = 6, deaths = 0, assists = 8, dpm = 750.0, cspm = 9.5),
                                PickSelection(championId = "Varus", role = Role.BOT, playerId = "Gumayusi", kills = 5, deaths = 1, assists = 6, dpm = 650.0, cspm = 10.0),
                                PickSelection(championId = "Bard", role = Role.SUPPORT, playerId = "Keria", kills = 1, deaths = 1, assists = 12, dpm = 200.0, cspm = 1.5),
                            ),
                        redPicks =
                            listOf(
                                PickSelection(championId = "K'Sante", role = Role.TOP, playerId = "Kiin", kills = 1, deaths = 3, assists = 2, dpm = 300.0),
                                PickSelection(championId = "Vi", role = Role.JUNGLE, playerId = "Canyon", kills = 2, deaths = 4, assists = 3, dpm = 280.0),
                                PickSelection(championId = "Corki", role = Role.MID, playerId = "Chovy", kills = 1, deaths = 2, assists = 2, dpm = 550.0),
                                PickSelection(championId = "Senna", role = Role.BOT, playerId = "Peyz", kills = 1, deaths = 4, assists = 3, dpm = 400.0),
                                PickSelection(championId = "Nautilus", role = Role.SUPPORT, playerId = "Lehends", kills = 0, deaths = 5, assists = 4, dpm = 120.0),
                            ),
                    ),
                blueStats = TeamGameStats(firstBlood = true, firstDragon = true, goldDiffAt15 = 2500.0),
            )

        val game2 =
            Game(
                id = "game-2",
                gameNumber = 2,
                patch = "14.2",
                season = "Spring",
                blueTeam = geng,
                redTeam = t1,
                winner = Side.RED,
                tournament = "LCK",
                draftState =
                    DraftState(
                        blueBans = listOf("Azir", "Bard", "Varus"),
                        redBans = listOf("Ashe", "Lucian", "Kalista"),
                        bluePicks =
                            listOf(
                                PickSelection(championId = "Gnar", role = Role.TOP, playerId = "Kiin"),
                                PickSelection(championId = "Lee Sin", role = Role.JUNGLE, playerId = "Canyon"),
                                PickSelection(championId = "Ahri", role = Role.MID, playerId = "Chovy"),
                                PickSelection(championId = "Zeri", role = Role.BOT, playerId = "Peyz"),
                                PickSelection(championId = "Rakan", role = Role.SUPPORT, playerId = "Lehends"),
                            ),
                        redPicks =
                            listOf(
                                PickSelection(championId = "Yone", role = Role.TOP, playerId = "Zeus"),
                                PickSelection(championId = "Poppy", role = Role.JUNGLE, playerId = "Oner"),
                                PickSelection(championId = "Orianna", role = Role.MID, playerId = "Faker"),
                                PickSelection(championId = "Senna", role = Role.BOT, playerId = "Smash"), // Substitute bot!
                                PickSelection(championId = "Tahm Kench", role = Role.SUPPORT, playerId = "Keria"),
                            ),
                    ),
            )

        val game3 =
            Game(
                id = "game-3",
                gameNumber = 1,
                patch = "14.3",
                season = "Summer",
                blueTeam = t1,
                redTeam = geng,
                winner = Side.RED,
                tournament = "EWC",
                draftState =
                    DraftState(
                        blueBans = listOf("Kalista", "Lucian", "Ashe"),
                        redBans = listOf("Azir", "Bard", "Draven"),
                        bluePicks =
                            listOf(
                                PickSelection(championId = "Jayce", role = Role.TOP, playerId = "Zeus"),
                                PickSelection(championId = "Sejuani", role = Role.JUNGLE, playerId = "Oner"),
                                PickSelection(championId = "Taliyah", role = Role.MID, playerId = "Faker"),
                                PickSelection(championId = "Varus", role = Role.BOT, playerId = "Gumayusi"),
                                PickSelection(championId = "Nautilus", role = Role.SUPPORT, playerId = "Keria"),
                            ),
                        redPicks =
                            listOf(
                                PickSelection(championId = "Rumble", role = Role.TOP, playerId = "Kiin"),
                                PickSelection(championId = "Nidalee", role = Role.JUNGLE, playerId = "Canyon"),
                                PickSelection(championId = "Tristana", role = Role.MID, playerId = "Chovy"),
                                PickSelection(championId = "Ezreal", role = Role.BOT, playerId = "Peyz"),
                                PickSelection(championId = "Leona", role = Role.SUPPORT, playerId = "Lehends"),
                            ),
                    ),
            )

        val game4 =
            Game(
                id = "game-4",
                gameNumber = 2,
                patch = "14.3",
                season = "Summer",
                blueTeam = geng,
                redTeam = t1,
                winner = Side.RED,
                tournament = "LCK",
                draftState =
                    DraftState(
                        blueBans = listOf("Azir", "Orianna", "Varus"),
                        redBans = listOf("Kalista", "Lucian", "Ashe"),
                        bluePicks =
                            listOf(
                                PickSelection(championId = "K'Sante", role = Role.TOP, playerId = "Kiin"),
                                PickSelection(championId = "Vi", role = Role.JUNGLE, playerId = "Canyon"),
                                PickSelection(championId = "Corki", role = Role.MID, playerId = "Chovy"),
                                PickSelection(championId = "Senna", role = Role.BOT, playerId = "Peyz"),
                                PickSelection(championId = "Nautilus", role = Role.SUPPORT, playerId = "Lehends"),
                            ),
                        redPicks =
                            listOf(
                                PickSelection(championId = "Aatrox", role = Role.TOP, playerId = "Zeus"),
                                PickSelection(championId = "Olaf", role = Role.JUNGLE, playerId = "Oner"),
                                PickSelection(championId = "Azir", role = Role.MID, playerId = "Faker"),
                                PickSelection(championId = "Varus", role = Role.BOT, playerId = "Gumayusi"),
                                PickSelection(championId = "Bard", role = Role.SUPPORT, playerId = "Keria"),
                            ),
                    ),
            )

        return listOf(game1, game2, game3, game4)
    }

    @Test
    fun testTournamentsAndTeamsFiltering() {
        val service = EsportsAnalyticsService(gamesSupplier = ::createTestGames)

        val tournaments = service.getAvailableTournaments()
        assertTrue(tournaments.contains("LCK"))
        assertTrue(tournaments.contains("EWC"))

        val splits = service.getAvailableSplits("LCK")
        assertTrue(splits.contains("Spring"))
        assertTrue(splits.contains("Summer"))

        val lckTeams = service.getAvailableTeams("LCK", "Spring")
        assertTrue(lckTeams.contains("T1"))
        assertTrue(lckTeams.contains("Gen.G"))
    }

    @Test
    fun testTeamRosterMatrixWithSubstitutesAndChampionPoolCount() {
        val service = EsportsAnalyticsService(gamesSupplier = ::createTestGames)

        // Query T1 in LCK (all splits)
        val matrix = service.getTeamRosterMatrix("T1", tournament = "LCK")
        assertEquals(3, matrix.totalTeamGames)
        assertEquals(3, matrix.teamWins) // won game1, game2, game4

        val botSlot = matrix.roles[Role.BOT]
        assertNotNull(botSlot)
        // Bot has both Gumayusi and Smash
        assertEquals(2, botSlot!!.availablePlayers.size)
        assertTrue(botSlot.availablePlayers.contains("Gumayusi"))
        assertTrue(botSlot.availablePlayers.contains("Smash"))

        // Active bot is Gumayusi (2 games)
        assertEquals("Gumayusi", botSlot.currentPlayer)
        assertEquals(1, botSlot.championPoolCount) // Varus
        assertEquals(botSlot.championPool.size, botSlot.championPoolCount)

        // Mid Faker played Azir (2 games) and Orianna (1 game) in LCK
        val midSlot = matrix.roles[Role.MID]
        assertNotNull(midSlot)
        assertEquals(2, midSlot!!.championPoolCount)
        // Check default sort: gamesPlayed DESC > winRate DESC
        assertEquals("Azir", midSlot.championPool[0].championName)
        assertEquals(2, midSlot.championPool[0].gamesPlayed)
        assertEquals("Orianna", midSlot.championPool[1].championName)
        assertEquals(1, midSlot.championPool[1].gamesPlayed)

        // Test substitute override
        val matrixWithSmash =
            service.getTeamRosterMatrix(
                teamNameOrId = "T1",
                tournament = "LCK",
                playerOverrides = mapOf(Role.BOT to "Smash"),
            )
        val smashSlot = matrixWithSmash.roles[Role.BOT]
        assertNotNull(smashSlot)
        assertEquals("Smash", smashSlot!!.currentPlayer)
        assertEquals(1, smashSlot.totalGames)
        assertEquals(1, smashSlot.championPoolCount)
        assertEquals("Senna", smashSlot.championPool[0].championName)
    }

    @Test
    fun testSplitFiltering() {
        val service = EsportsAnalyticsService(gamesSupplier = ::createTestGames)

        // Query T1 in LCK Spring only
        val springMatrix = service.getTeamRosterMatrix("T1", tournament = "LCK", split = "Spring")
        assertEquals(2, springMatrix.totalTeamGames)
        assertEquals("Spring", springMatrix.split)

        // Query T1 in LCK Summer only
        val summerMatrix = service.getTeamRosterMatrix("T1", tournament = "LCK", split = "Summer")
        assertEquals(1, summerMatrix.totalTeamGames)
        assertEquals("Summer", summerMatrix.split)

        // Player grid split filter
        val springPlayers = service.queryPlayerGrid(tournament = "LCK", split = "Spring")
        assertTrue(springPlayers.all { it.split == "Spring" })

        val summerPlayers = service.queryPlayerGrid(tournament = "LCK", split = "Summer")
        assertTrue(summerPlayers.all { it.split == "Summer" })
    }

    @Test
    fun testPlayerGridExcelFeatures() {
        val service = EsportsAnalyticsService(gamesSupplier = ::createTestGames)

        // Query LCK players sorted by winrate desc
        val rows = service.queryPlayerGrid(tournament = "LCK", sortColumn = "winrate", sortDirection = SortDirection.DESCENDING)
        assertTrue(rows.isNotEmpty())
        assertEquals(1.0, rows.first().winRate)

        // Test search
        val fakerRows = service.queryPlayerGrid(searchQuery = "Faker")
        assertEquals(1, fakerRows.size)
        assertEquals("Faker", fakerRows.first().playerName)
        assertEquals(4, fakerRows.first().games) // 3 LCK + 1 EWC
        assertEquals(3, fakerRows.first().championPoolCount) // Azir, Orianna, Taliyah

        // Test CSV and TSV export
        val csv = service.exportPlayersToCsv(fakerRows)
        assertTrue(csv.contains("Faker"))
        assertTrue(csv.contains("T1"))

        val tsv = service.exportPlayersToTsv(fakerRows)
        assertTrue(tsv.contains("Faker\tT1"))
    }

    @Test
    fun testTournamentSpecificFilteringForEwc() {
        val service = EsportsAnalyticsService(gamesSupplier = ::createTestGames)

        val ewcMatrix = service.getTeamRosterMatrix("T1", tournament = "EWC")
        assertEquals(1, ewcMatrix.totalTeamGames)
        assertEquals(0, ewcMatrix.teamWins) // lost game 3

        val midSlot = ewcMatrix.roles[Role.MID]
        assertNotNull(midSlot)
        assertEquals(1, midSlot!!.championPool.size)
        assertEquals(1, midSlot.championPoolCount)
        assertEquals("Taliyah", midSlot.championPool[0].championName)
    }
}

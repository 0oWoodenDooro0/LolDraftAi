package com.loldraft.client.compose

import com.loldraft.analytics.model.AnalyticsTab
import com.loldraft.analytics.model.SortDirection
import com.loldraft.analytics.service.EsportsAnalyticsService
import com.loldraft.client.compose.viewmodel.AnalyticsViewModel
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {
    private fun createSampleGames(): List<Game> {
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
                                PickSelection(championId = "Aatrox", role = Role.TOP, playerId = "Zeus"),
                                PickSelection(championId = "Olaf", role = Role.JUNGLE, playerId = "Oner"),
                                PickSelection(championId = "Azir", role = Role.MID, playerId = "Faker"),
                                PickSelection(championId = "Varus", role = Role.BOT, playerId = "Gumayusi"),
                                PickSelection(championId = "Bard", role = Role.SUPPORT, playerId = "Keria"),
                            ),
                        redPicks =
                            listOf(
                                PickSelection(championId = "K'Sante", role = Role.TOP, playerId = "Kiin"),
                                PickSelection(championId = "Vi", role = Role.JUNGLE, playerId = "Canyon"),
                                PickSelection(championId = "Corki", role = Role.MID, playerId = "Chovy"),
                                PickSelection(championId = "Senna", role = Role.BOT, playerId = "Peyz"),
                                PickSelection(championId = "Nautilus", role = Role.SUPPORT, playerId = "Lehends"),
                            ),
                    ),
            )

        val game2 =
            Game(
                id = "game-2",
                gameNumber = 2,
                patch = "14.2",
                season = "Spring",
                blueTeam = t1,
                redTeam = geng,
                winner = Side.BLUE,
                tournament = "LCK",
                draftState =
                    DraftState(
                        blueBans = listOf("Lucian", "Kalista"),
                        redBans = listOf("Azir", "Varus"),
                        bluePicks =
                            listOf(
                                PickSelection(championId = "Yone", role = Role.TOP, playerId = "Zeus"),
                                PickSelection(championId = "Poppy", role = Role.JUNGLE, playerId = "Oner"),
                                PickSelection(championId = "Orianna", role = Role.MID, playerId = "Faker"),
                                PickSelection(championId = "Senna", role = Role.BOT, playerId = "Smash"),
                                PickSelection(championId = "Tahm Kench", role = Role.SUPPORT, playerId = "Keria"),
                            ),
                        redPicks =
                            listOf(
                                PickSelection(championId = "Gnar", role = Role.TOP, playerId = "Kiin"),
                                PickSelection(championId = "Lee Sin", role = Role.JUNGLE, playerId = "Canyon"),
                                PickSelection(championId = "Ahri", role = Role.MID, playerId = "Chovy"),
                                PickSelection(championId = "Zeri", role = Role.BOT, playerId = "Peyz"),
                                PickSelection(championId = "Rakan", role = Role.SUPPORT, playerId = "Lehends"),
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
                        blueBans = listOf("Kalista"),
                        redBans = listOf("Azir"),
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

        return listOf(game1, game2, game3)
    }

    @Test
    fun testAnalyticsViewModelWorkflow() =
        runTest {
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val testScope = TestScope(testDispatcher)

            val service = EsportsAnalyticsService(gamesSupplier = ::createSampleGames)
            val viewModel = AnalyticsViewModel(analyticsService = service, coroutineScope = testScope)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.availableTournaments.contains("LCK"))
            assertTrue(state.availableTournaments.contains("EWC"))
            assertTrue(state.availableSplits.contains("Spring"))
            assertTrue(state.availableSplits.contains("Summer"))
            assertTrue(state.availableTeams.contains("T1"))

            // Select LCK tournament
            viewModel.selectTournament("LCK")
            viewModel.selectTeam("T1")

            val matrix = viewModel.uiState.value.rosterMatrix
            assertNotNull(matrix)
            assertEquals(2, matrix!!.totalTeamGames)
            assertEquals(2, matrix.teamWins)

            // Verify substitute handling & champion pool count
            val botSlot = matrix.roles[Role.BOT]
            assertNotNull(botSlot)
            assertEquals(2, botSlot!!.availablePlayers.size)
            assertEquals(1, botSlot.championPoolCount) // Gumayusi played 1 champion

            // Test default sorting: gamesPlayed DESC > winRate DESC
            val midPool = viewModel.getSortedChampionPool(Role.MID)
            assertTrue(midPool.isNotEmpty())

            viewModel.selectSubstituteForRole(Role.BOT, "Smash")
            val updatedMatrix = viewModel.uiState.value.rosterMatrix
            assertEquals("Smash", updatedMatrix!!.roles[Role.BOT]!!.currentPlayer)
            assertEquals(1, updatedMatrix.roles[Role.BOT]!!.championPoolCount)

            // Test split selection
            viewModel.selectSplit("Spring")
            assertEquals("Spring", viewModel.uiState.value.selectedSplit)
            val springMatrix = viewModel.uiState.value.rosterMatrix
            assertEquals(2, springMatrix!!.totalTeamGames)

            // Switch tabs
            viewModel.selectTab(AnalyticsTab.PLAYERS_GRID)
            assertEquals(AnalyticsTab.PLAYERS_GRID, viewModel.uiState.value.currentTab)

            // Sort players
            viewModel.sortPlayerGrid("games")
            assertEquals("games", viewModel.uiState.value.playerSortColumn)

            // Test search
            viewModel.setPlayerSearchQuery("Faker")
            val fakerRows = viewModel.uiState.value.playerRows
            assertEquals(1, fakerRows.size)
            assertEquals("Faker", fakerRows.first().playerName)

            // Switch to teams tab
            viewModel.selectTab(AnalyticsTab.TEAMS_GRID)
            assertEquals(AnalyticsTab.TEAMS_GRID, viewModel.uiState.value.currentTab)
            val teamRows = viewModel.uiState.value.teamRows
            assertTrue(teamRows.any { it.teamName == "T1" })
        }
}

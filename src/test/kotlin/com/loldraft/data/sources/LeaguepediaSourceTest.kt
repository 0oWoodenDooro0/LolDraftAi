package com.loldraft.data.sources

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Side
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeaguepediaSourceTest {
    private val samplePicksAndBansJson =
        """
        {
          "cargoquery": [
            {
              "title": {
                "Tournament": "LCK 2024 Spring",
                "Team1": "T1",
                "Team2": "Gen.G",
                "WinTeam": "T1",
                "MatchId": "LCK/2024 Season/Spring Season_W1D1_1",
                "GameId": "LCK/2024 Season/Spring Season_W1D1_1_1",
                "Patch": "14.1",
                "DateTime_UTC": "2024-01-17 08:00:00",
                "Team1Ban1": "Aatrox",
                "Team2Ban1": "Lucian",
                "Team1Ban2": "Sejuani",
                "Team2Ban2": "Kalista",
                "Team1Ban3": "Azir",
                "Team2Ban3": "Ashe",
                "Team1Pick1": "Orianna",
                "Team2Pick1": "Rell",
                "Team2Pick2": "K'Sante",
                "Team1Pick2": "Varus",
                "Team1Pick3": "Nautilus",
                "Team2Pick3": "Aphelios",
                "Team2Ban4": "Poppy",
                "Team1Ban4": "Vi",
                "Team2Ban5": "Lee Sin",
                "Team1Ban5": "Jarvan IV",
                "Team2Pick4": "Corki",
                "Team1Pick4": "Yone",
                "Team1Pick5": "Nocturne",
                "Team2Pick5": "Milio"
              }
            }
          ]
        }
        """.trimIndent()

    private val sampleScoreboardJson =
        """
        {
          "cargoquery": [
            {
              "title": {
                "GameId": "LCK/2024 Season/Spring Season_W1D1_1_1",
                "MatchId": "LCK/2024 Season/Spring Season_W1D1_1",
                "Tournament": "LCK 2024 Spring",
                "Team1": "T1",
                "Team2": "Gen.G",
                "WinTeam": "T1",
                "DateTime_UTC": "2024-01-17 08:00:00",
                "Gamelength_Number": 34.5,
                "Patch": "14.1"
              }
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `should parse PicksAndBansS7 into exact 20 draft turns`() =
        runBlocking {
            val mockTransport =
                MockHttpTransport(
                    responses =
                        mapOf(
                            "tables=PicksAndBansS7" to samplePicksAndBansJson,
                            "tables=ScoreboardGames" to sampleScoreboardJson,
                        ),
                )
            val client = LeaguepediaClient(mockTransport)
            val source = LeaguepediaSource(client)

            val games = source.fetchGames("LCK 2024 Spring")
            assertEquals(1, games.size)

            val game = games.first()
            assertEquals("LCK/2024 Season/Spring Season_W1D1_1_1", game.id)
            assertEquals("14.1", game.patch)
            assertEquals("T1", game.blueTeam.name)
            assertEquals("Gen.G", game.redTeam.name)
            assertEquals(Side.BLUE, game.winner)
            assertEquals(2070, game.durationSeconds) // 34.5 minutes * 60 = 2070 seconds

            val draft = game.draftState
            assertEquals(20, draft.turns.size)
            assertTrue(draft.isComplete)

            // Turn 1: T1 (Blue) Ban 1
            val t1 = draft.turns[0]
            assertEquals(1, t1.turnNumber)
            assertEquals(Side.BLUE, t1.side)
            assertEquals(ActionType.BAN, t1.actionType)
            assertEquals("Aatrox", t1.championId)

            // Turn 2: Gen.G (Red) Ban 1
            val t2 = draft.turns[1]
            assertEquals(2, t2.turnNumber)
            assertEquals(Side.RED, t2.side)
            assertEquals(ActionType.BAN, t2.actionType)
            assertEquals("Lucian", t2.championId)

            // Turn 7: T1 (Blue) Pick 1
            val t7 = draft.turns[6]
            assertEquals(7, t7.turnNumber)
            assertEquals(Side.BLUE, t7.side)
            assertEquals(ActionType.PICK, t7.actionType)
            assertEquals("Orianna", t7.championId)

            // Turn 8: Gen.G (Red) Pick 1
            val t8 = draft.turns[7]
            assertEquals(8, t8.turnNumber)
            assertEquals(Side.RED, t8.side)
            assertEquals(ActionType.PICK, t8.actionType)
            assertEquals("Rell", t8.championId)

            // Turn 13: Gen.G (Red) Ban 4 (Red side bans first in Phase 2!)
            val t13 = draft.turns[12]
            assertEquals(13, t13.turnNumber)
            assertEquals(Side.RED, t13.side)
            assertEquals(ActionType.BAN, t13.actionType)
            assertEquals("Poppy", t13.championId)

            // Turn 14: T1 (Blue) Ban 4
            val t14 = draft.turns[13]
            assertEquals(14, t14.turnNumber)
            assertEquals(Side.BLUE, t14.side)
            assertEquals(ActionType.BAN, t14.actionType)
            assertEquals("Vi", t14.championId)

            // Turn 17: Gen.G (Red) Pick 4 (Red side picks first in Phase 2!)
            val t17 = draft.turns[16]
            assertEquals(17, t17.turnNumber)
            assertEquals(Side.RED, t17.side)
            assertEquals(ActionType.PICK, t17.actionType)
            assertEquals("Corki", t17.championId)

            // Turn 20: Gen.G (Red) Pick 5 (Red side last pick!)
            val t20 = draft.turns[19]
            assertEquals(20, t20.turnNumber)
            assertEquals(Side.RED, t20.side)
            assertEquals(ActionType.PICK, t20.actionType)
            assertEquals("Milio", t20.championId)

            // Validate blue/red bans and picks lists in draft state
            assertEquals(5, draft.blueBans.size)
            assertEquals(5, draft.redBans.size)
            assertEquals(5, draft.bluePicks.size)
            assertEquals(5, draft.redPicks.size)
        }

    @Test
    fun `should handle empty or malformed Cargo API responses gracefully`() =
        runBlocking {
            val emptyTransport =
                MockHttpTransport(
                    responses =
                        mapOf(
                            "tables=PicksAndBansS7" to """{"cargoquery": []}""",
                            "tables=ScoreboardGames" to """{"cargoquery": []}""",
                        ),
                )
            val client = LeaguepediaClient(emptyTransport)
            val source = LeaguepediaSource(client)

            val games = source.fetchGames("NonExistentTournament")
            assertTrue(games.isEmpty())
        }
}

package com.loldraft.data.models

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DraftModelTest {
    @Test
    fun `Side opposite should invert correctly`() {
        assertEquals(Side.RED, Side.BLUE.opposite)
        assertEquals(Side.BLUE, Side.RED.opposite)
    }

    @Test
    fun `Role enum should contain standard 5 positions`() {
        val expected = listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT)
        assertEquals(expected, Role.entries)
    }

    @Test
    fun `DraftState should increment turns and accumulate bans and picks correctly`() {
        val emptyState = DraftState.empty()
        assertEquals(0, emptyState.turns.size)
        assertEquals(1, emptyState.currentTurnNumber)
        assertFalse(emptyState.isComplete)

        // Apply Turn 1: Blue Ban Aatrox
        val turn1 = DraftTurn(1, Side.BLUE, ActionType.BAN, "Aatrox")
        val state1 = emptyState.applyTurn(turn1)
        assertEquals(listOf("Aatrox"), state1.blueBans)
        assertTrue(state1.redBans.isEmpty())
        assertEquals(2, state1.currentTurnNumber)

        // Apply Turn 2: Red Ban Ahri
        val turn2 = DraftTurn(2, Side.RED, ActionType.BAN, "Ahri")
        val state2 = state1.applyTurn(turn2)
        assertEquals(listOf("Aatrox"), state2.blueBans)
        assertEquals(listOf("Ahri"), state2.redBans)

        // Fast-forward to Turn 7: Blue Pick Caitlyn
        val turn7 = DraftTurn(7, Side.BLUE, ActionType.PICK, "Caitlyn", role = Role.BOT, player = "Gumayusi")
        val state7 = state2.applyTurn(turn7)
        assertEquals(1, state7.bluePicks.size)
        assertEquals("Caitlyn", state7.bluePicks.first().championId)
        assertEquals(Role.BOT, state7.bluePicks.first().role)
        assertEquals("Gumayusi", state7.bluePicks.first().playerId)
        assertTrue(state7.allPickedChampions.contains("Caitlyn"))
        assertTrue(state7.allBannedChampions.contains("Aatrox"))
        assertTrue(state7.allBannedChampions.contains("Ahri"))
        assertEquals(setOf("Aatrox", "Ahri", "Caitlyn"), state7.allSelectedChampions)
    }

    @Test
    fun `Game and Match should encapsulate teams and draft state`() {
        val t1 = Team(id = "team_t1", name = "T1", code = "T1", region = "LCK")
        val gen = Team(id = "team_gen", name = "Gen.G", code = "GEN", region = "LCK")

        val fバイker = Player(id = "player_faker", name = "Faker", role = Role.MID, teamId = t1.id)
        val chovy = Player(id = "player_chovy", name = "Chovy", role = Role.MID, teamId = gen.id)

        val game1 =
            Game(
                id = "game_1",
                gameNumber = 1,
                patch = "14.18",
                blueTeam = t1,
                redTeam = gen,
                draftState = DraftState.empty(),
                winner = Side.BLUE,
                durationSeconds = 1860,
            )

        val match =
            Match(
                id = "match_finals",
                tournament = "Worlds 2024",
                patch = "14.18",
                bestOf = 5,
                blueTeam = t1,
                redTeam = gen,
                games = listOf(game1),
                winnerTeamId = t1.id,
            )

        assertEquals("Worlds 2024", match.tournament)
        assertEquals(5, match.bestOf)
        assertEquals(1, match.games.size)
        assertEquals(Side.BLUE, match.games.first().winner)
        assertEquals("team_t1", match.winnerTeamId)
    }
}

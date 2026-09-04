package com.loldraft.server

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChampionEmpiricalRoleTest {
    private val t1 = Team("team-t1", "T1", "T1", "LCK")
    private val gen = Team("team-gen", "Gen.G", "GEN", "LCK")

    @Test
    fun `test all champions in ProMatchRepository have non-null primaryRole`() {
        val repo = ProMatchRepository()
        repo.initialize()

        val champions = repo.getChampions()
        assertTrue(champions.isNotEmpty(), "Repository should have champions")

        for (champ in champions) {
            assertNotNull(
                champ.primaryRole,
                "Champion '${champ.name}' (id=${champ.id}) must have a valid primaryRole (cannot be null)",
            )
        }
    }

    @Test
    fun `test uncatalogued champion derives empirical role from match picks`() {
        // Create an uncatalogued champion "Smolder" picked 3 times as BOT and 1 time as MID
        val turns =
            listOf(
                DraftTurn(turnNumber = 1, side = Side.BLUE, actionType = ActionType.PICK, championId = "Smolder", role = Role.BOT),
            )
        val game1 =
            Game(
                id = "g1",
                gameNumber = 1,
                patch = "16.17",
                blueTeam = t1,
                redTeam = gen,
                draftState =
                    DraftState(
                        bluePicks = listOf(PickSelection("Smolder", Role.BOT, "Gumayusi")),
                        turns = turns,
                    ),
            )
        val game2 =
            Game(
                id = "g2",
                gameNumber = 2,
                patch = "16.17",
                blueTeam = t1,
                redTeam = gen,
                draftState =
                    DraftState(
                        bluePicks = listOf(PickSelection("Smolder", Role.BOT, "Gumayusi")),
                    ),
            )
        val game3 =
            Game(
                id = "g3",
                gameNumber = 3,
                patch = "16.17",
                blueTeam = t1,
                redTeam = gen,
                draftState =
                    DraftState(
                        redPicks = listOf(PickSelection("Smolder", Role.MID, "Chovy")),
                    ),
            )

        val repo = ProMatchRepository(initialGames = listOf(game1, game2, game3))
        repo.initialize()

        val champions = repo.getChampions()
        val smolder = champions.find { it.name.equals("Smolder", ignoreCase = true) }
        assertNotNull(smolder, "Smolder should be present in champions list")
        assertEquals(Role.BOT, smolder?.primaryRole, "Smolder should have BOT as empirical primary role")
    }

    @Test
    fun `test champion with only bans has a fallback non-null role`() {
        val game =
            Game(
                id = "g1",
                gameNumber = 1,
                patch = "16.17",
                blueTeam = t1,
                redTeam = gen,
                draftState =
                    DraftState(
                        blueBans = listOf("UnknownBannedHero"),
                    ),
            )
        val repo = ProMatchRepository(initialGames = listOf(game))
        repo.initialize()

        val champ = repo.getChampions().find { it.name.equals("UnknownBannedHero", ignoreCase = true) }
        assertNotNull(champ)
        assertNotNull(champ?.primaryRole, "Even a ban-only champion must have a non-null role")
    }
}

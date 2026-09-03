package com.loldraft.data.serialization

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftPhase
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.Match
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Player
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DraftSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `should serialize and deserialize Side, Role, ActionType, DraftPhase`() {
        assertEquals("\"BLUE\"", json.encodeToString(Side.BLUE))
        assertEquals(Side.BLUE, json.decodeFromString<Side>("\"BLUE\""))

        assertEquals("\"MID\"", json.encodeToString(Role.MID))
        assertEquals(Role.MID, json.decodeFromString<Role>("\"MID\""))

        assertEquals("\"BAN\"", json.encodeToString(ActionType.BAN))
        assertEquals(ActionType.BAN, json.decodeFromString<ActionType>("\"BAN\""))

        assertEquals("\"BAN_PHASE_1\"", json.encodeToString(DraftPhase.BAN_PHASE_1))
        assertEquals(DraftPhase.BAN_PHASE_1, json.decodeFromString<DraftPhase>("\"BAN_PHASE_1\""))
    }

    @Test
    fun `should round-trip serialize DraftTurn`() {
        val turn = DraftTurn(
            turnNumber = 7,
            side = Side.BLUE,
            actionType = ActionType.PICK,
            championId = "Caitlyn",
            role = Role.BOT,
            player = "Gumayusi"
        )

        val serialized = json.encodeToString(turn)
        val deserialized = json.decodeFromString<DraftTurn>(serialized)

        assertEquals(turn, deserialized)
        assertTrue(serialized.contains("\"turnNumber\":7"))
        assertTrue(serialized.contains("\"championId\":\"Caitlyn\""))
    }

    @Test
    fun `should round-trip serialize DraftState`() {
        val state = DraftState(
            blueBans = listOf("Aatrox", "Ahri"),
            redBans = listOf("Akali", "Ashe"),
            bluePicks = listOf(PickSelection("Caitlyn", Role.BOT, "Gumayusi")),
            redPicks = listOf(PickSelection("Corki", Role.MID, "Chovy")),
            turns = listOf(
                DraftTurn(1, Side.BLUE, ActionType.BAN, "Aatrox"),
                DraftTurn(2, Side.RED, ActionType.BAN, "Akali")
            )
        )

        val serialized = json.encodeToString(state)
        val deserialized = json.decodeFromString<DraftState>(serialized)

        assertEquals(state, deserialized)
    }

    @Test
    fun `should round-trip serialize Match and Game`() {
        val t1 = Team("team_t1", "T1", "T1", "LCK")
        val gen = Team("team_gen", "Gen.G", "GEN", "LCK")
        val player = Player("p_faker", "Faker", Role.MID, t1.id)

        val game = Game(
            id = "g_1",
            gameNumber = 1,
            patch = "14.18",
            blueTeam = t1,
            redTeam = gen,
            draftState = DraftState.empty(),
            winner = Side.BLUE,
            durationSeconds = 1920
        )

        val match = Match(
            id = "m_1",
            tournament = "Worlds 2024",
            patch = "14.18",
            bestOf = 5,
            blueTeam = t1,
            redTeam = gen,
            games = listOf(game),
            winnerTeamId = t1.id
        )

        val serializedMatch = json.encodeToString(match)
        val deserializedMatch = json.decodeFromString<Match>(serializedMatch)

        assertEquals(match, deserializedMatch)
        assertEquals(1, deserializedMatch.games.size)
        assertEquals("team_t1", deserializedMatch.winnerTeamId)
    }
}

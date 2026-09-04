package com.loldraft.server

import com.loldraft.data.meta.ChampionRoleDictionary
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.models.FlexPickAnalyzer
import com.loldraft.platform.live.LiveMatchCompanionEngine
import com.loldraft.platform.live.models.CreateLiveSessionRequest
import com.loldraft.platform.pro.api.ProChampionEntry
import com.loldraft.platform.pro.api.proApiRouting
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ChampionEmpiricalRoleTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private fun createGameWithPicks(
        id: String,
        bluePicks: List<PickSelection>,
        redPicks: List<PickSelection>,
    ): Game =
        Game(
            id = id,
            gameNumber = 1,
            patch = "16.17",
            blueTeam = Team("t1", "T1", "T1"),
            redTeam = Team("gen", "Gen.G", "GEN"),
            draftState =
                DraftState(
                    blueBans = emptyList(),
                    redBans = emptyList(),
                    bluePicks = bluePicks,
                    redPicks = redPicks,
                    turns = emptyList(),
                ),
            winner = Side.BLUE,
            durationSeconds = 1800,
            tournament = "LCK",
        )

    @Test
    fun `should calculate empirical primary and secondary roles dynamically from pro match data`() {
        // Construct games where:
        // Jayce: 3 TOP, 1 MID
        // Corki: 3 BOT, 1 MID
        // Poppy: 3 SUPPORT, 2 JUNGLE, 1 TOP
        // Ambessa: 3 TOP, 1 JUNGLE
        // Smolder: 2 BOT
        val g1 =
            createGameWithPicks(
                "g1",
                listOf(
                    PickSelection("Jayce", Role.TOP),
                    PickSelection("Poppy", Role.JUNGLE),
                    PickSelection("Corki", Role.MID),
                    PickSelection("Smolder", Role.BOT),
                    PickSelection("Alistar", Role.SUPPORT),
                ),
                listOf(
                    PickSelection("Ambessa", Role.TOP),
                    PickSelection("Sejuani", Role.JUNGLE),
                    PickSelection("Orianna", Role.MID),
                    PickSelection("Corki", Role.BOT),
                    PickSelection("Poppy", Role.SUPPORT),
                ),
            )

        val g2 =
            createGameWithPicks(
                "g2",
                listOf(
                    PickSelection("Jayce", Role.TOP),
                    PickSelection("Poppy", Role.JUNGLE),
                    PickSelection("Syndra", Role.MID),
                    PickSelection("Corki", Role.BOT),
                    PickSelection("Nautilus", Role.SUPPORT),
                ),
                listOf(
                    PickSelection("Ambessa", Role.TOP),
                    PickSelection("Vi", Role.JUNGLE),
                    PickSelection("Jayce", Role.MID),
                    PickSelection("Smolder", Role.BOT),
                    PickSelection("Poppy", Role.SUPPORT),
                ),
            )

        val g3 =
            createGameWithPicks(
                "g3",
                listOf(
                    PickSelection("Jayce", Role.TOP),
                    PickSelection("Ambessa", Role.JUNGLE),
                    PickSelection("Azir", Role.MID),
                    PickSelection("Corki", Role.BOT),
                    PickSelection("Poppy", Role.SUPPORT),
                ),
                listOf(
                    PickSelection("Poppy", Role.TOP),
                    PickSelection("Lee Sin", Role.JUNGLE),
                    PickSelection("Ahri", Role.MID),
                    PickSelection("Varus", Role.BOT),
                    PickSelection("Leona", Role.SUPPORT),
                ),
            )

        val repo = ProMatchRepository(initialGames = listOf(g1, g2, g3))
        repo.initialize()

        // Verify Jayce empirical roles: TOP primary, MID secondary
        val (jaycePrimary, jayceSecondary) = repo.getChampionEmpiricalRoles("Jayce")
        assertEquals(Role.TOP, jaycePrimary)
        assertTrue(jayceSecondary.contains(Role.MID))

        // Verify Corki empirical roles: BOT primary, MID secondary
        val (corkiPrimary, corkiSecondary) = repo.getChampionEmpiricalRoles("Corki")
        assertEquals(Role.BOT, corkiPrimary)
        assertTrue(corkiSecondary.contains(Role.MID))

        // Verify Poppy empirical roles: SUPPORT primary, JUNGLE and TOP secondary
        val (poppyPrimary, poppySecondary) = repo.getChampionEmpiricalRoles("Poppy")
        assertEquals(Role.SUPPORT, poppyPrimary)
        assertTrue(poppySecondary.contains(Role.JUNGLE))
        assertTrue(poppySecondary.contains(Role.TOP))

        // Verify Ambessa empirical roles: TOP primary, JUNGLE secondary
        val (ambessaPrimary, ambessaSecondary) = repo.getChampionEmpiricalRoles("Ambessa")
        assertEquals(Role.TOP, ambessaPrimary)
        assertTrue(ambessaSecondary.contains(Role.JUNGLE))

        // Verify Smolder empirical roles: BOT primary
        val (smolderPrimary, _) = repo.getChampionEmpiricalRoles("Smolder")
        assertEquals(Role.BOT, smolderPrimary)
    }

    @Test
    fun `FlexPickAnalyzer should dynamically compute role probabilities and flex status for champions`() {
        val analyzer = FlexPickAnalyzer()
        val jayceAnalysis = analyzer.analyzeChampion("Jayce")
        assertEquals(Role.TOP, jayceAnalysis.primaryRole)
        assertTrue((jayceAnalysis.roleProbabilities[Role.MID] ?: 0.0) > 0.15)

        val ambessaAnalysis = analyzer.analyzeChampion("Ambessa")
        assertEquals(Role.TOP, ambessaAnalysis.primaryRole)
        assertTrue((ambessaAnalysis.roleProbabilities[Role.JUNGLE] ?: 0.0) > 0.15)

        val corkiAnalysis = analyzer.analyzeChampion("Corki")
        assertTrue((corkiAnalysis.roleProbabilities[Role.BOT] ?: 0.0) > 0.15)
        assertTrue((corkiAnalysis.roleProbabilities[Role.MID] ?: 0.0) > 0.15)
    }

    @Test
    fun `ChampionRoleDictionary should provide AI-aligned baseline roles for all canonical champions`() {
        val canonicals = ChampionNormalizer.getCanonicalNames()
        assertTrue(canonicals.isNotEmpty())

        for (name in canonicals) {
            val (primary, secondary) = ChampionRoleDictionary.getBaselineRole(name)
            assertNotNull(primary, "Baseline primary role missing for $name")
            assertNotNull(secondary, "Secondary roles set should not be null for $name")
        }

        // Test specific bias fixes
        val (jaycePrimary, jayceSecondary) = ChampionRoleDictionary.getBaselineRole("Jayce")
        assertEquals(Role.TOP, jaycePrimary)
        assertTrue(jayceSecondary.contains(Role.MID))

        val (corkiPrimary, corkiSecondary) = ChampionRoleDictionary.getBaselineRole("Corki")
        assertEquals(Role.BOT, corkiPrimary)
        assertTrue(corkiSecondary.contains(Role.MID))

        val (smolderPrimary, smolderSecondary) = ChampionRoleDictionary.getBaselineRole("Smolder")
        assertEquals(Role.BOT, smolderPrimary)
        assertTrue(smolderSecondary.contains(Role.MID))

        val (ambessaPrimary, ambessaSecondary) = ChampionRoleDictionary.getBaselineRole("Ambessa")
        assertEquals(Role.TOP, ambessaPrimary)
        assertTrue(ambessaSecondary.contains(Role.JUNGLE))

        val (poppyPrimary, poppySecondary) = ChampionRoleDictionary.getBaselineRole("Poppy")
        assertTrue(poppyPrimary == Role.SUPPORT || poppyPrimary == Role.TOP)
        assertTrue(poppySecondary.contains(Role.JUNGLE))
    }

    @Test
    fun `ProMatchRepository getChampions should enrich entries with dynamic empirical and AI roles`() {
        val g1 =
            createGameWithPicks(
                "g1",
                listOf(
                    PickSelection("Jayce", Role.TOP),
                    PickSelection("Poppy", Role.SUPPORT),
                ),
                listOf(
                    PickSelection("Ambessa", Role.TOP),
                    PickSelection("Corki", Role.BOT),
                ),
            )
        val repo = ProMatchRepository(initialGames = listOf(g1))
        repo.initialize()

        val champions = repo.getChampions()
        assertTrue(champions.isNotEmpty())

        val jayce = champions.find { it.name.equals("Jayce", ignoreCase = true) }
        assertNotNull(jayce)
        assertEquals(Role.TOP, jayce.primaryRole)
        assertTrue(jayce.secondaryRoles.contains(Role.MID))

        val corki = champions.find { it.name.equals("Corki", ignoreCase = true) }
        assertNotNull(corki)
        assertEquals(Role.BOT, corki.primaryRole)

        val ambessa = champions.find { it.name.equals("Ambessa", ignoreCase = true) }
        assertNotNull(ambessa)
        assertEquals(Role.TOP, ambessa.primaryRole)

        val smolder = champions.find { it.name.equals("Smolder", ignoreCase = true) }
        assertNotNull(smolder)
        assertEquals(Role.BOT, smolder.primaryRole)
    }

    @Test
    fun `LiveMatchCompanionEngine deduceRole should assign correct primary and flex roles based on AI`() {
        val engine = LiveMatchCompanionEngine()
        val session =
            engine.createSession(
                CreateLiveSessionRequest(
                    sessionId = "test-role-deduction",
                    blueTeam = Team("t1", "T1", "T1"),
                    redTeam = Team("gen", "Gen.G", "GEN"),
                ),
            )

        // Turns 1..6: Ban Phase 1
        listOf("Renekton", "Aatrox", "Sejuani", "Maokai", "Vi", "Jarvan IV").forEach {
            engine.applyTurn(session.sessionId, it)
        }

        // Turn 7: Blue picks Jayce without specifying role -> AI deduces TOP as highest vacant probability
        val snap1 = engine.applyTurn(session.sessionId, "Jayce")
        assertEquals(Role.TOP, snap1.turn?.role)

        // Turn 8: Red picks Jinx -> AI deduces BOT
        val snap2 = engine.applyTurn(session.sessionId, "Jinx")
        assertEquals(Role.BOT, snap2.turn?.role)

        // Turn 9: Red picks Corki -> BOT is taken by Jinx, AI flex probability deduces MID
        val snap3 = engine.applyTurn(session.sessionId, "Corki")
        assertEquals(Role.MID, snap3.turn?.role)

        // Turn 10: Blue picks Ambessa -> TOP is taken by Jayce, AI flex probability deduces JUNGLE
        val snap4 = engine.applyTurn(session.sessionId, "Ambessa")
        assertEquals(Role.JUNGLE, snap4.turn?.role)
    }

    @Test
    fun `REST API GET champions should return secondaryRoles array in JSON response`() =
        testApplication {
            val games =
                listOf(
                    createGameWithPicks(
                        "g1",
                        listOf(PickSelection("Jayce", Role.TOP)),
                        listOf(PickSelection("Corki", Role.BOT)),
                    ),
                )
            val repo = ProMatchRepository(initialGames = games)
            repo.initialize()

            application {
                install(ContentNegotiation) { json(json) }
                routing { proApiRouting(repo) }
            }

            val response = client.get("/api/pro/champions")
            assertEquals(HttpStatusCode.OK, response.status)

            val champions = json.decodeFromString<List<ProChampionEntry>>(response.bodyAsText())
            val jayce = champions.find { it.name.equals("Jayce", ignoreCase = true) }
            assertNotNull(jayce)
            assertEquals(Role.TOP, jayce.primaryRole)
            assertTrue(jayce.secondaryRoles.contains(Role.MID))
        }
}

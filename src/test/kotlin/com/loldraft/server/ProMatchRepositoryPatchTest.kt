package com.loldraft.server

import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
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

class ProMatchRepositoryPatchTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private fun createGame(
        id: String,
        patch: String,
    ): Game {
        val bluePicks =
            listOf(
                PickSelection("Jayce", Role.TOP, "Player1"),
                PickSelection("Sejuani", Role.JUNGLE, "Player2"),
                PickSelection("Orianna", Role.MID, "Player3"),
                PickSelection("Corki", Role.BOT, "Player4"),
                PickSelection("Poppy", Role.SUPPORT, "Player5"),
            )
        val redPicks =
            listOf(
                PickSelection("Ambessa", Role.TOP, "Player6"),
                PickSelection("Vi", Role.JUNGLE, "Player7"),
                PickSelection("Azir", Role.MID, "Player8"),
                PickSelection("Smolder", Role.BOT, "Player9"),
                PickSelection("Nautilus", Role.SUPPORT, "Player10"),
            )
        return Game(
            id = id,
            gameNumber = 1,
            patch = patch,
            blueTeam = Team("t1", "T1", "T1"),
            redTeam = Team("gen", "Gen.G", "GEN"),
            draftState =
                DraftState(
                    blueBans = listOf("Lucian", "Kalista"),
                    redBans = listOf("Ashe", "Varus"),
                    bluePicks = bluePicks,
                    redPicks = redPicks,
                    turns = emptyList(),
                ),
            winner = Side.BLUE,
            durationSeconds = 1800,
            tournament = "LCK",
        )
    }

    @Test
    fun `should extract all distinct patches and sort them in strict descending numerical order`() {
        // Deliberately unordered patches with multi-digit versions
        val games =
            listOf(
                createGame("g1", "16.1"),
                createGame("g2", "16.10"),
                createGame("g3", "16.2"),
                createGame("g4", "16.9"),
                createGame("g5", "16.17"),
                createGame("g6", "16.17"), // duplicate
            )
        val repo = ProMatchRepository(initialGames = games)
        repo.initialize()

        val patches = repo.getPatches()
        // Strict version descending: 16.17 > 16.10 > 16.9 > 16.2 > 16.1
        assertEquals(listOf("16.17", "16.10", "16.9", "16.2", "16.1"), patches)
    }

    @Test
    fun `getLatestPatch should return the highest sorted patch or default to 16_17 when empty`() {
        // When matches exist
        val games =
            listOf(
                createGame("g1", "16.1"),
                createGame("g2", "16.17"),
                createGame("g3", "16.8"),
            )
        val repo = ProMatchRepository(initialGames = games)
        repo.initialize()
        assertEquals("16.17", repo.getLatestPatch())

        // When repository is empty
        val emptyRepo = ProMatchRepository(initialGames = emptyList())
        emptyRepo.initialize()
        assertEquals("16.17", emptyRepo.getLatestPatch())
    }

    @Test
    fun `getPatchMeta should calculate meta matrix for given patch and latest patch`() {
        val games =
            listOf(
                createGame("g1", "16.1"),
                createGame("g2", "16.17"),
                createGame("g3", "16.17"),
            )
        val repo = ProMatchRepository(initialGames = games)
        repo.initialize()

        // Query specific patch 16.17
        val meta17 = repo.getPatchMeta("16.17")
        assertNotNull(meta17)
        assertEquals("16.17", meta17.patch)
        assertEquals(2, meta17.totalGames)
        assertTrue(meta17.championStats.containsKey("jayce"))
        assertTrue(meta17.championStats.containsKey("ambessa"))

        // Query latest patch (null or "latest")
        val latestMeta = repo.getPatchMeta(null)
        assertEquals("16.17", latestMeta.patch)
        assertEquals(2, latestMeta.totalGames)

        val latestNamedMeta = repo.getPatchMeta("latest")
        assertEquals("16.17", latestNamedMeta.patch)
        assertEquals(2, latestNamedMeta.totalGames)

        // Query non-existent patch
        val emptyMeta = repo.getPatchMeta("99.99")
        assertEquals(0, emptyMeta.totalGames)
    }

    @Test
    fun `REST API GET patches should return sorted patches array`() =
        testApplication {
            val games =
                listOf(
                    createGame("g1", "16.02"),
                    createGame("g2", "16.17"),
                    createGame("g3", "16.09"),
                )
            val repo = ProMatchRepository(initialGames = games)
            repo.initialize()

            application {
                install(ContentNegotiation) { json(json) }
                routing { proApiRouting(repo) }
            }

            val response = client.get("/api/pro/patches")
            assertEquals(HttpStatusCode.OK, response.status)

            val patches = json.decodeFromString<List<String>>(response.bodyAsText())
            assertEquals(listOf("16.17", "16.09", "16.02"), patches)
        }

    @Test
    fun `REST API GET patch meta endpoint should return meta matrix for patch or latest`() =
        testApplication {
            val games =
                listOf(
                    createGame("g1", "16.17"),
                    createGame("g2", "16.17"),
                )
            val repo = ProMatchRepository(initialGames = games)
            repo.initialize()

            application {
                install(ContentNegotiation) { json(json) }
                routing { proApiRouting(repo) }
            }

            // Query by patch
            val res17 = client.get("/api/pro/patches/16.17/meta")
            assertEquals(HttpStatusCode.OK, res17.status)
            val meta17 = json.decodeFromString<PatchMetaMatrix>(res17.bodyAsText())
            assertEquals("16.17", meta17.patch)
            assertEquals(2, meta17.totalGames)

            // Query by "latest"
            val resLatest = client.get("/api/pro/patches/latest/meta")
            assertEquals(HttpStatusCode.OK, resLatest.status)
            val metaLatest = json.decodeFromString<PatchMetaMatrix>(resLatest.bodyAsText())
            assertEquals("16.17", metaLatest.patch)
            assertEquals(2, metaLatest.totalGames)
        }

    @Test
    fun `REST API GET health should include latestPatch and patchesCount`() =
        testApplication {
            val games = listOf(createGame("g1", "16.17"))
            val repo = ProMatchRepository(initialGames = games)
            repo.initialize()

            application {
                install(ContentNegotiation) { json(json) }
                routing { proApiRouting(repo) }
            }

            val response = client.get("/api/pro/health")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("\"latestPatch\":\"16.17\""))
            assertTrue(body.contains("\"patchesCount\":\"1\""))
        }
}

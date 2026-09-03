package com.loldraft.server

import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.platform.pro.api.ProChampionEntry
import com.loldraft.platform.pro.api.ProPlayerRosterEntry
import com.loldraft.platform.pro.api.ProTeamSummary
import com.loldraft.platform.pro.api.proApiRouting
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProApiRoutingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private fun createSampleRepository(): ProMatchRepository {
        val t1 = Team("t1", "T1", "T1")
        val gen = Team("gen", "Gen.G", "GEN")
        val blg = Team("blg", "Bilibili Gaming", "BLG")

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
                PickSelection("K'Sante", Role.TOP, "Kiin"),
                PickSelection("Rell", Role.JUNGLE, "Canyon"),
                PickSelection("Corki", Role.MID, "Chovy"),
                PickSelection("Aphelios", Role.BOT, "Peyz"),
                PickSelection("Milio", Role.SUPPORT, "Lehends"),
            )

        val game1 =
            Game(
                id = "game-1",
                gameNumber = 1,
                patch = "16.1",
                blueTeam = t1,
                redTeam = gen,
                draftState =
                    DraftState(
                        blueBans = listOf("Lucian", "Kalista"),
                        redBans = listOf("Sejuani", "Azir"),
                        bluePicks = bluePicks,
                        redPicks = redPicks,
                        turns = emptyList(),
                    ),
                winner = Side.BLUE,
                durationSeconds = 1800,
                tournament = "LCK",
            )

        val repo = ProMatchRepository(initialGames = listOf(game1))
        repo.initialize()
        return repo
    }

    @Test
    fun `test health endpoint returns status UP and metadata`() =
        testApplication {
            val repository = createSampleRepository()
            application {
                install(ContentNegotiation) { json(json) }
                routing { proApiRouting(repository) }
            }

            val response = client.get("/api/pro/health")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("UP"))
            assertTrue(body.contains("pro-match-repository"))
        }

    @Test
    fun `test get leagues endpoint returns leagues array`() =
        testApplication {
            val repository = createSampleRepository()
            application {
                install(ContentNegotiation) { json(json) }
                routing { proApiRouting(repository) }
            }

            val response = client.get("/api/pro/leagues")
            assertEquals(HttpStatusCode.OK, response.status)
            val leagues = json.decodeFromString<List<String>>(response.bodyAsText())
            assertEquals(listOf("LCK"), leagues)
        }

    @Test
    fun `test get teams endpoint supports filtering by league and query`() =
        testApplication {
            val repository = createSampleRepository()
            application {
                install(ContentNegotiation) { json(json) }
                routing { proApiRouting(repository) }
            }

            // All teams
            val allRes = client.get("/api/pro/teams")
            assertEquals(HttpStatusCode.OK, allRes.status)
            val allTeams = json.decodeFromString<List<ProTeamSummary>>(allRes.bodyAsText())
            assertEquals(2, allTeams.size)

            // Filter by league
            val lckRes = client.get("/api/pro/teams?league=LCK")
            val lckTeams = json.decodeFromString<List<ProTeamSummary>>(lckRes.bodyAsText())
            assertEquals(2, lckTeams.size)

            // Filter by query
            val queryRes = client.get("/api/pro/teams?query=T1")
            val queryTeams = json.decodeFromString<List<ProTeamSummary>>(queryRes.bodyAsText())
            assertEquals(1, queryTeams.size)
            assertEquals("T1", queryTeams.first().name)
        }

    @Test
    fun `test get team profile returns profile or 404`() =
        testApplication {
            val repository = createSampleRepository()
            application {
                install(ContentNegotiation) { json(json) }
                install(StatusPages) {
                    exception<NoSuchElementException> { call, cause ->
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to (cause.message ?: "Not found")))
                    }
                }
                routing { proApiRouting(repository) }
            }

            val okRes = client.get("/api/pro/teams/t1")
            assertEquals(HttpStatusCode.OK, okRes.status)
            assertTrue(okRes.bodyAsText().contains("T1"))

            val notFoundRes = client.get("/api/pro/teams/unknown-team-404")
            assertEquals(HttpStatusCode.NotFound, notFoundRes.status)
        }

    @Test
    fun `test get team roster returns players list`() =
        testApplication {
            val repository = createSampleRepository()
            application {
                install(ContentNegotiation) { json(json) }
                routing { proApiRouting(repository) }
            }

            val response = client.get("/api/pro/teams/t1/roster")
            assertEquals(HttpStatusCode.OK, response.status)
            val roster = json.decodeFromString<List<ProPlayerRosterEntry>>(response.bodyAsText())
            assertEquals(5, roster.size)
            val faker = roster.find { it.role == Role.MID }
            assertNotNull(faker)
            assertEquals("Faker", faker.playerName)
        }

    @Test
    fun `test get champions returns champion catalog`() =
        testApplication {
            val repository = createSampleRepository()
            application {
                install(ContentNegotiation) { json(json) }
                routing { proApiRouting(repository) }
            }

            val response = client.get("/api/pro/champions")
            assertEquals(HttpStatusCode.OK, response.status)
            val champions = json.decodeFromString<List<ProChampionEntry>>(response.bodyAsText())
            assertTrue(champions.isNotEmpty())
            assertTrue(champions.any { it.name.equals("Orianna", ignoreCase = true) })
        }
}

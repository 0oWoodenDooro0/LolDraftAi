package com.loldraft.platform.debrief.api

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Game
import com.loldraft.data.models.Match
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.platform.debrief.PostMatchDebriefEngine
import com.loldraft.platform.debrief.models.DebriefGameRequest
import com.loldraft.platform.debrief.models.DebriefMatchRequest
import com.loldraft.platform.debrief.models.DebriefReport
import com.loldraft.platform.debrief.models.MatchDebriefReport
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DebriefApiTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val blueTeam = Team("team-t1", "T1", "T1", "LCK")
    private val redTeam = Team("team-gen", "Gen.G", "GEN", "LCK")

    private fun createStandardTurns(): List<DraftTurn> =
        listOf(
            DraftTurn(1, Side.BLUE, ActionType.BAN, "Kalista"),
            DraftTurn(2, Side.RED, ActionType.BAN, "Rumble"),
            DraftTurn(3, Side.BLUE, ActionType.BAN, "Lucian"),
            DraftTurn(4, Side.RED, ActionType.BAN, "Ashe"),
            DraftTurn(5, Side.BLUE, ActionType.BAN, "Varus"),
            DraftTurn(6, Side.RED, ActionType.BAN, "Caitlyn"),
            DraftTurn(7, Side.BLUE, ActionType.PICK, "Jayce", Role.TOP, "Zeus"),
            DraftTurn(8, Side.RED, ActionType.PICK, "Sion", Role.TOP, "Kiin"),
            DraftTurn(9, Side.RED, ActionType.PICK, "Sejuani", Role.JUNGLE, "Canyon"),
            DraftTurn(10, Side.BLUE, ActionType.PICK, "Viego", Role.JUNGLE, "Oner"),
            DraftTurn(11, Side.BLUE, ActionType.PICK, "Azir", Role.MID, "Faker"),
            DraftTurn(12, Side.RED, ActionType.PICK, "Orianna", Role.MID, "Chovy"),
            DraftTurn(13, Side.RED, ActionType.BAN, "Braum"),
            DraftTurn(14, Side.BLUE, ActionType.BAN, "Kai'Sa"),
            DraftTurn(15, Side.RED, ActionType.BAN, "Leona"),
            DraftTurn(16, Side.BLUE, ActionType.BAN, "Xayah"),
            DraftTurn(17, Side.RED, ActionType.PICK, "Jinx", Role.BOT, "Peyz"),
            DraftTurn(18, Side.BLUE, ActionType.PICK, "Ezreal", Role.BOT, "Gumayusi"),
            DraftTurn(19, Side.BLUE, ActionType.PICK, "Nautilus", Role.SUPPORT, "Keria"),
            DraftTurn(20, Side.RED, ActionType.PICK, "Thresh", Role.SUPPORT, "Delight"),
        )

    private fun createGame(
        id: String,
        winner: Side = Side.BLUE,
    ): Game =
        Game(
            id = id,
            gameNumber = 1,
            patch = "14.10",
            blueTeam = blueTeam,
            redTeam = redTeam,
            draftState = DraftState.fromTurns(createStandardTurns()),
            winner = winner,
            durationSeconds = 1860,
            tournament = "LCK 2024 Summer",
        )

    @Test
    fun testHealthEndpoint() =
        testApplication {
            application {
                debriefModule(PostMatchDebriefEngine())
            }

            val response = client.get("/api/debrief/health")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("UP"))
            assertTrue(body.contains("post-match-debrief"))
        }

    @Test
    fun testAnalyzeGameRestEndpoint() =
        testApplication {
            val engine = PostMatchDebriefEngine()
            application {
                debriefModule(engine)
            }

            val game = createGame("game-api-1", Side.BLUE)
            val req = DebriefGameRequest(game = game)

            val res =
                client.post("/api/debrief/game") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(json.encodeToString(req))
                }

            assertEquals(HttpStatusCode.OK, res.status)
            val report = json.decodeFromString<DebriefReport>(res.bodyAsText())
            assertEquals("game-api-1", report.gameId)
            assertEquals(Side.BLUE, report.actualWinner)
            assertEquals(20, report.turns.size)
            assertNotNull(report.attribution)
            assertNotNull(report.blueCoachSummary)
            assertNotNull(report.redCoachSummary)

            // Verify report was stored and can be retrieved
            val getRes = client.get("/api/debrief/reports/${report.reportId}")
            assertEquals(HttpStatusCode.OK, getRes.status)

            // Verify markdown endpoint
            val mdRes = client.get("/api/debrief/reports/${report.reportId}/markdown")
            assertEquals(HttpStatusCode.OK, mdRes.status)
            assertTrue(mdRes.bodyAsText().contains("Post-Match BP Debrief Report"))
        }

    @Test
    fun testAnalyzeMatchRestEndpoint() =
        testApplication {
            val engine = PostMatchDebriefEngine()
            application {
                debriefModule(engine)
            }

            val match =
                Match(
                    id = "match-api-1",
                    tournament = "LCK Playoffs",
                    patch = "14.10",
                    bestOf = 3,
                    blueTeam = blueTeam,
                    redTeam = redTeam,
                    games = listOf(createGame("match-g1", Side.BLUE), createGame("match-g2", Side.RED)),
                    winnerTeamId = "team-t1",
                )
            val req = DebriefMatchRequest(match = match)

            val res =
                client.post("/api/debrief/match") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(json.encodeToString(req))
                }

            assertEquals(HttpStatusCode.OK, res.status)
            val report = json.decodeFromString<MatchDebriefReport>(res.bodyAsText())
            assertEquals("match-api-1", report.matchId)
            assertEquals(2, report.gamesPlayed)
            assertEquals(2, report.gameReports.size)
        }

    @Test
    fun testGetReportNotFound() =
        testApplication {
            application {
                debriefModule(PostMatchDebriefEngine())
            }

            val res = client.get("/api/debrief/reports/non-existent-report-id")
            assertEquals(HttpStatusCode.NotFound, res.status)
        }
}

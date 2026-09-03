package com.loldraft.platform.live.api

import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.platform.live.LiveMatchCompanionEngine
import com.loldraft.platform.live.models.ApplyTurnRequest
import com.loldraft.platform.live.models.CreateLiveSessionRequest
import com.loldraft.platform.live.models.LiveSessionSummaryResponse
import com.loldraft.platform.live.models.LiveTurnSnapshot
import com.loldraft.platform.live.models.LiveWsClientMessage
import com.loldraft.platform.live.models.LiveWsServerMessage
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveCompanionApiTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val t1 = Team("team-t1", "T1", "T1", "LCK")
    private val gen = Team("team-gen", "Gen.G", "GEN", "LCK")

    private fun createEngine(): LiveMatchCompanionEngine = LiveMatchCompanionEngine()

    @Test
    fun testHealthEndpoint() =
        testApplication {
            application {
                liveCompanionModule(createEngine())
            }

            val response = client.get("/api/live/health")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains("UP"))
            assertTrue(body.contains("live-match-companion"))
        }

    @Test
    fun testCreateAndGetSessionRest() =
        testApplication {
            val engine = createEngine()
            application {
                liveCompanionModule(engine)
            }

            val createReq =
                CreateLiveSessionRequest(
                    sessionId = "rest-session-1",
                    blueTeam = t1,
                    redTeam = gen,
                )

            val createRes =
                client.post("/api/live/sessions") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(json.encodeToString(createReq))
                }

            assertEquals(HttpStatusCode.Created, createRes.status)
            val created = json.decodeFromString<LiveSessionSummaryResponse>(createRes.bodyAsText())
            assertEquals("rest-session-1", created.sessionId)
            assertEquals("T1", created.blueTeam.code)
            assertEquals("GEN", created.redTeam.code)
            assertEquals(0, created.latestSnapshot.turnNumber)

            val getRes = client.get("/api/live/sessions/rest-session-1")
            assertEquals(HttpStatusCode.OK, getRes.status)
            val fetched = json.decodeFromString<LiveSessionSummaryResponse>(getRes.bodyAsText())
            assertEquals("rest-session-1", fetched.sessionId)
            assertEquals(1, fetched.currentTurnNumber)
        }

    @Test
    fun testApplyTurnRest() =
        testApplication {
            val engine = createEngine()
            application {
                liveCompanionModule(engine)
            }

            engine.createSession(
                CreateLiveSessionRequest(
                    sessionId = "turn-session-1",
                    blueTeam = t1,
                    redTeam = gen,
                ),
            )

            val applyRes =
                client.post("/api/live/sessions/turn-session-1/turns") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(json.encodeToString(ApplyTurnRequest(championId = "Kalista")))
                }

            assertEquals(HttpStatusCode.OK, applyRes.status)
            val snapshot = json.decodeFromString<LiveTurnSnapshot>(applyRes.bodyAsText())
            assertEquals(1, snapshot.turnNumber)
            assertEquals("Kalista", snapshot.turn?.championId)
            assertEquals(Side.BLUE, snapshot.turn?.side)
            assertEquals(ActionType.BAN, snapshot.turn?.actionType)
            assertNotNull(snapshot.coachPickFeedback)
        }

    @Test
    fun testUndoAndResetRest() =
        testApplication {
            val engine = createEngine()
            application {
                liveCompanionModule(engine)
            }

            val session =
                engine.createSession(
                    CreateLiveSessionRequest(
                        sessionId = "undo-reset-session",
                        blueTeam = t1,
                        redTeam = gen,
                    ),
                )

            engine.applyTurn(session.sessionId, "Kalista")
            engine.applyTurn(session.sessionId, "Rumble")

            // Test Undo
            val undoRes = client.post("/api/live/sessions/undo-reset-session/undo")
            assertEquals(HttpStatusCode.OK, undoRes.status)
            val undoSnapshot = json.decodeFromString<LiveTurnSnapshot>(undoRes.bodyAsText())
            assertEquals(1, undoSnapshot.turnNumber)

            // Test Reset
            val resetRes = client.post("/api/live/sessions/undo-reset-session/reset")
            assertEquals(HttpStatusCode.OK, resetRes.status)
            val resetSnapshot = json.decodeFromString<LiveTurnSnapshot>(resetRes.bodyAsText())
            assertEquals(0, resetSnapshot.turnNumber)
        }

    @Test
    fun testGetHistoryRest() =
        testApplication {
            val engine = createEngine()
            application {
                liveCompanionModule(engine)
            }

            val session =
                engine.createSession(
                    CreateLiveSessionRequest(
                        sessionId = "history-session",
                        blueTeam = t1,
                        redTeam = gen,
                    ),
                )

            engine.applyTurn(session.sessionId, "Kalista")
            engine.applyTurn(session.sessionId, "Rumble")

            val historyRes = client.get("/api/live/sessions/history-session/history")
            assertEquals(HttpStatusCode.OK, historyRes.status)
            val history = json.decodeFromString<List<LiveTurnSnapshot>>(historyRes.bodyAsText())
            assertEquals(3, history.size) // Turn 0, Turn 1, Turn 2
            assertEquals(0, history[0].turnNumber)
            assertEquals(1, history[1].turnNumber)
            assertEquals(2, history[2].turnNumber)
        }

    @Test
    fun testErrorHandlingRest() =
        testApplication {
            val engine = createEngine()
            application {
                liveCompanionModule(engine)
            }

            // Unknown session
            val notFoundRes = client.get("/api/live/sessions/unknown-session-id")
            assertEquals(HttpStatusCode.NotFound, notFoundRes.status)

            // Duplicate champion
            engine.createSession(
                CreateLiveSessionRequest(
                    sessionId = "dup-test",
                    blueTeam = t1,
                    redTeam = gen,
                ),
            )
            engine.applyTurn("dup-test", "Kalista")

            val dupRes =
                client.post("/api/live/sessions/dup-test/turns") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(json.encodeToString(ApplyTurnRequest(championId = "Kalista")))
                }
            assertEquals(HttpStatusCode.BadRequest, dupRes.status)
            assertTrue(dupRes.bodyAsText().contains("already") || dupRes.bodyAsText().contains("duplicate"))
        }

    @Test
    fun testWebSocketConnectionAndStreaming() =
        testApplication {
            val engine = createEngine()
            application {
                liveCompanionModule(engine)
            }

            engine.createSession(
                CreateLiveSessionRequest(
                    sessionId = "ws-session-1",
                    blueTeam = t1,
                    redTeam = gen,
                ),
            )

            val wsClient =
                createClient {
                    install(WebSockets)
                }

            wsClient.webSocket("/api/live/ws/ws-session-1") {
                // 1. Initial snapshot received upon connecting
                val initialFrame = incoming.receive() as Frame.Text
                val initialMsg = json.decodeFromString<LiveWsServerMessage>(initialFrame.readText())
                assertTrue(initialMsg is LiveWsServerMessage.SessionSnapshot)
                val snapshotMsg = initialMsg as LiveWsServerMessage.SessionSnapshot
                assertEquals("ws-session-1", snapshotMsg.session.sessionId)
                assertEquals(0, snapshotMsg.latestSnapshot.turnNumber)

                // 2. Client sends ping -> receives pong
                send(Frame.Text(json.encodeToString<LiveWsClientMessage>(LiveWsClientMessage.Ping)))
                val pongFrame = incoming.receive() as Frame.Text
                val pongMsg = json.decodeFromString<LiveWsServerMessage>(pongFrame.readText())
                assertTrue(pongMsg is LiveWsServerMessage.Pong)

                // 3. Client applies turn over WebSocket -> receives TurnApplied
                val applyMsg = LiveWsClientMessage.ApplyTurn(championId = "Kalista")
                send(Frame.Text(json.encodeToString<LiveWsClientMessage>(applyMsg)))
                val turnAppliedFrame = incoming.receive() as Frame.Text
                val turnAppliedMsg = json.decodeFromString<LiveWsServerMessage>(turnAppliedFrame.readText())
                assertTrue(turnAppliedMsg is LiveWsServerMessage.TurnApplied)
                val turnApplied = turnAppliedMsg as LiveWsServerMessage.TurnApplied
                assertEquals("Kalista", turnApplied.turn.championId)
                assertEquals(1, turnApplied.snapshot.turnNumber)

                // 4. Client sends Undo -> receives TurnUndone
                send(Frame.Text(json.encodeToString<LiveWsClientMessage>(LiveWsClientMessage.Undo)))
                val undoFrame = incoming.receive() as Frame.Text
                val undoMsg = json.decodeFromString<LiveWsServerMessage>(undoFrame.readText())
                assertTrue(undoMsg is LiveWsServerMessage.TurnUndone)
                val turnUndone = undoMsg as LiveWsServerMessage.TurnUndone
                assertEquals(1, turnUndone.undoneTurnNumber)
                assertEquals(0, turnUndone.snapshot.turnNumber)
            }
        }
}

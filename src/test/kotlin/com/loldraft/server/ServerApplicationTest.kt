package com.loldraft.server

import com.loldraft.data.models.Team
import com.loldraft.platform.live.models.CreateLiveSessionRequest
import com.loldraft.platform.live.models.LiveSessionSummaryResponse
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

class ServerApplicationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val t1 = Team("team-t1", "T1", "T1", "LCK")
    private val gen = Team("team-gen", "Gen.G", "GEN", "LCK")

    @Test
    fun `test all health endpoints are mounted and healthy on unified server`() =
        testApplication {
            application {
                mainModule()
            }

            // Live Companion health
            val liveRes = client.get("/api/live/health")
            assertEquals(HttpStatusCode.OK, liveRes.status)
            assertTrue(liveRes.bodyAsText().contains("live-match-companion"))

            // Sandbox health
            val sandboxRes = client.get("/api/sandbox/health")
            assertEquals(HttpStatusCode.OK, sandboxRes.status)
            assertTrue(sandboxRes.bodyAsText().contains("pre-match-sandbox"))

            // Debrief health
            val debriefRes = client.get("/api/debrief/health")
            assertEquals(HttpStatusCode.OK, debriefRes.status)
            assertTrue(debriefRes.bodyAsText().contains("post-match-debrief"))

            // Pro Match health
            val proRes = client.get("/api/pro/health")
            assertEquals(HttpStatusCode.OK, proRes.status)
            assertTrue(proRes.bodyAsText().contains("pro-match-repository"))
        }

    @Test
    fun `test static web dashboard index html is served at root`() =
        testApplication {
            application {
                mainModule()
            }

            val rootRes = client.get("/")
            assertEquals(HttpStatusCode.OK, rootRes.status)
            val rootHtml = rootRes.bodyAsText()
            assertTrue(rootHtml.contains("LoL Draft AI") || rootHtml.contains("BP"))

            val indexRes = client.get("/index.html")
            assertEquals(HttpStatusCode.OK, indexRes.status)
            val indexHtml = indexRes.bodyAsText()
            assertTrue(indexHtml.contains("<!DOCTYPE html>"))
        }

    @Test
    fun `test full websocket BP session flow on unified server`() =
        testApplication {
            application {
                mainModule()
            }

            val wsClient =
                createClient {
                    install(WebSockets)
                }

            // 1. Create a session via REST
            val createReq =
                CreateLiveSessionRequest(
                    sessionId = "unified-session-001",
                    blueTeam = t1,
                    redTeam = gen,
                )

            val createRes =
                client.post("/api/live/sessions") {
                    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    setBody(json.encodeToString(createReq))
                }
            assertEquals(HttpStatusCode.Created, createRes.status)

            val summary = json.decodeFromString<LiveSessionSummaryResponse>(createRes.bodyAsText())
            assertEquals("unified-session-001", summary.sessionId)

            // 2. Connect WebSocket and interact
            wsClient.webSocket("/api/live/ws/unified-session-001") {
                // Initial snapshot
                val initialFrame = incoming.receive() as Frame.Text
                val initialMsg = json.decodeFromString<LiveWsServerMessage>(initialFrame.readText())
                assertTrue(initialMsg is LiveWsServerMessage.SessionSnapshot)

                // Ping - Pong
                send(Frame.Text(json.encodeToString<LiveWsClientMessage>(LiveWsClientMessage.Ping)))
                val pongFrame = incoming.receive() as Frame.Text
                val pongMsg = json.decodeFromString<LiveWsServerMessage>(pongFrame.readText())
                assertTrue(pongMsg is LiveWsServerMessage.Pong)

                // Apply Turn 1 (Blue Ban: Kalista)
                val applyMsg = LiveWsClientMessage.ApplyTurn(championId = "Kalista")
                send(Frame.Text(json.encodeToString<LiveWsClientMessage>(applyMsg)))

                val turnFrame = incoming.receive() as Frame.Text
                val turnMsg = json.decodeFromString<LiveWsServerMessage>(turnFrame.readText())
                assertTrue(turnMsg is LiveWsServerMessage.TurnApplied)
                val turnApplied = turnMsg as LiveWsServerMessage.TurnApplied
                assertEquals("Kalista", turnApplied.turn.championId)
                assertEquals(1, turnApplied.snapshot.turnNumber)
                assertNotNull(turnApplied.snapshot.evalBar)

                // Undo Turn 1
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

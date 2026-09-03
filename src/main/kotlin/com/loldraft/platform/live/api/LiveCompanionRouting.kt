package com.loldraft.platform.live.api

import com.loldraft.platform.live.LiveMatchCompanionEngine
import com.loldraft.platform.live.models.ApplyTurnRequest
import com.loldraft.platform.live.models.CreateLiveSessionRequest
import com.loldraft.platform.live.models.LiveSessionSummaryResponse
import com.loldraft.platform.live.models.LiveWsClientMessage
import com.loldraft.platform.live.models.LiveWsServerMessage
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val liveJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

fun Route.liveCompanionRouting(engine: LiveMatchCompanionEngine) {
    route("/api/live") {
        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "status" to "UP",
                    "module" to "live-match-companion",
                ),
            )
        }

        post("/sessions") {
            val request = call.receive<CreateLiveSessionRequest>()
            val session = engine.createSession(request)
            val summary =
                LiveSessionSummaryResponse(
                    sessionId = session.sessionId,
                    blueTeam = session.blueTeam,
                    redTeam = session.redTeam,
                    status = session.status,
                    currentTurnNumber = session.currentState.currentTurnNumber,
                    isComplete = session.currentState.isComplete,
                    latestSnapshot = session.history.last(),
                )
            call.respond(HttpStatusCode.Created, summary)
        }

        get("/sessions/{sessionId}") {
            val sessionId = call.parameters["sessionId"] ?: throw IllegalArgumentException("Missing sessionId")
            val session = engine.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session '$sessionId' not found"))
                return@get
            }
            val summary =
                LiveSessionSummaryResponse(
                    sessionId = session.sessionId,
                    blueTeam = session.blueTeam,
                    redTeam = session.redTeam,
                    status = session.status,
                    currentTurnNumber = session.currentState.currentTurnNumber,
                    isComplete = session.currentState.isComplete,
                    latestSnapshot = session.history.last(),
                )
            call.respond(HttpStatusCode.OK, summary)
        }

        get("/sessions/{sessionId}/history") {
            val sessionId = call.parameters["sessionId"] ?: throw IllegalArgumentException("Missing sessionId")
            val session = engine.getSession(sessionId)
            if (session == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Session '$sessionId' not found"))
                return@get
            }
            call.respond(HttpStatusCode.OK, session.history)
        }

        post("/sessions/{sessionId}/turns") {
            val sessionId = call.parameters["sessionId"] ?: throw IllegalArgumentException("Missing sessionId")
            val request = call.receive<ApplyTurnRequest>()
            val snapshot = engine.applyTurn(sessionId, request.championId, request.role, request.player)
            call.respond(HttpStatusCode.OK, snapshot)
        }

        post("/sessions/{sessionId}/undo") {
            val sessionId = call.parameters["sessionId"] ?: throw IllegalArgumentException("Missing sessionId")
            val snapshot = engine.undoTurn(sessionId)
            call.respond(HttpStatusCode.OK, snapshot)
        }

        post("/sessions/{sessionId}/reset") {
            val sessionId = call.parameters["sessionId"] ?: throw IllegalArgumentException("Missing sessionId")
            val snapshot = engine.resetSession(sessionId)
            call.respond(HttpStatusCode.OK, snapshot)
        }

        webSocket("/ws/{sessionId}") {
            val sessionId =
                call.parameters["sessionId"] ?: run {
                    close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Missing sessionId"))
                    return@webSocket
                }

            val session = engine.getSession(sessionId)
            if (session == null) {
                send(
                    Frame.Text(
                        liveJson.encodeToString<LiveWsServerMessage>(
                            LiveWsServerMessage.Error("SESSION_NOT_FOUND", "Session '$sessionId' not found"),
                        ),
                    ),
                )
                close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Session not found"))
                return@webSocket
            }

            // Send initial snapshot
            val initialSummary =
                LiveSessionSummaryResponse(
                    sessionId = session.sessionId,
                    blueTeam = session.blueTeam,
                    redTeam = session.redTeam,
                    status = session.status,
                    currentTurnNumber = session.currentState.currentTurnNumber,
                    isComplete = session.currentState.isComplete,
                    latestSnapshot = session.history.last(),
                )
            send(
                Frame.Text(
                    liveJson.encodeToString<LiveWsServerMessage>(
                        LiveWsServerMessage.SessionSnapshot(initialSummary, session.history.last()),
                    ),
                ),
            )

            // Subscribe to real-time events
            val broadcastJob =
                launch {
                    engine.getSessionEventFlow(sessionId).collect { message ->
                        send(Frame.Text(liveJson.encodeToString<LiveWsServerMessage>(message)))
                    }
                }

            try {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            when (val clientMsg = liveJson.decodeFromString<LiveWsClientMessage>(text)) {
                                is LiveWsClientMessage.ApplyTurn -> {
                                    engine.applyTurn(sessionId, clientMsg.championId, clientMsg.role, clientMsg.player)
                                }
                                is LiveWsClientMessage.Undo -> {
                                    engine.undoTurn(sessionId)
                                }
                                is LiveWsClientMessage.Reset -> {
                                    engine.resetSession(sessionId)
                                }
                                is LiveWsClientMessage.Ping -> {
                                    send(Frame.Text(liveJson.encodeToString<LiveWsServerMessage>(LiveWsServerMessage.Pong)))
                                }
                            }
                        } catch (e: Exception) {
                            send(
                                Frame.Text(
                                    liveJson.encodeToString<LiveWsServerMessage>(
                                        LiveWsServerMessage.Error(
                                            "ACTION_ERROR",
                                            e.message ?: "Action failed",
                                        ),
                                    ),
                                ),
                            )
                        }
                    }
                }
            } catch (e: ClosedReceiveChannelException) {
                // Client disconnected
            } finally {
                broadcastJob.cancel()
            }
        }
    }
}

fun Application.liveCompanionModule(engine: LiveMatchCompanionEngine = LiveMatchCompanionEngine()) {
    install(WebSockets)

    install(ContentNegotiation) {
        json(liveJson)
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (cause.message ?: "Invalid request")),
            )
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to (cause.message ?: "Invalid state")),
            )
        }
        exception<NoSuchElementException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                mapOf("error" to (cause.message ?: "Resource not found")),
            )
        }
    }

    routing {
        liveCompanionRouting(engine)
    }
}

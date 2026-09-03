package com.loldraft.platform.debrief.api

import com.loldraft.platform.debrief.PostMatchDebriefEngine
import com.loldraft.platform.debrief.export.DebriefMarkdownExporter
import com.loldraft.platform.debrief.models.DebriefGameRequest
import com.loldraft.platform.debrief.models.DebriefMatchRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

private val debriefJson =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

fun Route.debriefRouting(engine: PostMatchDebriefEngine) {
    route("/api/debrief") {
        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "status" to "UP",
                    "module" to "post-match-debrief",
                ),
            )
        }

        post("/game") {
            val request = call.receive<DebriefGameRequest>()
            val report = engine.generateGameDebrief(request)
            call.respond(HttpStatusCode.OK, report)
        }

        post("/match") {
            val request = call.receive<DebriefMatchRequest>()
            val report = engine.generateMatchDebrief(request)
            call.respond(HttpStatusCode.OK, report)
        }

        get("/reports/{reportId}") {
            val reportId = call.parameters["reportId"] ?: throw IllegalArgumentException("Missing reportId")
            val report = engine.getReport(reportId)
            if (report == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Report '$reportId' not found"))
                return@get
            }
            call.respond(HttpStatusCode.OK, report)
        }

        get("/reports/{reportId}/markdown") {
            val reportId = call.parameters["reportId"] ?: throw IllegalArgumentException("Missing reportId")
            val report = engine.getReport(reportId)
            if (report == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Report '$reportId' not found"))
                return@get
            }
            val markdown = DebriefMarkdownExporter.exportGameDebrief(report)
            call.respondText(markdown, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
        }

        get("/match-reports/{matchId}") {
            val matchId = call.parameters["matchId"] ?: throw IllegalArgumentException("Missing matchId")
            val report = engine.getMatchReport(matchId)
            if (report == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Match report '$matchId' not found"))
                return@get
            }
            call.respond(HttpStatusCode.OK, report)
        }

        get("/match-reports/{matchId}/markdown") {
            val matchId = call.parameters["matchId"] ?: throw IllegalArgumentException("Missing matchId")
            val report = engine.getMatchReport(matchId)
            if (report == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Match report '$matchId' not found"))
                return@get
            }
            val markdown = DebriefMarkdownExporter.exportMatchDebrief(report)
            call.respondText(markdown, ContentType.Text.Plain.withCharset(Charsets.UTF_8))
        }
    }
}

fun Application.debriefModule(engine: PostMatchDebriefEngine = PostMatchDebriefEngine()) {
    install(ContentNegotiation) {
        json(debriefJson)
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
        debriefRouting(engine)
    }
}

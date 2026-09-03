package com.loldraft.platform.sandbox.api

import com.loldraft.platform.sandbox.PreMatchSandboxEngine
import com.loldraft.platform.sandbox.models.MatchupSandboxRequest
import com.loldraft.platform.sandbox.models.WhatIfBranchApiRequest
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
import kotlinx.serialization.json.Json

fun Route.sandboxRouting(engine: PreMatchSandboxEngine) {
    route("/api/sandbox") {
        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "status" to "UP",
                    "module" to "pre-match-sandbox",
                ),
            )
        }

        post("/simulate") {
            val request = call.receive<MatchupSandboxRequest>()
            val response = engine.generateScenarios(request)
            call.respond(HttpStatusCode.OK, response)
        }

        post("/what-if") {
            val request = call.receive<WhatIfBranchApiRequest>()
            val response =
                engine.simulateWhatIfBranch(
                    baseDraft = request.baseDraftState,
                    request = request.branchRequest,
                    context = request.context,
                )
            call.respond(HttpStatusCode.OK, response)
        }
    }
}

fun Application.sandboxModule(engine: PreMatchSandboxEngine = PreMatchSandboxEngine()) {
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                prettyPrint = false
            },
        )
    }

    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf(
                    "error" to (cause.message ?: "Invalid request"),
                ),
            )
        }
        exception<IllegalStateException> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "error" to (cause.message ?: "Internal processing error"),
                ),
            )
        }
    }

    routing {
        sandboxRouting(engine)
    }
}

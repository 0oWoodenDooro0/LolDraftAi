package com.loldraft.server

import com.loldraft.platform.debrief.PostMatchDebriefEngine
import com.loldraft.platform.debrief.api.debriefRouting
import com.loldraft.platform.live.LiveMatchCompanionEngine
import com.loldraft.platform.live.api.liveCompanionRouting
import com.loldraft.platform.pro.api.proApiRouting
import com.loldraft.platform.sandbox.PreMatchSandboxEngine
import com.loldraft.platform.sandbox.api.sandboxRouting
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.json.Json

fun main(args: Array<String>) {
    val port = System.getenv("PORT")?.toIntOrNull() ?: findAvailablePort(8080, 8088, 8090)
    println("Starting LoL Draft AI Server on port $port (http://localhost:$port)...")
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::mainModule)
        .start(wait = true)
}

private fun findAvailablePort(vararg preferredPorts: Int): Int {
    for (port in preferredPorts) {
        try {
            java.net.ServerSocket(port).use { return port }
        } catch (_: Exception) {
            // Port in use, try next
        }
    }
    return 8088
}

fun Application.mainModule(
    proRepository: ProMatchRepository = ProMatchRepository(),
    liveEngine: LiveMatchCompanionEngine = LiveMatchCompanionEngine(),
    sandboxEngine: PreMatchSandboxEngine = PreMatchSandboxEngine(),
    debriefEngine: PostMatchDebriefEngine = PostMatchDebriefEngine(),
) {
    proRepository.initialize()

    install(WebSockets)

    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
                prettyPrint = false
            },
        )
    }

    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
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
        liveCompanionRouting(liveEngine)
        sandboxRouting(sandboxEngine)
        debriefRouting(debriefEngine)
        proApiRouting(proRepository)
    }
}

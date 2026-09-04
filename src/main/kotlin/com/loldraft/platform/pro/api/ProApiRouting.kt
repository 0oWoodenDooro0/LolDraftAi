package com.loldraft.platform.pro.api

import com.loldraft.data.models.Role
import com.loldraft.server.ProMatchRepository
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class ProTeamSummary(
    val id: String,
    val name: String,
    val code: String,
    val league: String? = null,
    val totalGames: Int = 0,
    val wins: Int = 0,
    val winRate: Double = 0.0,
)

@Serializable
data class ProPlayerRosterEntry(
    val role: Role,
    val playerName: String,
    val gamesPlayed: Int,
    val topChampions: List<String>,
    val winRate: Double = 0.0,
)

@Serializable
data class ProChampionEntry(
    val id: String,
    val name: String,
    val primaryRole: Role? = null,
    val secondaryRoles: List<Role> = emptyList(),
    val tags: List<String> = emptyList(),
)

fun Route.proApiRouting(repository: ProMatchRepository) {
    route("/api/pro") {
        get("/health") {
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "status" to "UP",
                    "module" to "pro-match-repository",
                    "gamesLoaded" to repository.totalGamesCount.toString(),
                    "leaguesCount" to repository.getLeagues().size.toString(),
                    "teamsCount" to repository.getTeams().size.toString(),
                    "latestPatch" to repository.getLatestPatch(),
                    "patchesCount" to repository.getPatches().size.toString(),
                ),
            )
        }

        get("/patches") {
            call.respond(HttpStatusCode.OK, repository.getPatches())
        }

        get("/patches/{patch}/meta") {
            val patch = call.parameters["patch"] ?: "latest"
            val meta = repository.getPatchMeta(patch)
            call.respond(HttpStatusCode.OK, meta)
        }

        get("/leagues") {
            call.respond(HttpStatusCode.OK, repository.getLeagues())
        }

        get("/teams") {
            val league = call.request.queryParameters["league"]
            val query = call.request.queryParameters["query"]
            val teams = repository.getTeams(league = league, query = query)
            call.respond(HttpStatusCode.OK, teams)
        }

        get("/teams/{teamId}") {
            val teamId = call.parameters["teamId"] ?: throw IllegalArgumentException("Missing teamId")
            val profile = repository.getTeamProfile(teamId)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Team '$teamId' not found"))
                return@get
            }
            call.respond(HttpStatusCode.OK, profile)
        }

        get("/teams/{teamId}/roster") {
            val teamId = call.parameters["teamId"] ?: throw IllegalArgumentException("Missing teamId")
            val profile = repository.getTeamProfile(teamId)
            if (profile == null) {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Team '$teamId' not found"))
                return@get
            }
            val roster = repository.getTeamRoster(teamId)
            call.respond(HttpStatusCode.OK, roster)
        }

        get("/champions") {
            call.respond(HttpStatusCode.OK, repository.getChampions())
        }
    }
}

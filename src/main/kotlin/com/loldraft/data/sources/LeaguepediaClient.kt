package com.loldraft.data.sources

import kotlinx.serialization.json.Json

class LeaguepediaClient(
    val transport: HttpTransport,
    val baseUrl: String = "https://lol.fandom.com/api.php",
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    suspend fun queryPicksAndBans(
        tournament: String? = null,
        limit: Int = 50,
    ): List<PicksAndBansCargoRow> {
        val whereClause = if (!tournament.isNullOrBlank()) "&where=Tournament%3D%27$tournament%27" else ""
        val url = "$baseUrl?action=cargoquery&format=json&tables=PicksAndBansS7&limit=$limit$whereClause"
        val response = transport.get(url)
        return try {
            json.decodeFromString<CargoResponse<PicksAndBansCargoRow>>(response).cargoquery.map { it.title }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun queryScoreboardGames(
        tournament: String? = null,
        limit: Int = 50,
    ): List<ScoreboardGameCargoRow> {
        val whereClause = if (!tournament.isNullOrBlank()) "&where=Tournament%3D%27$tournament%27" else ""
        val url = "$baseUrl?action=cargoquery&format=json&tables=ScoreboardGames&limit=$limit$whereClause"
        val response = transport.get(url)
        return try {
            json.decodeFromString<CargoResponse<ScoreboardGameCargoRow>>(response).cargoquery.map { it.title }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

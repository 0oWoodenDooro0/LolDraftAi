package com.loldraft.data.sources

import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.models.TeamGameStats
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.normalization.PatchNormalizer

class OraclesElixirCsvParser(
    private val championNormalizer: ChampionNormalizer = ChampionNormalizer,
    private val patchNormalizer: PatchNormalizer = PatchNormalizer,
) {
    fun parseCsv(csvContent: String): List<Game> {
        val lines = csvContent.lineSequence().filter { it.isNotBlank() }
        return parseCsvLines(lines)
    }

    fun parseCsvLines(lines: Sequence<String>): List<Game> {
        val iterator = lines.iterator()
        if (!iterator.hasNext()) return emptyList()

        val headerLine = iterator.next()
        val headers = parseCsvLine(headerLine).map { it.trim().lowercase() }
        val colIndex = headers.withIndex().associate { it.value to it.index }

        val gameIdIdx = colIndex["gameid"] ?: return emptyList()
        val patchIdx = colIndex["patch"]
        val sideIdx = colIndex["side"]
        val posIdx = colIndex["position"]
        val playerIdx = colIndex["playername"]
        val playerIdIdx = colIndex["playerid"]
        val teamIdx = colIndex["teamname"]
        val teamIdIdx = colIndex["teamid"]
        val champIdx = colIndex["champion"]
        val ban1Idx = colIndex["ban1"]
        val ban2Idx = colIndex["ban2"]
        val ban3Idx = colIndex["ban3"]
        val ban4Idx = colIndex["ban4"]
        val ban5Idx = colIndex["ban5"]
        val lenIdx = colIndex["gamelength"]
        val resIdx = colIndex["result"]
        val gameNumIdx = colIndex["game"]
        val leagueIdx = colIndex["league"]
        val yearIdx = colIndex["year"]
        val splitIdx = colIndex["split"]
        val fbIdx = colIndex["firstblood"]
        val fdIdx = colIndex["firstdragon"]
        val gd15Idx = colIndex["golddiffat15"]
        val killsIdx = colIndex["teamkills"] ?: colIndex["kills"]
        val deathsIdx = colIndex["teamdeaths"] ?: colIndex["deaths"]
        val towersIdx = colIndex["towers"]

        val maxIdx =
            listOfNotNull(
                gameIdIdx,
                patchIdx,
                sideIdx,
                posIdx,
                playerIdx,
                playerIdIdx,
                teamIdx,
                teamIdIdx,
                champIdx,
                ban1Idx,
                ban2Idx,
                ban3Idx,
                ban4Idx,
                ban5Idx,
                lenIdx,
                resIdx,
                gameNumIdx,
                leagueIdx,
                yearIdx,
                splitIdx,
                fbIdx,
                fdIdx,
                gd15Idx,
                killsIdx,
                deathsIdx,
                towersIdx,
            ).maxOrNull() ?: gameIdIdx
        val colLimit = maxIdx + 1

        val rowsByGameId = mutableMapOf<String, MutableList<List<String>>>()

        while (iterator.hasNext()) {
            val line = iterator.next().trim()
            if (line.isBlank()) continue
            val row = parseCsvLine(line, colLimit)
            if (row.size <= gameIdIdx) continue
            val gameId = row[gameIdIdx].trim()
            if (gameId.isBlank() || gameId.equals("gameid", ignoreCase = true)) continue
            rowsByGameId.getOrPut(gameId) { mutableListOf() }.add(row)
        }

        return rowsByGameId.map { (gameId, rows) ->
            var patch = "unknown"
            var gameNumber = 1
            var durationSeconds: Int? = null
            var blueTeamName = "BlueTeam"
            var redTeamName = "RedTeam"
            var winner: Side? = null
            var tournament: String? = null
            var season: String? = null
            var year: Int? = null

            var blueFb: Boolean? = null
            var redFb: Boolean? = null
            var blueFd: Boolean? = null
            var redFd: Boolean? = null
            var blueGd15: Double? = null
            var redGd15: Double? = null
            var blueKills: Int? = null
            var redKills: Int? = null
            var blueDeaths: Int? = null
            var redDeaths: Int? = null
            var blueTowers: Int? = null
            var redTowers: Int? = null

            val blueBans = mutableListOf<String>()
            val redBans = mutableListOf<String>()
            val bluePicks = mutableListOf<PickSelection>()
            val redPicks = mutableListOf<PickSelection>()

            for (row in rows) {
                val sideStr = sideIdx?.let { row.getOrNull(it)?.trim()?.lowercase() } ?: ""
                val isBlue = sideStr == "blue" || sideStr == "1"
                val isRed = sideStr == "red" || sideStr == "2"
                val teamName = teamIdx?.let { row.getOrNull(it)?.trim() }
                val position = posIdx?.let { row.getOrNull(it)?.trim()?.lowercase() } ?: ""
                val champion = champIdx?.let { row.getOrNull(it)?.trim() }
                val playerName = playerIdx?.let { row.getOrNull(it)?.trim() } ?: playerIdIdx?.let { row.getOrNull(it)?.trim() }
                val rawPatch = patchIdx?.let { row.getOrNull(it)?.trim() }
                val result = resIdx?.let { row.getOrNull(it)?.trim()?.toIntOrNull() }

                if (tournament == null && leagueIdx != null) {
                    row
                        .getOrNull(leagueIdx)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { tournament = it }
                }
                if (season == null && splitIdx != null) {
                    row
                        .getOrNull(splitIdx)
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { season = it }
                }
                if (year == null && yearIdx != null) {
                    row
                        .getOrNull(yearIdx)
                        ?.trim()
                        ?.toIntOrNull()
                        ?.let { year = it }
                }

                val fbVal = fbIdx?.let { row.getOrNull(it)?.trim() }?.let { it == "1" || it.equals("true", ignoreCase = true) }
                val fdVal = fdIdx?.let { row.getOrNull(it)?.trim() }?.let { it == "1" || it.equals("true", ignoreCase = true) }
                val gd15Val = gd15Idx?.let { row.getOrNull(it)?.trim()?.toDoubleOrNull() }
                val killsVal = killsIdx?.let { row.getOrNull(it)?.trim()?.toIntOrNull() }
                val deathsVal = deathsIdx?.let { row.getOrNull(it)?.trim()?.toIntOrNull() }
                val towersVal = towersIdx?.let { row.getOrNull(it)?.trim()?.toIntOrNull() }

                if (isBlue) {
                    if (fbVal != null && blueFb == null) blueFb = fbVal
                    if (fdVal != null && blueFd == null) blueFd = fdVal
                    if (gd15Val != null && blueGd15 == null) blueGd15 = gd15Val
                    if (killsVal != null && blueKills == null) blueKills = killsVal
                    if (deathsVal != null && blueDeaths == null) blueDeaths = deathsVal
                    if (towersVal != null && blueTowers == null) blueTowers = towersVal
                } else if (isRed) {
                    if (fbVal != null && redFb == null) redFb = fbVal
                    if (fdVal != null && redFd == null) redFd = fdVal
                    if (gd15Val != null && redGd15 == null) redGd15 = gd15Val
                    if (killsVal != null && redKills == null) redKills = killsVal
                    if (deathsVal != null && redDeaths == null) redDeaths = deathsVal
                    if (towersVal != null && redTowers == null) redTowers = towersVal
                }

                if (!rawPatch.isNullOrBlank() && patch == "unknown") {
                    patch = patchNormalizer.normalize(rawPatch)
                }
                if (gameNumIdx != null) {
                    row
                        .getOrNull(gameNumIdx)
                        ?.trim()
                        ?.toIntOrNull()
                        ?.let { gameNumber = it }
                }
                if (lenIdx != null && durationSeconds == null) {
                    row
                        .getOrNull(lenIdx)
                        ?.trim()
                        ?.toDoubleOrNull()
                        ?.toInt()
                        ?.let { durationSeconds = it }
                }

                if (isBlue && !teamName.isNullOrBlank()) {
                    blueTeamName = teamName
                }
                if (isRed && !teamName.isNullOrBlank()) {
                    redTeamName = teamName
                }

                if (result == 1) {
                    if (isBlue) {
                        winner = Side.BLUE
                    } else if (isRed) {
                        winner = Side.RED
                    }
                }

                // Extract bans
                val bansTarget =
                    if (isBlue) {
                        blueBans
                    } else if (isRed) {
                        redBans
                    } else {
                        null
                    }
                if (bansTarget != null && bansTarget.isEmpty()) {
                    val rawBans =
                        listOfNotNull(
                            ban1Idx?.let { row.getOrNull(it) },
                            ban2Idx?.let { row.getOrNull(it) },
                            ban3Idx?.let { row.getOrNull(it) },
                            ban4Idx?.let { row.getOrNull(it) },
                            ban5Idx?.let { row.getOrNull(it) },
                        ).map { championNormalizer.normalize(it) }.filter { it.isNotBlank() }

                    if (rawBans.isNotEmpty()) {
                        bansTarget.addAll(rawBans)
                    }
                }

                // Extract picks
                if (position != "team" && !champion.isNullOrBlank()) {
                    val normalizedChamp = championNormalizer.normalize(champion)
                    val role = mapRole(position)
                    val selection =
                        PickSelection(
                            championId = normalizedChamp,
                            role = role,
                            playerId = playerName,
                        )
                    if (isBlue) {
                        bluePicks.add(selection)
                    } else if (isRed) {
                        redPicks.add(selection)
                    }
                }
            }

            val blueTeam =
                Team(
                    id = championNormalizer.toSlug(blueTeamName),
                    name = blueTeamName,
                    code = blueTeamName,
                )
            val redTeam =
                Team(
                    id = championNormalizer.toSlug(redTeamName),
                    name = redTeamName,
                    code = redTeamName,
                )

            val draftState =
                DraftState(
                    blueBans = blueBans,
                    redBans = redBans,
                    bluePicks = bluePicks,
                    redPicks = redPicks,
                    turns = emptyList(),
                )

            val blueStats =
                if (blueFb != null || blueFd != null || blueGd15 != null || blueKills != null) {
                    TeamGameStats(
                        teamId = blueTeam.id,
                        firstBlood = blueFb,
                        firstDragon = blueFd,
                        goldDiffAt15 = blueGd15,
                        kills = blueKills,
                        deaths = blueDeaths,
                        towers = blueTowers,
                    )
                } else {
                    null
                }

            val redStats =
                if (redFb != null || redFd != null || redGd15 != null || redKills != null) {
                    TeamGameStats(
                        teamId = redTeam.id,
                        firstBlood = redFb,
                        firstDragon = redFd,
                        goldDiffAt15 = redGd15,
                        kills = redKills,
                        deaths = redDeaths,
                        towers = redTowers,
                    )
                } else {
                    null
                }

            Game(
                id = gameId,
                gameNumber = gameNumber,
                patch = patch,
                blueTeam = blueTeam,
                redTeam = redTeam,
                draftState = draftState,
                winner = winner,
                durationSeconds = durationSeconds,
                blueStats = blueStats,
                redStats = redStats,
                tournament = tournament,
                season = season,
                year = year,
            )
        }
    }

    private fun mapRole(position: String): Role? =
        when (position.lowercase().trim()) {
            "top" -> Role.TOP
            "jng", "jungle" -> Role.JUNGLE
            "mid", "middle" -> Role.MID
            "bot", "bottom", "adc" -> Role.BOT
            "sup", "support" -> Role.SUPPORT
            else -> null
        }

    private fun parseCsvLine(
        line: String,
        limit: Int = Int.MAX_VALUE,
    ): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '\"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                    if (result.size >= limit) {
                        return result
                    }
                }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }
}

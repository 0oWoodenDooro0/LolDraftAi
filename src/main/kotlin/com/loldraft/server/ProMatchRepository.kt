package com.loldraft.server

import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.normalization.PatchNormalizer
import com.loldraft.data.sources.OraclesElixirCsvParser
import com.loldraft.data.style.TeamStyleAnalyzer
import com.loldraft.data.style.TeamTacticalProfile
import com.loldraft.platform.pro.api.ProChampionEntry
import com.loldraft.platform.pro.api.ProPlayerRosterEntry
import com.loldraft.platform.pro.api.ProTeamSummary
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class ProMatchRepository(
    private val dataFilePath: String = "data/raw/2026_LoL_esports_match_data_from_OraclesElixir.csv",
    private val initialGames: List<Game>? = null,
    private val parser: OraclesElixirCsvParser = OraclesElixirCsvParser(),
    private val teamStyleAnalyzer: TeamStyleAnalyzer = TeamStyleAnalyzer(),
    private val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
) {
    private val games = mutableListOf<Game>()
    private val teamProfilesCache = ConcurrentHashMap<String, TeamTacticalProfile?>()
    private val teamRostersCache = ConcurrentHashMap<String, List<ProPlayerRosterEntry>>()
    private var initialized = false

    val totalGamesCount: Int
        get() = games.size

    fun getAllLoadedGames(): List<Game> {
        ensureInitialized()
        return games.toList()
    }

    fun initialize() {
        if (initialized) return
        games.clear()
        teamProfilesCache.clear()
        teamRostersCache.clear()

        if (initialGames != null) {
            games.addAll(initialGames)
        } else {
            val file = File(dataFilePath)
            if (file.exists()) {
                try {
                    file.useLines { lines ->
                        val parsed = parser.parseCsvLines(lines)
                        games.addAll(parsed)
                    }
                } catch (_: Exception) {
                    // Fallback to empty if parse fails
                }
            }
        }
        initialized = true
    }

    fun getPatches(): List<String> {
        ensureInitialized()
        return games
            .mapNotNull { it.patch.takeIf { p -> p.isNotBlank() } }
            .distinct()
            .sortedWith(
                Comparator { a, b ->
                    val pa = PatchNormalizer.parse(a)
                    val pb = PatchNormalizer.parse(b)
                    when {
                        pa != null && pb != null -> pa.compareTo(pb)
                        pa != null -> 1
                        pb != null -> -1
                        else -> a.compareTo(b)
                    }
                },
            )
    }

    fun getDefaultPatch(): String {
        ensureInitialized()
        return getPatches().lastOrNull() ?: "16.17"
    }

    fun getLeagues(): List<String> {
        ensureInitialized()
        return games
            .mapNotNull { it.tournament }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    fun getTeams(
        league: String? = null,
        patch: String? = null,
        query: String? = null,
    ): List<ProTeamSummary> {
        ensureInitialized()

        var matchingGames: List<Game> = games
        if (!league.isNullOrBlank()) {
            matchingGames = matchingGames.filter { it.tournament.equals(league, ignoreCase = true) }
        }
        if (!patch.isNullOrBlank()) {
            val targetPatch = PatchNormalizer.normalize(patch)
            matchingGames =
                matchingGames.filter {
                    val gamePatch = PatchNormalizer.normalize(it.patch)
                    gamePatch.equals(targetPatch, ignoreCase = true) || it.patch.equals(patch, ignoreCase = true)
                }
        }

        val teamMap = mutableMapOf<String, MutableList<Pair<Team, Boolean>>>()
        val teamLeagueMap = mutableMapOf<String, String>()

        for (game in matchingGames) {
            val blueWon = game.winner == Side.BLUE
            val redWon = game.winner == Side.RED

            teamMap.getOrPut(game.blueTeam.id) { mutableListOf() }.add(game.blueTeam to blueWon)
            teamMap.getOrPut(game.redTeam.id) { mutableListOf() }.add(game.redTeam to redWon)

            game.tournament?.takeIf { it.isNotBlank() }?.let { t ->
                teamLeagueMap.putIfAbsent(game.blueTeam.id, t)
                teamLeagueMap.putIfAbsent(game.redTeam.id, t)
            }
        }

        var summaries =
            teamMap.map { (teamId, appearances) ->
                val canonicalTeam = appearances.first().first
                val total = appearances.size
                val wins = appearances.count { it.second }
                val winRate = if (total > 0) wins.toDouble() / total else 0.0
                ProTeamSummary(
                    id = teamId,
                    name = canonicalTeam.name,
                    code = canonicalTeam.code,
                    league = teamLeagueMap[teamId],
                    totalGames = total,
                    wins = wins,
                    winRate = winRate,
                )
            }

        if (!query.isNullOrBlank()) {
            val q = query.trim().lowercase()
            summaries =
                summaries.filter { team ->
                    team.name.lowercase().contains(q) ||
                        team.code.lowercase().contains(q) ||
                        team.id.lowercase().contains(q)
                }
        }

        return summaries.sortedWith(compareByDescending<ProTeamSummary> { it.totalGames }.thenBy { it.name })
    }

    fun getTeamProfile(teamId: String): TeamTacticalProfile? {
        ensureInitialized()
        return teamProfilesCache.computeIfAbsent(teamId.lowercase()) {
            teamStyleAnalyzer.analyzeTeam(teamId, games)
        }
    }

    fun getTeamRoster(teamId: String): List<ProPlayerRosterEntry> {
        ensureInitialized()
        return teamRostersCache.computeIfAbsent(teamId.lowercase()) {
            computeTeamRoster(teamId)
        }
    }

    fun getChampions(): List<ProChampionEntry> {
        ensureInitialized()
        val championMap = mutableMapOf<String, ProChampionEntry>()

        for (profile in tagRegistry.getAllProfiles()) {
            championMap[profile.championId.lowercase()] =
                ProChampionEntry(
                    id = profile.championId,
                    name = profile.displayName,
                    primaryRole = profile.primaryRole,
                    tags = profile.tags.map { it.name },
                )
        }

        // Tally empirical role counts from tournament match picks
        val pickRoleCounts = mutableMapOf<String, MutableMap<Role, Int>>()
        for (game in games) {
            val allPicks = game.draftState.bluePicks + game.draftState.redPicks
            for (pick in allPicks) {
                val r = pick.role
                if (r != null && pick.championId.isNotBlank()) {
                    pickRoleCounts
                        .getOrPut(pick.championId.lowercase()) { mutableMapOf() }
                        .merge(r, 1, Int::plus)
                }
            }
        }

        for (game in games) {
            val picksAndBans =
                game.draftState.bluePicks.map { it.championId } +
                    game.draftState.redPicks.map { it.championId } +
                    game.draftState.blueBans +
                    game.draftState.redBans

            for (champ in picksAndBans) {
                if (champ.isBlank()) continue
                val key = champ.lowercase()
                val existing = championMap[key]
                if (existing == null) {
                    val empiricalRole = pickRoleCounts[key]?.maxByOrNull { it.value }?.key ?: Role.MID
                    championMap[key] =
                        ProChampionEntry(
                            id = champ,
                            name = champ,
                            primaryRole = empiricalRole,
                            tags = emptyList(),
                        )
                } else if (existing.primaryRole == null) {
                    val empiricalRole = pickRoleCounts[key]?.maxByOrNull { it.value }?.key ?: Role.MID
                    championMap[key] = existing.copy(primaryRole = empiricalRole)
                }
            }
        }

        return championMap.values.sortedBy { it.name }
    }

    private fun computeTeamRoster(teamId: String): List<ProPlayerRosterEntry> {
        val teamGames = games.filter { matchesTeam(it.blueTeam, teamId) || matchesTeam(it.redTeam, teamId) }
        if (teamGames.isEmpty()) return emptyList()

        data class PlayerStats(
            var games: Int = 0,
            var wins: Int = 0,
            val champCounts: MutableMap<String, Int> = mutableMapOf(),
        )

        val statsByRolePlayer = mutableMapOf<Pair<Role, String>, PlayerStats>()

        for (game in teamGames) {
            val isBlue = matchesTeam(game.blueTeam, teamId)
            val picks = if (isBlue) game.draftState.bluePicks else game.draftState.redPicks
            val won = if (isBlue) game.winner == Side.BLUE else game.winner == Side.RED

            for (pick in picks) {
                val role = pick.role ?: continue
                val player = pick.playerId?.takeIf { it.isNotBlank() } ?: continue
                val stats = statsByRolePlayer.getOrPut(role to player) { PlayerStats() }
                stats.games++
                if (won) stats.wins++
                val champ = pick.championId
                if (champ.isNotBlank()) {
                    stats.champCounts[champ] = (stats.champCounts[champ] ?: 0) + 1
                }
            }
        }

        val standardRoles = listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT)
        val roster = mutableListOf<ProPlayerRosterEntry>()

        for (role in standardRoles) {
            val playersForRole = statsByRolePlayer.filterKeys { it.first == role }
            val topPlayer = playersForRole.maxByOrNull { it.value.games }
            if (topPlayer != null) {
                val (key, stats) = topPlayer
                val topChamps =
                    stats.champCounts.entries
                        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                        .map { it.key }
                roster.add(
                    ProPlayerRosterEntry(
                        role = role,
                        playerName = key.second,
                        gamesPlayed = stats.games,
                        topChampions = topChamps,
                        winRate = if (stats.games > 0) stats.wins.toDouble() / stats.games else 0.0,
                    ),
                )
            }
        }

        return roster
    }

    private fun matchesTeam(
        team: Team,
        identifier: String,
    ): Boolean =
        team.id.equals(identifier, ignoreCase = true) ||
            team.name.equals(identifier, ignoreCase = true) ||
            team.code.equals(identifier, ignoreCase = true)

    private fun ensureInitialized() {
        if (!initialized) {
            initialize()
        }
    }
}

package com.loldraft.server

import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.PatchMetaAnalyzer
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.normalization.PatchNormalizer
import com.loldraft.data.player.PlayerIntelligenceService
import com.loldraft.data.player.ProPlayerDetailedProfile
import com.loldraft.data.sources.OraclesElixirCsvParser
import com.loldraft.data.style.TeamStyleAnalyzer
import com.loldraft.data.style.TeamTacticalProfile
import com.loldraft.models.FlexPickAnalyzer
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
    private val patchMetaAnalyzer: PatchMetaAnalyzer = PatchMetaAnalyzer(),
    private val flexAnalyzer: FlexPickAnalyzer = FlexPickAnalyzer(tagRegistry = tagRegistry),
) {
    private val games = mutableListOf<Game>()
    private val teamProfilesCache = ConcurrentHashMap<String, TeamTacticalProfile?>()
    private val teamRostersCache = ConcurrentHashMap<String, List<ProPlayerRosterEntry>>()
    private val patchMetaCache = ConcurrentHashMap<String, PatchMetaMatrix>()
    private val championRoleStats = ConcurrentHashMap<String, MutableMap<Role, Int>>()
    private var initialized = false

    val totalGamesCount: Int
        get() = games.size

    fun initialize() {
        if (initialized) return
        games.clear()
        teamProfilesCache.clear()
        teamRostersCache.clear()
        patchMetaCache.clear()
        championRoleStats.clear()

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

        // Aggregate empirical role statistics across all games
        for (game in games) {
            val allPicks = game.draftState.bluePicks + game.draftState.redPicks
            for (pick in allPicks) {
                val role = pick.role ?: continue
                val slug = ChampionNormalizer.toSlug(pick.championId)
                if (slug.isNotBlank()) {
                    val roleMap = championRoleStats.computeIfAbsent(slug) { ConcurrentHashMap() }
                    roleMap[role] = (roleMap[role] ?: 0) + 1
                }
            }
        }

        initialized = true
    }

    val playerIntelligenceService: PlayerIntelligenceService by lazy {
        PlayerIntelligenceService(proMatchRepository = this)
    }

    fun getAllGames(): List<Game> {
        ensureInitialized()
        return games.toList()
    }

    fun getTeamPlayerProfiles(teamId: String): List<ProPlayerDetailedProfile> {
        ensureInitialized()
        return playerIntelligenceService.getTeamPlayerProfiles(teamId)
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
        query: String? = null,
    ): List<ProTeamSummary> {
        ensureInitialized()

        val matchingGames =
            if (league.isNullOrBlank()) {
                games
            } else {
                games.filter { it.tournament.equals(league, ignoreCase = true) }
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

    fun getPatches(): List<String> {
        ensureInitialized()
        return games
            .map { it.patch }
            .filter { it.isNotBlank() && it != "unknown" }
            .distinct()
            .sortedWith { a, b ->
                val vA = PatchNormalizer.parse(a)
                val vB = PatchNormalizer.parse(b)
                when {
                    vA != null && vB != null -> vB.compareTo(vA)
                    vA != null -> -1
                    vB != null -> 1
                    else -> b.compareTo(a)
                }
            }
    }

    fun getLatestPatch(): String {
        ensureInitialized()
        return getPatches().firstOrNull() ?: "16.17"
    }

    fun getPatchMeta(patch: String? = null): PatchMetaMatrix {
        ensureInitialized()
        val targetPatch =
            if (patch.isNullOrBlank() || patch.equals("latest", ignoreCase = true)) {
                getLatestPatch()
            } else {
                patch.trim()
            }

        return patchMetaCache.computeIfAbsent(targetPatch.lowercase()) {
            val normTarget = PatchNormalizer.normalize(targetPatch)
            val patchGames =
                games.filter {
                    it.patch.equals(targetPatch, ignoreCase = true) ||
                        PatchNormalizer.normalize(it.patch) == normTarget
                }
            if (patchGames.isEmpty()) {
                PatchMetaMatrix(
                    patch = targetPatch,
                    totalGames = 0,
                    championStats = emptyMap(),
                    synergies = emptyList(),
                    matchupCounters = emptyList(),
                )
            } else {
                patchMetaAnalyzer.analyzeGames(patchGames, patchLabel = targetPatch)
            }
        }
    }

    fun getChampionEmpiricalRoles(champion: String): Pair<Role?, List<Role>> {
        ensureInitialized()
        val slug = ChampionNormalizer.toSlug(champion)
        val stats = championRoleStats[slug]
        if (!stats.isNullOrEmpty()) {
            val sorted = stats.entries.sortedByDescending { it.value }
            val primary = sorted.first().key
            val secondaries = sorted.drop(1).filter { it.value > 0 }.map { it.key }
            return primary to secondaries
        }

        // Dynamic AI fallback using FlexPickAnalyzer
        val analysis = flexAnalyzer.analyzeChampion(champion)
        return analysis.primaryRole to analysis.secondaryRoles
    }

    fun getChampions(): List<ProChampionEntry> {
        ensureInitialized()
        val championMap = mutableMapOf<String, ProChampionEntry>()

        for (profile in tagRegistry.getAllProfiles()) {
            val (empiricalPrimary, empiricalSecondary) = getChampionEmpiricalRoles(profile.championId)
            val primaryRole = empiricalPrimary ?: profile.primaryRole
            val secondaryRoles =
                if (empiricalSecondary.isNotEmpty()) {
                    empiricalSecondary
                } else {
                    profile.secondaryRoles.toList()
                }
            championMap[profile.championId.lowercase()] =
                ProChampionEntry(
                    id = profile.championId,
                    name = profile.displayName,
                    primaryRole = primaryRole,
                    secondaryRoles = secondaryRoles,
                    tags = profile.tags.map { it.name },
                )
        }

        for (game in games) {
            val picksAndBans =
                game.draftState.bluePicks.map { it.championId } +
                    game.draftState.redPicks.map { it.championId } +
                    game.draftState.blueBans +
                    game.draftState.redBans

            for (champ in picksAndBans) {
                val slug = ChampionNormalizer.toSlug(champ)
                if (slug.isNotBlank() && !championMap.containsKey(slug)) {
                    val normalizedName = ChampionNormalizer.normalize(champ)
                    val (empiricalPrimary, empiricalSecondary) = getChampionEmpiricalRoles(champ)
                    val profile = tagRegistry.getProfile(champ)
                    championMap[slug] =
                        ProChampionEntry(
                            id = normalizedName,
                            name = normalizedName,
                            primaryRole = empiricalPrimary ?: profile?.primaryRole,
                            secondaryRoles =
                                if (empiricalSecondary.isNotEmpty()) {
                                    empiricalSecondary
                                } else {
                                    profile?.secondaryRoles?.toList() ?: emptyList()
                                },
                            tags = profile?.tags?.map { it.name } ?: emptyList(),
                        )
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

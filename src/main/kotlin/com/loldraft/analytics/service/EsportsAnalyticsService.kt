package com.loldraft.analytics.service

import com.loldraft.analytics.model.AnalyticsSummary
import com.loldraft.analytics.model.PlayerAnalyticsRow
import com.loldraft.analytics.model.PlayerChampionStats
import com.loldraft.analytics.model.RosterRoleSlot
import com.loldraft.analytics.model.SortDirection
import com.loldraft.analytics.model.TeamAnalyticsRow
import com.loldraft.analytics.model.TeamRosterMatrix
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.normalization.PatchNormalizer
import com.loldraft.server.ProMatchRepository
import java.util.Locale
import kotlin.math.roundToInt

class EsportsAnalyticsService(
    private val repository: ProMatchRepository? = null,
    private val gamesSupplier: (() -> List<Game>)? = null,
) {
    companion object {
        // 主要賽區 (LCK, LPL, LEC, LCS, LCP) + 世界賽 (Worlds / WLD, MSI, EWC)
        val ALLOWED_TOURNAMENTS = listOf("LCK", "LPL", "LEC", "LCS", "LCP", "Worlds", "MSI", "EWC")
        private val ALLOWED_TOURNAMENT_SET =
            setOf(
                "LCK",
                "LPL",
                "LEC",
                "LCS",
                "LCP",
                "WORLDS",
                "WLD",
                "MSI",
                "EWC",
            )

        fun isAllowedTournament(tournament: String?): Boolean {
            if (tournament.isNullOrBlank()) return false
            val t = tournament.trim().uppercase()
            return t in ALLOWED_TOURNAMENT_SET
        }
    }

    private fun getGames(): List<Game> {
        val allGames =
            when {
                gamesSupplier != null -> gamesSupplier.invoke()
                repository != null -> repository.getAllGames()
                else -> emptyList()
            }
        return allGames.filter { isAllowedTournament(it.tournament) }
    }

    fun getAvailableTournaments(): List<String> {
        val games = getGames()
        val tournaments =
            games
                .mapNotNull { it.tournament?.trim() }
                .filter { it.isNotBlank() }
                .distinct()

        return ALLOWED_TOURNAMENTS.filter { allowed ->
            tournaments.any {
                it.equals(allowed, ignoreCase = true) ||
                    (allowed.equals("Worlds", ignoreCase = true) && it.equals("WLD", ignoreCase = true))
            }
        }
    }

    fun getAvailableSplits(tournament: String? = null): List<String> {
        var games = getGames()
        if (!tournament.isNullOrBlank()) {
            games = games.filter { it.tournament.equals(tournament, ignoreCase = true) }
        }
        val splits =
            games
                .mapNotNull { it.season?.trim() }
                .filter { it.isNotBlank() }
                .distinct()

        val prioritySplits = listOf("Spring", "Summer", "Winter", "Split 1", "Split 2", "Split 3", "Cup", "Kickoff", "Final Four")
        val prioritySet = prioritySplits.map { it.lowercase() }.toSet()
        val highPriority = prioritySplits.filter { p -> splits.any { it.equals(p, ignoreCase = true) } }
        val others = splits.filter { !prioritySet.contains(it.lowercase()) }.sorted()
        return highPriority + others
    }

    fun getAvailablePatches(): List<String> {
        return getGames()
            .map { PatchNormalizer.normalize(it.patch) }
            .filter { it != "unknown" && it.isNotBlank() }
            .distinct()
            .sortedDescending()
    }

    fun getAvailableTeams(
        tournament: String? = null,
        split: String? = null,
    ): List<String> {
        var games = getGames()
        if (!tournament.isNullOrBlank()) {
            games = games.filter { it.tournament.equals(tournament, ignoreCase = true) }
        }
        if (!split.isNullOrBlank()) {
            games = games.filter { it.season.equals(split, ignoreCase = true) }
        }
        val names = mutableSetOf<String>()
        for (g in games) {
            if (g.blueTeam.name.isNotBlank()) names.add(g.blueTeam.name)
            if (g.redTeam.name.isNotBlank()) names.add(g.redTeam.name)
        }
        return names.sorted()
    }

    fun getTeamRosterMatrix(
        teamNameOrId: String,
        tournament: String? = null,
        split: String? = null,
        patch: String? = null,
        playerOverrides: Map<Role, String> = emptyMap(),
    ): TeamRosterMatrix {
        val targetSlug = ChampionNormalizer.toSlug(teamNameOrId)
        var teamGames =
            getGames().filter { g ->
                ChampionNormalizer.toSlug(g.blueTeam.name) == targetSlug ||
                    ChampionNormalizer.toSlug(g.blueTeam.id) == targetSlug ||
                    ChampionNormalizer.toSlug(g.redTeam.name) == targetSlug ||
                    ChampionNormalizer.toSlug(g.redTeam.id) == targetSlug
            }

        if (!tournament.isNullOrBlank()) {
            teamGames = teamGames.filter { it.tournament.equals(tournament, ignoreCase = true) }
        }
        if (!split.isNullOrBlank()) {
            teamGames = teamGames.filter { it.season.equals(split, ignoreCase = true) }
        }
        if (!patch.isNullOrBlank()) {
            val normPatch = PatchNormalizer.normalize(patch)
            teamGames = teamGames.filter { PatchNormalizer.normalize(it.patch).equals(normPatch, ignoreCase = true) }
        }

        val totalTeamGames = teamGames.size
        var teamWins = 0
        var canonicalTeamName = teamNameOrId

        for (g in teamGames) {
            val isBlue = ChampionNormalizer.toSlug(g.blueTeam.name) == targetSlug || ChampionNormalizer.toSlug(g.blueTeam.id) == targetSlug
            if (isBlue) {
                if (g.winner == Side.BLUE) teamWins++
                canonicalTeamName = g.blueTeam.name
            } else {
                if (g.winner == Side.RED) teamWins++
                canonicalTeamName = g.redTeam.name
            }
        }
        val teamLosses = totalTeamGames - teamWins
        val teamWinRate = if (totalTeamGames > 0) teamWins.toDouble() / totalTeamGames else 0.0

        // Calculate opponent bans against this team
        val opponentBanCounts = mutableMapOf<String, Int>()
        for (g in teamGames) {
            val isBlue = ChampionNormalizer.toSlug(g.blueTeam.name) == targetSlug || ChampionNormalizer.toSlug(g.blueTeam.id) == targetSlug
            val opponentBans = if (isBlue) g.draftState.redBans else g.draftState.blueBans
            for (b in opponentBans) {
                if (b.isNotBlank()) {
                    val norm = ChampionNormalizer.normalize(b)
                    opponentBanCounts[norm] = (opponentBanCounts[norm] ?: 0) + 1
                }
            }
        }

        // Process 5 roles
        val rolesMap = mutableMapOf<Role, RosterRoleSlot>()
        val standardRoles = listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT)

        for (role in standardRoles) {
            // Find all picks for this role in team games
            val rolePicks = mutableListOf<Pair<PickSelection, Boolean>>() // PickSelection to won
            val playerGameCounts = mutableMapOf<String, Int>()

            for (g in teamGames) {
                val isBlue = ChampionNormalizer.toSlug(g.blueTeam.name) == targetSlug || ChampionNormalizer.toSlug(g.blueTeam.id) == targetSlug
                val picks = if (isBlue) g.draftState.bluePicks else g.draftState.redPicks
                val won = (isBlue && g.winner == Side.BLUE) || (!isBlue && g.winner == Side.RED)

                for (p in picks) {
                    if (p.role == role) {
                        val pName = p.playerId?.takeIf { it.isNotBlank() } ?: "Unknown"
                        playerGameCounts[pName] = (playerGameCounts[pName] ?: 0) + 1
                        rolePicks.add(p to won)
                    }
                }
            }

            val availablePlayers = playerGameCounts.keys.sortedWith(compareByDescending<String> { playerGameCounts[it] ?: 0 }.thenBy { it })
            val override = playerOverrides[role]
            val activePlayer =
                when {
                    override != null && availablePlayers.contains(override) -> override
                    availablePlayers.isNotEmpty() -> availablePlayers.first()
                    else -> "No Data"
                }

            // Filter picks by active player
            val playerPicks = rolePicks.filter { it.first.playerId.equals(activePlayer, ignoreCase = true) }
            val pTotalGames = playerPicks.size
            val pWins = playerPicks.count { it.second }
            val pLosses = pTotalGames - pWins
            val pWinRate = if (pTotalGames > 0) pWins.toDouble() / pTotalGames else 0.0

            // Champion pool aggregation
            val champGroups = playerPicks.groupBy { ChampionNormalizer.normalize(it.first.championId) }
            val championPool =
                champGroups.map { (champName, picks) ->
                    val played = picks.size
                    val cWins = picks.count { it.second }
                    val cLosses = played - cWins
                    val cWinRate = if (played > 0) cWins.toDouble() / played else 0.0
                    val oppBans = opponentBanCounts[champName] ?: 0
                    val oppBanRate = if (totalTeamGames > 0) oppBans.toDouble() / totalTeamGames else 0.0

                    var totalKills = 0.0
                    var totalDeaths = 0.0
                    var totalAssists = 0.0
                    var totalDpm = 0.0
                    var totalCspm = 0.0
                    var countWithStats = 0

                    for ((pick, _) in picks) {
                        if (pick.kills != null || pick.dpm != null) {
                            totalKills += (pick.kills ?: 0)
                            totalDeaths += (pick.deaths ?: 0)
                            totalAssists += (pick.assists ?: 0)
                            totalDpm += (pick.dpm ?: 0.0)
                            totalCspm += (pick.cspm ?: 0.0)
                            countWithStats++
                        }
                    }

                    val kda =
                        if (totalDeaths > 0) {
                            (totalKills + totalAssists) / totalDeaths
                        } else {
                            (totalKills + totalAssists)
                        }

                    val avgDpm = if (countWithStats > 0) totalDpm / countWithStats else 0.0
                    val avgCspm = if (countWithStats > 0) totalCspm / countWithStats else 0.0
                    val avgKills = if (played > 0) totalKills / played else 0.0
                    val avgDeaths = if (played > 0) totalDeaths / played else 0.0
                    val avgAssists = if (played > 0) totalAssists / played else 0.0

                    PlayerChampionStats(
                        championId = ChampionNormalizer.toSlug(champName),
                        championName = champName,
                        gamesPlayed = played,
                        wins = cWins,
                        losses = cLosses,
                        winRate = cWinRate,
                        opponentBans = oppBans,
                        opponentBanRate = oppBanRate,
                        kills = avgKills,
                        deaths = avgDeaths,
                        assists = avgAssists,
                        kda = kda,
                        dpm = avgDpm,
                        cspm = avgCspm,
                    )
                }.sortedWith(
                    // Default sort: gamesPlayed DESC, then winRate DESC, then opponentBanRate DESC
                    compareByDescending<PlayerChampionStats> { it.gamesPlayed }
                        .thenByDescending { it.winRate }
                        .thenByDescending { it.opponentBanRate },
                )

            rolesMap[role] =
                RosterRoleSlot(
                    role = role,
                    currentPlayer = activePlayer,
                    availablePlayers = availablePlayers,
                    totalGames = pTotalGames,
                    totalWins = pWins,
                    totalLosses = pLosses,
                    winRate = pWinRate,
                    championPool = championPool,
                    championPoolCount = championPool.size,
                )
        }

        return TeamRosterMatrix(
            teamId = targetSlug,
            teamName = canonicalTeamName,
            tournament = tournament,
            split = split,
            patch = patch,
            totalTeamGames = totalTeamGames,
            teamWins = teamWins,
            teamLosses = teamLosses,
            teamWinRate = teamWinRate,
            roles = rolesMap,
        )
    }

    fun queryPlayerGrid(
        tournament: String? = null,
        split: String? = null,
        team: String? = null,
        role: Role? = null,
        patch: String? = null,
        minGames: Int = 1,
        searchQuery: String = "",
        sortColumn: String = "games",
        sortDirection: SortDirection = SortDirection.DESCENDING,
    ): List<PlayerAnalyticsRow> {
        var games = getGames()
        if (!tournament.isNullOrBlank()) {
            games = games.filter { it.tournament.equals(tournament, ignoreCase = true) }
        }
        if (!split.isNullOrBlank()) {
            games = games.filter { it.season.equals(split, ignoreCase = true) }
        }
        if (!patch.isNullOrBlank()) {
            val target = PatchNormalizer.normalize(patch)
            games = games.filter { PatchNormalizer.normalize(it.patch).equals(target, ignoreCase = true) }
        }

        data class Accumulator(
            val playerName: String,
            val teamName: String,
            val league: String,
            val split: String?,
            val role: Role,
            var games: Int = 0,
            var wins: Int = 0,
            var kills: Double = 0.0,
            var deaths: Double = 0.0,
            var assists: Double = 0.0,
            var dpmSum: Double = 0.0,
            var cspmSum: Double = 0.0,
            var vspmSum: Double = 0.0,
            var gd15Sum: Double = 0.0,
            var statsCount: Int = 0,
            val champions: MutableMap<String, Pair<Int, Int>> = mutableMapOf(), // champ -> games, wins
        )

        val map = mutableMapOf<Triple<String, String, Role>, Accumulator>()

        for (g in games) {
            val league = g.tournament?.takeIf { it.isNotBlank() } ?: "Unknown"
            val gameSplit = g.season

            // Blue
            val blueTeamName = g.blueTeam.name
            val blueWon = g.winner == Side.BLUE
            for (pick in g.draftState.bluePicks) {
                val pName = pick.playerId?.takeIf { it.isNotBlank() } ?: continue
                val pRole = pick.role ?: continue
                val key = Triple(pName.lowercase(), blueTeamName.lowercase(), pRole)
                val acc =
                    map.getOrPut(key) {
                        Accumulator(playerName = pName, teamName = blueTeamName, league = league, split = gameSplit, role = pRole)
                    }
                acc.games++
                if (blueWon) acc.wins++
                if (pick.kills != null || pick.dpm != null) {
                    acc.kills += (pick.kills ?: 0)
                    acc.deaths += (pick.deaths ?: 0)
                    acc.assists += (pick.assists ?: 0)
                    acc.dpmSum += (pick.dpm ?: 0.0)
                    acc.cspmSum += (pick.cspm ?: 0.0)
                    acc.vspmSum += (pick.vspm ?: 0.0)
                    acc.gd15Sum += (pick.goldDiffAt15 ?: 0.0)
                    acc.statsCount++
                }
                val champ = ChampionNormalizer.normalize(pick.championId)
                val cur = acc.champions[champ] ?: (0 to 0)
                acc.champions[champ] = (cur.first + 1) to (cur.second + if (blueWon) 1 else 0)
            }

            // Red
            val redTeamName = g.redTeam.name
            val redWon = g.winner == Side.RED
            for (pick in g.draftState.redPicks) {
                val pName = pick.playerId?.takeIf { it.isNotBlank() } ?: continue
                val pRole = pick.role ?: continue
                val key = Triple(pName.lowercase(), redTeamName.lowercase(), pRole)
                val acc =
                    map.getOrPut(key) {
                        Accumulator(playerName = pName, teamName = redTeamName, league = league, split = gameSplit, role = pRole)
                    }
                acc.games++
                if (redWon) acc.wins++
                if (pick.kills != null || pick.dpm != null) {
                    acc.kills += (pick.kills ?: 0)
                    acc.deaths += (pick.deaths ?: 0)
                    acc.assists += (pick.assists ?: 0)
                    acc.dpmSum += (pick.dpm ?: 0.0)
                    acc.cspmSum += (pick.cspm ?: 0.0)
                    acc.vspmSum += (pick.vspm ?: 0.0)
                    acc.gd15Sum += (pick.goldDiffAt15 ?: 0.0)
                    acc.statsCount++
                }
                val champ = ChampionNormalizer.normalize(pick.championId)
                val cur = acc.champions[champ] ?: (0 to 0)
                acc.champions[champ] = (cur.first + 1) to (cur.second + if (redWon) 1 else 0)
            }
        }

        var rows =
            map.values.map { acc ->
                val losses = acc.games - acc.wins
                val winRate = if (acc.games > 0) acc.wins.toDouble() / acc.games else 0.0
                val kda = if (acc.deaths > 0) (acc.kills + acc.assists) / acc.deaths else (acc.kills + acc.assists)
                val avgK = if (acc.games > 0) acc.kills / acc.games else 0.0
                val avgD = if (acc.games > 0) acc.deaths / acc.games else 0.0
                val avgA = if (acc.games > 0) acc.assists / acc.games else 0.0
                val avgDpm = if (acc.statsCount > 0) acc.dpmSum / acc.statsCount else 0.0
                val avgCspm = if (acc.statsCount > 0) acc.cspmSum / acc.statsCount else 0.0
                val avgVspm = if (acc.statsCount > 0) acc.vspmSum / acc.statsCount else 0.0
                val avgGd15 = if (acc.statsCount > 0) acc.gd15Sum / acc.statsCount else 0.0

                val topChampsStr =
                    acc.champions.entries
                        .sortedWith(compareByDescending<Map.Entry<String, Pair<Int, Int>>> { it.value.first }.thenByDescending { it.value.second })
                        .take(3)
                        .joinToString(", ") { (champ, record) ->
                            val rate = (record.second.toDouble() / record.first * 100).roundToInt()
                            "$champ (${record.first}場, $rate%)"
                        }

                PlayerAnalyticsRow(
                    playerName = acc.playerName,
                    teamName = acc.teamName,
                    league = acc.league,
                    split = acc.split,
                    role = acc.role,
                    games = acc.games,
                    wins = acc.wins,
                    losses = losses,
                    winRate = winRate,
                    kda = kda,
                    avgKills = avgK,
                    avgDeaths = avgD,
                    avgAssists = avgA,
                    avgDpm = avgDpm,
                    avgCspm = avgCspm,
                    avgVspm = avgVspm,
                    avgGoldDiffAt15 = avgGd15,
                    championPoolCount = acc.champions.size,
                    topChampions = topChampsStr,
                )
            }

        // Apply filters
        if (!team.isNullOrBlank()) {
            rows = rows.filter { it.teamName.equals(team, ignoreCase = true) }
        }
        if (role != null) {
            rows = rows.filter { it.role == role }
        }
        if (minGames > 1) {
            rows = rows.filter { it.games >= minGames }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            rows =
                rows.filter {
                    it.playerName.lowercase().contains(q) ||
                        it.teamName.lowercase().contains(q) ||
                        it.league.lowercase().contains(q) ||
                        it.topChampions.lowercase().contains(q)
                }
        }

        // Sort
        val comparator =
            when (sortColumn.lowercase()) {
                "player", "playername" -> compareBy<PlayerAnalyticsRow> { it.playerName.lowercase() }
                "team", "teamname" -> compareBy { it.teamName.lowercase() }
                "league" -> compareBy { it.league.lowercase() }
                "role" -> compareBy { it.role.ordinal }
                "wins" -> compareBy { it.wins }
                "losses" -> compareBy { it.losses }
                "winrate" -> compareBy { it.winRate }
                "kda" -> compareBy { it.kda }
                "dpm", "avgdpm" -> compareBy { it.avgDpm }
                "cspm", "avgcspm" -> compareBy { it.avgCspm }
                "vspm", "avgvspm" -> compareBy { it.avgVspm }
                "gd15", "golddiffat15" -> compareBy { it.avgGoldDiffAt15 }
                "champions", "championpoolcount" -> compareBy { it.championPoolCount }
                else -> compareBy<PlayerAnalyticsRow> { it.games }.thenBy { it.winRate }
            }

        return if (sortDirection == SortDirection.DESCENDING) {
            rows.sortedWith(comparator.reversed())
        } else {
            rows.sortedWith(comparator)
        }
    }

    fun queryTeamGrid(
        tournament: String? = null,
        split: String? = null,
        patch: String? = null,
        minGames: Int = 1,
        searchQuery: String = "",
        sortColumn: String = "games",
        sortDirection: SortDirection = SortDirection.DESCENDING,
    ): List<TeamAnalyticsRow> {
        var games = getGames()
        if (!tournament.isNullOrBlank()) {
            games = games.filter { it.tournament.equals(tournament, ignoreCase = true) }
        }
        if (!split.isNullOrBlank()) {
            games = games.filter { it.season.equals(split, ignoreCase = true) }
        }
        if (!patch.isNullOrBlank()) {
            val target = PatchNormalizer.normalize(patch)
            games = games.filter { PatchNormalizer.normalize(it.patch).equals(target, ignoreCase = true) }
        }

        data class TeamAcc(
            val teamId: String,
            val teamName: String,
            val league: String,
            val split: String?,
            var games: Int = 0,
            var wins: Int = 0,
            var blueGames: Int = 0,
            var blueWins: Int = 0,
            var redGames: Int = 0,
            var redWins: Int = 0,
            var fbCount: Int = 0,
            var fdCount: Int = 0,
            var gd15Sum: Double = 0.0,
            var durationSum: Long = 0,
            var statsGames: Int = 0,
        )

        val map = mutableMapOf<String, TeamAcc>()

        for (g in games) {
            val league = g.tournament?.takeIf { it.isNotBlank() } ?: "Unknown"
            val gameSplit = g.season

            // Blue
            val blueKey = g.blueTeam.id.lowercase()
            val blueAcc =
                map.getOrPut(blueKey) {
                    TeamAcc(teamId = g.blueTeam.id, teamName = g.blueTeam.name, league = league, split = gameSplit)
                }
            blueAcc.games++
            blueAcc.blueGames++
            if (g.winner == Side.BLUE) {
                blueAcc.wins++
                blueAcc.blueWins++
            }
            if (g.blueStats?.firstBlood == true) blueAcc.fbCount++
            if (g.blueStats?.firstDragon == true) blueAcc.fdCount++
            if (g.blueStats?.goldDiffAt15 != null) blueAcc.gd15Sum += g.blueStats.goldDiffAt15
            if (g.durationSeconds != null) blueAcc.durationSum += g.durationSeconds
            blueAcc.statsGames++

            // Red
            val redKey = g.redTeam.id.lowercase()
            val redAcc =
                map.getOrPut(redKey) {
                    TeamAcc(teamId = g.redTeam.id, teamName = g.redTeam.name, league = league, split = gameSplit)
                }
            redAcc.games++
            redAcc.redGames++
            if (g.winner == Side.RED) {
                redAcc.wins++
                redAcc.redWins++
            }
            if (g.redStats?.firstBlood == true) redAcc.fbCount++
            if (g.redStats?.firstDragon == true) redAcc.fdCount++
            if (g.redStats?.goldDiffAt15 != null) redAcc.gd15Sum += g.redStats.goldDiffAt15
            if (g.durationSeconds != null) redAcc.durationSum += g.durationSeconds
            redAcc.statsGames++
        }

        var rows =
            map.values.map { acc ->
                val losses = acc.games - acc.wins
                val winRate = if (acc.games > 0) acc.wins.toDouble() / acc.games else 0.0
                val blueRate = if (acc.blueGames > 0) acc.blueWins.toDouble() / acc.blueGames else 0.0
                val redRate = if (acc.redGames > 0) acc.redWins.toDouble() / acc.redGames else 0.0
                val fbRate = if (acc.statsGames > 0) acc.fbCount.toDouble() / acc.statsGames else 0.0
                val fdRate = if (acc.statsGames > 0) acc.fdCount.toDouble() / acc.statsGames else 0.0
                val avgGd15 = if (acc.statsGames > 0) acc.gd15Sum / acc.statsGames else 0.0
                val avgDur = if (acc.statsGames > 0) (acc.durationSum / acc.statsGames).toInt() else 0

                TeamAnalyticsRow(
                    teamId = acc.teamId,
                    teamName = acc.teamName,
                    league = acc.league,
                    split = acc.split,
                    games = acc.games,
                    wins = acc.wins,
                    losses = losses,
                    winRate = winRate,
                    blueGames = acc.blueGames,
                    blueWins = acc.blueWins,
                    blueWinRate = blueRate,
                    redGames = acc.redGames,
                    redWins = acc.redWins,
                    redWinRate = redRate,
                    firstBloodRate = fbRate,
                    firstDragonRate = fdRate,
                    avgGoldDiffAt15 = avgGd15,
                    avgGameDurationSeconds = avgDur,
                )
            }

        if (minGames > 1) {
            rows = rows.filter { it.games >= minGames }
        }
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            rows =
                rows.filter {
                    it.teamName.lowercase().contains(q) ||
                        it.teamId.lowercase().contains(q) ||
                        it.league.lowercase().contains(q)
                }
        }

        val comparator =
            when (sortColumn.lowercase()) {
                "team", "teamname" -> compareBy<TeamAnalyticsRow> { it.teamName.lowercase() }
                "league" -> compareBy { it.league.lowercase() }
                "wins" -> compareBy { it.wins }
                "losses" -> compareBy { it.losses }
                "winrate" -> compareBy { it.winRate }
                "bluewinrate" -> compareBy { it.blueWinRate }
                "redwinrate" -> compareBy { it.redWinRate }
                "firstblood", "firstbloodrate" -> compareBy { it.firstBloodRate }
                "firstdragon", "firstdragonrate" -> compareBy { it.firstDragonRate }
                "gd15", "golddiffat15" -> compareBy { it.avgGoldDiffAt15 }
                "duration" -> compareBy { it.avgGameDurationSeconds }
                else -> compareBy<TeamAnalyticsRow> { it.games }.thenBy { it.winRate }
            }

        return if (sortDirection == SortDirection.DESCENDING) {
            rows.sortedWith(comparator.reversed())
        } else {
            rows.sortedWith(comparator)
        }
    }

    fun exportPlayersToCsv(rows: List<PlayerAnalyticsRow>): String {
        val sb = StringBuilder()
        sb.append("選手,戰隊,賽區,賽期,定位,場次,勝場,敗場,勝率%,KDA,場均擊殺,場均死亡,場均助攻,DPM,CSPM,VSPM,15分經濟差,英雄池數量,常用英雄\n")
        for (r in rows) {
            val wr = String.format(Locale.US, "%.1f", r.winRate * 100)
            val kda = String.format(Locale.US, "%.2f", r.kda)
            val k = String.format(Locale.US, "%.1f", r.avgKills)
            val d = String.format(Locale.US, "%.1f", r.avgDeaths)
            val a = String.format(Locale.US, "%.1f", r.avgAssists)
            val dpm = String.format(Locale.US, "%.1f", r.avgDpm)
            val cspm = String.format(Locale.US, "%.1f", r.avgCspm)
            val vspm = String.format(Locale.US, "%.2f", r.avgVspm)
            val gd15 = String.format(Locale.US, "%.0f", r.avgGoldDiffAt15)
            val topChampsEscaped = "\"${r.topChampions.replace("\"", "\"\"")}\""
            val sp = r.split ?: ""
            sb.append("${r.playerName},${r.teamName},${r.league},$sp,${r.role.name},${r.games},${r.wins},${r.losses},$wr%,$kda,$k,$d,$a,$dpm,$cspm,$vspm,$gd15,${r.championPoolCount},$topChampsEscaped\n")
        }
        return sb.toString()
    }

    fun exportPlayersToTsv(rows: List<PlayerAnalyticsRow>): String {
        val sb = StringBuilder()
        sb.append("選手\t戰隊\t賽區\t賽期\t定位\t場次\t勝場\t敗場\t勝率%\tKDA\t場均擊殺\t場均死亡\t場均助攻\tDPM\tCSPM\tVSPM\t15分經濟差\t英雄池數量\t常用英雄\n")
        for (r in rows) {
            val wr = String.format(Locale.US, "%.1f%%", r.winRate * 100)
            val kda = String.format(Locale.US, "%.2f", r.kda)
            val k = String.format(Locale.US, "%.1f", r.avgKills)
            val d = String.format(Locale.US, "%.1f", r.avgDeaths)
            val a = String.format(Locale.US, "%.1f", r.avgAssists)
            val dpm = String.format(Locale.US, "%.1f", r.avgDpm)
            val cspm = String.format(Locale.US, "%.1f", r.avgCspm)
            val vspm = String.format(Locale.US, "%.2f", r.avgVspm)
            val gd15 = String.format(Locale.US, "%.0f", r.avgGoldDiffAt15)
            val sp = r.split ?: ""
            sb.append("${r.playerName}\t${r.teamName}\t${r.league}\t$sp\t${r.role.name}\t${r.games}\t${r.wins}\t${r.losses}\t$wr\t$kda\t$k\t$d\t$a\t$dpm\t$cspm\t$vspm\t$gd15\t${r.championPoolCount}\t${r.topChampions}\n")
        }
        return sb.toString()
    }

    fun exportTeamsToCsv(rows: List<TeamAnalyticsRow>): String {
        val sb = StringBuilder()
        sb.append("戰隊,賽區,賽期,場次,勝場,敗場,勝率%,藍方勝率%,紅方勝率%,首殺率%,首龍率%,15分經濟差,平均時長\n")
        for (r in rows) {
            val wr = String.format(Locale.US, "%.1f%%", r.winRate * 100)
            val bwr = String.format(Locale.US, "%.1f%%", r.blueWinRate * 100)
            val rwr = String.format(Locale.US, "%.1f%%", r.redWinRate * 100)
            val fb = String.format(Locale.US, "%.1f%%", r.firstBloodRate * 100)
            val fd = String.format(Locale.US, "%.1f%%", r.firstDragonRate * 100)
            val gd15 = String.format(Locale.US, "%.0f", r.avgGoldDiffAt15)
            val m = r.avgGameDurationSeconds / 60
            val s = r.avgGameDurationSeconds % 60
            val dur = String.format(Locale.US, "%02d:%02d", m, s)
            val sp = r.split ?: ""
            sb.append("${r.teamName},${r.league},$sp,${r.games},${r.wins},${r.losses},$wr,$bwr,$rwr,$fb,$fd,$gd15,$dur\n")
        }
        return sb.toString()
    }

    fun exportTeamsToTsv(rows: List<TeamAnalyticsRow>): String {
        val sb = StringBuilder()
        sb.append("戰隊\t賽區\t賽期\t場次\t勝場\t敗場\t勝率%\t藍方勝率%\t紅方勝率%\t首殺率%\t首龍率%\t15分經濟差\t平均時長\n")
        for (r in rows) {
            val wr = String.format(Locale.US, "%.1f%%", r.winRate * 100)
            val bwr = String.format(Locale.US, "%.1f%%", r.blueWinRate * 100)
            val rwr = String.format(Locale.US, "%.1f%%", r.redWinRate * 100)
            val fb = String.format(Locale.US, "%.1f%%", r.firstBloodRate * 100)
            val fd = String.format(Locale.US, "%.1f%%", r.firstDragonRate * 100)
            val gd15 = String.format(Locale.US, "%.0f", r.avgGoldDiffAt15)
            val m = r.avgGameDurationSeconds / 60
            val s = r.avgGameDurationSeconds % 60
            val dur = String.format(Locale.US, "%02d:%02d", m, s)
            val sp = r.split ?: ""
            sb.append("${r.teamName}\t${r.league}\t$sp\t${r.games}\t${r.wins}\t${r.losses}\t$wr\t$bwr\t$rwr\t$fb\t$fd\t$gd15\t$dur\n")
        }
        return sb.toString()
    }
}

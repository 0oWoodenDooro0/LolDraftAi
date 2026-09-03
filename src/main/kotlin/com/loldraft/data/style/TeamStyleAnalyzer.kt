package com.loldraft.data.style

import com.loldraft.data.lake.DataLakeStorage
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Game
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.models.Team
import com.loldraft.data.models.TeamGameStats

class TeamStyleAnalyzer {
    fun analyzeTeam(
        teamIdentifier: String,
        games: List<Game>,
    ): TeamTacticalProfile? {
        val teamGames = games.filter { isTeamGame(it, teamIdentifier) }
        if (teamGames.isEmpty()) return null

        val canonicalTeam = resolveCanonicalTeam(teamGames, teamIdentifier)

        val blueGames = teamGames.filter { matchesTeam(it.blueTeam, teamIdentifier) }
        val redGames = teamGames.filter { matchesTeam(it.redTeam, teamIdentifier) }

        val sidePreference = computeSidePreference(blueGames, redGames)
        val earlyGameMetrics = computeEarlyGameMetrics(teamGames, teamIdentifier)
        val tacticalStyleMetrics = computeTacticalStyleMetrics(teamGames, teamIdentifier)
        val firstPickAnalysis = computeFirstPickAnalysis(blueGames, redGames)
        val tags = synthesizeTags(sidePreference, earlyGameMetrics, tacticalStyleMetrics, firstPickAnalysis)

        return TeamTacticalProfile(
            team = canonicalTeam,
            totalGamesAnalyzed = teamGames.size,
            sidePreference = sidePreference,
            earlyGameMetrics = earlyGameMetrics,
            tacticalStyleMetrics = tacticalStyleMetrics,
            firstPickAnalysis = firstPickAnalysis,
            tags = tags,
        )
    }

    fun analyzeAllTeams(games: List<Game>): Map<String, TeamTacticalProfile> {
        val allTeams =
            games
                .flatMap { listOf(it.blueTeam, it.redTeam) }
                .distinctBy { it.id }

        return allTeams
            .mapNotNull { team ->
                analyzeTeam(team.id, games)?.let { team.id to it }
            }.toMap()
    }

    fun analyzeWithFilter(
        games: List<Game>,
        filter: TeamStyleFilter,
    ): List<TeamTacticalProfile> {
        val filteredGames =
            games.filter { game ->
                val matchPatch = filter.patch == null || game.patch == filter.patch
                val matchTournament = filter.tournament == null || game.tournament == filter.tournament
                val matchSeason = filter.season == null || game.season == filter.season
                val matchYear = filter.year == null || game.year == filter.year
                matchPatch && matchTournament && matchSeason && matchYear
            }

        val allProfiles = analyzeAllTeams(filteredGames)
        return allProfiles.values.filter { it.totalGamesAnalyzed >= filter.minGames }
    }

    fun analyzeFromStorage(
        storage: DataLakeStorage,
        teamIdentifier: String,
        filter: TeamStyleFilter = TeamStyleFilter(),
    ): TeamTacticalProfile? {
        val games =
            if (filter.patch != null) {
                storage.getGamesByPatch(filter.patch)
            } else {
                storage.getAllGames()
            }

        val filteredGames =
            games.filter { game ->
                val matchTournament = filter.tournament == null || game.tournament == filter.tournament
                val matchSeason = filter.season == null || game.season == filter.season
                val matchYear = filter.year == null || game.year == filter.year
                matchTournament && matchSeason && matchYear
            }

        return analyzeTeam(teamIdentifier, filteredGames)
    }

    private fun isTeamGame(
        game: Game,
        identifier: String,
    ): Boolean = matchesTeam(game.blueTeam, identifier) || matchesTeam(game.redTeam, identifier)

    private fun matchesTeam(
        team: Team,
        identifier: String,
    ): Boolean =
        team.id.equals(identifier, ignoreCase = true) ||
            team.name.equals(identifier, ignoreCase = true) ||
            team.code.equals(identifier, ignoreCase = true)

    private fun resolveCanonicalTeam(
        games: List<Game>,
        identifier: String,
    ): Team {
        val firstMatch = games.first()
        return if (matchesTeam(firstMatch.blueTeam, identifier)) {
            firstMatch.blueTeam
        } else {
            firstMatch.redTeam
        }
    }

    private fun computeSidePreference(
        blueGames: List<Game>,
        redGames: List<Game>,
    ): SidePreference {
        val blueWins = blueGames.count { it.winner == Side.BLUE }
        val blueLosses = blueGames.size - blueWins
        val blueWinRate = if (blueGames.isNotEmpty()) blueWins.toDouble() / blueGames.size else 0.0
        val blueRecord = SideRecord(blueGames.size, blueWins, blueLosses, blueWinRate)

        val redWins = redGames.count { it.winner == Side.RED }
        val redLosses = redGames.size - redWins
        val redWinRate = if (redGames.isNotEmpty()) redWins.toDouble() / redGames.size else 0.0
        val redRecord = SideRecord(redGames.size, redWins, redLosses, redWinRate)

        val totalGames = blueGames.size + redGames.size
        val totalWins = blueWins + redWins
        val totalLosses = blueLosses + redLosses
        val overallWinRate = if (totalGames > 0) totalWins.toDouble() / totalGames else 0.0
        val overallRecord = SideRecord(totalGames, totalWins, totalLosses, overallWinRate)

        val winRateDelta = blueWinRate - redWinRate
        val blueRate = if (totalGames > 0) blueGames.size.toDouble() / totalGames else 0.0
        val redRate = if (totalGames > 0) redGames.size.toDouble() / totalGames else 0.0

        val tendency =
            when {
                blueRate >= 0.55 -> SideTendency.BLUE_FAVORED
                redRate >= 0.55 -> SideTendency.RED_FAVORED
                else -> SideTendency.BALANCED
            }

        return SidePreference(
            blueRecord = blueRecord,
            redRecord = redRecord,
            overallRecord = overallRecord,
            winRateDelta = winRateDelta,
            blueRate = blueRate,
            redRate = redRate,
            tendency = tendency,
        )
    }

    private fun computeEarlyGameMetrics(
        games: List<Game>,
        teamIdentifier: String,
    ): EarlyGameMetrics {
        val fbList = mutableListOf<Boolean>()
        val fdList = mutableListOf<Boolean>()
        val gd15List = mutableListOf<Double>()

        for (game in games) {
            val isBlue = matchesTeam(game.blueTeam, teamIdentifier)
            val stats = if (isBlue) game.blueStats else game.redStats

            stats?.firstBlood?.let { fbList.add(it) }
            stats?.firstDragon?.let { fdList.add(it) }
            stats?.goldDiffAt15?.let { gd15List.add(it) }
        }

        val samples = maxOf(fbList.size, fdList.size, gd15List.size)
        val fbRate = if (fbList.isNotEmpty()) fbList.count { it }.toDouble() / fbList.size else 0.0
        val fdRate = if (fdList.isNotEmpty()) fdList.count { it }.toDouble() / fdList.size else 0.0
        val avgGd15 = if (gd15List.isNotEmpty()) gd15List.average() else 0.0

        val dominanceScore =
            if (samples == 0) {
                50.0
            } else {
                val fbContribution = (fbRate - 0.5) * 20.0
                val fdContribution = (fdRate - 0.5) * 20.0
                val gdContribution = (avgGd15 / 100.0).coerceIn(-30.0, 30.0)
                (50.0 + fbContribution + fdContribution + gdContribution).coerceIn(0.0, 100.0)
            }

        return EarlyGameMetrics(
            firstBloodRate = fbRate,
            firstDragonRate = fdRate,
            avgGoldDiffAt15 = avgGd15,
            gamesSampled = samples,
            dominanceScore = dominanceScore,
        )
    }

    private fun computeTacticalStyleMetrics(
        games: List<Game>,
        teamIdentifier: String,
    ): TacticalStyleMetrics {
        var totalTeamKills = 0
        var totalOppKills = 0
        var totalDurationSeconds = 0.0
        var validDurationGames = 0

        for (game in games) {
            val duration = game.durationSeconds ?: 0
            if (duration > 0) {
                totalDurationSeconds += duration
                validDurationGames++
            }

            val isBlue = matchesTeam(game.blueTeam, teamIdentifier)
            val teamStats: TeamGameStats? = if (isBlue) game.blueStats else game.redStats
            val oppStats: TeamGameStats? = if (isBlue) game.redStats else game.blueStats

            teamStats?.kills?.let { totalTeamKills += it }
            oppStats?.kills?.let { totalOppKills += it }
        }

        val totalMinutes = if (totalDurationSeconds > 0) totalDurationSeconds / 60.0 else 0.0
        val teamKpm = if (totalMinutes > 0) totalTeamKills / totalMinutes else 0.0
        val combinedKpm = if (totalMinutes > 0) (totalTeamKills + totalOppKills) / totalMinutes else 0.0
        val avgDurationSeconds = if (validDurationGames > 0) totalDurationSeconds / validDurationGames else 0.0

        val avgDurationFormatted = formatDuration(avgDurationSeconds)

        val pace =
            when {
                avgDurationSeconds > 0 && avgDurationSeconds < 1740.0 -> GamePace.FAST_PACED
                avgDurationSeconds > 2100.0 -> GamePace.SLOW_CONTROLLED
                else -> GamePace.AVERAGE
            }

        val aggression =
            when {
                teamKpm >= 0.55 || combinedKpm >= 0.90 -> AggressionLevel.VERY_AGGRESSIVE
                teamKpm <= 0.35 && combinedKpm <= 0.60 -> AggressionLevel.CONTROL_ORIENTED
                else -> AggressionLevel.BALANCED
            }

        return TacticalStyleMetrics(
            teamKillsPerMinute = teamKpm,
            combinedKillsPerMinute = combinedKpm,
            avgDurationSeconds = avgDurationSeconds,
            avgDurationFormatted = avgDurationFormatted,
            pace = pace,
            aggression = aggression,
        )
    }

    private fun computeFirstPickAnalysis(
        blueGames: List<Game>,
        redGames: List<Game>,
    ): FirstPickAnalysis {
        val b1Picks = mutableListOf<FirstPickEntry>()
        val teamFirstPicks = mutableListOf<FirstPickEntry>()

        // Blue side: B1 is Turn 7
        for (game in blueGames) {
            val b1Turn = game.draftState.turns.find { it.side == Side.BLUE && it.actionType == ActionType.PICK }
            val championId =
                b1Turn?.championId ?: game.draftState.bluePicks
                    .firstOrNull()
                    ?.championId
            val role =
                b1Turn?.role ?: game.draftState.bluePicks
                    .firstOrNull()
                    ?.role
            val won = game.winner == Side.BLUE

            if (championId != null) {
                val entry = FirstPickEntry(championId, role, won)
                b1Picks.add(entry)
                teamFirstPicks.add(entry)
            }
        }

        // Red side: team's first pick is Turn 8 / Turn 9 / first red pick
        for (game in redGames) {
            val r1Turn = game.draftState.turns.find { it.side == Side.RED && it.actionType == ActionType.PICK }
            val championId =
                r1Turn?.championId ?: game.draftState.redPicks
                    .firstOrNull()
                    ?.championId
            val role =
                r1Turn?.role ?: game.draftState.redPicks
                    .firstOrNull()
                    ?.role
            val won = game.winner == Side.RED

            if (championId != null) {
                teamFirstPicks.add(FirstPickEntry(championId, role, won))
            }
        }

        val b1Opportunities = blueGames.size
        val b1Priorities = aggregatePriorities(b1Picks, b1Opportunities)

        val totalFirstPickOpportunities = blueGames.size + redGames.size
        val teamFirstPickPriorities = aggregatePriorities(teamFirstPicks, totalFirstPickOpportunities)

        val roleDistribution = mutableMapOf<Role, Double>()
        val totalOpportunities = if (b1Opportunities > 0) b1Opportunities.toDouble() else 1.0
        val roleCounts = b1Picks.mapNotNull { it.role }.groupingBy { it }.eachCount()
        for ((role, count) in roleCounts) {
            roleDistribution[role] = count / totalOpportunities
        }

        return FirstPickAnalysis(
            b1Priorities = b1Priorities,
            teamFirstPickPriorities = teamFirstPickPriorities,
            roleDistribution = roleDistribution,
        )
    }

    private fun aggregatePriorities(
        entries: List<FirstPickEntry>,
        totalOpportunities: Int,
    ): List<FirstPickPriority> {
        if (totalOpportunities <= 0 || entries.isEmpty()) return emptyList()

        val grouped = entries.groupBy { it.championId }
        return grouped
            .map { (championId, picks) ->
                val pickCount = picks.size
                val wins = picks.count { it.won }
                val pickRate = pickCount.toDouble() / totalOpportunities
                val winRate = if (pickCount > 0) wins.toDouble() / pickCount else 0.0
                val role = picks.mapNotNull { it.role }.firstOrNull()

                FirstPickPriority(
                    championId = championId,
                    pickCount = pickCount,
                    totalOpportunities = totalOpportunities,
                    pickRate = pickRate,
                    wins = wins,
                    winRate = winRate,
                    role = role,
                )
            }.sortedWith(compareByDescending<FirstPickPriority> { it.pickCount }.thenByDescending { it.winRate })
    }

    private fun synthesizeTags(
        side: SidePreference,
        early: EarlyGameMetrics,
        tactical: TacticalStyleMetrics,
        firstPick: FirstPickAnalysis,
    ): Set<TacticalTag> {
        val tags = mutableSetOf<TacticalTag>()

        if (early.firstBloodRate >= 0.60 || early.avgGoldDiffAt15 >= 1000.0) {
            tags.add(TacticalTag.EARLY_AGGRESSOR)
        }
        if (early.firstDragonRate >= 0.60) {
            tags.add(TacticalTag.DRAGON_CONTROL)
        }
        if (tactical.combinedKillsPerMinute >= 0.90 || tactical.teamKillsPerMinute >= 0.55) {
            tags.add(TacticalTag.BLOODY_SKIRMISHER)
        }
        if (tactical.avgDurationSeconds >= 2100.0 && tactical.teamKillsPerMinute <= 0.40) {
            tags.add(TacticalTag.LATE_GAME_MACRO)
        }
        if (tactical.avgDurationSeconds in 1.0..1650.0) {
            tags.add(TacticalTag.FAST_TEMPO)
        }
        if (tactical.avgDurationSeconds >= 2100.0) {
            tags.add(TacticalTag.SLOW_TEMPO)
        }
        if (side.winRateDelta >= 0.30 && side.blueRecord.games >= 2) {
            tags.add(TacticalTag.BLUE_SIDE_SPECIALIST)
        }
        if (side.winRateDelta <= -0.30 && side.redRecord.games >= 2) {
            tags.add(TacticalTag.RED_SIDE_SPECIALIST)
        }

        val botShare = firstPick.roleDistribution[Role.BOT] ?: 0.0
        if (botShare >= 0.50) {
            tags.add(TacticalTag.BOT_CENTRIC_DRAFT)
        }

        val midShare = firstPick.roleDistribution[Role.MID] ?: 0.0
        if (midShare >= 0.50) {
            tags.add(TacticalTag.MID_CENTRIC_DRAFT)
        }

        val topShare = firstPick.roleDistribution[Role.TOP] ?: 0.0
        if (topShare >= 0.50) {
            tags.add(TacticalTag.TOP_CENTRIC_DRAFT)
        }

        return tags
    }

    private fun formatDuration(seconds: Double): String {
        val totalSec = seconds.toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return "%02d:%02d".format(m, s)
    }

    private data class FirstPickEntry(
        val championId: String,
        val role: Role?,
        val won: Boolean,
    )
}

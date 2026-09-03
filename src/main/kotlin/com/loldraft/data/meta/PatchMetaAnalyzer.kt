package com.loldraft.data.meta

import com.loldraft.data.lake.DataLakeStorage
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.normalization.ChampionNormalizer
import kotlin.math.log10

class PatchMetaAnalyzer(
    private val defaultConfig: PatchMetaConfig = PatchMetaConfig(),
) {
    fun analyzePatch(
        patch: String,
        games: List<Game>,
        config: PatchMetaConfig = defaultConfig,
    ): PatchMetaMatrix {
        val patchGames = games.filter { it.patch == patch }
        if (patchGames.isEmpty()) {
            return PatchMetaMatrix(
                patch = patch,
                totalGames = 0,
                championStats = emptyMap(),
                synergies = emptyList(),
                matchupCounters = emptyList(),
            )
        }

        return analyzeGames(patchGames, patch, config)
    }

    fun analyzeGames(
        games: List<Game>,
        patchLabel: String? = null,
        config: PatchMetaConfig = defaultConfig,
    ): PatchMetaMatrix {
        val totalGames = games.size
        val effectivePatch = patchLabel ?: games.firstOrNull()?.patch ?: "unknown"

        val champPicks = mutableMapOf<String, Int>()
        val champBans = mutableMapOf<String, Int>()
        val champWins = mutableMapOf<String, Int>()
        val champLosses = mutableMapOf<String, Int>()
        val champRoles = mutableMapOf<String, MutableMap<Role, Int>>()

        // Synergy tracking: (ChampA, ChampB) -> Pair<Games, Wins>
        val synergyAcc = mutableMapOf<Pair<String, String>, Pair<Int, Int>>()

        // Matchup tracking: Triple(Champ, Opponent, Role?) -> MatchupAccumulator
        val matchupAcc = mutableMapOf<Triple<String, String, Role?>, MatchupAccumulator>()

        for (game in games) {
            val bluePicks = extractPicks(game, Side.BLUE)
            val redPicks = extractPicks(game, Side.RED)
            val blueBans = extractBans(game, Side.BLUE)
            val redBans = extractBans(game, Side.RED)

            val blueWon = game.winner == Side.BLUE
            val redWon = game.winner == Side.RED

            // Process Bans
            for (ban in (blueBans + redBans)) {
                if (ChampionNormalizer.isNoneOrEmpty(ban)) continue
                val slug = ChampionNormalizer.toSlug(ban)
                champBans[slug] = (champBans[slug] ?: 0) + 1
            }

            // Process Blue Picks
            for (pick in bluePicks) {
                if (ChampionNormalizer.isNoneOrEmpty(pick.championId)) continue
                val slug = ChampionNormalizer.toSlug(pick.championId)
                champPicks[slug] = (champPicks[slug] ?: 0) + 1
                if (blueWon) {
                    champWins[slug] = (champWins[slug] ?: 0) + 1
                } else if (redWon) {
                    champLosses[slug] = (champLosses[slug] ?: 0) + 1
                }
                pick.role?.let { role ->
                    val roleMap = champRoles.getOrPut(slug) { mutableMapOf() }
                    roleMap[role] = (roleMap[role] ?: 0) + 1
                }
            }

            // Process Red Picks
            for (pick in redPicks) {
                if (ChampionNormalizer.isNoneOrEmpty(pick.championId)) continue
                val slug = ChampionNormalizer.toSlug(pick.championId)
                champPicks[slug] = (champPicks[slug] ?: 0) + 1
                if (redWon) {
                    champWins[slug] = (champWins[slug] ?: 0) + 1
                } else if (blueWon) {
                    champLosses[slug] = (champLosses[slug] ?: 0) + 1
                }
                pick.role?.let { role ->
                    val roleMap = champRoles.getOrPut(slug) { mutableMapOf() }
                    roleMap[role] = (roleMap[role] ?: 0) + 1
                }
            }

            // Process Combos / Synergies
            recordTeamSynergies(bluePicks, blueWon, synergyAcc)
            recordTeamSynergies(redPicks, redWon, synergyAcc)

            // Process Lane Matchups (Role vs Role)
            val blueGd15 = game.blueStats?.goldDiffAt15
            val redGd15 = game.redStats?.goldDiffAt15

            for (role in Role.entries) {
                val blueRolePick = bluePicks.find { it.role == role }
                val redRolePick = redPicks.find { it.role == role }

                if (blueRolePick != null && redRolePick != null) {
                    val bSlug = ChampionNormalizer.toSlug(blueRolePick.championId)
                    val rSlug = ChampionNormalizer.toSlug(redRolePick.championId)

                    if (bSlug.isNotBlank() && rSlug.isNotBlank()) {
                        // Blue vs Red
                        val acc1 = matchupAcc.getOrPut(Triple(bSlug, rSlug, role)) { MatchupAccumulator() }
                        acc1.games++
                        if (blueWon) {
                            acc1.wins++
                        } else if (redWon) {
                            acc1.losses++
                        }
                        blueGd15?.let { acc1.goldDiffs.add(it) }

                        // Red vs Blue
                        val acc2 = matchupAcc.getOrPut(Triple(rSlug, bSlug, role)) { MatchupAccumulator() }
                        acc2.games++
                        if (redWon) {
                            acc2.wins++
                        } else if (blueWon) {
                            acc2.losses++
                        }
                        redGd15?.let { acc2.goldDiffs.add(it) }
                    }
                }
            }
        }

        // Build ChampionMetaStats
        val allChamps = (champPicks.keys + champBans.keys).toSet()
        val statsMap = mutableMapOf<String, ChampionMetaStats>()

        for (slug in allChamps) {
            val picks = champPicks[slug] ?: 0
            val bans = champBans[slug] ?: 0
            val presence = picks + bans
            val presenceRate = if (totalGames > 0) presence.toDouble() / totalGames else 0.0
            val pickRate = if (totalGames > 0) picks.toDouble() / totalGames else 0.0
            val banRate = if (totalGames > 0) bans.toDouble() / totalGames else 0.0
            val wins = champWins[slug] ?: 0
            val losses = champLosses[slug] ?: 0
            val winRate = if (picks > 0) wins.toDouble() / picks else 0.0
            val roles = champRoles[slug] ?: emptyMap()

            val tier =
                when {
                    presenceRate >= config.t0PresenceThreshold ||
                        (presenceRate >= 0.50 && winRate >= config.t0WinRateThreshold) -> MetaTier.T0
                    presenceRate >= config.t1PresenceThreshold -> MetaTier.T1
                    presenceRate >= config.t2PresenceThreshold -> MetaTier.T2
                    presenceRate >= config.t3PresenceThreshold -> MetaTier.T3
                    else -> MetaTier.T4
                }

            statsMap[slug] =
                ChampionMetaStats(
                    championId = slug,
                    patch = effectivePatch,
                    picks = picks,
                    bans = bans,
                    presenceCount = presence,
                    presenceRate = presenceRate,
                    pickRate = pickRate,
                    banRate = banRate,
                    wins = wins,
                    losses = losses,
                    winRate = winRate,
                    roleDistribution = roles,
                    tier = tier,
                )
        }

        // Build Synergies
        val synergies = mutableListOf<ChampionSynergy>()
        for ((pair, record) in synergyAcc) {
            val (cA, cB) = pair
            val (gamesTogether, winsTogether) = record
            if (gamesTogether <= 0) continue

            val synergyWinRate = winsTogether.toDouble() / gamesTogether
            val winRateA = statsMap[cA]?.winRate ?: 0.50
            val winRateB = statsMap[cB]?.winRate ?: 0.50
            val expectedWinRate = (winRateA + winRateB) / 2.0
            val winRateDelta = synergyWinRate - expectedWinRate
            val synergyScore = winRateDelta * (1.0 + log10(gamesTogether.toDouble()))

            synergies.add(
                ChampionSynergy(
                    championA = cA,
                    championB = cB,
                    gamesTogether = gamesTogether,
                    winsTogether = winsTogether,
                    synergyWinRate = synergyWinRate,
                    expectedWinRate = expectedWinRate,
                    winRateDelta = winRateDelta,
                    synergyScore = synergyScore,
                ),
            )
        }

        // Build Matchups
        val matchups = mutableListOf<MatchupCounter>()
        for ((key, acc) in matchupAcc) {
            val (champ, opp, role) = key
            if (acc.games <= 0) continue

            val winRate = acc.wins.toDouble() / acc.games
            val champBaseline = statsMap[champ]?.winRate ?: 0.50
            val winRateDelta = winRate - champBaseline
            val avgGd = if (acc.goldDiffs.isNotEmpty()) acc.goldDiffs.average() else null
            val counterScore =
                (winRate - 0.5) * 40.0 +
                    (winRateDelta * 30.0) +
                    ((avgGd ?: 0.0) / 100.0).coerceIn(-30.0, 30.0)

            matchups.add(
                MatchupCounter(
                    champion = champ,
                    opponent = opp,
                    role = role,
                    gamesFaced = acc.games,
                    wins = acc.wins,
                    losses = acc.losses,
                    winRate = winRate,
                    winRateDelta = winRateDelta,
                    avgGoldDiffAt15 = avgGd,
                    counterScore = counterScore,
                ),
            )
        }

        return PatchMetaMatrix(
            patch = effectivePatch,
            totalGames = totalGames,
            championStats = statsMap,
            synergies = synergies,
            matchupCounters = matchups,
        )
    }

    fun analyzeFromStorage(
        storage: DataLakeStorage,
        patch: String,
        config: PatchMetaConfig = defaultConfig,
    ): PatchMetaMatrix? {
        val games = storage.getGamesByPatch(patch)
        if (games.isEmpty()) return null
        return analyzePatch(patch, games, config)
    }

    private fun extractPicks(
        game: Game,
        side: Side,
    ): List<PickSelection> {
        val picks = if (side == Side.BLUE) game.draftState.bluePicks else game.draftState.redPicks
        if (picks.isNotEmpty()) return picks

        return game.draftState.turns
            .filter { it.side == side && it.actionType == ActionType.PICK }
            .map { PickSelection(it.championId, it.role, it.player) }
    }

    private fun extractBans(
        game: Game,
        side: Side,
    ): List<String> {
        val bans = if (side == Side.BLUE) game.draftState.blueBans else game.draftState.redBans
        if (bans.isNotEmpty()) return bans

        return game.draftState.turns
            .filter { it.side == side && it.actionType == ActionType.BAN }
            .map { it.championId }
    }

    private fun recordTeamSynergies(
        picks: List<PickSelection>,
        won: Boolean,
        acc: MutableMap<Pair<String, String>, Pair<Int, Int>>,
    ) {
        val validSlugs =
            picks
                .map { ChampionNormalizer.toSlug(it.championId) }
                .filter { it.isNotBlank() }

        for (i in validSlugs.indices) {
            for (j in i + 1 until validSlugs.size) {
                val c1 = validSlugs[i]
                val c2 = validSlugs[j]
                val pair = if (c1 <= c2) c1 to c2 else c2 to c1
                val current = acc.getOrPut(pair) { 0 to 0 }
                val newGames = current.first + 1
                val newWins = current.second + (if (won) 1 else 0)
                acc[pair] = newGames to newWins
            }
        }
    }

    private class MatchupAccumulator {
        var games: Int = 0
        var wins: Int = 0
        var losses: Int = 0
        val goldDiffs = mutableListOf<Double>()
    }
}

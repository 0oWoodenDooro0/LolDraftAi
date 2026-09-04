package com.loldraft.data.meta

import com.loldraft.data.lake.DataLakeStorage
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Game
import com.loldraft.data.models.PickSelection
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.normalization.PatchNormalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.log10
import kotlin.math.roundToInt

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
    ): PatchMetaMatrix = analyzeWeightedGames(games.map { it to 1.0 }, patchLabel, config)

    fun analyzeGamesForPrediction(
        games: List<Game>,
        targetPatch: String,
        referenceDate: LocalDate? = null,
        maxAgeDays: Long = 30,
        config: PatchMetaConfig = defaultConfig,
    ): PatchMetaMatrix {
        val refDate = referenceDate ?: games.mapNotNull { parseDate(it.date) }.maxOrNull()
        val weightedGames = games.mapNotNull { game ->
            val w = calculateGameWeight(game, targetPatch, refDate, maxAgeDays)
            if (w != null && w > 0.0) {
                game to w
            } else {
                null
            }
        }
        if (weightedGames.isEmpty()) {
            return PatchMetaMatrix(
                patch = targetPatch,
                totalGames = 0,
                championStats = emptyMap(),
                synergies = emptyList(),
                matchupCounters = emptyList(),
            )
        }
        return analyzeWeightedGames(weightedGames, patchLabel = targetPatch, config = config)
    }

    fun analyzeWeightedGames(
        weightedGames: List<Pair<Game, Double>>,
        patchLabel: String? = null,
        config: PatchMetaConfig = defaultConfig,
    ): PatchMetaMatrix {
        val effectivePatch = patchLabel ?: weightedGames.firstOrNull()?.first?.patch ?: "unknown"
        val totalEffectiveGames = weightedGames.sumOf { it.second }
        val totalGames = Math.round(totalEffectiveGames).toInt()

        val champPicks = mutableMapOf<String, Double>()
        val champBans = mutableMapOf<String, Double>()
        val champWins = mutableMapOf<String, Double>()
        val champLosses = mutableMapOf<String, Double>()
        val champRoles = mutableMapOf<String, MutableMap<Role, Double>>()

        // Synergy tracking: (ChampA, ChampB) -> Pair<WeightedGames, WeightedWins>
        val synergyAcc = mutableMapOf<Pair<String, String>, Pair<Double, Double>>()

        // Matchup tracking: Triple(Champ, Opponent, Role?) -> MatchupAccumulator
        val matchupAcc = mutableMapOf<Triple<String, String, Role?>, MatchupAccumulator>()

        // Bot Duo Synergy: Pair(Bot, Sup) -> BotDuoAccumulator
        val botDuoAcc = mutableMapOf<Pair<String, String>, BotDuoAccumulator>()

        // Bot Duo Matchup: Pair(BlueDuo, RedDuo) -> BotDuoMatchupAccumulator
        val botDuoMatchupAcc = mutableMapOf<Pair<Pair<String, String>, Pair<String, String>>, BotDuoMatchupAccumulator>()

        for ((game, weight) in weightedGames) {
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
                champBans[slug] = (champBans[slug] ?: 0.0) + weight
            }

            // Process Blue Picks
            for (pick in bluePicks) {
                if (ChampionNormalizer.isNoneOrEmpty(pick.championId)) continue
                val slug = ChampionNormalizer.toSlug(pick.championId)
                champPicks[slug] = (champPicks[slug] ?: 0.0) + weight
                if (blueWon) {
                    champWins[slug] = (champWins[slug] ?: 0.0) + weight
                } else if (redWon) {
                    champLosses[slug] = (champLosses[slug] ?: 0.0) + weight
                }
                pick.role?.let { role ->
                    val roleMap = champRoles.getOrPut(slug) { mutableMapOf() }
                    roleMap[role] = (roleMap[role] ?: 0.0) + weight
                }
            }

            // Process Red Picks
            for (pick in redPicks) {
                if (ChampionNormalizer.isNoneOrEmpty(pick.championId)) continue
                val slug = ChampionNormalizer.toSlug(pick.championId)
                champPicks[slug] = (champPicks[slug] ?: 0.0) + weight
                if (redWon) {
                    champWins[slug] = (champWins[slug] ?: 0.0) + weight
                } else if (blueWon) {
                    champLosses[slug] = (champLosses[slug] ?: 0.0) + weight
                }
                pick.role?.let { role ->
                    val roleMap = champRoles.getOrPut(slug) { mutableMapOf() }
                    roleMap[role] = (roleMap[role] ?: 0.0) + weight
                }
            }

            // Process Combos / Synergies
            recordTeamSynergies(bluePicks, blueWon, synergyAcc, weight)
            recordTeamSynergies(redPicks, redWon, synergyAcc, weight)

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
                        acc1.games += weight
                        if (blueWon) {
                            acc1.wins += weight
                        } else if (redWon) {
                            acc1.losses += weight
                        }
                        blueGd15?.let { acc1.goldDiffs.add(it to weight) }

                        // Red vs Blue
                        val acc2 = matchupAcc.getOrPut(Triple(rSlug, bSlug, role)) { MatchupAccumulator() }
                        acc2.games += weight
                        if (redWon) {
                            acc2.wins += weight
                        } else if (blueWon) {
                            acc2.losses += weight
                        }
                        redGd15?.let { acc2.goldDiffs.add(it to weight) }
                    }
                }
            }

            // Process Bot 2v2 Duos (Bot + Support)
            val blueBotPick = bluePicks.find { it.role == Role.BOT }
            val blueSupPick = bluePicks.find { it.role == Role.SUPPORT }
            if (blueBotPick != null && blueSupPick != null) {
                val bBot = ChampionNormalizer.toSlug(blueBotPick.championId)
                val bSup = ChampionNormalizer.toSlug(blueSupPick.championId)
                if (bBot.isNotBlank() && bSup.isNotBlank()) {
                    val acc = botDuoAcc.getOrPut(bBot to bSup) { BotDuoAccumulator() }
                    acc.games += weight
                    if (blueWon) acc.wins += weight
                    blueGd15?.let { acc.goldDiffs.add(it to weight) }
                }
            }

            val redBotPick = redPicks.find { it.role == Role.BOT }
            val redSupPick = redPicks.find { it.role == Role.SUPPORT }
            if (redBotPick != null && redSupPick != null) {
                val rBot = ChampionNormalizer.toSlug(redBotPick.championId)
                val rSup = ChampionNormalizer.toSlug(redSupPick.championId)
                if (rBot.isNotBlank() && rSup.isNotBlank()) {
                    val acc = botDuoAcc.getOrPut(rBot to rSup) { BotDuoAccumulator() }
                    acc.games += weight
                    if (redWon) acc.wins += weight
                    redGd15?.let { acc.goldDiffs.add(it to weight) }
                }
            }

            if (blueBotPick != null && blueSupPick != null && redBotPick != null && redSupPick != null) {
                val bBot = ChampionNormalizer.toSlug(blueBotPick.championId)
                val bSup = ChampionNormalizer.toSlug(blueSupPick.championId)
                val rBot = ChampionNormalizer.toSlug(redBotPick.championId)
                val rSup = ChampionNormalizer.toSlug(redSupPick.championId)
                if (bBot.isNotBlank() && bSup.isNotBlank() && rBot.isNotBlank() && rSup.isNotBlank()) {
                    val mAcc = botDuoMatchupAcc.getOrPut((bBot to bSup) to (rBot to rSup)) { BotDuoMatchupAccumulator() }
                    mAcc.games += weight
                    if (blueWon) mAcc.blueWins += weight
                    blueGd15?.let { mAcc.goldDiffs.add(it to weight) }
                }
            }
        }

        // Build ChampionMetaStats
        val allChamps = (champPicks.keys + champBans.keys).toSet()
        val statsMap = mutableMapOf<String, ChampionMetaStats>()

        for (slug in allChamps) {
            val effectivePicks = champPicks[slug] ?: 0.0
            val effectiveBans = champBans[slug] ?: 0.0
            val presence = effectivePicks + effectiveBans
            val presenceRate = if (totalEffectiveGames > 0.0) (presence / totalEffectiveGames).coerceIn(0.0, 1.0) else 0.0
            val pickRate = if (totalEffectiveGames > 0.0) (effectivePicks / totalEffectiveGames).coerceIn(0.0, 1.0) else 0.0
            val banRate = if (totalEffectiveGames > 0.0) (effectiveBans / totalEffectiveGames).coerceIn(0.0, 1.0) else 0.0
            val effectiveWins = champWins[slug] ?: 0.0
            val effectiveLosses = champLosses[slug] ?: 0.0
            val winRate = if (effectivePicks > 0.0) (effectiveWins / effectivePicks).coerceIn(0.0, 1.0) else 0.0
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
                    picks = Math.round(effectivePicks).toInt(),
                    bans = Math.round(effectiveBans).toInt(),
                    presenceCount = Math.round(presence).toInt(),
                    presenceRate = presenceRate,
                    pickRate = pickRate,
                    banRate = banRate,
                    wins = Math.round(effectiveWins).toInt(),
                    losses = Math.round(effectiveLosses).toInt(),
                    winRate = winRate,
                    roleDistribution = roles.mapValues { Math.round(it.value).toInt() },
                    tier = tier,
                )
        }

        // Build Synergies
        val synergies = mutableListOf<ChampionSynergy>()
        for ((pair, record) in synergyAcc) {
            val (cA, cB) = pair
            val (gamesTogether, winsTogether) = record
            if (gamesTogether <= 0.0) continue

            val synergyWinRate = winsTogether / gamesTogether
            val winRateA = statsMap[cA]?.winRate ?: 0.50
            val winRateB = statsMap[cB]?.winRate ?: 0.50
            val expectedWinRate = (winRateA + winRateB) / 2.0
            val winRateDelta = synergyWinRate - expectedWinRate
            val synergyScore = winRateDelta * (1.0 + log10(maxOf(1.0, gamesTogether)))

            synergies.add(
                ChampionSynergy(
                    championA = cA,
                    championB = cB,
                    gamesTogether = Math.round(gamesTogether).toInt(),
                    winsTogether = Math.round(winsTogether).toInt(),
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
            if (acc.games <= 0.0) continue

            val winRate = acc.wins / acc.games
            val champBaseline = statsMap[champ]?.winRate ?: 0.50
            val winRateDelta = winRate - champBaseline
            val avgGd = if (acc.goldDiffs.isNotEmpty()) acc.goldDiffs.sumOf { it.first * it.second } / acc.goldDiffs.sumOf { it.second } else null
            val counterScore =
                (winRate - 0.5) * 40.0 +
                    (winRateDelta * 30.0) +
                    ((avgGd ?: 0.0) / 100.0).coerceIn(-30.0, 30.0)

            matchups.add(
                MatchupCounter(
                    champion = champ,
                    opponent = opp,
                    role = role,
                    gamesFaced = Math.round(acc.games).toInt(),
                    wins = Math.round(acc.wins).toInt(),
                    losses = Math.round(acc.losses).toInt(),
                    winRate = winRate,
                    winRateDelta = winRateDelta,
                    avgGoldDiffAt15 = avgGd,
                    counterScore = counterScore,
                ),
            )
        }

        // Build Bot Duo Synergies
        val duoMap = mutableMapOf<Pair<String, String>, BotDuoSynergy>()
        for ((pair, acc) in botDuoAcc) {
            if (acc.games <= 0.0) continue
            val (bot, sup) = pair
            val winRate = acc.wins / acc.games
            val avgGd = if (acc.goldDiffs.isNotEmpty()) acc.goldDiffs.sumOf { it.first * it.second } / acc.goldDiffs.sumOf { it.second } else 0.0
            val synergyScore = (winRate - 0.50) * 50.0 + (avgGd / 50.0).coerceIn(-25.0, 25.0) + 50.0
            val tags = resolveDuoStyleTags(bot, sup)
            duoMap[pair] =
                BotDuoSynergy(
                    botChampion = bot,
                    supportChampion = sup,
                    gamesTogether = Math.round(acc.games).toInt(),
                    winsTogether = Math.round(acc.wins).toInt(),
                    synergyWinRate = winRate,
                    avgGoldDiffAt15 = avgGd,
                    synergyScore = synergyScore,
                    styleTags = tags,
                )
        }
        for (classic in CLASSIC_BOT_DUOS) {
            val key = ChampionNormalizer.toSlug(classic.botChampion) to ChampionNormalizer.toSlug(classic.supportChampion)
            if (!duoMap.containsKey(key)) {
                duoMap[key] = classic
            }
        }
        val botDuoSynergies = duoMap.values.toList()

        // Build Bot Duo Matchups
        val duoMatchupMap = mutableMapOf<Pair<Pair<String, String>, Pair<String, String>>, BotDuoMatchup>()
        for ((duoPair, acc) in botDuoMatchupAcc) {
            if (acc.games <= 0.0) continue
            val (blueDuo, redDuo) = duoPair
            val blueWinRate = acc.blueWins / acc.games
            val avgGd = if (acc.goldDiffs.isNotEmpty()) acc.goldDiffs.sumOf { it.first * it.second } / acc.goldDiffs.sumOf { it.second } else 0.0
            val counterScore = (blueWinRate - 0.50) * 50.0 + (avgGd / 50.0).coerceIn(-25.0, 25.0) + 50.0
            duoMatchupMap[duoPair] =
                BotDuoMatchup(
                    blueDuo = blueDuo,
                    redDuo = redDuo,
                    gamesFaced = Math.round(acc.games).toInt(),
                    blueWins = Math.round(acc.blueWins).toInt(),
                    blueWinRate = blueWinRate,
                    avgGoldDiffAt15 = avgGd,
                    counterScore = counterScore,
                )
        }
        for (classicMatchup in CLASSIC_DUO_MATCHUPS) {
            val bKey = ChampionNormalizer.toSlug(classicMatchup.blueDuo.first) to ChampionNormalizer.toSlug(classicMatchup.blueDuo.second)
            val rKey = ChampionNormalizer.toSlug(classicMatchup.redDuo.first) to ChampionNormalizer.toSlug(classicMatchup.redDuo.second)
            val fullKey = bKey to rKey
            if (!duoMatchupMap.containsKey(fullKey)) {
                duoMatchupMap[fullKey] = classicMatchup
            }
        }
        val botDuoMatchups = duoMatchupMap.values.toList()

        return PatchMetaMatrix(
            patch = effectivePatch,
            totalGames = totalGames,
            championStats = statsMap,
            synergies = synergies,
            matchupCounters = matchups,
            botDuoSynergies = botDuoSynergies,
            botDuoMatchups = botDuoMatchups,
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
        acc: MutableMap<Pair<String, String>, Pair<Double, Double>>,
        weight: Double = 1.0,
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
                val current = acc.getOrPut(pair) { 0.0 to 0.0 }
                val newGames = current.first + weight
                val newWins = current.second + (if (won) weight else 0.0)
                acc[pair] = newGames to newWins
            }
        }
    }

    private class MatchupAccumulator {
        var games: Double = 0.0
        var wins: Double = 0.0
        var losses: Double = 0.0
        val goldDiffs = mutableListOf<Pair<Double, Double>>()
    }

    private class BotDuoAccumulator {
        var games: Double = 0.0
        var wins: Double = 0.0
        val goldDiffs = mutableListOf<Pair<Double, Double>>()
    }

    private class BotDuoMatchupAccumulator {
        var games: Double = 0.0
        var blueWins: Double = 0.0
        val goldDiffs = mutableListOf<Pair<Double, Double>>()
    }

    companion object {
        fun parseDate(dateStr: String?): LocalDate? {
            if (dateStr.isNullOrBlank()) return null
            val trimmed = dateStr.trim()
            if (trimmed.length >= 10) {
                val prefix = trimmed.substring(0, 10)
                try {
                    return LocalDate.parse(prefix)
                } catch (_: Exception) {}
            }
            return null
        }

        fun calculateGameWeight(
            game: Game,
            targetPatch: String,
            referenceDate: LocalDate? = null,
            maxAgeDays: Long = 30,
        ): Double? {
            val gameDate = parseDate(game.date)
            if (gameDate != null && referenceDate != null) {
                val daysAgo = ChronoUnit.DAYS.between(gameDate, referenceDate)
                if (daysAgo > maxAgeDays) {
                    return null // Exclude matches older than 1 month
                }
            }

            // Patch distance calculation
            val targetVer = PatchNormalizer.parse(targetPatch)
            val gameVer = PatchNormalizer.parse(game.patch)

            val patchWeight =
                if (targetVer != null && gameVer != null) {
                    val patchDiff = (targetVer.major - gameVer.major) * 24 + (targetVer.minor - gameVer.minor)
                    when {
                        patchDiff == 0 -> 1.0 // Same patch has highest weight!
                        patchDiff > 0 -> Math.pow(0.6, patchDiff.toDouble()) // Prior patches decay
                        else -> 0.4 // Future patch relative to target
                    }
                } else if (game.patch.equals(targetPatch, ignoreCase = true) ||
                    PatchNormalizer.normalize(game.patch).equals(PatchNormalizer.normalize(targetPatch), ignoreCase = true)
                ) {
                    1.0
                } else {
                    0.5
                }

            val timeWeight =
                if (gameDate != null && referenceDate != null) {
                    val days = ChronoUnit.DAYS.between(gameDate, referenceDate).coerceAtLeast(0)
                    1.0 - (days.toDouble() / maxAgeDays) * 0.4
                } else {
                    1.0
                }

            return (patchWeight * timeWeight).coerceIn(0.01, 1.0)
        }

        fun resolveDuoStyleTags(bot: String, sup: String): List<BotDuoStyleTag> {
            val b = ChampionNormalizer.toSlug(bot)
            val s = ChampionNormalizer.toSlug(sup)
            val tags = mutableListOf<BotDuoStyleTag>()
            if (s in setOf("nautilus", "leona", "rell", "alistar", "blitzcrank", "pyke", "thresh")) {
                tags.add(BotDuoStyleTag.ALL_IN_KILL)
                tags.add(BotDuoStyleTag.CROWD_CONTROL_CHAIN)
            }
            if (s in setOf("lux", "karma", "morgana", "zyra", "ashe", "xerath", "velkoz", "nami") || b in setOf("caitlyn", "varus", "ezreal", "lucian")) {
                tags.add(BotDuoStyleTag.POKE_SIEGE)
            }
            if (s in setOf("lulu", "yuumi", "janna", "soraka", "milio", "sona") || b in setOf("zeri", "jinx", "kogmaw", "vayne", "smolder")) {
                tags.add(BotDuoStyleTag.HYPER_CARRY_PEEL)
            }
            if (tags.isEmpty()) tags.add(BotDuoStyleTag.STANDARD)
            return tags.distinct()
        }

        val CLASSIC_BOT_DUOS =
            listOf(
                BotDuoSynergy("Lucian", "Nami", gamesTogether = 25, winsTogether = 15, synergyWinRate = 0.60, avgGoldDiffAt15 = 380.0, synergyScore = 78.0, styleTags = listOf(BotDuoStyleTag.ALL_IN_KILL, BotDuoStyleTag.POKE_SIEGE)),
                BotDuoSynergy("Xayah", "Rakan", gamesTogether = 30, winsTogether = 18, synergyWinRate = 0.60, avgGoldDiffAt15 = 250.0, synergyScore = 75.0, styleTags = listOf(BotDuoStyleTag.CROWD_CONTROL_CHAIN, BotDuoStyleTag.ALL_IN_KILL)),
                BotDuoSynergy("Kalista", "Renata Glasc", gamesTogether = 20, winsTogether = 12, synergyWinRate = 0.60, avgGoldDiffAt15 = 400.0, synergyScore = 77.0, styleTags = listOf(BotDuoStyleTag.ALL_IN_KILL, BotDuoStyleTag.CROWD_CONTROL_CHAIN)),
                BotDuoSynergy("Caitlyn", "Lux", gamesTogether = 18, winsTogether = 10, synergyWinRate = 0.556, avgGoldDiffAt15 = 420.0, synergyScore = 72.0, styleTags = listOf(BotDuoStyleTag.POKE_SIEGE)),
                BotDuoSynergy("Draven", "Nautilus", gamesTogether = 16, winsTogether = 10, synergyWinRate = 0.625, avgGoldDiffAt15 = 510.0, synergyScore = 80.0, styleTags = listOf(BotDuoStyleTag.ALL_IN_KILL, BotDuoStyleTag.CROWD_CONTROL_CHAIN)),
                BotDuoSynergy("Samira", "Rell", gamesTogether = 15, winsTogether = 9, synergyWinRate = 0.60, avgGoldDiffAt15 = 320.0, synergyScore = 74.0, styleTags = listOf(BotDuoStyleTag.ALL_IN_KILL, BotDuoStyleTag.CROWD_CONTROL_CHAIN)),
                BotDuoSynergy("Varus", "Ashe", gamesTogether = 22, winsTogether = 13, synergyWinRate = 0.59, avgGoldDiffAt15 = 350.0, synergyScore = 73.0, styleTags = listOf(BotDuoStyleTag.POKE_SIEGE, BotDuoStyleTag.CROWD_CONTROL_CHAIN)),
                BotDuoSynergy("Kai'Sa", "Nautilus", gamesTogether = 28, winsTogether = 16, synergyWinRate = 0.571, avgGoldDiffAt15 = 200.0, synergyScore = 70.0, styleTags = listOf(BotDuoStyleTag.ALL_IN_KILL, BotDuoStyleTag.CROWD_CONTROL_CHAIN)),
                BotDuoSynergy("Zeri", "Lulu", gamesTogether = 24, winsTogether = 14, synergyWinRate = 0.583, avgGoldDiffAt15 = -50.0, synergyScore = 69.0, styleTags = listOf(BotDuoStyleTag.HYPER_CARRY_PEEL)),
                BotDuoSynergy("Jinx", "Thresh", gamesTogether = 20, winsTogether = 11, synergyWinRate = 0.55, avgGoldDiffAt15 = 80.0, synergyScore = 67.0, styleTags = listOf(BotDuoStyleTag.HYPER_CARRY_PEEL, BotDuoStyleTag.CROWD_CONTROL_CHAIN)),
                BotDuoSynergy("Sivir", "Yuumi", gamesTogether = 14, winsTogether = 8, synergyWinRate = 0.571, avgGoldDiffAt15 = -120.0, synergyScore = 65.0, styleTags = listOf(BotDuoStyleTag.HYPER_CARRY_PEEL)),
                BotDuoSynergy("Aphelios", "Thresh", gamesTogether = 18, winsTogether = 10, synergyWinRate = 0.556, avgGoldDiffAt15 = 110.0, synergyScore = 68.0, styleTags = listOf(BotDuoStyleTag.HYPER_CARRY_PEEL, BotDuoStyleTag.CROWD_CONTROL_CHAIN)),
                BotDuoSynergy("Lucian", "Milio", gamesTogether = 15, winsTogether = 9, synergyWinRate = 0.60, avgGoldDiffAt15 = 280.0, synergyScore = 73.0, styleTags = listOf(BotDuoStyleTag.POKE_SIEGE, BotDuoStyleTag.ALL_IN_KILL)),
            )

        val CLASSIC_DUO_MATCHUPS =
            listOf(
                BotDuoMatchup(
                    blueDuo = "Draven" to "Nautilus",
                    redDuo = "Varus" to "Nami",
                    gamesFaced = 10,
                    blueWins = 7,
                    blueWinRate = 0.70,
                    avgGoldDiffAt15 = 450.0,
                    counterScore = 78.0,
                ),
                BotDuoMatchup(
                    blueDuo = "Caitlyn" to "Lux",
                    redDuo = "Kai'Sa" to "Nautilus",
                    gamesFaced = 12,
                    blueWins = 8,
                    blueWinRate = 0.667,
                    avgGoldDiffAt15 = 390.0,
                    counterScore = 74.0,
                ),
                BotDuoMatchup(
                    blueDuo = "Lucian" to "Nami",
                    redDuo = "Zeri" to "Lulu",
                    gamesFaced = 15,
                    blueWins = 10,
                    blueWinRate = 0.667,
                    avgGoldDiffAt15 = 420.0,
                    counterScore = 75.0,
                ),
            )
    }
}

package com.loldraft.platform.debrief

import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.Side
import com.loldraft.models.AnalyticalDraftEvaluator
import com.loldraft.models.CompositionFlawDetector
import com.loldraft.models.CompositionRadarCalculator
import com.loldraft.models.CompositionRadarScore
import com.loldraft.models.DraftEvaluator
import com.loldraft.models.DraftIntentPredictor
import com.loldraft.models.DraftRecommender
import com.loldraft.models.RadarDimension
import com.loldraft.models.TimeCurve
import com.loldraft.models.TimeCurveCalculator
import com.loldraft.platform.debrief.models.AttributionCategory
import com.loldraft.platform.debrief.models.AttributionChartData
import com.loldraft.platform.debrief.models.AttributionResult
import com.loldraft.platform.debrief.models.DebriefGameRequest
import com.loldraft.platform.debrief.models.DebriefMatchRequest
import com.loldraft.platform.debrief.models.DebriefReport
import com.loldraft.platform.debrief.models.MatchDebriefReport
import com.loldraft.platform.debrief.models.RadarDimensionComparison
import com.loldraft.platform.debrief.models.TeamCoachDebriefSummary
import com.loldraft.platform.debrief.models.TimeCurveChartPoint
import com.loldraft.platform.debrief.models.TimelineChartPoint
import com.loldraft.platform.debrief.models.TurnDebriefRecord
import com.loldraft.platform.debrief.models.VisualChartData
import com.loldraft.platform.live.CoachPickEvaluator
import com.loldraft.platform.live.models.CoachGrade
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.round

class PostMatchDebriefEngine(
    val draftEvaluator: DraftEvaluator = AnalyticalDraftEvaluator(),
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val flawDetector: CompositionFlawDetector = CompositionFlawDetector(tagRegistry),
    val timeCurveCalculator: TimeCurveCalculator = TimeCurveCalculator(tagRegistry),
    val draftRecommender: DraftRecommender = DraftRecommender(evaluator = draftEvaluator, tagRegistry = tagRegistry),
    val intentPredictor: DraftIntentPredictor = DraftIntentPredictor(tagRegistry = tagRegistry),
    val coachPickEvaluator: CoachPickEvaluator =
        CoachPickEvaluator(
            draftEvaluator = draftEvaluator,
            draftRecommender = draftRecommender,
            flawDetector = flawDetector,
            intentPredictor = intentPredictor,
        ),
) {
    private val reports = ConcurrentHashMap<String, DebriefReport>()
    private val matchReports = ConcurrentHashMap<String, MatchDebriefReport>()

    fun generateGameDebrief(request: DebriefGameRequest): DebriefReport {
        val game = request.game
        val reportId = "debrief-${game.id}-${UUID.randomUUID().toString().take(8)}"

        // Initial turn 0 evaluation
        val state0 = DraftState.empty()
        val eval0 =
            draftEvaluator.evaluate(
                draftState = state0,
                patchMeta = request.patchMeta,
                blueTeamProfile = request.blueTeamProfile,
                redTeamProfile = request.redTeamProfile,
            )
        val initialBlueWinRate = roundToFourDecimals(eval0.blueWinRate)
        val initialRedWinRate = roundToFourDecimals(1.0 - initialBlueWinRate)

        val timelinePoints = mutableListOf<TimelineChartPoint>()
        timelinePoints.add(
            TimelineChartPoint(
                turnNumber = 0,
                blueWinRate = initialBlueWinRate,
                redWinRate = initialRedWinRate,
                championId = null,
                side = null,
                actionType = null,
                deltaWinRate = 0.0,
            ),
        )

        val turnRecords = mutableListOf<TurnDebriefRecord>()
        var currentState = state0

        for (turn in game.draftState.turns) {
            val stateBefore = currentState
            val stateAfter = stateBefore.applyTurn(turn)

            val feedback =
                coachPickEvaluator.evaluate(
                    turn = turn,
                    stateBefore = stateBefore,
                    stateAfter = stateAfter,
                    patchMeta = request.patchMeta,
                    blueTeamProfile = request.blueTeamProfile,
                    redTeamProfile = request.redTeamProfile,
                    playerStatsByRole =
                        if (turn.side == Side.BLUE) {
                            request.playerStatsByRoleBlue
                        } else {
                            request.playerStatsByRoleRed
                        },
                )

            val record =
                TurnDebriefRecord(
                    turnNumber = turn.turnNumber,
                    side = turn.side,
                    actionType = turn.actionType,
                    championId = turn.championId,
                    role = turn.role,
                    player = turn.player,
                    winRateBefore = feedback.winRateBefore,
                    winRateAfter = feedback.winRateAfter,
                    deltaWinRate = feedback.winRateDelta,
                    evalScoreBefore = feedback.evalScoreBefore,
                    evalScoreAfter = feedback.evalScoreAfter,
                    deltaEvalScore = feedback.evalScoreDelta,
                    grade = feedback.grade,
                    critique = feedback.critique,
                    flawsIntroduced = feedback.flawsIntroduced,
                    flawsResolved = feedback.flawsResolved,
                    alternativePicks = feedback.alternativePicks,
                )
            turnRecords.add(record)

            val stepBlueWR =
                if (turn.side == Side.BLUE) {
                    feedback.winRateAfter
                } else {
                    roundToFourDecimals(1.0 - feedback.winRateAfter)
                }
            val stepRedWR = roundToFourDecimals(1.0 - stepBlueWR)

            timelinePoints.add(
                TimelineChartPoint(
                    turnNumber = turn.turnNumber,
                    blueWinRate = stepBlueWR,
                    redWinRate = stepRedWR,
                    championId = turn.championId,
                    side = turn.side,
                    actionType = turn.actionType,
                    deltaWinRate = feedback.winRateDelta,
                ),
            )

            currentState = stateAfter
        }

        val finalEval =
            draftEvaluator.evaluate(
                draftState = currentState,
                patchMeta = request.patchMeta,
                blueTeamProfile = request.blueTeamProfile,
                redTeamProfile = request.redTeamProfile,
            )
        val finalBlueWinRate = roundToFourDecimals(finalEval.blueWinRate)
        val finalRedWinRate = roundToFourDecimals(1.0 - finalBlueWinRate)

        // Identify MVP and Blunder turns for both sides
        val blueTurns = turnRecords.filter { it.side == Side.BLUE }
        val redTurns = turnRecords.filter { it.side == Side.RED }

        val blueMaxDelta = blueTurns.maxOfOrNull { it.deltaWinRate }
        val blueMinDelta = blueTurns.minOfOrNull { it.deltaWinRate }
        val redMaxDelta = redTurns.maxOfOrNull { it.deltaWinRate }
        val redMinDelta = redTurns.minOfOrNull { it.deltaWinRate }

        val finalizedTurns =
            turnRecords.map { r ->
                when (r.side) {
                    Side.BLUE ->
                        r.copy(
                            isMvpTurn = blueMaxDelta != null && r.deltaWinRate == blueMaxDelta,
                            isBlunderTurn = blueMinDelta != null && r.deltaWinRate == blueMinDelta,
                        )
                    Side.RED ->
                        r.copy(
                            isMvpTurn = redMaxDelta != null && r.deltaWinRate == redMaxDelta,
                            isBlunderTurn = redMinDelta != null && r.deltaWinRate == redMinDelta,
                        )
                }
            }

        val finalizedBlueTurns = finalizedTurns.filter { it.side == Side.BLUE }
        val finalizedRedTurns = finalizedTurns.filter { it.side == Side.RED }

        val blueCoachSummary =
            buildCoachSummary(
                side = Side.BLUE,
                team = game.blueTeam,
                turns = finalizedBlueTurns,
                unresolvedFlaws = finalEval.flaws?.blueReport?.flaws ?: emptyList(),
            )
        val redCoachSummary =
            buildCoachSummary(
                side = Side.RED,
                team = game.redTeam,
                turns = finalizedRedTurns,
                unresolvedFlaws = finalEval.flaws?.redReport?.flaws ?: emptyList(),
            )

        val actualWinner = game.winner ?: if (finalBlueWinRate >= 0.50) Side.BLUE else Side.RED

        val attribution =
            determineAttribution(
                finalBlueWinRate = finalBlueWinRate,
                actualWinner = actualWinner,
                durationSeconds = game.durationSeconds,
                blueFlaws = blueCoachSummary.unresolvedFlaws,
                redFlaws = redCoachSummary.unresolvedFlaws,
            )

        val timeCurve =
            finalEval.timeCurve
                ?: timeCurveCalculator.calculate(
                    draftState = currentState,
                    features = finalEval.features,
                    baselineBlueWinRate = finalBlueWinRate,
                    patchMeta = request.patchMeta,
                    blueTeamProfile = request.blueTeamProfile,
                    redTeamProfile = request.redTeamProfile,
                )

        val radarComparison =
            finalEval.compositionRadar
                ?: CompositionRadarCalculator.calculate(
                    blueRadar = finalEval.features.blueRadar,
                    redRadar = finalEval.features.redRadar,
                    blueDamageProfile = finalEval.features.blueDamageProfile,
                    redDamageProfile = finalEval.features.redDamageProfile,
                )

        val visualCharts =
            buildVisualChartData(
                timelinePoints = timelinePoints,
                timeCurve = timeCurve,
                radarScore = radarComparison,
                attribution = attribution,
            )

        val report =
            DebriefReport(
                reportId = reportId,
                gameId = game.id,
                matchId = null,
                patch = game.patch,
                tournament = game.tournament,
                blueTeam = game.blueTeam,
                redTeam = game.redTeam,
                actualWinner = actualWinner,
                durationSeconds = game.durationSeconds,
                initialBlueWinRate = initialBlueWinRate,
                finalBlueWinRate = finalBlueWinRate,
                finalRedWinRate = finalRedWinRate,
                attribution = attribution,
                turns = finalizedTurns,
                blueCoachSummary = blueCoachSummary,
                redCoachSummary = redCoachSummary,
                radarComparison = radarComparison,
                timeCurve = timeCurve,
                charts = visualCharts,
            )

        reports[reportId] = report
        return report
    }

    fun generateMatchDebrief(request: DebriefMatchRequest): MatchDebriefReport {
        val match = request.match
        val gameReports =
            match.games.map { game ->
                generateGameDebrief(
                    DebriefGameRequest(
                        game = game,
                        patchMeta = request.patchMeta,
                        blueTeamProfile = request.blueTeamProfile,
                        redTeamProfile = request.redTeamProfile,
                    ),
                )
            }

        val totalGames = gameReports.size
        val blueWins = gameReports.count { it.actualWinner == Side.BLUE }
        val redWins = gameReports.count { it.actualWinner == Side.RED }

        val sideWinRateStats =
            mapOf(
                Side.BLUE to if (totalGames > 0) roundToTwoDecimals(blueWins.toDouble() / totalGames) else 0.50,
                Side.RED to if (totalGames > 0) roundToTwoDecimals(redWins.toDouble() / totalGames) else 0.50,
            )

        val allBans = gameReports.flatMap { r -> r.turns.filter { it.actionType == ActionType.BAN }.map { it.championId } }
        val frequentBans =
            allBans
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }

        val allPicks = gameReports.flatMap { r -> r.turns.filter { it.actionType == ActionType.PICK }.map { it.championId } }
        val frequentPicks =
            allPicks
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key }

        val blueSeriesScore =
            if (gameReports.isNotEmpty()) {
                roundToTwoDecimals(gameReports.map { it.blueCoachSummary.coachBpScore }.average())
            } else {
                70.0
            }
        val redSeriesScore =
            if (gameReports.isNotEmpty()) {
                roundToTwoDecimals(gameReports.map { it.redCoachSummary.coachBpScore }.average())
            } else {
                70.0
            }

        val matchReport =
            MatchDebriefReport(
                matchId = match.id,
                tournament = match.tournament,
                patch = match.patch,
                bestOf = match.bestOf,
                blueTeam = match.blueTeam,
                redTeam = match.redTeam,
                seriesWinnerTeamId = match.winnerTeamId,
                gamesPlayed = totalGames,
                gameReports = gameReports,
                overallAttributionSummary =
                    "Series completed in $totalGames games. Blue side won $blueWins games, Red side won $redWins games. " +
                        "Coaching ratings: ${match.blueTeam.name} $blueSeriesScore vs ${match.redTeam.name} $redSeriesScore.",
                sideWinRateStats = sideWinRateStats,
                frequentBans = frequentBans,
                frequentPicks = frequentPicks,
                blueSeriesCoachScore = blueSeriesScore,
                redSeriesCoachScore = redSeriesScore,
            )

        matchReports[match.id] = matchReport
        return matchReport
    }

    fun getReport(reportId: String): DebriefReport? = reports[reportId]

    fun storeReport(report: DebriefReport) {
        reports[report.reportId] = report
    }

    fun listReports(): List<DebriefReport> = reports.values.toList()

    fun getMatchReport(matchId: String): MatchDebriefReport? = matchReports[matchId]

    private fun buildCoachSummary(
        side: Side,
        team: com.loldraft.data.models.Team,
        turns: List<TurnDebriefRecord>,
        unresolvedFlaws: List<com.loldraft.models.CompositionFlaw>,
    ): TeamCoachDebriefSummary {
        val netDelta = roundToFourDecimals(turns.sumOf { it.deltaWinRate })
        val phase1Delta = roundToFourDecimals(turns.filter { it.turnNumber <= 12 }.sumOf { it.deltaWinRate })
        val phase2Delta = roundToFourDecimals(turns.filter { it.turnNumber > 12 }.sumOf { it.deltaWinRate })

        val optimalCount = turns.count { it.grade == CoachGrade.OPTIMAL_S || it.grade == CoachGrade.STRONG_A }
        val blundersCount = turns.count { it.grade == CoachGrade.BLUNDER_D }

        var rawScore = 70.0 + (netDelta * 100.0)
        for (turn in turns) {
            when (turn.grade) {
                CoachGrade.OPTIMAL_S -> rawScore += 3.0
                CoachGrade.STRONG_A -> rawScore += 1.5
                CoachGrade.ACCEPTABLE_B -> rawScore += 0.5
                CoachGrade.QUESTIONABLE_C -> rawScore -= 2.0
                CoachGrade.BLUNDER_D -> rawScore -= 5.0
            }
        }
        val coachScore = roundToTwoDecimals(rawScore.coerceIn(15.0, 99.0))

        val coachGrade =
            when {
                coachScore >= 88.0 -> CoachGrade.OPTIMAL_S
                coachScore >= 78.0 -> CoachGrade.STRONG_A
                coachScore >= 65.0 -> CoachGrade.ACCEPTABLE_B
                coachScore >= 50.0 -> CoachGrade.QUESTIONABLE_C
                else -> CoachGrade.BLUNDER_D
            }

        val mvpTurn = turns.filter { it.isMvpTurn }.maxByOrNull { it.deltaWinRate }
        val worstTurn = turns.filter { it.isBlunderTurn }.minByOrNull { it.deltaWinRate }

        return TeamCoachDebriefSummary(
            side = side,
            team = team,
            netDraftDeltaWinRate = netDelta,
            coachBpGrade = coachGrade,
            coachBpScore = coachScore,
            phase1DeltaWinRate = phase1Delta,
            phase2DeltaWinRate = phase2Delta,
            optimalPicksCount = optimalCount,
            blundersCount = blundersCount,
            mvpTurn = mvpTurn,
            worstTurn = worstTurn,
            unresolvedFlaws = unresolvedFlaws,
        )
    }

    private fun determineAttribution(
        finalBlueWinRate: Double,
        actualWinner: Side,
        durationSeconds: Int?,
        blueFlaws: List<com.loldraft.models.CompositionFlaw>,
        redFlaws: List<com.loldraft.models.CompositionFlaw>,
    ): AttributionResult {
        val advantageSide =
            when {
                finalBlueWinRate >= 0.52 -> Side.BLUE
                finalBlueWinRate <= 0.48 -> Side.RED
                else -> null
            }

        val factors = mutableListOf<String>()
        val winnerDraftWR = if (actualWinner == Side.BLUE) finalBlueWinRate else 1.0 - finalBlueWinRate
        val loserDraftWR = 1.0 - winnerDraftWR

        val category =
            when {
                advantageSide == null -> {
                    factors.add("Evenly matched draft ($finalBlueWinRate vs ${(1.0 - finalBlueWinRate)}).")
                    factors.add("Game was decided primarily by Summoner's Rift execution and macro.")
                    AttributionCategory.BALANCED_CONTEST
                }
                advantageSide == actualWinner -> {
                    if (loserDraftWR <= 0.40 || (advantageSide == Side.BLUE && redFlaws.isNotEmpty())) {
                        factors.add(
                            "Clear composition advantage for ${if (advantageSide == Side.BLUE) "Blue" else "Red"} " +
                                "(${(winnerDraftWR * 100).toInt()}% expected WR).",
                        )
                        if (redFlaws.isNotEmpty()) {
                            factors.add(
                                "Defeated team suffered from unaddressed draft flaws: ${redFlaws.joinToString { it.title }}.",
                            )
                        }
                        AttributionCategory.DRAFT_CARRIED
                    } else {
                        factors.add("Favorable composition matchup successfully converted into a win.")
                        AttributionCategory.DRAFT_CARRIED
                    }
                }
                else -> {
                    // advantageSide != actualWinner
                    val loserSide = advantageSide
                    if (durationSeconds != null && durationSeconds >= 2400) {
                        factors.add(
                            "Favored team (${if (loserSide == Side.BLUE) "Blue" else "Red"}) had composition edge but collapsed in extended game.",
                        )
                        factors.add("Tactical throws and missed power-spike execution led to defeat.")
                        AttributionCategory.EXECUTION_THROW
                    } else {
                        factors.add(
                            "Under-drafted side overcame statistical deficit (${(winnerDraftWR * 100).toInt()}% projected WR).",
                        )
                        factors.add("Superior individual player execution, clutch teamfighting, and late macro turnaround.")
                        AttributionCategory.EXECUTION_UPSET
                    }
                }
            }

        val advantageMagnitude = abs(finalBlueWinRate - 0.50) * 2.0 // 0.0 to 1.0

        val (draftPct, execPct) =
            when (category) {
                AttributionCategory.BALANCED_CONTEST -> {
                    Pair(0.15, 0.85)
                }
                AttributionCategory.DRAFT_CARRIED, AttributionCategory.COMPOSITION_GAP -> {
                    var dPct = 0.60 + (advantageMagnitude * 0.25)
                    if (durationSeconds != null && durationSeconds < 1600) {
                        dPct += 0.08 // Stomp reinforces draft superiority
                    } else if (durationSeconds != null && durationSeconds > 2400) {
                        dPct -= 0.05 // Long game allowed execution swings
                    }
                    val roundedDraft = roundToTwoDecimals(dPct.coerceIn(0.55, 0.85))
                    Pair(roundedDraft, roundToTwoDecimals(1.0 - roundedDraft))
                }
                AttributionCategory.EXECUTION_THROW -> {
                    var ePct = 0.72 + (advantageMagnitude * 0.15)
                    if (durationSeconds != null && durationSeconds > 2200) {
                        ePct += 0.05
                    }
                    val roundedExec = roundToTwoDecimals(ePct.coerceIn(0.68, 0.90))
                    Pair(roundToTwoDecimals(1.0 - roundedExec), roundedExec)
                }
                AttributionCategory.EXECUTION_UPSET -> {
                    var ePct = 0.72 + (advantageMagnitude * 0.15)
                    val roundedExec = roundToTwoDecimals(ePct.coerceIn(0.68, 0.90))
                    Pair(roundToTwoDecimals(1.0 - roundedExec), roundedExec)
                }
            }

        val title =
            when (category) {
                AttributionCategory.DRAFT_CARRIED -> "Draft Superiority Converted: Favorable Composition Secured Victory"
                AttributionCategory.COMPOSITION_GAP -> "Composition Deficit Defeat: Unresolved Flaws Exploited"
                AttributionCategory.EXECUTION_THROW -> "In-Game Collapse / Throw: Favorable Draft Conceded On-Rift"
                AttributionCategory.EXECUTION_UPSET -> "In-Game Execution Upset: Overcame Draft Disadvantage"
                AttributionCategory.BALANCED_CONTEST -> "Even Draft Contest: Outcome Decided by On-Rift Execution"
            }

        val explanation =
            buildString {
                append(title)
                append(". ")
                append(factors.joinToString(" "))
            }

        return AttributionResult(
            advantageSide = advantageSide,
            actualWinner = actualWinner,
            category = category,
            draftInfluencePct = draftPct,
            executionInfluencePct = execPct,
            title = title,
            explanation = explanation,
            keyContributingFactors = factors,
        )
    }

    private fun buildVisualChartData(
        timelinePoints: List<TimelineChartPoint>,
        timeCurve: TimeCurve,
        radarScore: CompositionRadarScore,
        attribution: AttributionResult,
    ): VisualChartData {
        val timeCurvePoints =
            timeCurve.points.map { pt ->
                TimeCurveChartPoint(
                    minute = pt.minute,
                    blueWinRate = pt.blueWinRate,
                    redWinRate = pt.redWinRate,
                )
            }

        val radarDimensions =
            listOf(
                RadarDimensionComparison(
                    dimension = "Laning Strength",
                    blueScore = radarScore.blueRadar.laning,
                    redScore = radarScore.redRadar.laning,
                    delta = radarScore.deltaRadar.laning,
                    advantage = radarScore.dimensionAdvantages[RadarDimension.LANING],
                ),
                RadarDimensionComparison(
                    dimension = "Engage & Pick",
                    blueScore = radarScore.blueRadar.engage,
                    redScore = radarScore.redRadar.engage,
                    delta = radarScore.deltaRadar.engage,
                    advantage = radarScore.dimensionAdvantages[RadarDimension.ENGAGE],
                ),
                RadarDimensionComparison(
                    dimension = "Waveclear & Siege",
                    blueScore = radarScore.blueRadar.waveclear,
                    redScore = radarScore.redRadar.waveclear,
                    delta = radarScore.deltaRadar.waveclear,
                    advantage = radarScore.dimensionAdvantages[RadarDimension.WAVECLEAR],
                ),
                RadarDimensionComparison(
                    dimension = "Damage Profile Balance",
                    blueScore = radarScore.blueRadar.damageBalance,
                    redScore = radarScore.redRadar.damageBalance,
                    delta = radarScore.deltaRadar.damageBalance,
                    advantage = radarScore.dimensionAdvantages[RadarDimension.DAMAGE_BALANCE],
                ),
                RadarDimensionComparison(
                    dimension = "Late Game Scaling",
                    blueScore = radarScore.blueRadar.lateScaling,
                    redScore = radarScore.redRadar.lateScaling,
                    delta = radarScore.deltaRadar.lateScaling,
                    advantage = radarScore.dimensionAdvantages[RadarDimension.LATE_SCALING],
                ),
            )

        return VisualChartData(
            timelinePoints = timelinePoints,
            timeCurvePoints = timeCurvePoints,
            radarComparison = radarDimensions,
            attributionBreakdown =
                AttributionChartData(
                    draftInfluencePct = attribution.draftInfluencePct,
                    executionInfluencePct = attribution.executionInfluencePct,
                ),
        )
    }

    private fun roundToFourDecimals(value: Double): Double = round(value * 10000.0) / 10000.0

    private fun roundToTwoDecimals(value: Double): Double = round(value * 100.0) / 100.0
}

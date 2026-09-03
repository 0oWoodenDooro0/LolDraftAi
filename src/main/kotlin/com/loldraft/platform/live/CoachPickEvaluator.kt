package com.loldraft.platform.live

import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.style.TeamTacticalProfile
import com.loldraft.models.AnalyticalDraftEvaluator
import com.loldraft.models.CompositionFlaw
import com.loldraft.models.CompositionFlawDetector
import com.loldraft.models.DraftEvaluator
import com.loldraft.models.DraftIntentPredictor
import com.loldraft.models.DraftRecommender
import com.loldraft.models.FlawSeverity
import com.loldraft.platform.live.models.CoachGrade
import com.loldraft.platform.live.models.CoachPickFeedback
import java.util.Locale
import kotlin.math.round

class CoachPickEvaluator(
    val draftEvaluator: DraftEvaluator = AnalyticalDraftEvaluator(),
    val draftRecommender: DraftRecommender = DraftRecommender(evaluator = draftEvaluator),
    val flawDetector: CompositionFlawDetector = CompositionFlawDetector(),
    val intentPredictor: DraftIntentPredictor = DraftIntentPredictor(),
) {
    fun evaluate(
        turn: DraftTurn,
        stateBefore: DraftState,
        stateAfter: DraftState,
        patchMeta: PatchMetaMatrix? = null,
        blueTeamProfile: TeamTacticalProfile? = null,
        redTeamProfile: TeamTacticalProfile? = null,
        playerStatsByRole: Map<Role, PlayerCareerStats>? = null,
    ): CoachPickFeedback {
        val evalBefore = draftEvaluator.evaluate(stateBefore, patchMeta, blueTeamProfile, redTeamProfile)
        val evalAfter = draftEvaluator.evaluate(stateAfter, patchMeta, blueTeamProfile, redTeamProfile)

        val winRateBefore = if (turn.side == Side.BLUE) evalBefore.blueWinRate else evalBefore.redWinRate
        val winRateAfter = if (turn.side == Side.BLUE) evalAfter.blueWinRate else evalAfter.redWinRate
        val winRateDelta = roundToFourDecimals(winRateAfter - winRateBefore)

        val evalScoreBefore = if (turn.side == Side.BLUE) evalBefore.evalScore else -evalBefore.evalScore
        val evalScoreAfter = if (turn.side == Side.BLUE) evalAfter.evalScore else -evalAfter.evalScore
        val evalScoreDelta = roundToTwoDecimals(evalScoreAfter - evalScoreBefore)

        val recReport =
            draftRecommender.recommend(
                draftState = stateBefore,
                targetSide = turn.side,
                patchMeta = patchMeta,
                blueTeamProfile = blueTeamProfile,
                redTeamProfile = redTeamProfile,
                playerStatsByRole = playerStatsByRole,
                targetRole = null,
                limit = 10,
            )
        val alternativePicks = recReport.recommendations

        val matchIndex = alternativePicks.indexOfFirst { it.championId.equals(turn.championId, ignoreCase = true) }
        val aiRank = if (matchIndex >= 0) matchIndex + 1 else null

        val flawsBeforeList =
            (if (turn.side == Side.BLUE) evalBefore.flaws?.blueReport?.flaws else evalBefore.flaws?.redReport?.flaws)
                ?: emptyList()
        val flawsAfterList =
            (if (turn.side == Side.BLUE) evalAfter.flaws?.blueReport?.flaws else evalAfter.flaws?.redReport?.flaws)
                ?: emptyList()

        val flawsIntroduced: List<CompositionFlaw> =
            flawsAfterList.filter { afterFlaw ->
                val beforeFlaw = flawsBeforeList.find { it.id == afterFlaw.id }
                beforeFlaw == null ||
                    (afterFlaw.severity == FlawSeverity.CRITICAL && beforeFlaw.severity != FlawSeverity.CRITICAL)
            }
        val flawsResolved: List<CompositionFlaw> =
            flawsBeforeList.filter { beforeFlaw ->
                val afterFlaw = flawsAfterList.find { it.id == beforeFlaw.id }
                afterFlaw == null ||
                    (beforeFlaw.severity == FlawSeverity.CRITICAL && afterFlaw.severity != FlawSeverity.CRITICAL)
            }

        val hasCriticalFlawIntroduced = flawsIntroduced.any { it.severity == FlawSeverity.CRITICAL }

        val grade =
            when (turn.actionType) {
                ActionType.PICK -> {
                    when {
                        hasCriticalFlawIntroduced || winRateDelta < -0.06 -> CoachGrade.BLUNDER_D
                        aiRank == 1 && winRateDelta >= -0.015 -> CoachGrade.OPTIMAL_S
                        aiRank in 1..2 && winRateDelta >= -0.025 -> CoachGrade.OPTIMAL_S
                        aiRank in 2..3 && winRateDelta >= -0.035 -> CoachGrade.STRONG_A
                        aiRank in 4..5 && winRateDelta >= -0.050 -> CoachGrade.ACCEPTABLE_B
                        flawsIntroduced.isNotEmpty() || winRateDelta < -0.030 || aiRank == null -> CoachGrade.QUESTIONABLE_C
                        winRateDelta >= -0.040 -> CoachGrade.ACCEPTABLE_B
                        else -> CoachGrade.QUESTIONABLE_C
                    }
                }
                ActionType.BAN -> {
                    when {
                        winRateDelta >= 0.0 || evalScoreDelta >= 0.0 -> CoachGrade.OPTIMAL_S
                        winRateDelta >= -0.015 -> CoachGrade.STRONG_A
                        winRateDelta >= -0.035 -> CoachGrade.ACCEPTABLE_B
                        else -> CoachGrade.QUESTIONABLE_C
                    }
                }
            }

        val critique =
            buildCritique(
                turn = turn,
                grade = grade,
                aiRank = aiRank,
                winRateDelta = winRateDelta,
                evalScoreDelta = evalScoreDelta,
                flawsIntroduced = flawsIntroduced,
                flawsResolved = flawsResolved,
                alternativePicks = alternativePicks,
            )

        return CoachPickFeedback(
            turnNumber = turn.turnNumber,
            side = turn.side,
            actionType = turn.actionType,
            lockedChampionId = turn.championId,
            role = turn.role,
            winRateBefore = winRateBefore,
            winRateAfter = winRateAfter,
            winRateDelta = winRateDelta,
            evalScoreBefore = evalScoreBefore,
            evalScoreAfter = evalScoreAfter,
            evalScoreDelta = evalScoreDelta,
            aiRank = aiRank,
            grade = grade,
            flawsIntroduced = flawsIntroduced,
            flawsResolved = flawsResolved,
            critique = critique,
            alternativePicks = alternativePicks,
        )
    }

    private fun buildCritique(
        turn: DraftTurn,
        grade: CoachGrade,
        aiRank: Int?,
        winRateDelta: Double,
        evalScoreDelta: Double,
        flawsIntroduced: List<CompositionFlaw>,
        flawsResolved: List<CompositionFlaw>,
        alternativePicks: List<com.loldraft.models.PickRecommendation>,
    ): String {
        val sidePrefix = if (turn.side == Side.BLUE) "Blue" else "Red"
        val pctString = String.format(Locale.US, "%+.1f%%", winRateDelta * 100.0)

        val header =
            when (grade) {
                CoachGrade.OPTIMAL_S -> "Optimal Pick: $sidePrefix locked AI #$aiRank priority ${turn.championId} ($pctString)."
                CoachGrade.STRONG_A -> "Strong Alternative: $sidePrefix locked ${turn.championId} (AI #$aiRank, $pctString)."
                CoachGrade.ACCEPTABLE_B -> "Acceptable Pick: $sidePrefix selected ${turn.championId} ($pctString)."
                CoachGrade.QUESTIONABLE_C -> "Questionable Selection: $sidePrefix selected off-priority ${turn.championId} ($pctString)."
                CoachGrade.BLUNDER_D ->
                    "Draft Blunder: $sidePrefix locked ${turn.championId} resulting in major win-rate loss ($pctString)."
            }

        val flawNotes = mutableListOf<String>()
        if (flawsIntroduced.isNotEmpty()) {
            val names = flawsIntroduced.joinToString(", ") { it.title }
            flawNotes.add("Introduced composition flaw: $names (structural vulnerability)")
        }
        if (flawsResolved.isNotEmpty()) {
            val names = flawsResolved.joinToString(", ") { it.title }
            flawNotes.add("Resolved prior composition flaw: $names")
        }

        val altNote =
            if (grade == CoachGrade.QUESTIONABLE_C || grade == CoachGrade.BLUNDER_D) {
                val topAlts = alternativePicks.take(2).joinToString(", ") { "${it.championId} (${it.recommendedRole})" }
                if (topAlts.isNotBlank()) " AI recommended: $topAlts instead." else ""
            } else {
                ""
            }

        return buildString {
            append(header)
            if (flawNotes.isNotEmpty()) {
                append(" ")
                append(flawNotes.joinToString(". "))
                append(".")
            }
            if (altNote.isNotBlank()) {
                append(altNote)
            }
        }
    }

    private fun roundToFourDecimals(value: Double): Double = round(value * 10000.0) / 10000.0

    private fun roundToTwoDecimals(value: Double): Double = round(value * 100.0) / 100.0
}

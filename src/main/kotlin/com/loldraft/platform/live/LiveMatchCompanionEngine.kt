package com.loldraft.platform.live

import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.player.PlayerIntelligenceService
import com.loldraft.models.AnalyticalDraftEvaluator
import com.loldraft.models.CompositionFlawDetector
import com.loldraft.models.DraftEvaluationResult
import com.loldraft.models.DraftEvaluator
import com.loldraft.models.DraftIntentPredictor
import com.loldraft.models.DraftRecommender
import com.loldraft.models.EvalBarCalculator
import com.loldraft.models.FiveDimensionRadarScores
import com.loldraft.models.FlexPickAnalyzer
import com.loldraft.models.TimeCurveCalculator
import com.loldraft.platform.live.models.CreateLiveSessionRequest
import com.loldraft.platform.live.models.LiveMatchSession
import com.loldraft.platform.live.models.LiveSessionStatus
import com.loldraft.platform.live.models.LiveTurnSnapshot
import com.loldraft.platform.live.models.LiveWsServerMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LiveMatchCompanionEngine(
    val draftEvaluator: DraftEvaluator = AnalyticalDraftEvaluator(),
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val flawDetector: CompositionFlawDetector = CompositionFlawDetector(tagRegistry),
    val timeCurveCalculator: TimeCurveCalculator = TimeCurveCalculator(tagRegistry),
    val draftRecommender: DraftRecommender = DraftRecommender(evaluator = draftEvaluator, tagRegistry = tagRegistry),
    val intentPredictor: DraftIntentPredictor = DraftIntentPredictor(tagRegistry = tagRegistry),
    val flexAnalyzer: FlexPickAnalyzer = FlexPickAnalyzer(tagRegistry = tagRegistry),
    val playerIntelligenceService: PlayerIntelligenceService = PlayerIntelligenceService(),
    val coachPickEvaluator: CoachPickEvaluator =
        CoachPickEvaluator(
            draftEvaluator = draftEvaluator,
            draftRecommender = draftRecommender,
            flawDetector = flawDetector,
            intentPredictor = intentPredictor,
        ),
) {
    private val sessions = ConcurrentHashMap<String, LiveMatchSession>()
    private val eventFlows = ConcurrentHashMap<String, MutableSharedFlow<LiveWsServerMessage>>()

    fun createSession(request: CreateLiveSessionRequest): LiveMatchSession {
        val sessionId = request.sessionId ?: UUID.randomUUID().toString()

        val initialFlow =
            MutableSharedFlow<LiveWsServerMessage>(
                replay = 1,
                extraBufferCapacity = 64,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )
        eventFlows[sessionId] = initialFlow

        val resolvedProfilesBlue =
            request.playerProfilesByRoleBlue
                ?: playerIntelligenceService
                    .getTeamPlayerProfiles(request.blueTeam.id)
                    .associateBy { it.role }
                    .ifEmpty {
                        playerIntelligenceService
                            .getTeamPlayerProfiles(request.blueTeam.name)
                            .associateBy { it.role }
                    }.takeIf { it.isNotEmpty() }

        val resolvedProfilesRed =
            request.playerProfilesByRoleRed
                ?: playerIntelligenceService
                    .getTeamPlayerProfiles(request.redTeam.id)
                    .associateBy { it.role }
                    .ifEmpty {
                        playerIntelligenceService
                            .getTeamPlayerProfiles(request.redTeam.name)
                            .associateBy { it.role }
                    }.takeIf { it.isNotEmpty() }

        val resolvedStatsBlue = request.playerStatsByRoleBlue ?: resolvedProfilesBlue?.mapValues { it.value.careerStats }
        val resolvedStatsRed = request.playerStatsByRoleRed ?: resolvedProfilesRed?.mapValues { it.value.careerStats }

        val enrichedRequest =
            request.copy(
                playerProfilesByRoleBlue = resolvedProfilesBlue,
                playerProfilesByRoleRed = resolvedProfilesRed,
                playerStatsByRoleBlue = resolvedStatsBlue,
                playerStatsByRoleRed = resolvedStatsRed,
            )

        // Generate Turn 0 initial snapshot
        val initialDraft = DraftState.empty()
        val initialSnapshot =
            buildSnapshot(
                sessionId = sessionId,
                turnNumber = 0,
                turn = null,
                draftState = initialDraft,
                request = enrichedRequest,
            )

        var currentSession =
            LiveMatchSession(
                sessionId = sessionId,
                blueTeam = enrichedRequest.blueTeam,
                redTeam = enrichedRequest.redTeam,
                patchMeta = enrichedRequest.patchMeta,
                blueTeamProfile = enrichedRequest.blueTeamProfile,
                redTeamProfile = enrichedRequest.redTeamProfile,
                playerStatsByRoleBlue = enrichedRequest.playerStatsByRoleBlue,
                playerStatsByRoleRed = enrichedRequest.playerStatsByRoleRed,
                playerProfilesByRoleBlue = enrichedRequest.playerProfilesByRoleBlue,
                playerProfilesByRoleRed = enrichedRequest.playerProfilesByRoleRed,
                currentState = initialDraft,
                history = listOf(initialSnapshot),
                status = LiveSessionStatus.IN_PROGRESS,
            )

        // If initial turns provided, execute them sequentially
        for (turn in request.initialTurns) {
            currentSession = applyTurnToSession(currentSession, turn.championId, turn.role, turn.player)
        }

        sessions[sessionId] = currentSession
        return currentSession
    }

    fun getSession(sessionId: String): LiveMatchSession? = sessions[sessionId]

    fun applyTurn(
        sessionId: String,
        championId: String,
        role: Role? = null,
        player: String? = null,
    ): LiveTurnSnapshot {
        val session =
            sessions[sessionId]
                ?: throw IllegalArgumentException("Session '$sessionId' does not exist")

        val updatedSession = applyTurnToSession(session, championId, role, player)
        sessions[sessionId] = updatedSession

        val latestSnapshot = updatedSession.history.last()
        val turn = latestSnapshot.turn ?: throw IllegalStateException("Turn missing in snapshot")

        eventFlows[sessionId]?.tryEmit(LiveWsServerMessage.TurnApplied(turn, latestSnapshot))
        return latestSnapshot
    }

    fun undoTurn(sessionId: String): LiveTurnSnapshot {
        val session =
            sessions[sessionId]
                ?: throw IllegalArgumentException("Session '$sessionId' does not exist")

        if (session.currentState.turns.isEmpty() || session.history.size <= 1) {
            throw IllegalStateException("No turns available to undo in session '$sessionId'")
        }

        val undoneTurn = session.currentState.turns.last()
        val newTurns = session.currentState.turns.dropLast(1)
        val newHistory = session.history.dropLast(1)
        val restoredState = DraftState.fromTurns(newTurns)
        val restoredSnapshot = newHistory.last()

        val updatedSession =
            session.copy(
                currentState = restoredState,
                history = newHistory,
                status = LiveSessionStatus.IN_PROGRESS,
                updatedAt = System.currentTimeMillis(),
            )
        sessions[sessionId] = updatedSession

        eventFlows[sessionId]?.tryEmit(LiveWsServerMessage.TurnUndone(undoneTurn.turnNumber, restoredSnapshot))
        return restoredSnapshot
    }

    fun resetSession(sessionId: String): LiveTurnSnapshot {
        val session =
            sessions[sessionId]
                ?: throw IllegalArgumentException("Session '$sessionId' does not exist")

        val snapshot0 = session.history.first()
        val resetSession =
            session.copy(
                currentState = DraftState.empty(),
                history = listOf(snapshot0),
                status = LiveSessionStatus.IN_PROGRESS,
                updatedAt = System.currentTimeMillis(),
            )
        sessions[sessionId] = resetSession

        eventFlows[sessionId]?.tryEmit(LiveWsServerMessage.SessionReset(snapshot0))
        return snapshot0
    }

    fun getSessionEventFlow(sessionId: String): SharedFlow<LiveWsServerMessage> =
        eventFlows
            .computeIfAbsent(sessionId) {
                MutableSharedFlow(replay = 1, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
            }.asSharedFlow()

    private fun applyTurnToSession(
        session: LiveMatchSession,
        championId: String,
        role: Role?,
        player: String?,
    ): LiveMatchSession {
        if (session.currentState.turns.size >= 20 || session.currentState.isComplete) {
            throw IllegalStateException("Draft is already complete (20 turns reached)")
        }

        val normalizedSlug = ChampionNormalizer.toSlug(championId)
        val isDuplicate = session.currentState.allSelectedChampions.any { ChampionNormalizer.toSlug(it) == normalizedSlug }
        if (isDuplicate) {
            throw IllegalArgumentException("Champion '$championId' has already been selected or banned in this draft")
        }

        val turnNumber = session.currentState.currentTurnNumber
        val turnSpec = DraftTurnSpec.forTurn(turnNumber)

        val resolvedRole =
            if (turnSpec.actionType == ActionType.PICK) {
                role ?: deduceRole(championId, turnSpec.side, session.currentState, session.patchMeta)
            } else {
                null
            }

        val draftTurn =
            DraftTurn(
                turnNumber = turnNumber,
                side = turnSpec.side,
                actionType = turnSpec.actionType,
                championId = championId,
                role = resolvedRole,
                player = player,
            )

        val stateBefore = session.currentState
        val stateAfter = stateBefore.applyTurn(draftTurn)

        val feedback =
            coachPickEvaluator.evaluate(
                turn = draftTurn,
                stateBefore = stateBefore,
                stateAfter = stateAfter,
                patchMeta = session.patchMeta,
                blueTeamProfile = session.blueTeamProfile,
                redTeamProfile = session.redTeamProfile,
                playerStatsByRole = if (turnSpec.side == Side.BLUE) session.playerStatsByRoleBlue else session.playerStatsByRoleRed,
            )

        val req =
            CreateLiveSessionRequest(
                sessionId = session.sessionId,
                blueTeam = session.blueTeam,
                redTeam = session.redTeam,
                patchMeta = session.patchMeta,
                blueTeamProfile = session.blueTeamProfile,
                redTeamProfile = session.redTeamProfile,
                playerStatsByRoleBlue = session.playerStatsByRoleBlue,
                playerStatsByRoleRed = session.playerStatsByRoleRed,
                playerProfilesByRoleBlue = session.playerProfilesByRoleBlue,
                playerProfilesByRoleRed = session.playerProfilesByRoleRed,
            )

        val snapshot =
            buildSnapshot(
                sessionId = session.sessionId,
                turnNumber = turnNumber,
                turn = draftTurn,
                draftState = stateAfter,
                request = req,
                coachFeedback = feedback,
            )

        return session.copy(
            currentState = stateAfter,
            history = session.history + snapshot,
            status = if (stateAfter.isComplete) LiveSessionStatus.COMPLETED else LiveSessionStatus.IN_PROGRESS,
            updatedAt = System.currentTimeMillis(),
        )
    }

    fun deduceRole(
        championId: String,
        side: Side,
        draftState: DraftState,
        patchMeta: PatchMetaMatrix? = null,
    ): Role {
        val teamPicks = if (side == Side.BLUE) draftState.bluePicks else draftState.redPicks
        val filledRoles = teamPicks.mapNotNull { it.role }.toSet()
        val vacantRoles = Role.entries.filterNot { it in filledRoles }
        if (vacantRoles.isEmpty()) return Role.MID
        if (vacantRoles.size == 1) return vacantRoles.first()

        val roleProbs =
            flexAnalyzer.getRoleProbabilities(
                championId = championId,
                patchMeta = patchMeta,
                teamExistingRoles = filledRoles,
            )

        return vacantRoles.maxByOrNull { roleProbs[it] ?: 0.0 } ?: vacantRoles.first()
    }

    private fun buildSnapshot(
        sessionId: String,
        turnNumber: Int,
        turn: DraftTurn?,
        draftState: DraftState,
        request: CreateLiveSessionRequest,
        coachFeedback: com.loldraft.platform.live.models.CoachPickFeedback? = null,
    ): LiveTurnSnapshot {
        val eval: DraftEvaluationResult =
            draftEvaluator.evaluate(
                draftState = draftState,
                patchMeta = request.patchMeta,
                blueTeamProfile = request.blueTeamProfile,
                redTeamProfile = request.redTeamProfile,
            )

        val evalBar = eval.evalBar ?: EvalBarCalculator.calculate(eval.blueWinRate)
        val timeCurve =
            eval.timeCurve
                ?: timeCurveCalculator.calculate(
                    draftState = draftState,
                    features = eval.features,
                    baselineBlueWinRate = eval.blueWinRate,
                    patchMeta = request.patchMeta,
                    blueTeamProfile = request.blueTeamProfile,
                    redTeamProfile = request.redTeamProfile,
                )

        val blueRadar = eval.compositionRadar?.blueRadar ?: FiveDimensionRadarScores(5.0, 5.0, 5.0, 5.0, 5.0)
        val redRadar = eval.compositionRadar?.redRadar ?: FiveDimensionRadarScores(5.0, 5.0, 5.0, 5.0, 5.0)
        val radarDelta = eval.compositionRadar?.deltaRadar ?: FiveDimensionRadarScores(0.0, 0.0, 0.0, 0.0, 0.0)

        val blueFlaws = eval.flaws?.blueReport?.flaws ?: emptyList()
        val redFlaws = eval.flaws?.redReport?.flaws ?: emptyList()

        val isComplete = draftState.isComplete || turnNumber >= 20
        val nextTurnSpec = if (isComplete) null else DraftTurnSpec.forTurn(draftState.currentTurnNumber)

        val nextSide = nextTurnSpec?.side
        val nextPlayerStats = if (nextSide == Side.BLUE) request.playerStatsByRoleBlue else request.playerStatsByRoleRed
        val nextPlayerProfiles = if (nextSide == Side.BLUE) request.playerProfilesByRoleBlue else request.playerProfilesByRoleRed
        val nextTeamProfile = if (nextSide == Side.BLUE) request.blueTeamProfile else request.redTeamProfile

        val aiRecommendations =
            if (nextTurnSpec != null) {
                draftRecommender
                    .recommend(
                        draftState = draftState,
                        targetSide = nextTurnSpec.side,
                        patchMeta = request.patchMeta,
                        blueTeamProfile = request.blueTeamProfile,
                        redTeamProfile = request.redTeamProfile,
                        playerStatsByRole = nextPlayerStats,
                        targetRole = null,
                        limit = 5,
                    ).recommendations
            } else {
                emptyList()
            }

        val aiIntentPredictions =
            if (nextTurnSpec != null) {
                intentPredictor
                    .predictNextAction(
                        draftState = draftState,
                        patchMeta = request.patchMeta,
                        teamProfile = nextTeamProfile,
                        playerStatsByRole = nextPlayerStats,
                        playerProfilesByRole = nextPlayerProfiles,
                        topN = 5,
                    ).predictions
            } else {
                emptyList()
            }

        return LiveTurnSnapshot(
            turnNumber = turnNumber,
            turn = turn,
            draftState = draftState,
            evalBar = evalBar,
            timeCurve = timeCurve,
            blueRadar = blueRadar,
            redRadar = redRadar,
            radarDelta = radarDelta,
            blueFlaws = blueFlaws,
            redFlaws = redFlaws,
            coachPickFeedback = coachFeedback,
            nextTurnSpec = nextTurnSpec,
            aiRecommendations = aiRecommendations,
            aiIntentPredictions = aiIntentPredictions,
        )
    }
}

package com.loldraft.platform.sandbox

import com.loldraft.data.meta.ChampionProfile
import com.loldraft.data.meta.ChampionTag
import com.loldraft.data.meta.ChampionTagRegistry
import com.loldraft.data.meta.PatchMetaMatrix
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.normalization.ChampionNormalizer
import com.loldraft.data.player.PlayerCareerStats
import com.loldraft.data.player.PlayerIntelligenceDossier
import com.loldraft.data.player.SpikeAlertSeverity
import com.loldraft.data.style.AggressionLevel
import com.loldraft.data.style.GamePace
import com.loldraft.data.style.TacticalTag
import com.loldraft.data.style.TeamTacticalProfile
import com.loldraft.data.validation.DraftValidator
import com.loldraft.models.AnalyticalDraftEvaluator
import com.loldraft.models.CompositionFlawDetector
import com.loldraft.models.DraftEvaluator
import com.loldraft.models.DraftIntentPredictor
import com.loldraft.models.DraftPolicyEngine
import com.loldraft.models.EvalBarCalculator
import com.loldraft.models.FlexPickAnalyzer
import com.loldraft.platform.sandbox.models.BranchComparativeDelta
import com.loldraft.platform.sandbox.models.DraftPivotPoint
import com.loldraft.platform.sandbox.models.DraftScenario
import com.loldraft.platform.sandbox.models.DraftTreeNode
import com.loldraft.platform.sandbox.models.MatchupSandboxRequest
import com.loldraft.platform.sandbox.models.MatchupSandboxResponse
import com.loldraft.platform.sandbox.models.PivotType
import com.loldraft.platform.sandbox.models.ScenarioPreset
import com.loldraft.platform.sandbox.models.TurnTrajectoryPoint
import com.loldraft.platform.sandbox.models.WhatIfBranchRequest
import com.loldraft.platform.sandbox.models.WhatIfBranchResult
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

class PreMatchSandboxEngine(
    val evaluator: DraftEvaluator = AnalyticalDraftEvaluator(),
    val tagRegistry: ChampionTagRegistry = ChampionTagRegistry.createDefault(),
    val validator: DraftValidator = DraftValidator(),
    val flexAnalyzer: FlexPickAnalyzer = FlexPickAnalyzer(tagRegistry),
    val flawDetector: CompositionFlawDetector = CompositionFlawDetector(tagRegistry),
    val intentPredictor: DraftIntentPredictor = DraftIntentPredictor(tagRegistry, flexAnalyzer, flawDetector),
    val policyEngine: DraftPolicyEngine =
        DraftPolicyEngine(
            evaluator = evaluator,
            tagRegistry = tagRegistry,
            flexAnalyzer = flexAnalyzer,
            flawDetector = flawDetector,
            intentPredictor = intentPredictor,
        ),
) : AutoCloseable {
    fun generateScenarios(request: MatchupSandboxRequest): MatchupSandboxResponse {
        val scenarios =
            listOf(
                simulateScenario(
                    preset = ScenarioPreset.META_OPTIMAL,
                    title = "標準版本爭奪 (Standard Meta Contest)",
                    description = "雙方優先爭奪版本 T0/T1 強勢英雄與標準體系，實施對稱防守型 Ban 位。",
                    likelihood = 0.45,
                    request = request,
                ),
                simulateScenario(
                    preset = ScenarioPreset.TARGETED_COUNTER,
                    title = "針對性封鎖與絕活反制 (Targeted Ban & Comfort Counter)",
                    description = "深度封鎖對手選手生涯絕活與近期天梯練角突增英雄，並針對先選位進行強勢反制與搖擺。",
                    likelihood = 0.35,
                    request = request,
                ),
                simulateScenario(
                    preset = ScenarioPreset.STYLE_CLASH,
                    title = "戰隊戰術風格碰撞 (Tactical Style Clash)",
                    description = "雙方依照戰隊戰術傾向（前期進攻搶線 vs 後期運營團戰）展開鮮明的戰略對局。",
                    likelihood = 0.20,
                    request = request,
                ),
            )

        val rootTree = buildDraftTree(scenarios)
        val matchupSummary =
            "${request.blueTeam.code} vs ${request.redTeam.code} Pre-Match Sandbox Simulation"

        return MatchupSandboxResponse(
            matchupSummary = matchupSummary,
            blueTeam = request.blueTeam,
            redTeam = request.redTeam,
            scenarios = scenarios,
            rootDraftTree = rootTree,
        )
    }

    private fun simulateScenario(
        preset: ScenarioPreset,
        title: String,
        description: String,
        likelihood: Double,
        request: MatchupSandboxRequest,
    ): DraftScenario {
        var state =
            if (request.initialTurns.isNotEmpty()) {
                DraftState.fromTurns(request.initialTurns)
            } else {
                DraftState.empty()
            }

        val trajectories = mutableListOf<TurnTrajectoryPoint>()

        // Add trajectory points for already completed initial turns if any
        if (request.initialTurns.isNotEmpty()) {
            var replayState = DraftState.empty()
            for (turn in request.initialTurns) {
                replayState = replayState.applyTurn(turn)
                val eval =
                    evaluator.evaluate(
                        replayState,
                        request.patchMeta,
                        request.blueTeamProfile,
                        request.redTeamProfile,
                    )
                val evalBar = eval.evalBar ?: EvalBarCalculator.calculate(eval.blueWinRate)
                trajectories.add(
                    TurnTrajectoryPoint(
                        turnNumber = turn.turnNumber,
                        turnSpec = DraftTurnSpec.forTurn(turn.turnNumber),
                        championId = turn.championId,
                        role = turn.role,
                        rationale = "Initial pre-set draft turn",
                        evalBarScore = evalBar,
                        blueWinRate = eval.blueWinRate,
                    ),
                )
            }
        }

        val startTurn = state.currentTurnNumber
        for (turnNumber in startTurn..20) {
            val turnSpec = DraftTurnSpec.forTurn(turnNumber)
            val actingSide = turnSpec.side
            val isBan = turnSpec.actionType == ActionType.BAN

            val actingTeamProfile = if (actingSide == Side.BLUE) request.blueTeamProfile else request.redTeamProfile
            val opponentTeamProfile = if (actingSide == Side.BLUE) request.redTeamProfile else request.blueTeamProfile
            val actingPlayerStats = if (actingSide == Side.BLUE) request.bluePlayerStats else request.redPlayerStats
            val opponentPlayerStats = if (actingSide == Side.BLUE) request.redPlayerStats else request.bluePlayerStats
            val actingDossiers = if (actingSide == Side.BLUE) request.blueSoloQDossiers else request.redSoloQDossiers
            val opponentDossiers = if (actingSide == Side.BLUE) request.redSoloQDossiers else request.blueSoloQDossiers

            val (championId, assignedRole, rationale) =
                selectChampionForTurn(
                    turnSpec = turnSpec,
                    draftState = state,
                    preset = preset,
                    patchMeta = request.patchMeta,
                    actingTeamProfile = actingTeamProfile,
                    opponentTeamProfile = opponentTeamProfile,
                    actingPlayerStats = actingPlayerStats,
                    opponentPlayerStats = opponentPlayerStats,
                    actingDossiers = actingDossiers,
                    opponentDossiers = opponentDossiers,
                )

            val turn =
                DraftTurn(
                    turnNumber = turnNumber,
                    side = actingSide,
                    actionType = turnSpec.actionType,
                    championId = championId,
                    role = assignedRole,
                )

            val validation = validator.validateTurn(turn, state.turns)
            if (!validation.isValid) {
                throw IllegalStateException("Generated invalid turn $turnNumber: ${validation.errors}")
            }

            state = state.applyTurn(turn)

            val eval =
                evaluator.evaluate(
                    state,
                    request.patchMeta,
                    request.blueTeamProfile,
                    request.redTeamProfile,
                )
            val evalBar = eval.evalBar ?: EvalBarCalculator.calculate(eval.blueWinRate)

            trajectories.add(
                TurnTrajectoryPoint(
                    turnNumber = turnNumber,
                    turnSpec = turnSpec,
                    championId = championId,
                    role = assignedRole,
                    rationale = rationale,
                    evalBarScore = evalBar,
                    blueWinRate = eval.blueWinRate,
                ),
            )
        }

        val finalEvaluation =
            evaluator.evaluate(
                state,
                request.patchMeta,
                request.blueTeamProfile,
                request.redTeamProfile,
            )

        val pivotPoints = detectPivotPoints(trajectories, state)
        val scenarioId = "scen-${preset.name.lowercase()}"

        return DraftScenario(
            scenarioId = scenarioId,
            preset = preset,
            title = title,
            description = description,
            likelihood = likelihood,
            draftState = state,
            turnTrajectories = trajectories,
            evaluation = finalEvaluation,
            pivotPoints = pivotPoints,
        )
    }

    private data class SelectedTurnAction(
        val championId: String,
        val role: Role?,
        val rationale: String,
    )

    private fun selectChampionForTurn(
        turnSpec: DraftTurnSpec,
        draftState: DraftState,
        preset: ScenarioPreset,
        patchMeta: PatchMetaMatrix?,
        actingTeamProfile: TeamTacticalProfile?,
        opponentTeamProfile: TeamTacticalProfile?,
        actingPlayerStats: Map<Role, PlayerCareerStats>?,
        opponentPlayerStats: Map<Role, PlayerCareerStats>?,
        actingDossiers: List<PlayerIntelligenceDossier>?,
        opponentDossiers: List<PlayerIntelligenceDossier>?,
    ): SelectedTurnAction {
        val unavailableSlugs = draftState.allSelectedChampions.map { ChampionNormalizer.toSlug(it) }.toSet()
        val actingPicks = if (turnSpec.side == Side.BLUE) draftState.bluePicks else draftState.redPicks
        val filledRoles = actingPicks.mapNotNull { it.role }.toSet()
        val vacantRoles = Role.entries.filterNot { it in filledRoles }.toSet()

        val isBan = turnSpec.actionType == ActionType.BAN

        when (preset) {
            ScenarioPreset.TARGETED_COUNTER -> {
                if (isBan) {
                    // Check opponent SoloQ spike alerts
                    val spikeTargets =
                        opponentDossiers
                            ?.flatMap { it.activeSpikeAlerts }
                            ?.filter { it.severity == SpikeAlertSeverity.HIGH || it.severity == SpikeAlertSeverity.MEDIUM }
                            ?.map { it.championId }
                            ?.filter { ChampionNormalizer.toSlug(it) !in unavailableSlugs }
                            .orEmpty()

                    if (spikeTargets.isNotEmpty()) {
                        val targetChamp = resolveDisplayName(spikeTargets.first())
                        return SelectedTurnAction(
                            championId = targetChamp,
                            role = null,
                            rationale = "Targeted Ban: Neutralizing opponent high-frequency SoloQ practice spike",
                        )
                    }

                    // Check opponent signature picks
                    val signatureTargets =
                        opponentPlayerStats
                            ?.values
                            ?.flatMap { it.signaturePicks }
                            ?.sortedByDescending { it.signatureScore }
                            ?.map { it.championId }
                            ?.filter { ChampionNormalizer.toSlug(it) !in unavailableSlugs }
                            .orEmpty()

                    if (signatureTargets.isNotEmpty()) {
                        val targetChamp = resolveDisplayName(signatureTargets.first())
                        return SelectedTurnAction(
                            championId = targetChamp,
                            role = null,
                            rationale = "Targeted Ban: Denying opponent player signature comfort pick",
                        )
                    }
                } else {
                    // In PICK: Check if acting players have signature or spike picks that fit vacant roles
                    val playerSpikes =
                        actingDossiers
                            ?.flatMap { it.activeSpikeAlerts }
                            ?.map { it.championId }
                            ?.filter { ChampionNormalizer.toSlug(it) !in unavailableSlugs }
                            .orEmpty()

                    for (spikeChamp in playerSpikes) {
                        val profile = tagRegistry.getProfile(spikeChamp)
                        if (profile != null) {
                            val matchingRole = findFittingRole(profile, vacantRoles)
                            if (matchingRole != null) {
                                return SelectedTurnAction(
                                    championId = profile.displayName,
                                    role = matchingRole,
                                    rationale = "Pocket Pick: Leveraging confident SoloQ practice spike in $matchingRole lane",
                                )
                            }
                        }
                    }

                    // Check signature picks
                    val signatures =
                        actingPlayerStats
                            ?.values
                            ?.flatMap { it.signaturePicks }
                            ?.sortedByDescending { it.signatureScore }
                            ?.map { it.championId }
                            ?.filter { ChampionNormalizer.toSlug(it) !in unavailableSlugs }
                            .orEmpty()

                    for (sigChamp in signatures) {
                        val profile = tagRegistry.getProfile(sigChamp)
                        if (profile != null) {
                            val matchingRole = findFittingRole(profile, vacantRoles)
                            if (matchingRole != null) {
                                return SelectedTurnAction(
                                    championId = profile.displayName,
                                    role = matchingRole,
                                    rationale = "Comfort Pick: Locking player signature champion with proven mastery",
                                )
                            }
                        }
                    }
                }
            }

            ScenarioPreset.STYLE_CLASH -> {
                if (!isBan) {
                    val isEarlyAggressive =
                        actingTeamProfile?.tacticalStyleMetrics?.aggression == AggressionLevel.VERY_AGGRESSIVE ||
                            actingTeamProfile?.tags?.contains(TacticalTag.EARLY_AGGRESSOR) == true
                    val isLateScaling =
                        actingTeamProfile?.tacticalStyleMetrics?.pace == GamePace.SLOW_CONTROLLED ||
                            actingTeamProfile?.tags?.contains(TacticalTag.LATE_GAME_MACRO) == true

                    val candidates =
                        tagRegistry.getAllProfiles().filter {
                            ChampionNormalizer.toSlug(it.championId) !in unavailableSlugs
                        }

                    if (isEarlyAggressive) {
                        val earlyBully =
                            candidates
                                .filter {
                                    (it.tags.contains(ChampionTag.EARLY_BULLY) || it.tags.contains(ChampionTag.HARD_ENGAGE)) &&
                                        findFittingRole(it, vacantRoles) != null
                                }.maxByOrNull { it.radar.laningStrength }

                        if (earlyBully != null) {
                            val role = findFittingRole(earlyBully, vacantRoles) ?: vacantRoles.firstOrNull()
                            return SelectedTurnAction(
                                championId = earlyBully.displayName,
                                role = role,
                                rationale = "Style Priority: Early bully aggression and lane priority alignment",
                            )
                        }
                    } else if (isLateScaling) {
                        val lateScaler =
                            candidates
                                .filter {
                                    (it.tags.contains(ChampionTag.HYPER_CARRY) || it.tags.contains(ChampionTag.VANGUARD_TANK)) &&
                                        findFittingRole(it, vacantRoles) != null
                                }.maxByOrNull { it.radar.lateGameScaling }

                        if (lateScaler != null) {
                            val role = findFittingRole(lateScaler, vacantRoles) ?: vacantRoles.firstOrNull()
                            return SelectedTurnAction(
                                championId = lateScaler.displayName,
                                role = role,
                                rationale = "Style Priority: Hyper-scaling macro teamfight profile",
                            )
                        }
                    }
                }
            }

            ScenarioPreset.META_OPTIMAL -> {
                // Uses standard intent prediction below
            }
        }

        // Standard / Meta-Optimal fallback using Intent Predictor
        val intentResult =
            intentPredictor.predictNextAction(
                draftState = draftState,
                patchMeta = patchMeta,
                teamProfile = actingTeamProfile,
                playerStatsByRole = actingPlayerStats,
                topN = 10,
            )

        val isPhase1Ban = isBan && turnSpec.phase == com.loldraft.data.models.DraftPhase.BAN_PHASE_1
        val reservedB1Picks = setOf("ashe", "varus")

        for (pred in intentResult.predictions) {
            val slug = ChampionNormalizer.toSlug(pred.championId)
            if (slug in unavailableSlugs) continue
            if (isPhase1Ban && slug in reservedB1Picks) continue

            val profile = tagRegistry.getProfile(pred.championId)
            if (isBan) {
                val displayName = profile?.displayName ?: pred.championId
                val rationale = if (pred.rationale.isNotBlank()) pred.rationale else "Meta Denial: High priority ban"
                return SelectedTurnAction(championId = displayName, role = null, rationale = rationale)
            } else {
                val candidateRole = pred.predictedRole ?: profile?.let { findFittingRole(it, vacantRoles) }
                val resolvedRole =
                    if (candidateRole != null && candidateRole in vacantRoles) {
                        candidateRole
                    } else {
                        profile?.let { findFittingRole(it, vacantRoles) } ?: vacantRoles.firstOrNull()
                    }

                if (resolvedRole != null) {
                    val displayName = profile?.displayName ?: pred.championId
                    val rationale = if (pred.rationale.isNotBlank()) pred.rationale else "Meta Pick: High draft value"
                    return SelectedTurnAction(championId = displayName, role = resolvedRole, rationale = rationale)
                }
            }
        }

        // Failsafe selection if predictions are exhausted
        val availableProfiles =
            tagRegistry.getAllProfiles().filter {
                val s = ChampionNormalizer.toSlug(it.championId)
                s !in unavailableSlugs && (!isPhase1Ban || s !in reservedB1Picks)
            }

        if (isBan) {
            val fallbackBan =
                availableProfiles.firstOrNull()
                    ?: throw IllegalStateException("Exhausted champions pool for ban at turn ${turnSpec.turnNumber}")
            return SelectedTurnAction(
                championId = fallbackBan.displayName,
                role = null,
                rationale = "General defensive ban",
            )
        } else {
            for (role in vacantRoles) {
                val fitting = availableProfiles.firstOrNull { it.primaryRole == role || it.secondaryRoles.contains(role) }
                if (fitting != null) {
                    return SelectedTurnAction(
                        championId = fitting.displayName,
                        role = role,
                        rationale = "Filling position $role with compatible champion",
                    )
                }
            }

            val fallbackPick =
                availableProfiles.firstOrNull()
                    ?: throw IllegalStateException("Exhausted champions pool for pick at turn ${turnSpec.turnNumber}")
            val fallbackRole = vacantRoles.firstOrNull()
            return SelectedTurnAction(
                championId = fallbackPick.displayName,
                role = fallbackRole,
                rationale = "Failsafe selection for $fallbackRole",
            )
        }
    }

    private fun findFittingRole(
        profile: ChampionProfile,
        vacantRoles: Set<Role>,
    ): Role? {
        if (profile.primaryRole in vacantRoles) return profile.primaryRole
        for (sec in profile.secondaryRoles) {
            if (sec in vacantRoles) return sec
        }
        return null
    }

    private fun resolveDisplayName(nameOrSlug: String): String {
        val profile = tagRegistry.getProfile(nameOrSlug)
        return profile?.displayName ?: nameOrSlug
    }

    fun simulateWhatIfBranch(
        baseDraft: DraftState,
        request: WhatIfBranchRequest,
        context: MatchupSandboxRequest,
    ): WhatIfBranchResult {
        if (request.branchTurnNumber !in 1..20) {
            throw IllegalArgumentException("branchTurnNumber must be in 1..20, was ${request.branchTurnNumber}")
        }

        val originalTurn =
            baseDraft.turns.find { it.turnNumber == request.branchTurnNumber }
                ?: throw IllegalArgumentException("Turn ${request.branchTurnNumber} not found in base draft")

        val spec = DraftTurnSpec.forTurn(request.branchTurnNumber)
        val turnsBefore = baseDraft.turns.filter { it.turnNumber < request.branchTurnNumber }
        val championsBefore = turnsBefore.map { ChampionNormalizer.toSlug(it.championId) }.toSet()

        val newSlug = ChampionNormalizer.toSlug(request.newChampionId)
        if (newSlug in championsBefore) {
            throw IllegalArgumentException("Champion '${request.newChampionId}' has already been selected in a previous turn")
        }

        val profile = tagRegistry.getProfile(request.newChampionId)
        val displayName = profile?.displayName ?: request.newChampionId

        val isPick = spec.actionType == ActionType.PICK
        val resolvedRole: Role? =
            if (isPick) {
                if (request.newRole != null) {
                    request.newRole
                } else {
                    val actingPicksBefore =
                        turnsBefore
                            .filter { it.side == spec.side && it.actionType == ActionType.PICK }
                    val filledRoles = actingPicksBefore.mapNotNull { it.role }.toSet()
                    val vacant = Role.entries.filterNot { it in filledRoles }.toSet()
                    profile?.let { findFittingRole(it, vacant) } ?: vacant.firstOrNull()
                }
            } else {
                null
            }

        val replacementTurn =
            DraftTurn(
                turnNumber = request.branchTurnNumber,
                side = spec.side,
                actionType = spec.actionType,
                championId = displayName,
                role = resolvedRole,
            )

        var branchedState = DraftState.fromTurns(turnsBefore).applyTurn(replacementTurn)
        val trajectories = mutableListOf<TurnTrajectoryPoint>()

        // Replay turnsBefore trajectories
        var replay = DraftState.empty()
        for (turn in turnsBefore) {
            replay = replay.applyTurn(turn)
            val eval = evaluator.evaluate(replay, context.patchMeta, context.blueTeamProfile, context.redTeamProfile)
            val evalBar = eval.evalBar ?: EvalBarCalculator.calculate(eval.blueWinRate)
            trajectories.add(
                TurnTrajectoryPoint(
                    turnNumber = turn.turnNumber,
                    turnSpec = DraftTurnSpec.forTurn(turn.turnNumber),
                    championId = turn.championId,
                    role = turn.role,
                    rationale = "Original branch path",
                    evalBarScore = evalBar,
                    blueWinRate = eval.blueWinRate,
                ),
            )
        }

        // Add replacement turn trajectory
        val branchEval =
            evaluator.evaluate(
                branchedState,
                context.patchMeta,
                context.blueTeamProfile,
                context.redTeamProfile,
            )
        val branchEvalBar = branchEval.evalBar ?: EvalBarCalculator.calculate(branchEval.blueWinRate)
        trajectories.add(
            TurnTrajectoryPoint(
                turnNumber = request.branchTurnNumber,
                turnSpec = spec,
                championId = displayName,
                role = resolvedRole,
                rationale = request.rationale ?: "What-If Coach Override",
                evalBarScore = branchEvalBar,
                blueWinRate = branchEval.blueWinRate,
                isPivotPoint = true,
            ),
        )

        // Re-simulate subsequent turns from branchTurnNumber + 1 to 20
        for (turnNumber in (request.branchTurnNumber + 1)..20) {
            val turnSpec = DraftTurnSpec.forTurn(turnNumber)
            val actingSide = turnSpec.side
            val actingTeamProfile = if (actingSide == Side.BLUE) context.blueTeamProfile else context.redTeamProfile
            val opponentTeamProfile = if (actingSide == Side.BLUE) context.redTeamProfile else context.blueTeamProfile
            val actingPlayerStats = if (actingSide == Side.BLUE) context.bluePlayerStats else context.redPlayerStats
            val opponentPlayerStats = if (actingSide == Side.BLUE) context.redPlayerStats else context.bluePlayerStats
            val actingDossiers = if (actingSide == Side.BLUE) context.blueSoloQDossiers else context.redSoloQDossiers
            val opponentDossiers = if (actingSide == Side.BLUE) context.redSoloQDossiers else context.blueSoloQDossiers

            val (championId, assignedRole, rationale) =
                selectChampionForTurn(
                    turnSpec = turnSpec,
                    draftState = branchedState,
                    preset = request.scenarioPreset,
                    patchMeta = context.patchMeta,
                    actingTeamProfile = actingTeamProfile,
                    opponentTeamProfile = opponentTeamProfile,
                    actingPlayerStats = actingPlayerStats,
                    opponentPlayerStats = opponentPlayerStats,
                    actingDossiers = actingDossiers,
                    opponentDossiers = opponentDossiers,
                )

            val turn =
                DraftTurn(
                    turnNumber = turnNumber,
                    side = actingSide,
                    actionType = turnSpec.actionType,
                    championId = championId,
                    role = assignedRole,
                )

            branchedState = branchedState.applyTurn(turn)
            val eval =
                evaluator.evaluate(
                    branchedState,
                    context.patchMeta,
                    context.blueTeamProfile,
                    context.redTeamProfile,
                )
            val evalBar = eval.evalBar ?: EvalBarCalculator.calculate(eval.blueWinRate)

            trajectories.add(
                TurnTrajectoryPoint(
                    turnNumber = turnNumber,
                    turnSpec = turnSpec,
                    championId = championId,
                    role = assignedRole,
                    rationale = rationale,
                    evalBarScore = evalBar,
                    blueWinRate = eval.blueWinRate,
                ),
            )
        }

        val originalEval =
            evaluator.evaluate(
                baseDraft,
                context.patchMeta,
                context.blueTeamProfile,
                context.redTeamProfile,
            )
        val newEval =
            evaluator.evaluate(
                branchedState,
                context.patchMeta,
                context.blueTeamProfile,
                context.redTeamProfile,
            )

        val pivotPoints = detectPivotPoints(trajectories, branchedState)

        val newScenario =
            DraftScenario(
                scenarioId = "branch-turn-${request.branchTurnNumber}-${displayName.lowercase()}",
                preset = request.scenarioPreset,
                title = "What-If 分叉: 第 ${request.branchTurnNumber} 手更換為 $displayName",
                description = "將第 ${request.branchTurnNumber} 手原始選角 (${originalTurn.championId}) 替換為 $displayName，AI 動態推演後續對抗局面。",
                likelihood = 1.0,
                draftState = branchedState,
                turnTrajectories = trajectories,
                evaluation = newEval,
                pivotPoints = pivotPoints,
            )

        val winRateChange = round4(newEval.blueWinRate - originalEval.blueWinRate)
        val evalScoreChange = round2(newEval.evalScore - originalEval.evalScore)

        val origBlueFlaws =
            originalEval.flaws
                ?.blueReport
                ?.flaws
                ?.map { it.id }
                ?.toSet()
                .orEmpty()
        val newBlueFlaws =
            newEval.flaws
                ?.blueReport
                ?.flaws
                ?.map { it.id }
                ?.toSet()
                .orEmpty()
        val origRedFlaws =
            originalEval.flaws
                ?.redReport
                ?.flaws
                ?.map { it.id }
                ?.toSet()
                .orEmpty()
        val newRedFlaws =
            newEval.flaws
                ?.redReport
                ?.flaws
                ?.map { it.id }
                ?.toSet()
                .orEmpty()

        val flawsAddedBlue = (newBlueFlaws - origBlueFlaws).toList()
        val flawsResolvedBlue = (origBlueFlaws - newBlueFlaws).toList()
        val flawsAddedRed = (newRedFlaws - origRedFlaws).toList()
        val flawsResolvedRed = (origRedFlaws - newRedFlaws).toList()

        val sign = if (winRateChange >= 0.0) "+" else ""
        val favoredSide =
            if (winRateChange > 0.0) {
                "藍方獲益"
            } else if (winRateChange < 0.0) {
                "紅方獲益"
            } else {
                "情勢持平"
            }
        val strategicSummary = "替換選角至 $displayName 使藍方勝率變動 $sign${String.format(Locale.US, "%.1f", winRateChange * 100.0)}% ($favoredSide)。"

        val comparativeDelta =
            BranchComparativeDelta(
                blueWinRateChange = winRateChange,
                evalScoreChange = evalScoreChange,
                flawsAddedBlue = flawsAddedBlue,
                flawsResolvedBlue = flawsResolvedBlue,
                flawsAddedRed = flawsAddedRed,
                flawsResolvedRed = flawsResolvedRed,
                strategicSummary = strategicSummary,
            )

        return WhatIfBranchResult(
            branchId = "whatif-${System.currentTimeMillis()}",
            branchTurnNumber = request.branchTurnNumber,
            originalTurn = originalTurn,
            replacementTurn = replacementTurn,
            newScenario = newScenario,
            comparativeDelta = comparativeDelta,
        )
    }

    fun detectPivotPoints(
        trajectories: List<TurnTrajectoryPoint>,
        draftState: DraftState,
    ): List<DraftPivotPoint> {
        val pivots = mutableListOf<DraftPivotPoint>()

        var previousEval = 0.0
        var maxDeltaTurn: TurnTrajectoryPoint? = null
        var maxDelta = 0.0

        for (point in trajectories) {
            val currentEval = point.evalBarScore.score
            val delta = currentEval - previousEval
            previousEval = currentEval

            if (abs(delta) > abs(maxDelta)) {
                maxDelta = delta
                maxDeltaTurn = point
            }

            if (abs(delta) >= 0.25) {
                val turn = draftState.turns.find { it.turnNumber == point.turnNumber }
                val pivotType =
                    when {
                        abs(delta) >= 0.6 -> PivotType.MOMENTUM_SWING
                        point.turnSpec.actionType == ActionType.BAN -> PivotType.HIGH_PRIORITY_DENIAL
                        point.turnSpec.side == Side.BLUE && delta > 0.35 -> PivotType.CRITICAL_COUNTER
                        point.turnSpec.side == Side.RED && delta < -0.35 -> PivotType.CRITICAL_COUNTER
                        point.turnSpec.side == Side.BLUE && delta < -0.35 -> PivotType.COMPOSITION_BLUNDER
                        point.turnSpec.side == Side.RED && delta > 0.35 -> PivotType.COMPOSITION_BLUNDER
                        else -> PivotType.FLEX_LOCK
                    }

                val sign = if (delta >= 0.0) "+" else ""
                val desc = "手次 ${point.turnNumber} [${point.turnSpec.side}] ${point.championId}: 陣容評估值變動 $sign${String.format(
                    Locale.US,
                    "%.1f",
                    delta,
                )}，觸發 ${pivotType.name} 關鍵博弈手次。"

                pivots.add(
                    DraftPivotPoint(
                        turnNumber = point.turnNumber,
                        side = point.turnSpec.side,
                        actionType = point.turnSpec.actionType,
                        championId = point.championId,
                        role = point.role,
                        evalDelta = round2(delta),
                        impactDescription = desc,
                        pivotType = pivotType,
                    ),
                )
            }
        }

        // Failsafe: if no turns crossed 0.25, add the largest swing turn
        if (pivots.isEmpty() && maxDeltaTurn != null) {
            pivots.add(
                DraftPivotPoint(
                    turnNumber = maxDeltaTurn.turnNumber,
                    side = maxDeltaTurn.turnSpec.side,
                    actionType = maxDeltaTurn.turnSpec.actionType,
                    championId = maxDeltaTurn.championId,
                    role = maxDeltaTurn.role,
                    evalDelta = round2(maxDelta),
                    impactDescription = "手次 ${maxDeltaTurn.turnNumber} 為全場最大局勢變動點 (${String.format(Locale.US, "%.1f", maxDelta)})",
                    pivotType = PivotType.MOMENTUM_SWING,
                ),
            )
        }

        return pivots
    }

    fun buildDraftTree(scenarios: List<DraftScenario>): DraftTreeNode {
        val rootState = DraftState.empty()
        val rootEval = EvalBarCalculator.calculate(0.50)

        // Build root
        val root =
            DraftTreeNode(
                nodeId = "root",
                parentNodeId = null,
                turnNumber = 0,
                turn = null,
                draftState = rootState,
                evalBarScore = rootEval,
                blueWinRate = 0.50,
                children = emptyList(),
                isBranchPoint = false,
                branchRationale = "Draft Start",
            )

        // Helper recursive builder
        fun buildSubtree(
            parent: DraftTreeNode,
            remainingTrajectories: List<Pair<DraftScenario, List<TurnTrajectoryPoint>>>,
        ): DraftTreeNode {
            if (remainingTrajectories.isEmpty()) return parent

            // Group by the next turn action (turnNumber, championId)
            val groups =
                remainingTrajectories.groupBy { (_, trajs) ->
                    trajs.firstOrNull()?.let { "${it.turnNumber}:${it.championId}" }
                }

            val childNodes = mutableListOf<DraftTreeNode>()
            val isFork = groups.size > 1

            for ((key, group) in groups) {
                if (key == null) continue
                val firstPoint = group.first().second.first()
                val (scen, _) = group.first()
                val turn = scen.draftState.turns.find { it.turnNumber == firstPoint.turnNumber }

                val partialState =
                    DraftState.fromTurns(
                        scen.draftState.turns.filter { it.turnNumber <= firstPoint.turnNumber },
                    )

                val node =
                    DraftTreeNode(
                        nodeId = "node-${parent.nodeId}-${firstPoint.turnNumber}-${firstPoint.championId.lowercase()}",
                        parentNodeId = parent.nodeId,
                        turnNumber = firstPoint.turnNumber,
                        turn = turn,
                        draftState = partialState,
                        evalBarScore = firstPoint.evalBarScore,
                        blueWinRate = firstPoint.blueWinRate,
                        children = emptyList(),
                        isBranchPoint = isFork,
                        branchRationale = if (isFork) "Divergence in ${group.map { it.first.preset }.distinct()}" else null,
                    )

                val nextLevel = group.map { (s, trajs) -> Pair(s, trajs.drop(1)) }
                val populatedNode = buildSubtree(node, nextLevel)
                childNodes.add(populatedNode)
            }

            return parent.copy(children = childNodes)
        }

        val initialPairs = scenarios.map { Pair(it, it.turnTrajectories) }
        return buildSubtree(root, initialPairs)
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0

    private fun round4(value: Double): Double = round(value * 10000.0) / 10000.0

    override fun close() {
        policyEngine.close()
    }
}

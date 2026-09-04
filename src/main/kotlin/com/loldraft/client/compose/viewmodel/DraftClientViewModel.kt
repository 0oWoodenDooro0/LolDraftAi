package com.loldraft.client.compose.viewmodel

import com.loldraft.client.compose.state.BoardSlot
import com.loldraft.client.compose.state.DraftClientState
import com.loldraft.client.compose.state.EvalBarState
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftState
import com.loldraft.data.models.DraftTurn
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.data.player.PlayerIntelligenceService
import com.loldraft.data.player.PlayerRosterIntelligence
import com.loldraft.models.AnalyticalDraftEvaluator
import com.loldraft.models.CompositionFlawDetector
import com.loldraft.models.DraftEvaluator
import com.loldraft.models.DraftIntentPredictor
import com.loldraft.models.DraftRecommender
import com.loldraft.models.EvalBarCalculator
import com.loldraft.server.ProMatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class DraftClientViewModel(
    private val repository: ProMatchRepository = ProMatchRepository(),
    private val playerIntelService: PlayerIntelligenceService = PlayerIntelligenceService(),
    private val intentPredictor: DraftIntentPredictor = DraftIntentPredictor(),
    private val recommender: DraftRecommender = DraftRecommender(),
    private val evaluator: DraftEvaluator = AnalyticalDraftEvaluator(),
    private val flawDetector: CompositionFlawDetector = CompositionFlawDetector(),
) {
    private val appliedDraftTurns = mutableListOf<DraftTurn>()
    private val _uiState = MutableStateFlow(DraftClientState())
    val uiState: StateFlow<DraftClientState> = _uiState.asStateFlow()

    init {
        repository.initialize()
        val patches = repository.getPatches().ifEmpty { listOf("16.17") }
        val defaultPatch = repository.getDefaultPatch()
        val teams = repository.getTeams()

        val blue = teams.find { it.name.contains("T1", ignoreCase = true) } ?: teams.firstOrNull()
        val red =
            teams.find { it.name.contains("Gen.G", ignoreCase = true) || it.name.contains("GEN", ignoreCase = true) }
                ?: teams.getOrNull(1) ?: blue

        val allChamps = repository.getChampions()

        val initialSlots =
            (1..20).map { turnNum ->
                val spec = DraftTurnSpec.forTurn(turnNum, Side.BLUE)
                BoardSlot(
                    turnNumber = turnNum,
                    side = spec.side,
                    actionType = spec.actionType,
                    isCurrentTurn = turnNum == 1,
                )
            }

        val blueRoster = if (blue != null) computeRosterIntelligence(blue.id) else emptyMap()
        val redRoster = if (red != null) computeRosterIntelligence(red.id) else emptyMap()

        _uiState.value =
            DraftClientState(
                selectedPatch = defaultPatch,
                availablePatches = patches,
                allTeams = teams,
                blueTeam = blue,
                redTeam = red,
                blueRosterIntelligence = blueRoster,
                redRosterIntelligence = redRoster,
                currentTurnNumber = 1,
                firstPickSide = Side.BLUE,
                currentTurnSpec = DraftTurnSpec.forTurn(1, Side.BLUE),
                boardSlots = initialSlots,
                allChampions = allChamps,
                filteredChampions = allChamps,
                evalBar = EvalBarState(),
            )

        recalculateDraftCalculations()
    }

    fun setFirstPickSide(side: Side) {
        val current = _uiState.value
        if (current.firstPickSide == side) return
        val updatedSlots = current.boardSlots.map { slot ->
            val spec = DraftTurnSpec.forTurn(slot.turnNumber, side)
            slot.copy(
                side = spec.side,
                actionType = spec.actionType,
            )
        }
        _uiState.value = current.copy(
            firstPickSide = side,
            currentTurnSpec = DraftTurnSpec.forTurn(current.currentTurnNumber, side),
            boardSlots = updatedSlots,
        )
        recalculateDraftCalculations()
    }

    fun swapTeams() {
        val current = _uiState.value
        val oldBlue = current.blueTeam
        val oldRed = current.redTeam
        val oldBlueRoster = current.blueRosterIntelligence
        val oldRedRoster = current.redRosterIntelligence
        _uiState.value = current.copy(
            blueTeam = oldRed,
            redTeam = oldBlue,
            blueRosterIntelligence = oldRedRoster,
            redRosterIntelligence = oldBlueRoster,
        )
        recalculateDraftCalculations()
    }

    fun selectPatch(patch: String) {
        val current = _uiState.value
        val teams = repository.getTeams()
        val blue = teams.find { it.id == current.blueTeam?.id } ?: teams.firstOrNull()
        val red = teams.find { it.id == current.redTeam?.id } ?: teams.getOrNull(1) ?: blue
        _uiState.value =
            current.copy(
                selectedPatch = patch,
                allTeams = teams,
                blueTeam = blue,
                redTeam = red,
            )
        refreshRostersAndRecalculate()
    }

    fun selectLeague(league: String?) {
        // No-op or kept for backwards compatibility
    }


    fun selectBlueTeam(teamId: String) {
        val current = _uiState.value
        val team = current.allTeams.find { it.id == teamId }
            ?: repository.getTeams().find { it.id == teamId }
            ?: return
        val roster = computeRosterIntelligence(team.id)
        _uiState.value =
            current.copy(
                blueTeam = team,
                blueRosterIntelligence = roster,
            )
        recalculateDraftCalculations()
    }

    fun selectRedTeam(teamId: String) {
        val current = _uiState.value
        val team = current.allTeams.find { it.id == teamId }
            ?: repository.getTeams().find { it.id == teamId }
            ?: return
        val roster = computeRosterIntelligence(team.id)
        _uiState.value =
            current.copy(
                redTeam = team,
                redRosterIntelligence = roster,
            )
        recalculateDraftCalculations()
    }

    fun setSearchQuery(query: String) {
        val current = _uiState.value
        val filtered = filterChampions(current.allChampions, query, null)
        _uiState.value =
            current.copy(
                searchQuery = query,
                selectedRoleFilter = null,
                filteredChampions = filtered,
            )
    }

    fun setRoleFilter(role: Role?) {
        val current = _uiState.value
        val filtered = filterChampions(current.allChampions, current.searchQuery, role)
        _uiState.value =
            current.copy(
                selectedRoleFilter = role,
                filteredChampions = filtered,
            )
    }

    fun selectChampion(championId: String?) {
        _uiState.value = _uiState.value.copy(selectedChampionId = championId)
    }

    fun lockInChampion(championId: String) {
        val current = _uiState.value
        val turnNum = current.currentTurnNumber
        if (turnNum > 20) return

        if (current.bannedChampionIds.contains(championId) || current.pickedChampionIds.contains(championId)) {
            return
        }

        val spec = DraftTurnSpec.forTurn(turnNum, current.firstPickSide)
        val champEntry = current.allChampions.find { it.id.equals(championId, ignoreCase = true) }
        val champName = champEntry?.name ?: championId

        // Assign role and player for picks
        var assignedRole: Role? = null
        var assignedPlayer: String? = null

        if (spec.actionType == ActionType.PICK) {
            val rosterIntel = if (spec.side == Side.BLUE) current.blueRosterIntelligence else current.redRosterIntelligence
            val lockedRoles =
                if (spec.side == Side.BLUE) {
                    current.boardSlots
                        .filter {
                            it.side == Side.BLUE && it.actionType == ActionType.PICK && it.championId != null
                        }.mapNotNull { it.role }
                        .toSet()
                } else {
                    current.boardSlots
                        .filter {
                            it.side == Side.RED && it.actionType == ActionType.PICK && it.championId != null
                        }.mapNotNull { it.role }
                        .toSet()
                }
            val vacantRoles = Role.entries.filterNot { it in lockedRoles }
            assignedRole =
                if (champEntry?.primaryRole != null && champEntry.primaryRole in vacantRoles) {
                    champEntry.primaryRole
                } else {
                    vacantRoles.firstOrNull() ?: Role.MID
                }
            assignedPlayer = rosterIntel[assignedRole]?.playerId
        }

        val turn =
            DraftTurn(
                turnNumber = turnNum,
                side = spec.side,
                actionType = spec.actionType,
                championId = championId,
                role = assignedRole,
                player = assignedPlayer,
            )
        appliedDraftTurns.add(turn)

        val nextTurnNum = turnNum + 1
        val isComplete = nextTurnNum > 20

        val updatedBanned = if (spec.actionType == ActionType.BAN) current.bannedChampionIds + championId else current.bannedChampionIds
        val updatedPicked = if (spec.actionType == ActionType.PICK) current.pickedChampionIds + championId else current.pickedChampionIds

        val updatedSlots =
            current.boardSlots.map { slot ->
                if (slot.turnNumber == turnNum) {
                    slot.copy(
                        championId = championId,
                        championName = champName,
                        role = assignedRole,
                        playerName = assignedPlayer,
                        isCurrentTurn = false,
                    )
                } else if (slot.turnNumber == nextTurnNum) {
                    slot.copy(isCurrentTurn = true)
                } else {
                    slot.copy(isCurrentTurn = false)
                }
            }

        _uiState.value =
            current.copy(
                currentTurnNumber = nextTurnNum.coerceAtMost(20),
                currentTurnSpec = if (nextTurnNum <= 20) DraftTurnSpec.forTurn(nextTurnNum, current.firstPickSide) else DraftTurnSpec.forTurn(20, current.firstPickSide),
                boardSlots = updatedSlots,
                bannedChampionIds = updatedBanned,
                pickedChampionIds = updatedPicked,
                selectedChampionId = null,
                isDraftComplete = isComplete,
            )

        recalculateDraftCalculations()
    }

    fun undoLastTurn() {
        if (appliedDraftTurns.isEmpty()) return
        val undoneTurn = appliedDraftTurns.removeAt(appliedDraftTurns.size - 1)
        val targetTurnNum = undoneTurn.turnNumber

        val current = _uiState.value
        val updatedBanned =
            if (undoneTurn.actionType ==
                ActionType.BAN
            ) {
                current.bannedChampionIds - undoneTurn.championId
            } else {
                current.bannedChampionIds
            }
        val updatedPicked =
            if (undoneTurn.actionType ==
                ActionType.PICK
            ) {
                current.pickedChampionIds - undoneTurn.championId
            } else {
                current.pickedChampionIds
            }

        val updatedSlots =
            current.boardSlots.map { slot ->
                if (slot.turnNumber == targetTurnNum) {
                    slot.copy(
                        championId = null,
                        championName = null,
                        role = null,
                        playerName = null,
                        isCurrentTurn = true,
                    )
                } else if (slot.turnNumber > targetTurnNum) {
                    slot.copy(isCurrentTurn = false)
                } else {
                    slot.copy(isCurrentTurn = false)
                }
            }

        _uiState.value =
            current.copy(
                currentTurnNumber = targetTurnNum,
                currentTurnSpec = DraftTurnSpec.forTurn(targetTurnNum, current.firstPickSide),
                boardSlots = updatedSlots,
                bannedChampionIds = updatedBanned,
                pickedChampionIds = updatedPicked,
                isDraftComplete = false,
            )

        recalculateDraftCalculations()
    }

    fun resetDraft() {
        appliedDraftTurns.clear()
        val current = _uiState.value
        val resetSlots =
            (1..20).map { turnNum ->
                val spec = DraftTurnSpec.forTurn(turnNum, current.firstPickSide)
                BoardSlot(
                    turnNumber = turnNum,
                    side = spec.side,
                    actionType = spec.actionType,
                    isCurrentTurn = turnNum == 1,
                )
            }

        _uiState.value =
            current.copy(
                currentTurnNumber = 1,
                currentTurnSpec = DraftTurnSpec.forTurn(1, current.firstPickSide),
                boardSlots = resetSlots,
                bannedChampionIds = emptySet(),
                pickedChampionIds = emptySet(),
                selectedChampionId = null,
                isDraftComplete = false,
                evalBar = EvalBarState(),
            )

        recalculateDraftCalculations()
    }

    private fun refreshRostersAndRecalculate() {
        val current = _uiState.value
        val blueRoster = if (current.blueTeam != null) computeRosterIntelligence(current.blueTeam.id) else emptyMap()
        val redRoster = if (current.redTeam != null) computeRosterIntelligence(current.redTeam.id) else emptyMap()
        _uiState.value =
            current.copy(
                blueRosterIntelligence = blueRoster,
                redRosterIntelligence = redRoster,
            )
        recalculateDraftCalculations()
    }

    private fun recalculateDraftCalculations() {
        val current = _uiState.value
        val draftState = DraftState.fromTurns(appliedDraftTurns)
        val patchMeta = repository.getPatchMeta(current.selectedPatch)

        // 1. Eval Bar
        val evalResult = evaluator.evaluate(draftState, patchMeta = patchMeta)
        val blueWr = evalResult.blueWinRate
        val redWr = 1.0 - blueWr
        val evalScore = EvalBarCalculator.calculate(blueWr).score
        val advSide =
            when {
                evalScore > 0.15 -> Side.BLUE
                evalScore < -0.15 -> Side.RED
                else -> null
            }
        val phaseDesc =
            when (advSide) {
                Side.BLUE -> String.format(Locale.US, "Blue Advantage (+%.2f)", evalScore)
                Side.RED -> String.format(Locale.US, "Red Advantage (%.2f)", evalScore)
                null -> "Even Matchup (0.00)"
            }

        // 2. Intent Predictions & Recommendations for current action
        val actingSide = current.currentTurnSpec.side
        val isBan = current.currentTurnSpec.actionType == ActionType.BAN
        val opponentTeam = if (actingSide == Side.BLUE) current.redTeam else current.blueTeam

        val actingRoster = if (actingSide == Side.BLUE) current.blueRosterIntelligence else current.redRosterIntelligence
        val oppRoster = if (actingSide == Side.BLUE) current.redRosterIntelligence else current.blueRosterIntelligence

        val actingProfiles = actingRoster.mapValues {
            it.value.dossier?.let { d -> com.loldraft.data.player.ProPlayerDetailedProfile.fromDossier(it.key, d) } ?: fallbackProfile(it.value)
        }
        val oppProfiles = oppRoster.mapValues {
            it.value.dossier?.let { d -> com.loldraft.data.player.ProPlayerDetailedProfile.fromDossier(it.key, d) } ?: fallbackProfile(it.value)
        }

        val opponentBans = if (opponentTeam != null) {
            repository.getOpponentBansAgainstTeam(opponentTeam.id)
        } else {
            emptyList()
        }

        val predictions =
            if (isBan) {
                intentPredictor.predictNextAction(
                    draftState = draftState,
                    patchMeta = patchMeta,
                    opponentPlayerProfilesByRole = oppProfiles,
                    opponentBansAgainstTargetTeam = opponentBans,
                    targetTeamName = opponentTeam?.name,
                    firstPickSide = current.firstPickSide,
                    topN = 3,
                ).predictions
            } else {
                intentPredictor.predictNextAction(
                    draftState = draftState,
                    patchMeta = patchMeta,
                    playerProfilesByRole = actingProfiles,
                    firstPickSide = current.firstPickSide,
                    topN = 3,
                ).predictions
            }

        // 3. Recommendations: Best Bans during Ban phase, Best Picks during Pick phase
        val recommendations =
            if (isBan) {
                recommender.recommendBestBans(
                    draftState = draftState,
                    targetSide = actingSide,
                    patchMeta = patchMeta,
                    opponentBansAgainstTargetTeam = opponentBans,
                    opponentPlayerProfilesByRole = oppProfiles,
                    opponentTeamName = opponentTeam?.name,
                    limit = 3,
                )
            } else {
                recommender.recommendBestPicks(
                    draftState = draftState,
                    targetSide = actingSide,
                    patchMeta = patchMeta,
                    limit = 3,
                )
            }

        // 4. Flaw warnings
        val allFlaws = flawDetector.analyzeDraft(draftState).allFlaws

        _uiState.value =
            _uiState.value.copy(
                evalBar =
                    EvalBarState(
                        blueWinRate = blueWr,
                        redWinRate = redWr,
                        evalScore = evalScore,
                        advantageSide = advSide,
                        phaseDescription = phaseDesc,
                    ),
                intentPredictions = predictions,
                counterRecommendations = recommendations,
                compositionFlaws = allFlaws,
            )
    }


    private fun filterChampions(
        all: List<com.loldraft.platform.pro.api.ProChampionEntry>,
        query: String,
        role: Role?,
    ): List<com.loldraft.platform.pro.api.ProChampionEntry> {
        val q = query.trim().lowercase()
        return all.filter { champ ->
            val matchesQuery = q.isEmpty() || champ.name.lowercase().contains(q) || champ.id.lowercase().contains(q)
            val matchesRole = role == null || champ.primaryRole == role
            matchesQuery && matchesRole
        }
    }

    private fun computeRosterIntelligence(teamId: String): Map<Role, PlayerRosterIntelligence> {
        val games = repository.getAllLoadedGames()
        val rosterIntel =
            playerIntelService.getTeamRosterIntelligence(
                teamId = teamId,
                proGames = games,
                soloQGames = emptyList(),
            )
        if (rosterIntel.isNotEmpty()) return rosterIntel

        val rosterEntries = repository.getTeamRoster(teamId)
        return rosterEntries.associate { entry ->
            val sigs =
                entry.topChampions.map { champ ->
                    com.loldraft.data.player.SignaturePick(
                        championId = champ,
                        gamesPlayed = entry.gamesPlayed,
                        wins = (entry.gamesPlayed * entry.winRate).toInt(),
                        winRate = entry.winRate,
                        pickRate = 0.3,
                        signatureScore = 70.0,
                        tier = com.loldraft.data.player.SignatureTier.SIGNATURE,
                        role = entry.role,
                    )
                }
            entry.role to
                PlayerRosterIntelligence(
                    role = entry.role,
                    playerId = entry.playerName,
                    signaturePicks = sigs,
                )
        }
    }

    private fun fallbackProfile(intel: PlayerRosterIntelligence): com.loldraft.data.player.ProPlayerDetailedProfile =
        com.loldraft.data.player.ProPlayerDetailedProfile.fromDossier(intel.role, fallbackDossier(intel))

    private fun fallbackDossier(intel: PlayerRosterIntelligence): com.loldraft.data.player.PlayerIntelligenceDossier {
        val careerStats =
            com.loldraft.data.player.PlayerCareerStats(
                playerId = intel.playerId,
                totalProGames = intel.signaturePicks.sumOf { it.gamesPlayed },
                totalWins = intel.signaturePicks.sumOf { it.wins },
                winRate = 0.55,
                roleDistribution = mapOf(intel.role to 10),
                championRecords =
                    intel.signaturePicks.associate {
                        it.championId to
                            com.loldraft.data.player.ChampionCareerRecord(
                                championId = it.championId,
                                gamesPlayed = it.gamesPlayed,
                                wins = it.wins,
                                losses = it.gamesPlayed - it.wins,
                                winRate = it.winRate,
                                pickRate = it.pickRate,
                                role = it.role,
                            )
                    },
                signaturePicks = intel.signaturePicks,
            )
        return com.loldraft.data.player.PlayerIntelligenceDossier(
            playerId = intel.playerId,
            careerStats = careerStats,
            linkedAccounts = emptyList(),
            recentSoloQ3Days = intel.recentSoloQ7Days,
            recentSoloQ7Days = intel.recentSoloQ7Days,
            activeSpikeAlerts = intel.practiceSpikes,
            blindPickConfidences = emptyMap(),
        )
    }
}

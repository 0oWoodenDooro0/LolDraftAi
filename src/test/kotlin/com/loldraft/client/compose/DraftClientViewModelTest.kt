package com.loldraft.client.compose

import com.loldraft.client.compose.viewmodel.DraftClientViewModel
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.models.BpPredictionAlgorithm
import com.loldraft.server.ProMatchRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DraftClientViewModelTest {
    private lateinit var repository: ProMatchRepository
    private lateinit var viewModel: DraftClientViewModel

    @BeforeEach
    fun setUp() {
        repository = ProMatchRepository()
        repository.initialize()
        viewModel = DraftClientViewModel(repository = repository)
    }

    @AfterEach
    fun tearDown() {
        viewModel.close()
    }

    @Test
    fun `test initial state defaults to patch 16_17 and turn 1`() {
        val state = viewModel.uiState.value
        assertEquals("16.17", state.selectedPatch)
        assertEquals(1, state.currentTurnNumber)
        assertEquals(Side.BLUE, state.currentTurnSpec.side)
        assertEquals(ActionType.BAN, state.currentTurnSpec.actionType)
        assertEquals(20, state.boardSlots.size)
        assertNotNull(state.evalBar, "Eval bar must be initialized")
        assertFalse(state.evalBar.isEvaluated, "Eval bar must not be evaluated before 10 picks")
    }

    @Test
    fun `test locking in a champion advances turn and updates draft slots`() {
        // Turn 1: Blue Ban -> Lock in Kalista
        viewModel.lockInChampion("Kalista")

        val stateAfterT1 = viewModel.uiState.value
        assertEquals(2, stateAfterT1.currentTurnNumber)
        assertEquals(Side.RED, stateAfterT1.currentTurnSpec.side)
        assertEquals(ActionType.BAN, stateAfterT1.currentTurnSpec.actionType)

        val slot1 = stateAfterT1.boardSlots.first { it.turnNumber == 1 }
        assertEquals("Kalista", slot1.championId)
        assertTrue(stateAfterT1.bannedChampionIds.contains("Kalista"))
        assertFalse(slot1.isCurrentTurn)

        val slot2 = stateAfterT1.boardSlots.first { it.turnNumber == 2 }
        assertTrue(slot2.isCurrentTurn)
    }

    @Test
    fun `test undo reverts turn state and restores board slot`() {
        viewModel.lockInChampion("Kalista")
        assertEquals(2, viewModel.uiState.value.currentTurnNumber)

        viewModel.undoLastTurn()
        val stateAfterUndo = viewModel.uiState.value
        assertEquals(1, stateAfterUndo.currentTurnNumber)
        assertFalse(stateAfterUndo.bannedChampionIds.contains("Kalista"))

        val slot1 = stateAfterUndo.boardSlots.first { it.turnNumber == 1 }
        assertNull(slot1.championId)
        assertTrue(slot1.isCurrentTurn)
    }

    @Test
    fun `test resetDraft resets all 20 turns to empty`() {
        viewModel.lockInChampion("Kalista")
        viewModel.lockInChampion("Ashe")
        assertEquals(3, viewModel.uiState.value.currentTurnNumber)

        viewModel.resetDraft()
        val stateReset = viewModel.uiState.value
        assertEquals(1, stateReset.currentTurnNumber)
        assertTrue(stateReset.bannedChampionIds.isEmpty())
        assertTrue(stateReset.pickedChampionIds.isEmpty())
        assertTrue(stateReset.boardSlots.all { it.championId == null })
        assertFalse(stateReset.evalBar.isEvaluated)
        assertTrue(stateReset.evalBar.phaseDescription.contains("(0/10)"))
    }

    @Test
    fun `test champion search and role filter`() {
        // Initially filteredChampions contains all champions
        val initialCount = viewModel.uiState.value.filteredChampions.size
        assertTrue(initialCount > 50)

        // Filter by text search "Aatrox"
        viewModel.setSearchQuery("Aatrox")
        val searchResults = viewModel.uiState.value.filteredChampions
        assertEquals(1, searchResults.size)
        assertEquals("Aatrox", searchResults.first().id)

        // Clear search and filter by TOP role
        viewModel.setSearchQuery("")
        viewModel.setRoleFilter(Role.TOP)
        val topResults = viewModel.uiState.value.filteredChampions
        assertTrue(topResults.isNotEmpty())
        assertTrue(topResults.all { it.primaryRole == Role.TOP })
    }

    @Test
    fun `test selecting team updates roster and player intelligence pools`() {
        val teams = repository.getTeams()
        assertTrue(teams.isNotEmpty(), "Teams list should not be empty")

        val targetTeam = teams.first()
        viewModel.selectBlueTeam(targetTeam.id)

        val state = viewModel.uiState.value
        assertEquals(targetTeam.id, state.blueTeam?.id)
        assertTrue(state.blueRosterIntelligence.isNotEmpty(), "Blue roster intelligence should be populated")
    }

    @Test
    fun `test league filter reduces filtered teams list`() {
        val leagues = repository.getLeagues()
        if (leagues.size > 1) {
            val selectedLeague = leagues.first()
            viewModel.selectLeague(selectedLeague)

            val filtered = viewModel.uiState.value.filteredTeams
            assertTrue(filtered.isNotEmpty(), "Filtered teams should not be empty for $selectedLeague")
            assertTrue(filtered.all { it.league == selectedLeague }, "All teams in filtered list should belong to $selectedLeague")
        }
    }

    @Test
    fun `test dual independent league filters for blue and red sides`() {
        val leagues = repository.getLeagues()
        if (leagues.size >= 2) {
            val leagueA = leagues[0]
            val leagueB = leagues[1]

            viewModel.selectBlueLeague(leagueA)
            viewModel.selectRedLeague(leagueB)

            val state = viewModel.uiState.value
            assertEquals(leagueA, state.blueSelectedLeague)
            assertEquals(leagueB, state.redSelectedLeague)
            assertTrue(state.blueFilteredTeams.all { it.league == leagueA })
            assertTrue(state.redFilteredTeams.all { it.league == leagueB })
        }
    }

    @Test
    fun `test fearless draft exclusion prevents lock-in and manages exclusions`() {
        viewModel.addFearlessExcludedChampion("Aatrox")
        assertTrue(
            viewModel.uiState.value.fearlessExcludedChampionIds
                .contains("Aatrox"),
        )

        // Attempting to lock in Aatrox should be ignored because it is fearless-excluded
        viewModel.lockInChampion("Aatrox")
        assertEquals(1, viewModel.uiState.value.currentTurnNumber, "Lock-in should be prevented for fearless excluded champion")

        // Remove from fearless
        viewModel.removeFearlessExcludedChampion("Aatrox")
        assertFalse(
            viewModel.uiState.value.fearlessExcludedChampionIds
                .contains("Aatrox"),
        )

        // Now lock-in should succeed
        viewModel.lockInChampion("Aatrox")
        assertEquals(2, viewModel.uiState.value.currentTurnNumber)
    }

    @Test
    fun `test fearless draft importCurrentPicksToFearless and clear`() {
        // Fast forward 6 bans to reach Turn 7 (Blue Pick 1)
        repeat(6) {
            viewModel.lockInChampion("BanChamp$it")
        }
        assertEquals(7, viewModel.uiState.value.currentTurnNumber)

        // Pick Renekton
        viewModel.lockInChampion("Renekton")
        assertTrue(
            viewModel.uiState.value.pickedChampionIds
                .contains("Renekton"),
        )

        // Import current picks into fearless
        viewModel.importCurrentPicksToFearless()
        assertTrue(
            viewModel.uiState.value.fearlessExcludedChampionIds
                .contains("Renekton"),
        )

        // Clear fearless
        viewModel.clearFearlessExcludedChampions()
        assertTrue(
            viewModel.uiState.value.fearlessExcludedChampionIds
                .isEmpty(),
        )
    }

    @Test
    fun `test selectChampion with preferredRole from prediction locks into intended role`() {
        // Fast forward 6 bans to reach Turn 7 (Blue Pick 1)
        repeat(6) {
            viewModel.lockInChampion("BanChamp$it")
        }
        assertEquals(7, viewModel.uiState.value.currentTurnNumber)

        // Select Nautilus (default primaryRole: SUPPORT) with preferredRole = MID
        viewModel.selectChampion("Nautilus", preferredRole = Role.MID)
        assertEquals("Nautilus", viewModel.uiState.value.selectedChampionId)
        assertEquals(Role.MID, viewModel.uiState.value.preferredRoleForSelection)

        // Lock Nautilus in -> should occupy MID slot
        viewModel.lockInChampion("Nautilus")
        val slot7 =
            viewModel.uiState.value.boardSlots
                .first { it.turnNumber == 7 }
        assertEquals(Role.MID, slot7.role, "Nautilus should be assigned to MID because of preferredRole")
    }

    @Test
    fun `test updatePickRole changes role and updates board slot`() {
        // Fast forward 6 bans to reach Turn 7 (Blue Pick 1)
        repeat(6) {
            viewModel.lockInChampion("BanChamp$it")
        }

        // Turn 7: Blue Pick 1 -> Lock Renekton in (default primaryRole: TOP)
        viewModel.lockInChampion("Renekton")
        val slot7Before =
            viewModel.uiState.value.boardSlots
                .first { it.turnNumber == 7 }
        assertEquals(Role.TOP, slot7Before.role)

        // Update role of Turn 7 to MID
        viewModel.updatePickRole(7, Role.MID)
        val slot7After =
            viewModel.uiState.value.boardSlots
                .first { it.turnNumber == 7 }
        assertEquals(Role.MID, slot7After.role, "Turn 7 role should now be updated to MID")
    }

    @Test
    fun `test updatePickRole swaps roles when new role is already occupied by a teammate`() {
        // Fast forward 6 bans to reach Turn 7
        repeat(6) {
            viewModel.lockInChampion("BanChamp$it")
        }

        // Turn 7: Blue Pick 1 -> Lock Nautilus in as MID
        viewModel.selectChampion("Nautilus", preferredRole = Role.MID)
        viewModel.lockInChampion("Nautilus")
        assertEquals(
            Role.MID,
            viewModel.uiState.value.boardSlots
                .first { it.turnNumber == 7 }
                .role,
        )

        // Turn 8: Red Pick 1
        viewModel.lockInChampion("Aatrox")
        // Turn 9: Red Pick 2
        viewModel.lockInChampion("Sejuani")

        // Turn 10: Blue Pick 2 -> Lock Leona in as SUP
        viewModel.selectChampion("Leona", preferredRole = Role.SUPPORT)
        viewModel.lockInChampion("Leona")
        assertEquals(
            Role.SUPPORT,
            viewModel.uiState.value.boardSlots
                .first { it.turnNumber == 10 }
                .role,
        )

        // Now adjust Nautilus (Turn 7) to Role.SUPPORT
        // Since Leona (Turn 10) currently has Role.SUPPORT, they should swap!
        viewModel.updatePickRole(7, Role.SUPPORT)

        val slot7 =
            viewModel.uiState.value.boardSlots
                .first { it.turnNumber == 7 }
        val slot10 =
            viewModel.uiState.value.boardSlots
                .first { it.turnNumber == 10 }

        assertEquals(Role.SUPPORT, slot7.role, "Turn 7 (Nautilus) should now be SUPPORT")
        assertEquals(Role.MID, slot10.role, "Turn 10 (Leona) should now be swapped to MID")
    }

    @Test
    fun `test updatePickRole recalculates predictions without retaining champion default role`() {
        // Fast forward 6 bans to reach Turn 7
        repeat(6) {
            viewModel.lockInChampion("BanChamp$it")
        }

        // Turn 7: Blue Pick 1 -> Lock in Renekton (default primaryRole: TOP)
        viewModel.lockInChampion("Renekton")
        val slot7Before =
            viewModel.uiState.value.boardSlots
                .first { it.turnNumber == 7 }
        assertEquals(Role.TOP, slot7Before.role)

        // Change role of Renekton from TOP to MID
        viewModel.updatePickRole(7, Role.MID)
        viewModel.awaitCalculations()
        val slot7After =
            viewModel.uiState.value.boardSlots
                .first { it.turnNumber == 7 }
        assertEquals(Role.MID, slot7After.role)

        // Recalculations were executed for Turn 8 (Red pick 1).
        // Predictions for Red should not see Renekton as TOP opponent.
        val predictions = viewModel.uiState.value.intentPredictions
        val topPredictions = predictions.filter { it.predictedRole == Role.TOP }
        for (pred in topPredictions) {
            assertFalse(
                pred.rationale.contains("Lane counter vs Renekton"),
                "Red TOP candidate must not claim lane counter vs Renekton after Renekton moved to MID",
            )
        }
    }

    @Test
    fun `test win rate prediction is NOT evaluated while total picks is less than 10`() {
        // During 6 bans: total picks = 0
        repeat(6) {
            viewModel.lockInChampion("BanChamp$it")
        }
        viewModel.awaitCalculations()
        val evalBarAfterBans = viewModel.uiState.value.evalBar
        assertFalse(evalBarAfterBans.isEvaluated, "Must not evaluate win rate during ban phase")
        assertEquals(0.50, evalBarAfterBans.blueWinRate, 0.001)
        assertEquals(0.50, evalBarAfterBans.redWinRate, 0.001)
        assertTrue(evalBarAfterBans.phaseDescription.contains("(0/10)"))

        // Turns 7 to 11 (5 picks locked in: Blue 3, Red 2)
        val pickChamps = listOf("Renekton", "Aatrox", "Sejuani", "Leona", "Jinx")
        pickChamps.forEach { champ ->
            viewModel.lockInChampion(champ)
        }
        viewModel.awaitCalculations()

        val evalBarMidPicks = viewModel.uiState.value.evalBar
        assertFalse(evalBarMidPicks.isEvaluated, "Must not evaluate win rate when picks < 10 (currently 5)")
        assertEquals(0.50, evalBarMidPicks.blueWinRate, 0.001)
        assertEquals(0.50, evalBarMidPicks.redWinRate, 0.001)
        assertTrue(evalBarMidPicks.phaseDescription.contains("(5/10)"))
    }

    @Test
    fun `test win rate prediction IS evaluated when all 10 picks are selected`() {
        // 20 turns:
        // Turns 1..6: 6 bans
        val bans1 = listOf("Ban1", "Ban2", "Ban3", "Ban4", "Ban5", "Ban6")
        bans1.forEach { viewModel.lockInChampion(it) }

        // Turns 7..12: 6 picks (B1, R1, R2, B2, B3, R3)
        val picks1 = listOf("Renekton", "Aatrox", "Sejuani", "Leona", "Jinx", "Viktor")
        picks1.forEach { viewModel.lockInChampion(it) }

        // Turns 13..16: 4 bans
        val bans2 = listOf("Ban7", "Ban8", "Ban9", "Ban10")
        bans2.forEach { viewModel.lockInChampion(it) }

        // Turns 17..19: 3 picks (R4, B4, B5) -> Total picks = 9
        val picks2 = listOf("Thresh", "Ahri", "Vi")
        picks2.forEach { viewModel.lockInChampion(it) }

        viewModel.awaitCalculations()
        val evalBar9Picks = viewModel.uiState.value.evalBar
        assertFalse(evalBar9Picks.isEvaluated, "Must not evaluate win rate with only 9 picks")
        assertEquals(0.50, evalBar9Picks.blueWinRate, 0.001)
        assertTrue(evalBar9Picks.phaseDescription.contains("(9/10)"))

        // Turn 20: Red pick 5 (10th pick) -> Total picks = 10!
        viewModel.lockInChampion("Aphelios")
        viewModel.awaitCalculations()

        val stateFinal = viewModel.uiState.value
        assertTrue(stateFinal.isDraftComplete)
        val evalBarFinal = stateFinal.evalBar
        assertTrue(evalBarFinal.isEvaluated, "Win rate prediction MUST be evaluated when all 10 picks are selected")
        assertTrue(evalBarFinal.blueWinRate in 0.0..1.0, "Blue win rate should be in [0, 1]")
        assertTrue(evalBarFinal.redWinRate in 0.0..1.0, "Red win rate should be in [0, 1]")
        assertEquals(1.0, evalBarFinal.blueWinRate + evalBarFinal.redWinRate, 0.001, "Win rates must sum to 1.0")
        assertTrue(
            evalBarFinal.phaseDescription.contains("Advantage") || evalBarFinal.phaseDescription.contains("Matchup"),
            "Phase description should show advantage or matchup",
        )

        // Undo 1 turn: reverts to 9 picks -> evaluation must return to pending
        viewModel.undoLastTurn()
        viewModel.awaitCalculations()

        val evalBarAfterUndo = viewModel.uiState.value.evalBar
        assertFalse(evalBarAfterUndo.isEvaluated, "Undoing back to 9 picks must revert to unpredicted state")
        assertEquals(0.50, evalBarAfterUndo.blueWinRate, 0.001)
        assertEquals(0.50, evalBarAfterUndo.redWinRate, 0.001)
        assertTrue(evalBarAfterUndo.phaseDescription.contains("(9/10)"))
    }

    @Test
    fun `test selectPredictionAlgorithm switches between heuristic and deep learning`() {
        assertEquals(BpPredictionAlgorithm.HEURISTIC_EXPERT, viewModel.uiState.value.selectedAlgorithm)
        viewModel.awaitCalculations()
        val heuristicPredictions = viewModel.uiState.value.intentPredictions
        assertTrue(heuristicPredictions.isNotEmpty())

        // Switch to Deep Learning
        viewModel.selectPredictionAlgorithm(BpPredictionAlgorithm.DEEP_LEARNING_POLICY)
        assertEquals(BpPredictionAlgorithm.DEEP_LEARNING_POLICY, viewModel.uiState.value.selectedAlgorithm)
        viewModel.awaitCalculations()

        val dlPredictions = viewModel.uiState.value.intentPredictions
        assertTrue(dlPredictions.isNotEmpty())
        assertTrue(dlPredictions.first().rationale.contains("[DL Policy]"))
    }
}

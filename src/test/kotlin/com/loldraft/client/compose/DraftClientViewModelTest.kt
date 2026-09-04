package com.loldraft.client.compose

import com.loldraft.client.compose.viewmodel.DraftClientViewModel
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.server.ProMatchRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

    @Test
    fun `test initial state defaults to patch 16_17 and turn 1`() {
        val state = viewModel.uiState.value

        assertEquals("16.17", state.selectedPatch, "Default patch must be 16.17")
        assertTrue(state.availablePatches.contains("16.17"), "Available patches must contain 16.17")
        assertEquals(1, state.currentTurnNumber, "Initial turn must be 1")
        assertEquals(Side.BLUE, state.currentTurnSpec.side, "Turn 1 is Blue Side")
        assertEquals(ActionType.BAN, state.currentTurnSpec.actionType, "Turn 1 is Ban")
        assertEquals(20, state.boardSlots.size, "Board must have 20 draft slots")
        assertNotNull(state.evalBar, "Eval bar must be initialized")
        assertTrue(state.allChampions.isNotEmpty(), "Champion list must be populated")
    }

    @Test
    fun `test locking in a champion advances turn and updates draft slots`() {
        // Turn 1: Blue Ban -> Lock in "Kalista"
        viewModel.lockInChampion("Kalista")

        val stateAfterT1 = viewModel.uiState.value
        assertEquals(2, stateAfterT1.currentTurnNumber, "Turn must advance to 2")
        assertEquals(Side.RED, stateAfterT1.currentTurnSpec.side, "Turn 2 is Red Side")
        assertEquals(ActionType.BAN, stateAfterT1.currentTurnSpec.actionType, "Turn 2 is Ban")

        val slot1 = stateAfterT1.boardSlots.first { it.turnNumber == 1 }
        assertEquals("Kalista", slot1.championId, "Slot 1 must have Kalista")
        assertTrue(stateAfterT1.bannedChampionIds.contains("Kalista"))

        // Turn 2: Red Ban -> Lock in "Ashe"
        viewModel.lockInChampion("Ashe")
        val stateAfterT2 = viewModel.uiState.value
        assertEquals(3, stateAfterT2.currentTurnNumber, "Turn must advance to 3")
        assertTrue(stateAfterT2.bannedChampionIds.contains("Ashe"))
    }

    @Test
    fun `test undo reverts turn state and restores board slot`() {
        viewModel.lockInChampion("Kalista")
        assertEquals(2, viewModel.uiState.value.currentTurnNumber)

        viewModel.undoLastTurn()
        val reverted = viewModel.uiState.value
        assertEquals(1, reverted.currentTurnNumber, "Turn must revert to 1")
        assertFalse(reverted.bannedChampionIds.contains("Kalista"))
        val slot1 = reverted.boardSlots.first { it.turnNumber == 1 }
        assertEquals(null, slot1.championId, "Slot 1 champion must be cleared")
    }

    @Test
    fun `test resetDraft resets all 20 turns to empty`() {
        viewModel.lockInChampion("Kalista")
        viewModel.lockInChampion("Ashe")
        viewModel.lockInChampion("Lucian")
        assertEquals(4, viewModel.uiState.value.currentTurnNumber)

        viewModel.resetDraft()
        val resetState = viewModel.uiState.value
        assertEquals(1, resetState.currentTurnNumber)
        assertTrue(resetState.bannedChampionIds.isEmpty())
        assertTrue(resetState.pickedChampionIds.isEmpty())
    }

    @Test
    fun `test champion search and role filter`() {
        viewModel.setSearchQuery("Ahri")
        val searchFiltered = viewModel.uiState.value.filteredChampions
        assertEquals(1, searchFiltered.size)
        assertEquals("Ahri", searchFiltered[0].name)

        viewModel.setSearchQuery("")
        viewModel.setRoleFilter(Role.TOP)
        val topChamps = viewModel.uiState.value.filteredChampions
        assertTrue(topChamps.isNotEmpty())
        assertTrue(topChamps.all { it.primaryRole == Role.TOP })
    }

    @Test
    fun `test selecting team updates roster and player intelligence pools`() {
        val teams = repository.getTeams()
        if (teams.size >= 2) {
            val teamA = teams[0]
            val teamB = teams[1]

            viewModel.selectBlueTeam(teamA.id)
            viewModel.selectRedTeam(teamB.id)

            val state = viewModel.uiState.value
            assertEquals(teamA.id, state.blueTeam?.id)
            assertEquals(teamB.id, state.redTeam?.id)
            assertTrue(state.blueRosterIntelligence.isNotEmpty(), "Blue roster intelligence should be populated")
            assertTrue(state.redRosterIntelligence.isNotEmpty(), "Red roster intelligence should be populated")
        }
    }

    @Test
    fun `test league filter reduces filtered teams list`() {
        val allTeams = viewModel.uiState.value.allTeams
        val leagues = viewModel.uiState.value.availableLeagues

        if (leagues.isNotEmpty()) {
            val targetLeague = leagues.first()
            viewModel.selectLeague(targetLeague)

            val state = viewModel.uiState.value
            assertEquals(targetLeague, state.selectedLeague)
            assertTrue(state.filteredTeams.isNotEmpty())
            assertTrue(state.filteredTeams.all { it.league.equals(targetLeague, ignoreCase = true) })
            assertTrue(state.filteredTeams.size <= allTeams.size)

            // Select "ALL" resets back to all teams
            viewModel.selectLeague("ALL")
            val allState = viewModel.uiState.value
            assertEquals(null, allState.selectedLeague)
            assertEquals(allTeams.size, allState.filteredTeams.size)
        }
    }

    @Test
    fun `test dual independent league filters for blue and red sides`() {
        val allTeams = viewModel.uiState.value.allTeams
        val leagues = viewModel.uiState.value.availableLeagues

        if (leagues.size >= 2) {
            val leagueA = leagues[0]
            val leagueB = leagues[1]

            viewModel.selectBlueLeague(leagueA)
            viewModel.selectRedLeague(leagueB)

            val state = viewModel.uiState.value
            assertEquals(leagueA, state.blueSelectedLeague)
            assertEquals(leagueB, state.redSelectedLeague)
            assertTrue(state.blueFilteredTeams.isNotEmpty())
            assertTrue(state.redFilteredTeams.isNotEmpty())
            assertTrue(state.blueFilteredTeams.all { it.league.equals(leagueA, ignoreCase = true) })
            assertTrue(state.redFilteredTeams.all { it.league.equals(leagueB, ignoreCase = true) })

            // Test swap teams swaps the selected leagues and lists
            viewModel.swapTeams()
            val swapped = viewModel.uiState.value
            assertEquals(leagueB, swapped.blueSelectedLeague)
            assertEquals(leagueA, swapped.redSelectedLeague)
            assertEquals(state.redFilteredTeams, swapped.blueFilteredTeams)
            assertEquals(state.blueFilteredTeams, swapped.redFilteredTeams)
        }
    }

    @Test
    fun `test fearless draft exclusion prevents lock-in and manages exclusions`() {
        viewModel.addFearlessExcludedChampion("Aatrox")
        assertTrue(viewModel.uiState.value.fearlessExcludedChampionIds.contains("Aatrox"))

        // Attempt to lock in excluded champion Aatrox should be rejected
        val turnBefore = viewModel.uiState.value.currentTurnNumber
        viewModel.lockInChampion("Aatrox")
        assertEquals(turnBefore, viewModel.uiState.value.currentTurnNumber, "Turn must not advance for fearless excluded champion")

        // Remove from fearless exclusions
        viewModel.removeFearlessExcludedChampion("Aatrox")
        assertFalse(viewModel.uiState.value.fearlessExcludedChampionIds.contains("Aatrox"))

        // Now lock-in should succeed
        viewModel.lockInChampion("Aatrox")
        assertEquals(turnBefore + 1, viewModel.uiState.value.currentTurnNumber, "Turn should advance once fearless exclusion is removed")
    }

    @Test
    fun `test fearless draft importCurrentPicksToFearless and clear`() {
        // Fast forward to picks (turns 1-6 are bans, turn 7 is first pick)
        repeat(6) {
            viewModel.lockInChampion("Champ$it")
        }
        assertEquals(7, viewModel.uiState.value.currentTurnNumber)
        assertEquals(ActionType.PICK, viewModel.uiState.value.currentTurnSpec.actionType)

        // Lock in a pick
        viewModel.lockInChampion("Orianna")
        assertTrue(viewModel.uiState.value.pickedChampionIds.contains("Orianna"))

        // Import current picks into Fearless Draft
        viewModel.importCurrentPicksToFearless()
        assertTrue(viewModel.uiState.value.fearlessExcludedChampionIds.contains("Orianna"))

        // Clear all fearless exclusions
        viewModel.clearFearlessExcludedChampions()
        assertTrue(viewModel.uiState.value.fearlessExcludedChampionIds.isEmpty())
    }
}

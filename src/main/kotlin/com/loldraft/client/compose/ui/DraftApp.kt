package com.loldraft.client.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loldraft.client.compose.ui.components.AiDecisionPanelView
import com.loldraft.client.compose.ui.components.ChampionGridView
import com.loldraft.client.compose.ui.components.DraftBoardSideView
import com.loldraft.client.compose.ui.components.EvalBarView
import com.loldraft.client.compose.ui.components.RosterPlayerPoolView
import com.loldraft.client.compose.ui.components.TopBarView
import com.loldraft.client.compose.ui.theme.BgDark
import com.loldraft.client.compose.ui.theme.BlueSideColor
import com.loldraft.client.compose.ui.theme.LolDraftAiTheme
import com.loldraft.client.compose.ui.theme.RedSideColor
import com.loldraft.client.compose.viewmodel.DraftClientViewModel
import com.loldraft.data.models.Side

@Composable
fun DraftApp(viewModel: DraftClientViewModel = remember { DraftClientViewModel() }) {
    val state by viewModel.uiState.collectAsState()
    val leftScrollState = rememberScrollState()
    val rightScrollState = rememberScrollState()

    LolDraftAiTheme {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(BgDark),
        ) {
            // Top Navigation & Configuration Bar
            TopBarView(
                state = state,
                onSelectLeague = viewModel::selectLeague,
                onSelectBlueTeam = viewModel::selectBlueTeam,
                onSelectRedTeam = viewModel::selectRedTeam,
                onSelectPatch = viewModel::selectPatch,
                onUndo = viewModel::undoLastTurn,
                onReset = viewModel::resetDraft,
            )

            // Dynamic Win-Rate Eval Bar
            EvalBarView(evalBar = state.evalBar)

            // Main Content Area: 3-column layout
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Left Column: Blue Side BP Slots + Blue Roster Intelligence Pool
                Column(
                    modifier =
                        Modifier
                            .width(310.dp)
                            .verticalScroll(leftScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DraftBoardSideView(
                        side = Side.BLUE,
                        teamName = state.blueTeam?.name ?: "Blue Team",
                        slots = state.boardSlots,
                    )

                    RosterPlayerPoolView(
                        teamName = state.blueTeam?.name ?: "Blue Team",
                        sideColor = BlueSideColor,
                        roster = state.blueRosterIntelligence,
                    )
                }

                // Middle Column: Champion Selection Grid & Filter
                ChampionGridView(
                    champions = state.filteredChampions,
                    searchQuery = state.searchQuery,
                    onSearchChanged = viewModel::setSearchQuery,
                    selectedRoleFilter = state.selectedRoleFilter,
                    onRoleFilterSelected = viewModel::setRoleFilter,
                    selectedChampionId = state.selectedChampionId,
                    onChampionSelected = viewModel::selectChampion,
                    bannedChampionIds = state.bannedChampionIds,
                    pickedChampionIds = state.pickedChampionIds,
                    currentTurnSpec = state.currentTurnSpec,
                    currentTurnNumber = state.currentTurnNumber,
                    onLockIn = viewModel::lockInChampion,
                    modifier = Modifier.weight(1f),
                )

                // Right Column: Red Side BP Slots + AI Intelligence Decision Panel + Red Roster
                Column(
                    modifier =
                        Modifier
                            .width(340.dp)
                            .verticalScroll(rightScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    DraftBoardSideView(
                        side = Side.RED,
                        teamName = state.redTeam?.name ?: "Red Team",
                        slots = state.boardSlots,
                    )

                    AiDecisionPanelView(
                        intentPredictions = state.intentPredictions,
                        recommendations = state.counterRecommendations,
                        compositionFlaws = state.compositionFlaws,
                    )

                    RosterPlayerPoolView(
                        teamName = state.redTeam?.name ?: "Red Team",
                        sideColor = RedSideColor,
                        roster = state.redRosterIntelligence,
                    )
                }
            }
        }
    }
}

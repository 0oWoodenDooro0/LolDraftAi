package com.loldraft.client.compose.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loldraft.client.compose.ui.components.ChampionGridView
import com.loldraft.client.compose.ui.components.DraftBoardSideView
import com.loldraft.client.compose.ui.components.EvalBarView
import com.loldraft.client.compose.ui.components.FearlessDraftDialog
import com.loldraft.client.compose.ui.components.NextBpPredictionView
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
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(BgDark),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                // Top Navigation & Configuration Bar
                TopBarView(
                    state = state,
                    onSelectBlueLeague = viewModel::selectBlueLeague,
                    onSelectRedLeague = viewModel::selectRedLeague,
                    onSelectBlueTeam = viewModel::selectBlueTeam,
                    onSelectRedTeam = viewModel::selectRedTeam,
                    onSelectPatch = viewModel::selectPatch,
                    onSetFirstPickSide = viewModel::setFirstPickSide,
                    onSwapTeams = viewModel::swapTeams,
                    onUndo = viewModel::undoLastTurn,
                    onReset = viewModel::resetDraft,
                    onOpenFearlessDialog = { viewModel.setFearlessDialogOpen(true) },
                )

                // Dynamic Win-Rate Eval Bar
                EvalBarView(evalBar = state.evalBar)

                // Main Content Area: 3-column layout extending all the way to the bottom
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Left Column: Expands to fill available space on the left (weight 1f)
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(leftScrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DraftBoardSideView(
                            side = Side.BLUE,
                            teamName = state.blueTeam?.name ?: "Blue Team",
                            slots = state.boardSlots,
                            onUpdateRole = viewModel::updatePickRole,
                        )

                        RosterPlayerPoolView(
                            teamName = state.blueTeam?.name ?: "Blue Team",
                            sideColor = BlueSideColor,
                            roster = state.blueRosterIntelligence,
                        )
                    }

                    // Middle Column: Increased width by 1.5x (720.dp), Champion Grid + Next BP Prediction
                    Column(
                        modifier =
                            Modifier
                                .width(720.dp)
                                .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChampionGridView(
                            champions = state.filteredChampions,
                            searchQuery = state.searchQuery,
                            onSearchChanged = viewModel::setSearchQuery,
                            selectedChampionId = state.selectedChampionId,
                            onChampionSelected = viewModel::selectChampion,
                            bannedChampionIds = state.bannedChampionIds,
                            pickedChampionIds = state.pickedChampionIds,
                            currentTurnSpec = state.currentTurnSpec,
                            currentTurnNumber = state.currentTurnNumber,
                            onLockIn = viewModel::lockInChampion,
                            fearlessExcludedChampionIds = state.fearlessExcludedChampionIds,
                            modifier = Modifier.fillMaxWidth().weight(1.05f),
                        )

                        // Bottom Center: Next BP Intent Prediction (taller, prominent, vertical layout)
                        NextBpPredictionView(
                            currentTurnNumber = state.currentTurnNumber,
                            currentTurnSpec = state.currentTurnSpec,
                            intentPredictions = state.intentPredictions,
                            selectedChampionId = state.selectedChampionId,
                            onChampionSelected = { champId, role -> viewModel.selectChampion(champId, role) },
                            bannedChampionIds = state.bannedChampionIds,
                            pickedChampionIds = state.pickedChampionIds,
                            fearlessExcludedChampionIds = state.fearlessExcludedChampionIds,
                            modifier = Modifier.fillMaxWidth().weight(0.95f),
                        )
                    }

                    // Right Column: Expands to fill available space on the right (weight 1f)
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rightScrollState),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        DraftBoardSideView(
                            side = Side.RED,
                            teamName = state.redTeam?.name ?: "Red Team",
                            slots = state.boardSlots,
                            onUpdateRole = viewModel::updatePickRole,
                        )

                        RosterPlayerPoolView(
                            teamName = state.redTeam?.name ?: "Red Team",
                            sideColor = RedSideColor,
                            roster = state.redRosterIntelligence,
                        )
                    }
                }
            }

            // Fearless Draft Exclusions Management Modal Dialog
            if (state.isFearlessDialogOpen) {
                FearlessDraftDialog(
                    allChampions = state.allChampions,
                    excludedChampionIds = state.fearlessExcludedChampionIds,
                    currentPicksCount = state.pickedChampionIds.size,
                    onAddChampion = viewModel::addFearlessExcludedChampion,
                    onRemoveChampion = viewModel::removeFearlessExcludedChampion,
                    onClearAll = viewModel::clearFearlessExcludedChampions,
                    onImportCurrentPicks = viewModel::importCurrentPicksToFearless,
                    onDismiss = { viewModel.setFearlessDialogOpen(false) },
                )
            }
        }
    }
}

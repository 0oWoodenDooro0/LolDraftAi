package com.loldraft.client.compose.ui.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loldraft.analytics.model.PlayerChampionStats
import com.loldraft.analytics.model.RosterRoleSlot
import com.loldraft.analytics.model.SortDirection
import com.loldraft.client.compose.ui.components.ChampionAvatar
import com.loldraft.client.compose.ui.theme.BlueSideColor
import com.loldraft.client.compose.ui.theme.BlueSideDark
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.GreenAccent
import com.loldraft.client.compose.ui.theme.OrangeWarning
import com.loldraft.client.compose.ui.theme.RedSideColor
import com.loldraft.client.compose.ui.theme.RedSideDark
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.client.compose.ui.theme.TextSecondary
import com.loldraft.client.compose.viewmodel.AnalyticsViewModel
import com.loldraft.data.models.Role
import java.util.Locale

@Composable
fun TeamRosterPoolView(viewModel: AnalyticsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val matrix = state.rosterMatrix

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Filter Bar: Tournament Chips + Split Selector + Team Selector + Patch Selector
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Team Selector
            TeamDropdownSelector(
                selectedTeam = state.selectedTeam,
                availableTeams = state.availableTeams,
                onSelectTeam = viewModel::selectTeam,
            )

            // Tournament Chips Filter (All, LCK, EWC, Worlds, MSI, LPL, LEC, etc.)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("賽事:", color = TextSecondary, fontSize = 12.sp)

                TournamentFilterChip(
                    name = "全部賽事",
                    isSelected = state.selectedTournament == null,
                    onClick = { viewModel.selectTournament(null) },
                )

                val majorTournaments = listOf("LCK", "LPL", "LEC", "LCS", "LCP", "Worlds", "MSI", "EWC")
                majorTournaments.forEach { t ->
                    if (state.availableTournaments.any { it.equals(t, ignoreCase = true) }) {
                        TournamentFilterChip(
                            name = t,
                            isSelected = state.selectedTournament.equals(t, ignoreCase = true),
                            onClick = { viewModel.selectTournament(t) },
                        )
                    }
                }
            }

            // Split (賽期階段) Filter
            SplitDropdownSelector(
                availableSplits = state.availableSplits,
                selectedSplit = state.selectedSplit,
                onSelectSplit = viewModel::selectSplit,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Patch Filter
            PatchFilterDropdown(
                availablePatches = state.availablePatches,
                selectedPatch = state.selectedPatch,
                onSelectPatch = viewModel::selectPatch,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Team Summary Banner
        if (matrix != null) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(CardDark, RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = matrix.teamName,
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Text(
                        text = if (state.selectedTournament != null) "[ ${state.selectedTournament} ]" else "[ 全部賽事 ]",
                        color = BlueSideColor,
                        fontSize = 13.sp,
                    )
                    if (state.selectedSplit != null) {
                        Text(
                            text = "[ 賽期: ${state.selectedSplit} ]",
                            color = GoldAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                    if (state.selectedPatch != null) {
                        Text(
                            text = "版本: ${state.selectedPatch}",
                            color = TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    val wrStr = String.format(Locale.US, "%.1f%%", matrix.teamWinRate * 100)
                    Text("總出賽: ${matrix.totalTeamGames} 場", color = TextPrimary, fontSize = 13.sp)
                    Text("戰績: ${matrix.teamWins}勝 ${matrix.teamLosses}敗", color = TextPrimary, fontSize = 13.sp)
                    Text("勝率: $wrStr", color = if (matrix.teamWinRate >= 0.5) GreenAccent else OrangeWarning, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 5-Column Roster Matrix (TOP, JNG, MID, BOT, SUP)
        if (matrix == null || matrix.totalTeamGames == 0) {
            Box(
                modifier = Modifier.fillMaxSize().background(SurfaceDark, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("所選條件下無出賽記錄，請切換戰隊、賽事或賽期篩選條件。", color = TextMuted, fontSize = 15.sp)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val roles = listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT)
                roles.forEach { role ->
                    val slot = matrix.roles[role]
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        if (slot != null) {
                            RolePoolCard(
                                slot = slot,
                                sortedChampions = viewModel.getSortedChampionPool(role),
                                sortColumn = state.rolePoolSortColumn[role] ?: "games",
                                sortDirection = state.rolePoolSortDirection[role] ?: SortDirection.DESCENDING,
                                onSelectSubstitute = { playerName ->
                                    viewModel.selectSubstituteForRole(role, playerName)
                                },
                                onSortColumn = { col ->
                                    viewModel.sortRoleChampionPool(role, col)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RolePoolCard(
    slot: RosterRoleSlot,
    sortedChampions: List<PlayerChampionStats>,
    sortColumn: String,
    sortDirection: SortDirection,
    onSelectSubstitute: (String) -> Unit,
    onSortColumn: (String) -> Unit,
) {
    var isSubstituteMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp)),
    ) {
        // Role Header
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(CardDark)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = slot.role.name,
                    color = BlueSideColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )

                val wrStr = String.format(Locale.US, "%.0f%%", slot.winRate * 100)
                Text(
                    text = "${slot.totalGames}場 ($wrStr)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
            }
        }

        // Player Selector & Champion Pool Count Row
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(CardDark.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (slot.availablePlayers.size > 1) {
                // Dropdown trigger for substitute
                Box {
                    Row(
                        modifier =
                            Modifier
                                .background(BorderDark, RoundedCornerShape(4.dp))
                                .clickable { isSubstituteMenuOpen = true }
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = slot.currentPlayer,
                            color = GoldAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "▼ (${slot.availablePlayers.size}人替補)",
                            color = TextSecondary,
                            fontSize = 10.sp,
                        )
                    }

                    DropdownMenu(
                        expanded = isSubstituteMenuOpen,
                        onDismissRequest = { isSubstituteMenuOpen = false },
                        modifier = Modifier.background(SurfaceDark).border(1.dp, BorderDark),
                    ) {
                        slot.availablePlayers.forEach { player ->
                            val isCurrent = player == slot.currentPlayer
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isCurrent) "$player · 目前選取" else player,
                                        color = if (isCurrent) GoldAccent else TextPrimary,
                                        fontSize = 12.sp,
                                    )
                                },
                                onClick = {
                                    onSelectSubstitute(player)
                                    isSubstituteMenuOpen = false
                                },
                            )
                        }
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = slot.currentPlayer,
                        color = GoldAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    Text("(主力)", color = TextSecondary, fontSize = 11.sp)
                }
            }

            // Champion Pool Count (玩過多少英雄)
            Text(
                text = "英雄池: ${slot.championPoolCount}",
                color = BlueSideColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
            )
        }

        HorizontalDivider(color = BorderDark, thickness = 1.dp)

        // Mini Table Header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(CardDark)
                    .height(30.dp)
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderSortCell("英雄", "champion", sortColumn, sortDirection, onSortColumn, Modifier.weight(1.3f))
            HeaderSortCell("場次", "games", sortColumn, sortDirection, onSortColumn, Modifier.weight(0.9f), TextAlign.End)
            HeaderSortCell("勝率", "winrate", sortColumn, sortDirection, onSortColumn, Modifier.weight(1.1f), TextAlign.End)
            HeaderSortCell("敵Ban", "bans", sortColumn, sortDirection, onSortColumn, Modifier.weight(1.1f), TextAlign.End)
        }

        HorizontalDivider(color = BorderDark, thickness = 0.5.dp)

        // Champion Pool Scrollable Rows
        if (sortedChampions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("無使用英雄記錄", color = TextMuted, fontSize = 11.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
            ) {
                items(sortedChampions) { champ ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(34.dp)
                                .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Champion Name with Avatar
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1.3f),
                        ) {
                            ChampionAvatar(
                                championNameOrId = champ.championName,
                                avatarSize = 20.dp,
                                shape = RoundedCornerShape(3.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = champ.championName,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Games Played
                        Text(
                            text = "${champ.gamesPlayed}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(0.9f),
                        )

                        // Win Rate
                        val wrText = String.format(Locale.US, "%.0f%%", champ.winRate * 100)
                        val wrColor =
                            when {
                                champ.winRate >= 0.60 -> GreenAccent
                                champ.winRate <= 0.40 -> RedSideColor
                                else -> TextPrimary
                            }
                        Text(
                            text = wrText,
                            color = wrColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.End,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.weight(1.1f),
                        )

                        // Opponent Ban Rate
                        val banPct = champ.opponentBanRate * 100
                        val banText = String.format(Locale.US, "%.0f%%", banPct)
                        val isHighBan = banPct >= 25.0

                        Box(
                            modifier = Modifier.weight(1.1f),
                            contentAlignment = Alignment.CenterEnd,
                        ) {
                            if (champ.opponentBans > 0) {
                                Text(
                                    text = "$banText · ${champ.opponentBans}次",
                                    color = if (isHighBan) RedSideColor else TextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isHighBan) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.End,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            } else {
                                Text(
                                    text = "-",
                                    color = TextMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = BorderDark.copy(alpha = 0.3f), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun HeaderSortCell(
    title: String,
    columnId: String,
    currentSortColumn: String,
    currentSortDirection: SortDirection,
    onSortColumn: (String) -> Unit,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.Start,
) {
    val isSorted = currentSortColumn.equals(columnId, ignoreCase = true)
    val indicator = if (isSorted) (if (currentSortDirection == SortDirection.ASCENDING) "▲" else "▼") else ""

    Box(
        modifier =
            modifier
                .clickable { onSortColumn(columnId) }
                .padding(vertical = 4.dp),
        contentAlignment = if (textAlign == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = "$title$indicator",
            color = if (isSorted) GoldAccent else TextSecondary,
            fontSize = 11.sp,
            fontWeight = if (isSorted) FontWeight.Bold else FontWeight.Normal,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun TeamDropdownSelector(
    selectedTeam: String,
    availableTeams: List<String>,
    onSelectTeam: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("目標戰隊: ", color = TextSecondary, fontSize = 12.sp)
        Box {
            Row(
                modifier =
                    Modifier
                        .background(CardDark, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = selectedTeam.ifBlank { "選擇戰隊" },
                    color = GoldAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
                Text("▼", color = TextSecondary, fontSize = 10.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceDark).border(1.dp, BorderDark),
            ) {
                availableTeams.forEach { team ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = team,
                                color = if (team == selectedTeam) GoldAccent else TextPrimary,
                                fontSize = 12.sp,
                            )
                        },
                        onClick = {
                            onSelectTeam(team)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TournamentFilterChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .background(
                    if (isSelected) BlueSideDark else CardDark,
                    RoundedCornerShape(4.dp),
                )
                .border(
                    1.dp,
                    if (isSelected) BlueSideColor else BorderDark,
                    RoundedCornerShape(4.dp),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = name,
            color = if (isSelected) Color.White else TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SplitDropdownSelector(
    availableSplits: List<String>,
    selectedSplit: String?,
    onSelectSplit: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("賽期:", color = TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.width(4.dp))
        Box {
            Row(
                modifier =
                    Modifier
                        .background(
                            if (selectedSplit != null) GoldAccent.copy(alpha = 0.15f) else CardDark,
                            RoundedCornerShape(4.dp),
                        )
                        .border(
                            1.dp,
                            if (selectedSplit != null) GoldAccent else BorderDark,
                            RoundedCornerShape(4.dp),
                        )
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = selectedSplit ?: "全部賽期",
                    color = if (selectedSplit != null) GoldAccent else TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = if (selectedSplit != null) FontWeight.Bold else FontWeight.Normal,
                )
                Text("▼", color = TextSecondary, fontSize = 9.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceDark).border(1.dp, BorderDark),
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "全部賽期",
                            color = if (selectedSplit == null) GoldAccent else TextPrimary,
                            fontSize = 12.sp,
                        )
                    },
                    onClick = {
                        onSelectSplit(null)
                        expanded = false
                    },
                )
                availableSplits.forEach { split ->
                    val isSel = selectedSplit.equals(split, ignoreCase = true)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = split,
                                color = if (isSel) GoldAccent else TextPrimary,
                                fontSize = 12.sp,
                            )
                        },
                        onClick = {
                            onSelectSplit(split)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PatchFilterDropdown(
    availablePatches: List<String>,
    selectedPatch: String?,
    onSelectPatch: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("版本: ", color = TextSecondary, fontSize = 12.sp)
        Box {
            Row(
                modifier =
                    Modifier
                        .background(CardDark, RoundedCornerShape(4.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = selectedPatch ?: "全部版本",
                    color = TextPrimary,
                    fontSize = 11.sp,
                )
                Text("▼", color = TextSecondary, fontSize = 9.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceDark).border(1.dp, BorderDark),
            ) {
                DropdownMenuItem(
                    text = { Text("全部版本", color = if (selectedPatch == null) GoldAccent else TextPrimary, fontSize = 12.sp) },
                    onClick = {
                        onSelectPatch(null)
                        expanded = false
                    },
                )
                availablePatches.forEach { patch ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = patch,
                                color = if (patch == selectedPatch) GoldAccent else TextPrimary,
                                fontSize = 12.sp,
                            )
                        },
                        onClick = {
                            onSelectPatch(patch)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

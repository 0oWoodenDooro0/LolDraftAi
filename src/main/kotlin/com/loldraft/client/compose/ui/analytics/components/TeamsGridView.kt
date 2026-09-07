package com.loldraft.client.compose.ui.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loldraft.analytics.model.TeamAnalyticsRow
import com.loldraft.client.compose.ui.theme.BlueSideColor
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.GreenAccent
import com.loldraft.client.compose.ui.theme.OrangeWarning
import com.loldraft.client.compose.ui.theme.RedSideColor
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.client.compose.ui.theme.TextSecondary
import com.loldraft.client.compose.viewmodel.AnalyticsViewModel
import java.util.Locale

@Composable
fun TeamsGridView(viewModel: AnalyticsViewModel) {
    val state by viewModel.uiState.collectAsState()

    val columns =
        remember {
            listOf(
                DataGridColumn<TeamAnalyticsRow>("team", "戰隊名稱", 150.dp) { item ->
                    Text(item.teamName, color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                },
                DataGridColumn("league", "賽區/賽事", 90.dp) { item ->
                    Text(item.league, color = BlueSideColor, fontSize = 12.sp)
                },
                DataGridColumn("split", "賽期", 80.dp) { item ->
                    Text(item.split ?: "-", color = TextSecondary, fontSize = 11.sp)
                },
                DataGridColumn("games", "總場次", 70.dp, TextAlign.End) { item ->
                    Text("${item.games}", color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("wins", "勝-負", 70.dp, TextAlign.Center) { item ->
                    Text("${item.wins}-${item.losses}", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                },
                DataGridColumn("winrate", "總勝率%", 80.dp, TextAlign.End) { item ->
                    val wrStr = String.format(Locale.US, "%.1f%%", item.winRate * 100)
                    val color = if (item.winRate >= 0.60) GreenAccent else if (item.winRate <= 0.40) RedSideColor else TextPrimary
                    Text(wrStr, color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("bluewinrate", "藍方勝率%", 90.dp, TextAlign.End) { item ->
                    val bStr = if (item.blueGames > 0) String.format(Locale.US, "%.0f%% (%d)", item.blueWinRate * 100, item.blueGames) else "-"
                    Text(bStr, color = BlueSideColor, fontSize = 11.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("redwinrate", "紅方勝率%", 90.dp, TextAlign.End) { item ->
                    val rStr = if (item.redGames > 0) String.format(Locale.US, "%.0f%% (%d)", item.redWinRate * 100, item.redGames) else "-"
                    Text(rStr, color = RedSideColor, fontSize = 11.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("firstblood", "首殺率%", 80.dp, TextAlign.End) { item ->
                    val fbStr = String.format(Locale.US, "%.1f%%", item.firstBloodRate * 100)
                    Text(fbStr, color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("firstdragon", "首龍率%", 80.dp, TextAlign.End) { item ->
                    val fdStr = String.format(Locale.US, "%.1f%%", item.firstDragonRate * 100)
                    Text(fdStr, color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("gd15", "15分經濟差", 85.dp, TextAlign.End) { item ->
                    val gdStr = if (item.avgGoldDiffAt15 != 0.0) String.format(Locale.US, "%+.0f", item.avgGoldDiffAt15) else "-"
                    val color = if (item.avgGoldDiffAt15 > 0) GreenAccent else if (item.avgGoldDiffAt15 < 0) RedSideColor else TextSecondary
                    Text(gdStr, color = color, fontSize = 12.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("duration", "平均時長", 85.dp, TextAlign.End) { item ->
                    val m = item.avgGameDurationSeconds / 60
                    val s = item.avgGameDurationSeconds % 60
                    val durStr = if (item.avgGameDurationSeconds > 0) String.format(Locale.US, "%02d:%02d", m, s) else "-"
                    Text(durStr, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.End)
                },
            )
        }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Filter Toolbar
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Search Input
            Row(
                modifier =
                    Modifier
                        .width(220.dp)
                        .background(CardDark, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔍 ", fontSize = 11.sp)
                BasicTextField(
                    value = state.teamSearchQuery,
                    onValueChange = viewModel::setTeamSearchQuery,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                    cursorBrush = SolidColor(GoldAccent),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (state.teamSearchQuery.isEmpty()) {
                            Text("搜尋戰隊名稱/代碼...", color = TextMuted, fontSize = 11.sp)
                        }
                        innerTextField()
                    },
                )
            }

            // League Filter Dropdown
            TeamLeagueDropdown(
                availableTournaments = state.availableTournaments,
                selectedLeague = state.teamLeagueFilter,
                onSelectLeague = viewModel::setTeamLeagueFilter,
            )

            // Split Filter Dropdown
            TeamSplitDropdown(
                availableSplits = state.availableSplits,
                selectedSplit = state.teamSplitFilter,
                onSelectSplit = viewModel::setTeamSplitFilter,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Min Games Filter
            TeamMinGamesDropdown(
                selectedMinGames = state.teamMinGames,
                onSelect = viewModel::setTeamMinGames,
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Data Table
        ExcelDataGrid(
            columns = columns,
            items = state.teamRows,
            currentSortColumn = state.teamSortColumn,
            currentSortDirection = state.teamSortDirection,
            onSortColumn = viewModel::sortTeamGrid,
            modifier = Modifier.fillMaxSize(),
            summaryContent = {
                val sum = state.teamSummary
                val wrStr = String.format(Locale.US, "%.1f%%", sum.avgWinRate * 100)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("共篩選出: ${sum.totalCount} 支戰隊", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("總出賽場次: ${sum.totalGames} 場", color = TextSecondary, fontSize = 12.sp)
                    Text("平均勝率: $wrStr", color = TextPrimary, fontSize = 12.sp)
                }
            },
        )
    }
}

@Composable
private fun TeamLeagueDropdown(
    availableTournaments: List<String>,
    selectedLeague: String?,
    onSelectLeague: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("賽事: ", color = TextSecondary, fontSize = 11.sp)
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
                Text(selectedLeague ?: "全部賽事", color = TextPrimary, fontSize = 11.sp)
                Text("▼", color = TextSecondary, fontSize = 9.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceDark).border(1.dp, BorderDark),
            ) {
                DropdownMenuItem(
                    text = { Text("全部賽事", color = if (selectedLeague == null) GoldAccent else TextPrimary, fontSize = 12.sp) },
                    onClick = {
                        onSelectLeague(null)
                        expanded = false
                    },
                )
                availableTournaments.forEach { tourn ->
                    DropdownMenuItem(
                        text = { Text(tourn, color = if (tourn == selectedLeague) GoldAccent else TextPrimary, fontSize = 12.sp) },
                        onClick = {
                            onSelectLeague(tourn)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamMinGamesDropdown(
    selectedMinGames: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(1, 3, 5, 10)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("場次門檻: ", color = TextSecondary, fontSize = 11.sp)
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
                Text("≥ $selectedMinGames 場", color = TextPrimary, fontSize = 11.sp)
                Text("▼", color = TextSecondary, fontSize = 9.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceDark).border(1.dp, BorderDark),
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text("≥ $opt 場", color = if (opt == selectedMinGames) GoldAccent else TextPrimary, fontSize = 12.sp) },
                        onClick = {
                            onSelect(opt)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamSplitDropdown(
    availableSplits: List<String>,
    selectedSplit: String?,
    onSelectSplit: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("賽期: ", color = TextSecondary, fontSize = 11.sp)
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
                Text(selectedSplit ?: "全部賽期", color = TextPrimary, fontSize = 11.sp)
                Text("▼", color = TextSecondary, fontSize = 9.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceDark).border(1.dp, BorderDark),
            ) {
                DropdownMenuItem(
                    text = { Text("全部賽期", color = if (selectedSplit == null) GoldAccent else TextPrimary, fontSize = 12.sp) },
                    onClick = {
                        onSelectSplit(null)
                        expanded = false
                    },
                )
                availableSplits.forEach { split ->
                    DropdownMenuItem(
                        text = { Text(split, color = if (split == selectedSplit) GoldAccent else TextPrimary, fontSize = 12.sp) },
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

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loldraft.analytics.model.PlayerAnalyticsRow
import com.loldraft.client.compose.ui.theme.BlueSideColor
import com.loldraft.client.compose.ui.theme.BlueSideDark
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.GreenAccent
import com.loldraft.client.compose.ui.theme.RedSideColor
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.client.compose.ui.theme.TextSecondary
import com.loldraft.client.compose.viewmodel.AnalyticsViewModel
import com.loldraft.data.models.Role
import java.util.Locale

@Composable
fun PlayersGridView(viewModel: AnalyticsViewModel) {
    val state by viewModel.uiState.collectAsState()

    val columns =
        remember {
            listOf(
                DataGridColumn<PlayerAnalyticsRow>("player", "選手姓名", 110.dp) { item ->
                    Text(item.playerName, color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                },
                DataGridColumn("team", "所屬戰隊", 90.dp) { item ->
                    Text(item.teamName, color = TextPrimary, fontSize = 12.sp)
                },
                DataGridColumn("league", "賽區/賽事", 80.dp) { item ->
                    Text(item.league, color = BlueSideColor, fontSize = 12.sp)
                },
                DataGridColumn("split", "賽期", 75.dp) { item ->
                    Text(item.split ?: "-", color = TextSecondary, fontSize = 11.sp)
                },
                DataGridColumn("role", "定位", 60.dp, TextAlign.Center) { item ->
                    Text(item.role.name, color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                },
                DataGridColumn("games", "場次", 60.dp, TextAlign.End) { item ->
                    Text("${item.games}", color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("wins", "勝-負", 60.dp, TextAlign.Center) { item ->
                    Text("${item.wins}-${item.losses}", color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                },
                DataGridColumn("winrate", "勝率%", 70.dp, TextAlign.End) { item ->
                    val wrStr = String.format(Locale.US, "%.1f%%", item.winRate * 100)
                    val color = if (item.winRate >= 0.60) GreenAccent else if (item.winRate <= 0.40) RedSideColor else TextPrimary
                    Text(wrStr, color = color, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("kda", "KDA", 65.dp, TextAlign.End) { item ->
                    val kdaStr = String.format(Locale.US, "%.2f", item.kda)
                    Text(kdaStr, color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("dpm", "DPM", 70.dp, TextAlign.End) { item ->
                    val dpmStr = if (item.avgDpm > 0) String.format(Locale.US, "%.0f", item.avgDpm) else "-"
                    Text(dpmStr, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("cspm", "CSPM", 65.dp, TextAlign.End) { item ->
                    val cspmStr = if (item.avgCspm > 0) String.format(Locale.US, "%.1f", item.avgCspm) else "-"
                    Text(cspmStr, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("gd15", "GD@15", 70.dp, TextAlign.End) { item ->
                    val gdStr = if (item.avgGoldDiffAt15 != 0.0) String.format(Locale.US, "%+.0f", item.avgGoldDiffAt15) else "-"
                    val color = if (item.avgGoldDiffAt15 > 0) GreenAccent else if (item.avgGoldDiffAt15 < 0) RedSideColor else TextSecondary
                    Text(gdStr, color = color, fontSize = 12.sp, textAlign = TextAlign.End)
                },
                DataGridColumn("champions", "英雄池數", 75.dp, TextAlign.End) { item ->
                    Text("${item.championPoolCount}", color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.End)
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Search Input
            Row(
                modifier =
                    Modifier
                        .width(200.dp)
                        .background(CardDark, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔍 ", fontSize = 11.sp)
                BasicTextField(
                    value = state.playerSearchQuery,
                    onValueChange = viewModel::setPlayerSearchQuery,
                    textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
                    cursorBrush = SolidColor(GoldAccent),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (state.playerSearchQuery.isEmpty()) {
                            Text("搜尋選手/戰隊/英雄...", color = TextMuted, fontSize = 11.sp)
                        }
                        innerTextField()
                    },
                )
            }

            // League Filter Dropdown
            DropdownFilter(
                label = "賽事",
                options = listOf("全部賽事") + state.availableTournaments,
                selected = state.playerLeagueFilter ?: "全部賽事",
                onSelect = { viewModel.setPlayerLeagueFilter(if (it == "全部賽事") null else it) },
            )

            // Split Filter Dropdown
            DropdownFilter(
                label = "賽期",
                options = listOf("全部賽期") + state.availableSplits,
                selected = state.playerSplitFilter ?: "全部賽期",
                onSelect = { viewModel.setPlayerSplitFilter(if (it == "全部賽期") null else it) },
            )

            // Role Filter Chips
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("定位:", color = TextSecondary, fontSize = 11.sp)
                FilterChip("All", state.playerRoleFilter == null) { viewModel.setPlayerRoleFilter(null) }
                listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT).forEach { r ->
                    FilterChip(r.name, state.playerRoleFilter == r) { viewModel.setPlayerRoleFilter(r) }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Min Games Filter
            DropdownFilter(
                label = "場次門檻",
                options = listOf("≥ 1 場", "≥ 3 場", "≥ 5 場", "≥ 10 場"),
                selected = "≥ ${state.playerMinGames} 場",
                onSelect = {
                    val count = it.filter { c -> c.isDigit() }.toIntOrNull() ?: 1
                    viewModel.setPlayerMinGames(count)
                },
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Data Table
        ExcelDataGrid(
            columns = columns,
            items = state.playerRows,
            currentSortColumn = state.playerSortColumn,
            currentSortDirection = state.playerSortDirection,
            onSortColumn = viewModel::sortPlayerGrid,
            modifier = Modifier.fillMaxSize(),
            summaryContent = {
                val sum = state.playerSummary
                val wrStr = String.format(Locale.US, "%.1f%%", sum.avgWinRate * 100)
                val kdaStr = sum.avgKda?.let { String.format(Locale.US, "%.2f", it) } ?: "-"
                val dpmStr = sum.avgDpm?.let { String.format(Locale.US, "%.0f", it) } ?: "-"

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("共篩選出: ${sum.totalCount} 位選手", color = GoldAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("總出賽樣本: ${sum.totalGames} 人次", color = TextSecondary, fontSize = 12.sp)
                    Text("平均勝率: $wrStr", color = TextPrimary, fontSize = 12.sp)
                    Text("平均 KDA: $kdaStr", color = TextPrimary, fontSize = 12.sp)
                    Text("平均 DPM: $dpmStr", color = TextPrimary, fontSize = 12.sp)
                }
            },
        )
    }
}

@Composable
private fun FilterChip(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .background(if (isSelected) BlueSideDark else CardDark, RoundedCornerShape(4.dp))
                .border(1.dp, if (isSelected) BlueSideColor else BorderDark, RoundedCornerShape(4.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(text, color = if (isSelected) Color.White else TextSecondary, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun DropdownFilter(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$label: ", color = TextSecondary, fontSize = 11.sp)
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
                Text(selected, color = TextPrimary, fontSize = 11.sp)
                Text("▼", color = TextSecondary, fontSize = 9.sp)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(SurfaceDark).border(1.dp, BorderDark),
            ) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = if (opt == selected) GoldAccent else TextPrimary, fontSize = 12.sp) },
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

package com.loldraft.client.compose.ui.analytics.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.loldraft.client.compose.viewmodel.SoloQPlayerItem
import com.loldraft.data.models.Role
import com.loldraft.data.soloq.models.SoloQChampionSummary
import com.loldraft.data.soloq.models.SoloQMatchRecord
import com.loldraft.data.soloq.models.SoloQRoleFilter
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun SoloQIntelligenceView(viewModel: AnalyticsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val intel = state.soloQIntelligence
    val account = state.selectedSoloQAccount
    val apiKey = viewModel.soloQService.soloQRepository.riotApiClient.apiKey

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 1. Top Control Bar: League, Team, Player, Riot ID, Filters, Actions
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left Controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // League Selector
                SoloQLeagueDropdown(
                    selectedLeague = state.selectedSoloQLeague,
                    availableLeagues = state.soloQAvailableLeagues,
                    onSelectLeague = viewModel::selectSoloQLeague,
                )

                // Team Selector
                SoloQTeamDropdown(
                    selectedTeam = state.selectedSoloQTeam ?: "未選擇隊伍",
                    availableTeams = state.soloQAvailableTeams,
                    onSelectTeam = viewModel::selectSoloQTeam,
                )

                // Player Selector
                SoloQPlayerDropdown(
                    selectedPlayer = state.selectedSoloQPlayer ?: "未選擇選手",
                    players = state.availableSoloQPlayers,
                    onSelectPlayer = viewModel::selectSoloQPlayer,
                )

                // Riot ID Badge / Edit / Delete
                if (account != null) {
                    Row(
                        modifier =
                            Modifier
                                .background(CardDark, RoundedCornerShape(6.dp))
                                .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "${account.riotId} (${account.platformId})",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "編輯",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp).clickable { viewModel.openAddPlayerDialog(true, account.playerId) },
                        )
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "刪除",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp).clickable { viewModel.deletePlayerAccount(account.playerId) },
                        )
                    }
                } else if (!state.selectedSoloQPlayer.isNullOrBlank()) {
                    Box(
                        modifier =
                            Modifier
                                .background(RedSideDark.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .border(1.dp, RedSideColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .clickable { viewModel.openAddPlayerDialog(true, state.selectedSoloQPlayer) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "+ 綁定 Riot ID",
                            color = RedSideColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Time Range Filter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("區間:", color = TextMuted, fontSize = 11.sp)
                    listOf(7, 14, 30).forEach { days ->
                        val isSelected = state.soloQTimeRangeDays == days
                        Box(
                            modifier =
                                Modifier
                                    .background(if (isSelected) CardDark else Color.Transparent, RoundedCornerShape(4.dp))
                                    .border(1.dp, if (isSelected) GoldAccent else BorderDark, RoundedCornerShape(4.dp))
                                    .clickable { viewModel.selectSoloQTimeRange(days) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = "${days}天",
                                color = if (isSelected) GoldAccent else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }

                // Role Filter
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SoloQRoleFilter.values().forEach { filter ->
                        val isSelected = state.soloQRoleFilter == filter
                        Box(
                            modifier =
                                Modifier
                                    .background(if (isSelected) CardDark else Color.Transparent, RoundedCornerShape(4.dp))
                                    .border(1.dp, if (isSelected) BlueSideColor else BorderDark, RoundedCornerShape(4.dp))
                                    .clickable { viewModel.selectSoloQRoleFilter(filter) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = filter.displayName,
                                color = if (isSelected) BlueSideColor else TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // Right Actions: Add Player, API Key, Sync
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.openAddPlayerDialog(true, null) },
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = GoldAccent),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                ) {
                    Text("+ 新增選手帳號", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { viewModel.openApiKeyDialog(true) },
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (apiKey.isNullOrBlank()) OrangeWarning else BorderDark),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (apiKey.isNullOrBlank()) OrangeWarning else TextSecondary),
                ) {
                    Text(if (apiKey.isNullOrBlank()) "設定 API Key" else "API Key", fontSize = 11.sp)
                }

                Button(
                    onClick = viewModel::syncRiotApiData,
                    enabled = !state.isSoloQSyncing && account != null,
                    shape = RoundedCornerShape(6.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = CardDark,
                            contentColor = GoldAccent,
                        ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.6f)),
                ) {
                    if (state.isSoloQSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = GoldAccent,
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("同步中...", fontSize = 11.sp)
                    } else {
                        Text("同步天梯數據", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 1.5 Sync Error Banner
        val syncError = state.soloQSyncError
        if (syncError != null) {
            val isKeyError = syncError.contains("金鑰") || syncError.contains("Key") || syncError.contains("401") || syncError.contains("403")
            val isAccountError = syncError.contains("找不到") || syncError.contains("404")

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(RedSideDark.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                        .border(1.dp, RedSideColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RedSideColor, modifier = Modifier.size(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "天梯數據同步失敗",
                            color = RedSideColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            text = syncError,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isKeyError) {
                        Button(
                            onClick = { viewModel.openApiKeyDialog(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = RedSideColor, contentColor = Color.White),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("更新金鑰", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    } else if (isAccountError) {
                        Button(
                            onClick = { viewModel.openAddPlayerDialog(true, state.selectedSoloQPlayer) },
                            colors = ButtonDefaults.buttonColors(containerColor = RedSideColor, contentColor = Color.White),
                            shape = RoundedCornerShape(4.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("修改帳號", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }
                    }

                    OutlinedButton(
                        onClick = viewModel::syncRiotApiData,
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("重新嘗試", fontSize = 11.sp, color = TextSecondary, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        // 2. Secret Weapons / Pocket Picks Radar Banner (橫幅卡片)
        if (intel != null && intel.secretPicks.isNotEmpty()) {
            SecretPicksRadarBanner(secretPicks = intel.secretPicks, primaryRole = intel.primaryRole)
        }

        // 3. Content Area: Clean empty states or Dual-pane
        when {
            state.selectedSoloQPlayer.isNullOrBlank() -> {
                EmptyStateCard(
                    title = "尚未選取選手",
                    subtitle = "請在上方選單選擇賽區、隊伍與選手，或點擊 [+ 新增選手帳號] 自訂新增選手。",
                    actionText = "+ 新增選手帳號",
                    onAction = { viewModel.openAddPlayerDialog(true, null) },
                )
            }
            account == null -> {
                EmptyStateCard(
                    title = "選手 ${state.selectedSoloQPlayer} 尚未綁定天梯帳號",
                    subtitle = "系統不提供假數據。請為該選手綁定 Riot ID，以透過官方 API 取得真實排位戰績。",
                    actionText = "立即綁定 Riot ID",
                    onAction = { viewModel.openAddPlayerDialog(true, state.selectedSoloQPlayer) },
                )
            }
            apiKey.isNullOrBlank() -> {
                EmptyStateCard(
                    title = "尚未設定 Riot Games API Key",
                    subtitle = "選手已綁定 ${account.riotId}，但尚未輸入開發者金鑰。請至官方開發者後台取得免費 Token。",
                    actionText = "設定 Riot API Key",
                    onAction = { viewModel.openApiKeyDialog(true) },
                )
            }
            intel == null || intel.recentMatches.isEmpty() -> {
                EmptyStateCard(
                    title = "尚未抓取到 ${account.playerId} · ${account.riotId} 的天梯紀錄",
                    subtitle = "請確認 Riot ID 與伺服器 ${account.platformId} 正確無誤，然後點擊下方按鈕進行同步。",
                    actionText = "立即同步天梯數據",
                    onAction = viewModel::syncRiotApiData,
                )
            }
            else -> {
                // Dual-Pane Content: Champion Summaries (Left) + Match Stream (Right)
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Left Pane: Champion Pool Summary Table
                    Box(
                        modifier =
                            Modifier
                                .weight(0.55f)
                                .fillMaxHeight()
                                .background(SurfaceDark, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                    ) {
                        ChampionPoolSummaryList(intel = intel)
                    }

                    // Right Pane: Match History Stream
                    Box(
                        modifier =
                            Modifier
                                .weight(0.45f)
                                .fillMaxHeight()
                                .background(SurfaceDark, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                    ) {
                        MatchHistoryStream(matches = intel.recentMatches)
                    }
                }
            }
        }
    }

    // Add / Edit Player Dialog
    if (state.isAddPlayerDialogOpen) {
        AddPlayerAccountDialog(
            league = state.addPlayerLeague,
            team = state.addPlayerTeam,
            name = state.addPlayerName,
            role = state.addPlayerRole,
            gameName = state.addPlayerGameName,
            tagLine = state.addPlayerTagLine,
            platform = state.addPlayerPlatform,
            region = state.addPlayerRegion,
            onFieldChange = viewModel::setAddPlayerFields,
            onSave = viewModel::savePlayerAccount,
            onDismiss = { viewModel.openAddPlayerDialog(false) },
        )
    }

    // Riot API Key Dialog
    if (state.isApiKeyDialogOpen) {
        RiotApiKeyDialog(
            currentKey = state.riotApiKeyInput,
            onValueChange = viewModel::setRiotApiKeyInput,
            onSave = viewModel::saveRiotApiKey,
            onDismiss = { viewModel.openApiKeyDialog(false) },
        )
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    subtitle: String,
    actionText: String,
    onAction: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                color = GoldAccent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(480.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardDark, contentColor = GoldAccent),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.8f)),
            ) {
                Text(actionText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SecretPicksRadarBanner(
    secretPicks: List<SoloQChampionSummary>,
    primaryRole: Role,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CardDark, RoundedCornerShape(8.dp))
                .border(1.dp, GoldAccent.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "秘密武器雷達：比賽未曾登場特殊練角",
                color = GoldAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "・ 僅在選手本職路線 [${primaryRole.name}] 密集使用且職業賽事 0 選",
                color = TextMuted,
                fontSize = 11.sp,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            secretPicks.forEach { pick ->
                SecretPickCard(pick = pick)
            }
        }
    }
}

@Composable
fun SecretPickCard(pick: SoloQChampionSummary) {
    val winRatePct = String.format(Locale.US, "%.0f%%", pick.winRate * 100)

    Row(
        modifier =
            Modifier
                .background(SurfaceDark, RoundedCornerShape(6.dp))
                .border(1.dp, RedSideColor.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChampionAvatar(
            championNameOrId = pick.championName,
            avatarSize = 40.dp,
            shape = RoundedCornerShape(6.dp),
            borderColor = GoldAccent,
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = pick.championName,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "(${pick.playedRole.name} 本職)",
                    color = BlueSideColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "${pick.gamesPlayed}場 · 勝率 $winRatePct",
                    color = GreenAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "KDA ${pick.kda}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
            }

            Text(
                text = "職業賽 0 場 · ${pick.secretBadgeText ?: "秘密武器"}",
                color = RedSideColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ChampionPoolSummaryList(intel: com.loldraft.data.soloq.models.SoloQPlayerIntelligence) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "天梯英雄總表 · ${intel.championSummaries.size} 位英雄",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )

            val wrPct = String.format(Locale.US, "%.1f%%", intel.overallWinRate * 100)
            val pWrPct = String.format(Locale.US, "%.1f%%", intel.primaryRoleWinRate * 100)
            Text(
                text = "總場次: ${intel.totalMatches} | 總勝率: $wrPct | 本職勝率: $pWrPct",
                color = TextSecondary,
                fontSize = 11.sp,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(CardDark, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("英雄", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(115.dp), maxLines = 1, softWrap = false)
            Text("路線", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(60.dp), maxLines = 1, softWrap = false)
            Text("場次", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(45.dp), maxLines = 1, softWrap = false)
            Text("勝負", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(55.dp), maxLines = 1, softWrap = false)
            Text("勝率", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(55.dp), maxLines = 1, softWrap = false)
            Text("KDA", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(50.dp), maxLines = 1, softWrap = false)
            Text("CS", color = TextMuted, fontSize = 11.sp, modifier = Modifier.width(50.dp), maxLines = 1, softWrap = false)
            Text("職業賽", color = TextMuted, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, softWrap = false)
        }

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(intel.championSummaries) { champ ->
                ChampionSummaryRow(champ = champ)
            }
        }
    }
}

@Composable
fun ChampionSummaryRow(champ: SoloQChampionSummary) {
    val winRatePct = String.format(Locale.US, "%.0f%%", champ.winRate * 100)
    val wrColor =
        when {
            champ.winRate >= 0.60 -> GreenAccent
            champ.winRate >= 0.50 -> GoldAccent
            else -> RedSideColor
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(if (champ.isSecretPick) CardDark else Color.Transparent, RoundedCornerShape(4.dp))
                .border(
                    1.dp,
                    if (champ.isSecretPick) GoldAccent.copy(alpha = 0.4f) else BorderDark.copy(alpha = 0.5f),
                    RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Champion
        Row(
            modifier = Modifier.width(115.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChampionAvatar(
                championNameOrId = champ.championName,
                avatarSize = 24.dp,
                shape = RoundedCornerShape(4.dp),
            )
            Text(
                text = champ.championName,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Role (Clean, exact role name with no redundant words, strictly 1 line)
        Box(modifier = Modifier.width(60.dp)) {
            Text(
                text = champ.playedRole.name,
                color = if (champ.isPrimaryRole) BlueSideColor else TextMuted,
                fontSize = 11.sp,
                fontWeight = if (champ.isPrimaryRole) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
            )
        }

        // Games (Exact number)
        Text(
            text = "${champ.gamesPlayed}",
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(45.dp),
        )

        // W-L (Exact numbers)
        Text(
            text = "${champ.wins}-${champ.losses}",
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(55.dp),
        )

        // Win Rate (Exact percentage)
        Text(
            text = winRatePct,
            color = wrColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(55.dp),
        )

        // KDA (Exact number)
        Text(
            text = "${champ.kda}",
            color = TextPrimary,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(50.dp),
        )

        // CS (Exact number)
        Text(
            text = "${champ.avgCs.toInt()}",
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.width(50.dp),
        )

        // Pro Games (Exact number, clean highlight without verbose text)
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "${champ.playerProGames}",
                color = if (champ.playerProGames == 0 && champ.isPrimaryRole) RedSideColor else TextMuted,
                fontSize = 11.sp,
                fontWeight = if (champ.playerProGames == 0 && champ.isPrimaryRole) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
            )
            if (champ.isSecretPick) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "秘密武器",
                    tint = GoldAccent,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
fun MatchHistoryStream(matches: List<SoloQMatchRecord>) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "近期對局紀錄 · ${matches.size}場",
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "單雙排天梯",
                color = TextMuted,
                fontSize = 11.sp,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(matches) { match ->
                MatchHistoryCard(match = match)
            }
        }
    }
}

@Composable
fun MatchHistoryCard(match: SoloQMatchRecord) {
    val isWin = match.win
    val bgSideColor = if (isWin) Color(0xFF0F261F) else Color(0xFF281318)
    val borderSideColor = if (isWin) GreenAccent.copy(alpha = 0.5f) else RedSideColor.copy(alpha = 0.4f)
    val resultText = if (isWin) "勝利" else "敗北"
    val resultColor = if (isWin) GreenAccent else RedSideColor

    val durationMin = match.durationSeconds / 60
    val durationSec = match.durationSeconds % 60
    val timeAgo = formatTimeAgo(match.timestamp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(bgSideColor, RoundedCornerShape(6.dp))
                .border(1.dp, borderSideColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Result & Time info
        Column(modifier = Modifier.width(85.dp)) {
            Text(
                text = resultText,
                color = resultColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = "${durationMin}分${durationSec}秒",
                color = TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = timeAgo,
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
            )
        }

        // Champion & Role
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            ChampionAvatar(
                championNameOrId = match.championName,
                avatarSize = 34.dp,
                shape = RoundedCornerShape(4.dp),
                borderColor = if (match.isSecretPick) GoldAccent else null,
            )

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = match.championName,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (match.isSecretPick) {
                        Text(
                            text = "(秘密武器)",
                            color = GoldAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Text(
                    text = if (match.isPrimaryRole) "(${match.playedRole.name} 本職)" else "(${match.playedRole.name} 副路)",
                    color = if (match.isPrimaryRole) BlueSideColor else TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Stats: KDA & CS
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "${match.kills} / ${match.deaths} / ${match.assists}",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
            val kdaStr = String.format(Locale.US, "%.1f", match.kda)
            Text(
                text = "KDA $kdaStr · ${match.cs} CS",
                color = TextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    return when {
        hours < 1 -> "剛剛"
        hours < 24 -> "${hours}小時前"
        else -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            "${days}天前"
        }
    }
}

@Composable
fun SoloQLeagueDropdown(
    selectedLeague: String?,
    availableLeagues: List<String>,
    onSelectLeague: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier =
                Modifier
                    .background(CardDark, RoundedCornerShape(6.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("賽區: ${selectedLeague ?: "全部"}", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("▾", color = GoldAccent, fontSize = 11.sp)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(CardDark).border(1.dp, BorderDark, RoundedCornerShape(4.dp)),
        ) {
            DropdownMenuItem(
                text = { Text("全部賽區", color = GoldAccent, fontSize = 12.sp) },
                onClick = {
                    onSelectLeague(null)
                    expanded = false
                },
            )
            availableLeagues.forEach { league ->
                DropdownMenuItem(
                    text = { Text(league, color = TextPrimary, fontSize = 12.sp) },
                    onClick = {
                        onSelectLeague(league)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun SoloQTeamDropdown(
    selectedTeam: String,
    availableTeams: List<String>,
    onSelectTeam: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier =
                Modifier
                    .background(CardDark, RoundedCornerShape(6.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("隊伍: $selectedTeam", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("▾", color = GoldAccent, fontSize = 11.sp)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(CardDark).border(1.dp, BorderDark, RoundedCornerShape(4.dp)),
        ) {
            availableTeams.forEach { team ->
                DropdownMenuItem(
                    text = { Text(team, color = TextPrimary, fontSize = 12.sp) },
                    onClick = {
                        onSelectTeam(team)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun SoloQPlayerDropdown(
    selectedPlayer: String,
    players: List<SoloQPlayerItem>,
    onSelectPlayer: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentItem = players.find { it.playerName.equals(selectedPlayer, ignoreCase = true) }

    Box {
        Row(
            modifier =
                Modifier
                    .background(CardDark, RoundedCornerShape(6.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "選手: $selectedPlayer ${currentItem?.let { "· ${it.role.name}" } ?: ""}",
                color = GoldAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("▾", color = GoldAccent, fontSize = 11.sp)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(CardDark).border(1.dp, BorderDark, RoundedCornerShape(4.dp)),
        ) {
            players.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.playerName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("· ${p.role.name}", color = BlueSideColor, fontSize = 11.sp)
                            if (p.isBound && p.riotId != null) {
                                Text(p.riotId, color = GreenAccent, fontSize = 10.sp)
                            } else {
                                Text("[未綁定]", color = TextMuted, fontSize = 10.sp)
                            }
                        }
                    },
                    onClick = {
                        onSelectPlayer(p.playerName)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun AddPlayerAccountDialog(
    league: String,
    team: String,
    name: String,
    role: Role,
    gameName: String,
    tagLine: String,
    platform: String,
    region: String,
    onFieldChange: (String?, String?, String?, Role?, String?, String?, String?, String?) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var roleExpanded by remember { mutableStateOf(false) }
    var platformExpanded by remember { mutableStateOf(false) }

    val platformOptions =
        listOf(
            Triple("南韓伺服器", "KR", "asia"),
            Triple("台港澳伺服器", "TW2", "asia"),
            Triple("北美伺服器", "NA1", "americas"),
            Triple("西歐伺服器", "EUW1", "europe"),
            Triple("北東歐伺服器", "EUN1", "europe"),
            Triple("日本伺服器", "JP1", "asia"),
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "新增 / 綁定選手天梯帳號",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "設定選手的賽區、隊伍、選手名稱以及對應的 Riot ID，系統將即時向官方伺服器抓取真實天梯對局。",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // League
                    Column(modifier = Modifier.weight(1f)) {
                        Text("賽區", color = TextMuted, fontSize = 11.sp)
                        DialogInputField(value = league, onValueChange = { onFieldChange(it, null, null, null, null, null, null, null) })
                    }
                    // Team
                    Column(modifier = Modifier.weight(1f)) {
                        Text("隊伍", color = TextMuted, fontSize = 11.sp)
                        DialogInputField(value = team, onValueChange = { onFieldChange(null, it, null, null, null, null, null, null) })
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Player Name
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text("選手名稱", color = TextMuted, fontSize = 11.sp)
                        DialogInputField(value = name, onValueChange = { onFieldChange(null, null, it, null, null, null, null, null) })
                    }
                    // Role Dropdown
                    Column(modifier = Modifier.weight(0.8f)) {
                        Text("主要位置", color = TextMuted, fontSize = 11.sp)
                        Box {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(CardDark, RoundedCornerShape(6.dp))
                                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                                        .clickable { roleExpanded = true }
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(role.name, color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("▾", color = TextMuted, fontSize = 10.sp)
                            }
                            DropdownMenu(
                                expanded = roleExpanded,
                                onDismissRequest = { roleExpanded = false },
                                modifier = Modifier.background(CardDark).border(1.dp, BorderDark),
                            ) {
                                Role.values().forEach { r ->
                                    DropdownMenuItem(
                                        text = { Text(r.name, color = TextPrimary, fontSize = 12.sp) },
                                        onClick = {
                                            onFieldChange(null, null, null, r, null, null, null, null)
                                            roleExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Riot Game Name
                    Column(modifier = Modifier.weight(1.3f)) {
                        Text("召喚師名稱", color = TextMuted, fontSize = 11.sp)
                        DialogInputField(
                            value = gameName,
                                                        onValueChange = { onFieldChange(null, null, null, null, it, null, null, null) },
                        )
                    }
                    // Riot Tag Line
                    Column(modifier = Modifier.weight(0.7f)) {
                        Text("標籤", color = TextMuted, fontSize = 11.sp)
                        DialogInputField(
                            value = tagLine,
                                                        onValueChange = { onFieldChange(null, null, null, null, null, it, null, null) },
                        )
                    }
                }

                // Platform / Region Dropdown
                Column {
                    Text("伺服器區域", color = TextMuted, fontSize = 11.sp)
                    Box {
                        val currentOpt = platformOptions.find { it.second.equals(platform, ignoreCase = true) }
                        val label = currentOpt?.first ?: "$platform ($region)"
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .background(CardDark, RoundedCornerShape(6.dp))
                                    .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                                    .clickable { platformExpanded = true }
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, color = TextPrimary, fontSize = 12.sp)
                            Text("▾", color = TextMuted, fontSize = 10.sp)
                        }
                        DropdownMenu(
                            expanded = platformExpanded,
                            onDismissRequest = { platformExpanded = false },
                            modifier = Modifier.background(CardDark).border(1.dp, BorderDark),
                        ) {
                            platformOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.first, color = TextPrimary, fontSize = 12.sp) },
                                    onClick = {
                                        onFieldChange(null, null, null, null, null, null, opt.second, opt.third)
                                        platformExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("儲存並開始同步", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun DialogInputField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(CardDark, RoundedCornerShape(6.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(color = TextPrimary, fontSize = 12.sp),
            cursorBrush = SolidColor(GoldAccent),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
    }
}

@Composable
fun RiotApiKeyDialog(
    currentKey: String,
    onValueChange: (String) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Riot Developer API Token",
                color = GoldAccent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "請輸入來自 developer.riotgames.com 的 RGAPI Token。系統將直接向 Riot API 發送請求並自動快取於本機，絕無假數據。",
                    color = TextSecondary,
                    fontSize = 12.sp,
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(CardDark, RoundedCornerShape(6.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                            .padding(10.dp),
                ) {
                    BasicTextField(
                        value = currentKey,
                        onValueChange = onValueChange,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(GoldAccent),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        decorationBox = { innerTextField -> innerTextField() },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(currentKey) },
                colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("儲存金鑰", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(8.dp),
    )
}

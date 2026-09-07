package com.loldraft.client.compose.ui.analytics

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loldraft.analytics.model.AnalyticsTab
import com.loldraft.client.compose.ui.analytics.components.PlayersGridView
import com.loldraft.client.compose.ui.analytics.components.TeamRosterPoolView
import com.loldraft.client.compose.ui.analytics.components.TeamsGridView
import com.loldraft.client.compose.ui.theme.BgDark
import com.loldraft.client.compose.ui.theme.BlueSideColor
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.GreenAccent
import com.loldraft.client.compose.ui.theme.LolDraftAiTheme
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.client.compose.ui.theme.TextSecondary
import com.loldraft.client.compose.viewmodel.AnalyticsViewModel
import kotlinx.coroutines.delay

@Composable
fun DataAnalyticsApp(viewModel: AnalyticsViewModel) {
    val state by viewModel.uiState.collectAsState()

    // Auto-clear notification after 4 seconds
    LaunchedEffect(state.notificationMessage) {
        if (state.notificationMessage != null) {
            delay(4000)
            viewModel.clearNotification()
        }
    }

    LolDraftAiTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(BgDark),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(SurfaceDark)
                            .border(1.dp, BorderDark)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .background(GoldAccent, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "ANALYTICS",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            text = "賽事數據與戰隊英雄池中心",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                    }

                    // Tab Navigation
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AnalyticsTab.values().forEach { tab ->
                            val isSelected = state.currentTab == tab
                            Box(
                                modifier =
                                    Modifier
                                        .background(
                                            if (isSelected) CardDark else Color.Transparent,
                                            RoundedCornerShape(6.dp),
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) GoldAccent else BorderDark,
                                            RoundedCornerShape(6.dp),
                                        )
                                        .clickable { viewModel.selectTab(tab) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) GoldAccent else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }

                    // Action Buttons (Copy TSV, Export CSV)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::copyToClipboard,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = TextPrimary,
                                ),
                        ) {
                            Text("⎘ 複製表格 (Excel)", fontSize = 12.sp)
                        }

                        Button(
                            onClick = viewModel::exportToCsv,
                            shape = RoundedCornerShape(6.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = CardDark,
                                    contentColor = GoldAccent,
                                ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                        ) {
                            Text("📥 匯出 CSV", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Main Content Body based on selected tab
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.currentTab) {
                        AnalyticsTab.TEAM_ROSTER_MATRIX -> TeamRosterPoolView(viewModel)
                        AnalyticsTab.PLAYERS_GRID -> PlayersGridView(viewModel)
                        AnalyticsTab.TEAMS_GRID -> TeamsGridView(viewModel)
                    }

                    // Floating Notification Snackbar
                    if (state.notificationMessage != null) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 20.dp)
                                    .background(CardDark, RoundedCornerShape(8.dp))
                                    .border(1.dp, GreenAccent, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text("✔", color = GreenAccent, fontSize = 14.sp)
                                Text(
                                    text = state.notificationMessage!!,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

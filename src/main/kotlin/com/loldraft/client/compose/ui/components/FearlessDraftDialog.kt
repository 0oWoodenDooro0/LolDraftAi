package com.loldraft.client.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.RedSideColor
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.client.compose.ui.theme.TextSecondary
import com.loldraft.platform.pro.api.ProChampionEntry

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FearlessDraftDialog(
    allChampions: List<ProChampionEntry>,
    excludedChampionIds: Set<String>,
    currentPicksCount: Int,
    onAddChampion: (String) -> Unit,
    onRemoveChampion: (String) -> Unit,
    onClearAll: () -> Unit,
    onImportCurrentPicks: () -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val candidateChampions = remember(searchQuery, excludedChampionIds, allChampions) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) {
            emptyList()
        } else {
            allChampions.filter { champ ->
                !excludedChampionIds.any { it.equals(champ.id, ignoreCase = true) || it.equals(champ.name, ignoreCase = true) } &&
                    (champ.name.lowercase().contains(q) || champ.id.lowercase().contains(q))
            }.take(8)
        }
    }

    // Modal Backdrop overlay
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        contentAlignment = Alignment.Center,
    ) {
        // Modal Content Card (stops click propagation)
        Column(
            modifier =
                Modifier
                    .width(620.dp)
                    .background(SurfaceDark, RoundedCornerShape(12.dp))
                    .border(1.dp, GoldAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* consume click */ },
                    )
                    .padding(20.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier =
                            Modifier
                                .background(GoldAccent, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "FEARLESS DRAFT",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "全局 BP 歷史角色排除管理",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .size(28.dp)
                            .background(CardDark, CircleShape)
                            .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "前幾局已被選用過的角色在後續局數不可重複選用。在此手動添加或從本局直接導入排除名單。",
                color = TextMuted,
                fontSize = 12.sp,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Search & Quick Add Section
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("搜尋英雄加入全局 BP 排除名單...", color = TextMuted, fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GoldAccent,
                        unfocusedBorderColor = BorderDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = CardDark,
                        unfocusedContainerColor = CardDark,
                    ),
            )

            // Autocomplete candidate suggestions
            if (candidateChampions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .background(CardDark, RoundedCornerShape(6.dp))
                            .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                            .padding(4.dp),
                ) {
                    items(candidateChampions, key = { it.id }) { champ ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onAddChampion(champ.id)
                                        searchQuery = ""
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(champ.name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("+ 排除", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Excluded Champions List Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "已排除英雄 (${excludedChampionIds.size})",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                if (excludedChampionIds.isNotEmpty()) {
                    Text(
                        text = "清空全部",
                        color = RedSideColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onClearAll() },
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Excluded Champions Chip Area
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .background(CardDark, RoundedCornerShape(8.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                if (excludedChampionIds.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "尚未排除任何英雄（所有英雄皆可正常選用）",
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        excludedChampionIds.forEach { champId ->
                            val champName = allChampions.find { it.id.equals(champId, ignoreCase = true) }?.name ?: champId
                            Row(
                                modifier =
                                    Modifier
                                        .background(SurfaceDark, RoundedCornerShape(4.dp))
                                        .border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = champName,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "✕",
                                    color = RedSideColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onRemoveChampion(champId) },
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = onImportCurrentPicks,
                    enabled = currentPicksCount > 0,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GoldAccent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "📥 導入本局選角 ($currentPicksCount 英雄)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoldAccent, contentColor = Color.Black),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Text("完成", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

package com.loldraft.client.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.client.compose.ui.theme.TextSecondary
import com.loldraft.data.models.Role
import com.loldraft.data.player.PlayerRosterIntelligence

@Composable
fun RosterPlayerPoolView(
    teamName: String,
    sideColor: Color,
    roster: Map<Role, PlayerRosterIntelligence>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$teamName 戰隊英雄池",
                color = sideColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Text(
                text = "選手常用角色",
                color = TextSecondary,
                fontSize = 10.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT).forEach { role ->
                val intel = roster[role]
                PlayerRolePoolItem(role = role, intel = intel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerRolePoolItem(
    role: Role,
    intel: PlayerRosterIntelligence?,
    modifier: Modifier = Modifier,
) {
    val playerName = intel?.playerId ?: "Unknown"

    val allChampions: List<String> =
        remember(intel) {
            val fromRecords =
                intel?.dossier?.careerStats?.championRecords?.values
                    ?.sortedByDescending { it.gamesPlayed }
                    ?.map { it.championId }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
            val fromSignatures =
                intel?.signaturePicks
                    ?.sortedByDescending { it.gamesPlayed }
                    ?.map { it.championId }
                    ?.filter { it.isNotBlank() }
                    ?: emptyList()
            (fromRecords + fromSignatures).distinct()
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CardDark, RoundedCornerShape(6.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        // Player header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(role.name, color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Text(playerName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            if (allChampions.isNotEmpty()) {
                Text(
                    text = "${allChampions.size} 角色",
                    color = TextSecondary,
                    fontSize = 10.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // All Champions - Icons Only
        if (allChampions.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                allChampions.forEach { champId ->
                    ChampionAvatar(
                        championNameOrId = champId,
                        avatarSize = 24.dp,
                        shape = RoundedCornerShape(4.dp),
                    )
                }
            }
        } else {
            Text(
                text = "尚無使用英雄記錄",
                color = TextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

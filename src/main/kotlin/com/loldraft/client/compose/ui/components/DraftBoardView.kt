package com.loldraft.client.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.loldraft.client.compose.state.BoardSlot
import com.loldraft.client.compose.ui.theme.BlueSideColor
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.RedSideColor
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.client.compose.ui.theme.TextSecondary
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side

@Composable
fun DraftBoardSideView(
    side: Side,
    teamName: String,
    slots: List<BoardSlot>,
    onUpdateRole: ((turnNumber: Int, newRole: Role) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val sideColor = if (side == Side.BLUE) BlueSideColor else RedSideColor
    val sidePicks = slots.filter { it.side == side && it.actionType == ActionType.PICK }
    val sideBans = slots.filter { it.side == side && it.actionType == ActionType.BAN }

    Column(
        modifier =
            modifier
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                .padding(12.dp),
    ) {
        // Team Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = teamName,
                color = sideColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
            Text(
                text = if (side == Side.BLUE) "BLUE SIDE" else "RED SIDE",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // PICKS Section (5 slots)
        Text("PICKS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            sidePicks.forEach { slot ->
                DraftSlotRow(
                    slot = slot,
                    sideColor = sideColor,
                    teamPickSlots = sidePicks,
                    onUpdateRole = onUpdateRole,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // BANS Section (5 slots)
        Text("BANS", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sideBans.forEach { slot ->
                BanSlotBox(slot = slot, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun DraftSlotRow(
    slot: BoardSlot,
    sideColor: Color,
    teamPickSlots: List<BoardSlot> = emptyList(),
    onUpdateRole: ((turnNumber: Int, newRole: Role) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isLocked = slot.championId != null
    var menuExpanded by remember { mutableStateOf(false) }

    val borderModifier =
        if (slot.isCurrentTurn) {
            Modifier.border(2.dp, GoldAccent, RoundedCornerShape(6.dp))
        } else {
            Modifier.border(1.dp, BorderDark, RoundedCornerShape(6.dp))
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .background(if (slot.isCurrentTurn) CardDark else SurfaceDark, RoundedCornerShape(6.dp))
                .then(borderModifier)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Turn badge
            Box(
                modifier =
                    Modifier
                        .background(if (slot.isCurrentTurn) GoldAccent else CardDark, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "T${slot.turnNumber}",
                    color = if (slot.isCurrentTurn) Color.Black else TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))

            // Champion Name & Role
            Column {
                Text(
                    text = slot.championName ?: if (slot.isCurrentTurn) "PICKING..." else "Empty",
                    color =
                        if (isLocked) {
                            TextPrimary
                        } else if (slot.isCurrentTurn) {
                            GoldAccent
                        } else {
                            TextMuted
                        },
                    fontWeight = if (isLocked || slot.isCurrentTurn) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                )
                if (slot.playerName != null || slot.role != null) {
                    Text(
                        text = listOfNotNull(slot.playerName, slot.role?.name).joinToString(" • "),
                        color = TextSecondary,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        if (isLocked) {
            Box {
                Row(
                    modifier =
                        Modifier
                            .background(CardDark, RoundedCornerShape(4.dp))
                            .border(1.dp, if (menuExpanded) GoldAccent else BorderDark, RoundedCornerShape(4.dp))
                            .clickable(enabled = onUpdateRole != null) { menuExpanded = true }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = slot.role?.name ?: "選擇位置",
                        color = if (slot.role != null) sideColor else GoldAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "▾",
                        color = TextSecondary,
                        fontSize = 9.sp,
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier =
                        Modifier
                            .background(SurfaceDark)
                            .border(1.dp, BorderDark, RoundedCornerShape(6.dp)),
                ) {
                    Role.entries.forEach { roleOption ->
                        val otherSlot = teamPickSlots.find { it.role == roleOption && it.turnNumber != slot.turnNumber && it.championId != null }
                        val isCurrentRole = slot.role == roleOption
                        val roleLabel =
                            when (roleOption) {
                                Role.TOP -> "TOP 上路"
                                Role.JUNGLE -> "JGL 打野"
                                Role.MID -> "MID 中路"
                                Role.BOT -> "BOT 下路"
                                Role.SUPPORT -> "SUP 輔助"
                            }
                        val subText =
                            when {
                                isCurrentRole -> "(目前位置)"
                                otherSlot != null -> "(⇄ 與 ${otherSlot.championName ?: otherSlot.championId} 交換)"
                                else -> "(空缺)"
                            }

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = roleLabel,
                                        color = if (isCurrentRole) GoldAccent else TextPrimary,
                                        fontWeight = if (isCurrentRole) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp,
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = subText,
                                        color = if (isCurrentRole) GoldAccent else TextSecondary,
                                        fontSize = 10.sp,
                                    )
                                }
                            },
                            onClick = {
                                menuExpanded = false
                                if (!isCurrentRole) {
                                    onUpdateRole?.invoke(slot.turnNumber, roleOption)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BanSlotBox(
    slot: BoardSlot,
    modifier: Modifier = Modifier,
) {
    val isLocked = slot.championId != null
    val borderModifier =
        if (slot.isCurrentTurn) {
            Modifier.border(2.dp, GoldAccent, RoundedCornerShape(6.dp))
        } else {
            Modifier.border(1.dp, BorderDark, RoundedCornerShape(6.dp))
        }

    Box(
        modifier =
            modifier
                .height(60.dp)
                .background(if (slot.isCurrentTurn) CardDark else SurfaceDark, RoundedCornerShape(6.dp))
                .then(borderModifier)
                .padding(horizontal = 2.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "T${slot.turnNumber}",
                color = if (slot.isCurrentTurn) GoldAccent else TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = slot.championName?.take(6) ?: if (slot.isCurrentTurn) "BAN" else "—",
                color =
                    if (isLocked) {
                        Color(0xFFFF5252)
                    } else if (slot.isCurrentTurn) {
                        GoldAccent
                    } else {
                        TextMuted
                    },
                fontSize = 11.sp,
                fontWeight = if (isLocked) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
            if (isLocked) {
                Text(
                    text = "BAN",
                    color = Color(0xFFFF5252).copy(alpha = 0.8f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

package com.loldraft.client.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
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
import com.loldraft.client.compose.state.DraftClientState
import com.loldraft.client.compose.ui.theme.BlueSideColor
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.RedSideColor
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextPrimary
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import com.loldraft.client.compose.ui.theme.TextSecondary
import com.loldraft.data.models.Side

@Composable
fun TopBarView(
    state: DraftClientState,
    onSelectBlueLeague: (String?) -> Unit,
    onSelectRedLeague: (String?) -> Unit,
    onSelectBlueTeam: (String) -> Unit,
    onSelectRedTeam: (String) -> Unit,
    onSelectPatch: (String) -> Unit,
    onSetFirstPickSide: (Side) -> Unit,
    onSwapTeams: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onOpenFearlessDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Logo & Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .background(GoldAccent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = "LOL DRAFT AI",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "BP Intelligence System",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
            )
        }

        // Middle Selectors: Patch, First Pick Side, Blue Region, Blue Team, Swap, Red Region, Red Team, Fearless
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Patch Dropdown (defaults to 16.17)
            DropdownSelector(
                label = "Patch",
                selectedItem = "v${state.selectedPatch}",
                items = state.availablePatches.map { "v$it" },
                accentColor = GoldAccent,
                onItemSelected = { onSelectPatch(it.removePrefix("v")) },
            )

            // First Pick Side Selector
            Row(
                modifier =
                    Modifier
                        .background(CardDark, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                        .clickable {
                            val nextSide = if (state.firstPickSide == Side.BLUE) Side.RED else Side.BLUE
                            onSetFirstPickSide(nextSide)
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "First Pick: ",
                    color = TextSecondary,
                    fontSize = 11.sp,
                )
                Text(
                    text = if (state.firstPickSide == Side.BLUE) "BLUE" else "RED",
                    color = if (state.firstPickSide == Side.BLUE) BlueSideColor else RedSideColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }

            val leagueOptions = listOf("ALL") + state.availableLeagues

            // Blue Region Dropdown
            DropdownSelector(
                label = "Blue Region",
                selectedItem = state.blueSelectedLeague ?: "ALL",
                items = leagueOptions,
                accentColor = BlueSideColor,
                onItemSelected = { selected ->
                    onSelectBlueLeague(if (selected == "ALL") null else selected)
                },
            )

            // Blue Team Dropdown
            DropdownSelector(
                label = "Blue Side",
                selectedItem = state.blueTeam?.name ?: "Select Blue Team",
                items = state.blueFilteredTeams.map { it.name },
                accentColor = BlueSideColor,
                onItemSelected = { teamName ->
                    val found = state.blueFilteredTeams.find { it.name == teamName }
                        ?: state.allTeams.find { it.name == teamName }
                    found?.let { onSelectBlueTeam(it.id) }
                },
            )

            // Swap Teams Button
            OutlinedButton(
                onClick = onSwapTeams,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = GoldAccent,
                    ),
            ) {
                Text("⇄ Swap", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Red Region Dropdown
            DropdownSelector(
                label = "Red Region",
                selectedItem = state.redSelectedLeague ?: "ALL",
                items = leagueOptions,
                accentColor = RedSideColor,
                onItemSelected = { selected ->
                    onSelectRedLeague(if (selected == "ALL") null else selected)
                },
            )

            // Red Team Dropdown
            DropdownSelector(
                label = "Red Side",
                selectedItem = state.redTeam?.name ?: "Select Red Team",
                items = state.redFilteredTeams.map { it.name },
                accentColor = RedSideColor,
                onItemSelected = { teamName ->
                    val found = state.redFilteredTeams.find { it.name == teamName }
                        ?: state.allTeams.find { it.name == teamName }
                    found?.let { onSelectRedTeam(it.id) }
                },
            )

            // Fearless Draft (全局BP) Button
            val fearlessCount = state.fearlessExcludedChampionIds.size
            OutlinedButton(
                onClick = onOpenFearlessDialog,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                border = BorderStroke(1.dp, if (fearlessCount > 0) GoldAccent else BorderDark),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        containerColor = if (fearlessCount > 0) GoldAccent.copy(alpha = 0.15f) else Color.Transparent,
                        contentColor = if (fearlessCount > 0) GoldAccent else TextSecondary,
                    ),
            ) {
                Text(
                    text = if (fearlessCount == 0) "⚡ 全局BP" else "⚡ 全局BP ($fearlessCount)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Action Buttons: Undo & Reset
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onUndo,
                enabled = state.currentTurnNumber > 1,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(6.dp),
                colors =
                    ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary,
                    ),
            ) {
                Text("Undo (T${state.currentTurnNumber - 1})", fontSize = 12.sp)
            }

            Button(
                onClick = onReset,
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(6.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = CardDark,
                        contentColor = RedSideColor,
                    ),
            ) {
                Text("Reset", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DropdownSelector(
    label: String,
    selectedItem: String,
    items: List<String>,
    onItemSelected: (String) -> Unit,
    accentColor: Color = TextPrimary,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .background(CardDark, RoundedCornerShape(6.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$label: ",
                color = TextSecondary,
                fontSize = 11.sp,
            )
            Text(
                text = selectedItem,
                color = accentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("▼", color = TextSecondary, fontSize = 9.sp)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SurfaceDark),
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item, color = TextPrimary, fontSize = 12.sp) },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                )
            }
        }
    }
}

package com.loldraft.client.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.platform.pro.api.ProChampionEntry

@Composable
fun ChampionGridView(
    champions: List<ProChampionEntry>,
    searchQuery: String,
    onSearchChanged: (String) -> Unit,
    selectedChampionId: String?,
    onChampionSelected: (String) -> Unit,
    bannedChampionIds: Set<String>,
    pickedChampionIds: Set<String>,
    currentTurnSpec: DraftTurnSpec,
    currentTurnNumber: Int,
    onLockIn: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val actingSideColor = if (currentTurnSpec.side == Side.BLUE) BlueSideColor else RedSideColor
    val actionName = if (currentTurnSpec.actionType == ActionType.BAN) "BAN" else "PICK"

    Column(
        modifier =
            modifier
                .fillMaxHeight()
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                .padding(12.dp),
    ) {
        // Search Champion Input (Full-width, default Material sizing)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChanged,
            placeholder = { Text("Search Champion by name...", fontSize = 13.sp, color = TextMuted) },
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

        Spacer(modifier = Modifier.height(10.dp))

        // Grid of Champions
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 88.dp),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(champions, key = { it.id }) { champ ->
                val isBanned = bannedChampionIds.any { it.equals(champ.id, ignoreCase = true) || it.equals(champ.name, ignoreCase = true) }
                val isPicked = pickedChampionIds.any { it.equals(champ.id, ignoreCase = true) || it.equals(champ.name, ignoreCase = true) }
                val isUnavailable = isBanned || isPicked
                val isSelected = selectedChampionId?.equals(champ.id, ignoreCase = true) == true

                ChampionCard(
                    champion = champ,
                    isBanned = isBanned,
                    isPicked = isPicked,
                    isSelected = isSelected,
                    onClick = { if (!isUnavailable) onChampionSelected(champ.id) },
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Lock In Button
        val canLockIn = selectedChampionId != null && currentTurnNumber <= 20
        Button(
            onClick = {
                if (selectedChampionId != null) {
                    onLockIn(selectedChampionId)
                }
            },
            enabled = canLockIn,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(6.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = actingSideColor,
                    contentColor = if (currentTurnSpec.side == Side.BLUE) Color.Black else Color.White,
                    disabledContainerColor = CardDark,
                    disabledContentColor = TextMuted,
                ),
        ) {
            val label =
                if (selectedChampionId != null) {
                    "LOCK IN $selectedChampionId (Turn $currentTurnNumber: ${currentTurnSpec.side.name} $actionName)"
                } else {
                    "SELECT A CHAMPION TO LOCK IN (Turn $currentTurnNumber: ${currentTurnSpec.side.name} $actionName)"
                }
            Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
fun ChampionCard(
    champion: ProChampionEntry,
    isBanned: Boolean,
    isPicked: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isUnavailable = isBanned || isPicked
    val borderColor =
        when {
            isSelected -> GoldAccent
            isBanned -> Color(0xFFFF5252)
            isPicked -> BlueSideColor
            else -> BorderDark
        }

    Box(
        modifier =
            modifier
                .height(84.dp)
                .background(CardDark, RoundedCornerShape(6.dp))
                .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(6.dp))
                .clickable(enabled = !isUnavailable) { onClick() }
                .alpha(if (isUnavailable) 0.35f else 1.0f)
                .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            // Champion Avatar Placeholder
            Box(
                modifier =
                    Modifier
                        .width(36.dp)
                        .height(36.dp)
                        .background(BorderDark, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = champion.name.take(2).uppercase(),
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = champion.name,
                color = if (isSelected) GoldAccent else TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )

            if (isBanned) {
                Text("BANNED", color = Color(0xFFFF5252), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            } else if (isPicked) {
                Text("PICKED", color = BlueSideColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            } else if (champion.primaryRole != null) {
                Text(champion.primaryRole.name, color = TextMuted, fontSize = 9.sp)
            }
        }
    }
}

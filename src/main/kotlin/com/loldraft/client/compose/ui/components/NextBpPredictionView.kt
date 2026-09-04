package com.loldraft.client.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import com.loldraft.data.models.Side
import com.loldraft.models.ChampionIntentCandidate
import java.util.Locale

@Composable
fun NextBpPredictionView(
    currentTurnNumber: Int,
    currentTurnSpec: DraftTurnSpec,
    intentPredictions: List<ChampionIntentCandidate>,
    modifier: Modifier = Modifier,
) {
    val isBan = currentTurnSpec.actionType == ActionType.BAN
    val actingSideColor = if (currentTurnSpec.side == Side.BLUE) BlueSideColor else RedSideColor
    val actingSideName = currentTurnSpec.side.name
    val actionName = if (isBan) "BAN" else "PICK"

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                .padding(12.dp),
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
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "AI BP 意圖預測",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Turn $currentTurnNumber: $actingSideName $actionName 預測分析",
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Box(
                modifier =
                    Modifier
                        .background(actingSideColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "$actingSideName NEXT",
                    color = actingSideColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (intentPredictions.isEmpty() || currentTurnNumber > 20) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(CardDark, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (currentTurnNumber > 20) "Draft Complete (BP 已結束)" else "計算意圖預測中...",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }
        } else {
            // Vertical stacked candidate list (直的排列)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                intentPredictions.take(3).forEachIndexed { index, candidate ->
                    val probPercent = String.format(Locale.US, "%.1f%%", candidate.probability * 100)
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(CardDark, RoundedCornerShape(6.dp))
                                .border(
                                    1.dp,
                                    if (index == 0) GoldAccent.copy(alpha = 0.6f) else BorderDark,
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        // Top row: Rank, Champ Name, Role badge, and Probability
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier =
                                        Modifier
                                            .background(
                                                if (index == 0) GoldAccent else BorderDark,
                                                RoundedCornerShape(4.dp),
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        color = if (index == 0) Color.Black else TextPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = candidate.championId,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (candidate.predictedRole != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier =
                                            Modifier
                                                .background(SurfaceDark, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = candidate.predictedRole.name,
                                            color = TextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }

                            Text(
                                text = probPercent,
                                color = if (index == 0) GoldAccent else TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Thick probability progress bar
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .background(BorderDark, RoundedCornerShape(3.dp)),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth(candidate.probability.toFloat().coerceIn(0f, 1f))
                                        .height(5.dp)
                                        .background(
                                            if (index == 0) GoldAccent else actingSideColor,
                                            RoundedCornerShape(3.dp),
                                        ),
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = candidate.rationale,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

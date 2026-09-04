package com.loldraft.client.compose.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loldraft.client.compose.state.EvalBarState
import com.loldraft.client.compose.ui.theme.BlueSideColor
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.RedSideColor
import com.loldraft.client.compose.ui.theme.SurfaceDark
import java.util.Locale

@Composable
fun EvalBarView(
    evalBar: EvalBarState,
    modifier: Modifier = Modifier,
) {
    val bluePercentage by animateFloatAsState(
        targetValue = (evalBar.blueWinRate * 100).toFloat().coerceIn(10f, 90f),
    )
    val redPercentage = 100f - bluePercentage

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Labels above the bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .background(BlueSideColor, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("BLUE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = String.format(Locale.US, "%.1f%%", evalBar.blueWinRate * 100),
                    color = BlueSideColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Center Advantage / Score Chip
            Box(
                modifier =
                    Modifier
                        .background(CardDark, RoundedCornerShape(12.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 3.dp),
            ) {
                Text(
                    text = evalBar.phaseDescription,
                    color =
                        when {
                            evalBar.evalScore > 0.15 -> BlueSideColor
                            evalBar.evalScore < -0.15 -> RedSideColor
                            else -> GoldAccent
                        },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(Locale.US, "%.1f%%", evalBar.redWinRate * 100),
                    color = RedSideColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier =
                        Modifier
                            .background(RedSideColor, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("RED", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Split Bar
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(CardDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(5.dp)),
        ) {
            Row(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                Box(
                    modifier =
                        Modifier
                            .weight(bluePercentage)
                            .fillMaxHeight()
                            .background(BlueSideColor),
                )
                Box(
                    modifier =
                        Modifier
                            .weight(redPercentage)
                            .fillMaxHeight()
                            .background(RedSideColor),
                )
            }
        }
    }
}

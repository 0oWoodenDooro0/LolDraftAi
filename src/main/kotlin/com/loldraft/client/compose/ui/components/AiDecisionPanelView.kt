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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.loldraft.client.compose.ui.theme.GreenAccent
import com.loldraft.client.compose.ui.theme.OrangeWarning
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.client.compose.ui.theme.TextSecondary
import com.loldraft.models.ChampionIntentCandidate
import com.loldraft.models.CompositionFlaw
import com.loldraft.models.FlawSeverity
import com.loldraft.models.PickRecommendation
import java.util.Locale

@Composable
fun AiDecisionPanelView(
    intentPredictions: List<ChampionIntentCandidate>,
    recommendations: List<PickRecommendation>,
    compositionFlaws: List<CompositionFlaw>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                .padding(12.dp),
    ) {
        // Section 1: Intent Prediction
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .background(GoldAccent, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("AI INTENT", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enemy Next Action Predictions", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (intentPredictions.isEmpty()) {
            Text("Calculating opponent intent...", color = TextMuted, fontSize = 11.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                intentPredictions.take(3).forEachIndexed { index, candidate ->
                    IntentPredictionCard(rank = index + 1, candidate = candidate)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 2: Counter & Win Rate Recommendations
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .background(BlueSideColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("RECOMMEND", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Optimal Counter & Synergy Picks", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (recommendations.isEmpty()) {
            Text("Calculating counter recommendations...", color = TextMuted, fontSize = 11.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                recommendations.take(3).forEach { rec ->
                    CounterRecommendationCard(rec = rec)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 3: Composition Warnings
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .background(OrangeWarning, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("FLAWS", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Composition Warnings", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (compositionFlaws.isEmpty()) {
            Text("No critical composition flaws detected.", color = GreenAccent, fontSize = 11.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                compositionFlaws.forEach { flaw ->
                    CompositionFlawCard(flaw = flaw)
                }
            }
        }
    }
}

@Composable
fun IntentPredictionCard(
    rank: Int,
    candidate: ChampionIntentCandidate,
    modifier: Modifier = Modifier,
) {
    val probPercent = String.format(Locale.US, "%.1f%%", candidate.probability * 100)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CardDark, RoundedCornerShape(6.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#$rank", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Text(candidate.championId, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (candidate.predictedRole != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("(${candidate.predictedRole.name})", color = TextSecondary, fontSize = 10.sp)
                }
            }

            Text(probPercent, color = GoldAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Probability bar
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(BorderDark, RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(candidate.probability.toFloat().coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(GoldAccent, RoundedCornerShape(2.dp)),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Rationale text (includes player career & SoloQ spike!)
        Text(
            text = candidate.rationale,
            color = TextSecondary,
            fontSize = 10.sp,
            lineHeight = 13.sp,
        )
    }
}

@Composable
fun CounterRecommendationCard(
    rec: PickRecommendation,
    modifier: Modifier = Modifier,
) {
    val gainText =
        if (rec.winRateGain >= 0) {
            String.format(Locale.US, "+%.1f%% ΔWR", rec.winRateGain * 100)
        } else {
            String.format(Locale.US, "%.1f%% ΔWR", rec.winRateGain * 100)
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CardDark, RoundedCornerShape(6.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                .padding(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rec.championId, color = BlueSideColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Text("(${rec.recommendedRole.name})", color = TextSecondary, fontSize = 10.sp)
            }

            Text(gainText, color = GreenAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (rec.reasons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = rec.reasons.first(),
                color = TextSecondary,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
fun CompositionFlawCard(
    flaw: CompositionFlaw,
    modifier: Modifier = Modifier,
) {
    val color =
        when (flaw.severity) {
            FlawSeverity.CRITICAL -> Color(0xFFFF5252)
            FlawSeverity.WARNING -> OrangeWarning
            FlawSeverity.INFO -> BlueSideColor
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CardDark, RoundedCornerShape(6.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .background(color, RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
        ) {
            Text(flaw.severity.name, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(flaw.title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(flaw.suggestion, color = TextSecondary, fontSize = 9.sp)
        }
    }
}

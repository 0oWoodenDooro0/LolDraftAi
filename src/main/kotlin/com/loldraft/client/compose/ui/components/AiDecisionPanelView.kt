package com.loldraft.client.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loldraft.client.compose.ui.theme.BlueSideColor
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.GreenAccent
import com.loldraft.client.compose.ui.theme.OrangeWarning
import com.loldraft.client.compose.ui.theme.RedSideColor
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.client.compose.ui.theme.TextSecondary
import com.loldraft.data.models.ActionType
import com.loldraft.data.models.DraftTurnSpec
import com.loldraft.data.models.Role
import com.loldraft.data.models.Side
import com.loldraft.models.ChampionIntentCandidate
import com.loldraft.models.CompositionFlaw
import com.loldraft.models.FlawSeverity
import com.loldraft.models.PickRecommendation
import java.util.Locale

@Composable
fun AiDecisionPanelView(
    currentTurnNumber: Int,
    currentTurnSpec: DraftTurnSpec,
    intentPredictions: List<ChampionIntentCandidate>,
    recommendations: List<PickRecommendation>,
    compositionFlaws: List<CompositionFlaw>,
    selectedChampionId: String? = null,
    onChampionSelected: ((championId: String, preferredRole: Role?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isBan = currentTurnSpec.actionType == ActionType.BAN
    val actingSideColor = if (currentTurnSpec.side == Side.BLUE) BlueSideColor else RedSideColor
    val actingSideName = currentTurnSpec.side.name
    val actionName = if (isBan) "BAN" else "PICK"

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(SurfaceDark, RoundedCornerShape(8.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(8.dp))
                .padding(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(195.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Section 1: Intent Prediction for acting team
            Column(
                modifier =
                    Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .background(CardDark, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                        .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .background(GoldAccent, RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("AI INTENT", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Turn $currentTurnNumber: $actingSideName $actionName Prediction",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (intentPredictions.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Draft is complete or calculating intent...", color = TextMuted, fontSize = 11.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        intentPredictions.take(3).forEachIndexed { index, candidate ->
                            IntentPredictionCard(
                                rank = index + 1,
                                candidate = candidate,
                                isSelected = selectedChampionId?.equals(candidate.championId, ignoreCase = true) == true,
                                onClick = onChampionSelected?.let { { it(candidate.championId, candidate.predictedRole) } },
                            )
                        }
                    }
                }
            }

            // Section 2: Tactical Recommendations (Bans or Counter Picks)
            Column(
                modifier =
                    Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .background(CardDark, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                        .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val badgeColor = if (isBan) RedSideColor else BlueSideColor
                    val badgeText = if (isBan) "RECOMMEND BAN" else "RECOMMEND PICK"
                    val titleText = if (isBan) "Target & Priority Bans ($actingSideName)" else "Optimal Synergy & Counter ($actingSideName)"

                    Box(
                        modifier =
                            Modifier
                                .background(badgeColor, RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(badgeText, color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = titleText,
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (recommendations.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("Calculating tactical recommendations...", color = TextMuted, fontSize = 11.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        recommendations.take(3).forEach { rec ->
                            TacticalRecommendationCard(
                                rec = rec,
                                isBan = isBan,
                                sideColor = actingSideColor,
                                isSelected = selectedChampionId?.equals(rec.championId, ignoreCase = true) == true,
                                onClick = onChampionSelected?.let { { it(rec.championId, rec.recommendedRole) } },
                            )
                        }
                    }
                }
            }

            // Section 3: Composition Synergy & Flaws
            Column(
                modifier =
                    Modifier
                        .weight(0.9f)
                        .fillMaxHeight()
                        .background(CardDark, RoundedCornerShape(6.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                        .padding(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .background(OrangeWarning, RoundedCornerShape(3.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text("COMPOSITION", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Synergy & Flaw Alerts",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (compositionFlaws.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(SurfaceDark, RoundedCornerShape(4.dp))
                                .padding(8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✓ Balanced Synergy", color = GreenAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "No critical composition flaws or engage voids detected.",
                                color = TextSecondary,
                                fontSize = 10.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        compositionFlaws.take(3).forEach { flaw ->
                            CompositionFlawCard(flaw = flaw)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IntentPredictionCard(
    rank: Int,
    candidate: ChampionIntentCandidate,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val probPercent = String.format(Locale.US, "%.1f%%", candidate.probability * 100)

    val cardBorderColor = if (isSelected) GoldAccent else BorderDark
    val cardBg = if (isSelected) GoldAccent.copy(alpha = 0.12f) else SurfaceDark

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(4.dp))
                .border(if (isSelected) 2.dp else 1.dp, cardBorderColor, RoundedCornerShape(4.dp))
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#$rank", color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(5.dp))
                Text(candidate.championId, color = if (isSelected) GoldAccent else TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (candidate.predictedRole != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("(${candidate.predictedRole.name})", color = TextSecondary, fontSize = 9.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Text("已選中", color = GoldAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(probPercent, color = GoldAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Probability bar
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(BorderDark, RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(candidate.probability.toFloat().coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(GoldAccent, RoundedCornerShape(2.dp)),
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Rationale text
        Text(
            text = candidate.rationale,
            color = TextSecondary,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun TacticalRecommendationCard(
    rec: PickRecommendation,
    isBan: Boolean,
    sideColor: Color,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scoreText =
        if (isBan) {
            String.format(Locale.US, "%.1f%% Threat", rec.winRateGain * 100)
        } else {
            if (rec.winRateGain >= 0) {
                String.format(Locale.US, "+%.1f%% ΔWR", rec.winRateGain * 100)
            } else {
                String.format(Locale.US, "%.1f%% ΔWR", rec.winRateGain * 100)
            }
        }

    val cardBorderColor = if (isSelected) GoldAccent else BorderDark
    val cardBg = if (isSelected) GoldAccent.copy(alpha = 0.12f) else SurfaceDark

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(cardBg, RoundedCornerShape(4.dp))
                .border(if (isSelected) 2.dp else 1.dp, cardBorderColor, RoundedCornerShape(4.dp))
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rec.championId, color = if (isSelected) GoldAccent else sideColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(5.dp))
                Text("(${rec.recommendedRole.name})", color = TextSecondary, fontSize = 9.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSelected) {
                    Text("已選中", color = GoldAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(scoreText, color = GreenAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        if (rec.reasons.isNotEmpty()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = rec.reasons.first(),
                color = TextSecondary,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                .background(SurfaceDark, RoundedCornerShape(4.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .background(color, RoundedCornerShape(2.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            Text(flaw.severity.name, color = Color.Black, fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = flaw.title,
                color = TextPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = flaw.suggestion,
                color = TextSecondary,
                fontSize = 8.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

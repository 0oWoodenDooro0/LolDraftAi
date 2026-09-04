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
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.GoldAccent
import com.loldraft.client.compose.ui.theme.OrangeWarning
import com.loldraft.client.compose.ui.theme.SurfaceDark
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.client.compose.ui.theme.TextSecondary
import com.loldraft.data.models.Role
import com.loldraft.data.player.PlayerRosterIntelligence
import java.util.Locale

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
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "$teamName Player Pool",
                color = sideColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            Text(
                text = "Career & SoloQ Intel",
                color = TextSecondary,
                fontSize = 10.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(Role.TOP, Role.JUNGLE, Role.MID, Role.BOT, Role.SUPPORT).forEach { role ->
                val intel = roster[role]
                PlayerRolePoolItem(role = role, intel = intel)
            }
        }
    }
}

@Composable
fun PlayerRolePoolItem(
    role: Role,
    intel: PlayerRosterIntelligence?,
    modifier: Modifier = Modifier,
) {
    val playerName = intel?.playerId ?: "Unknown"
    val signatures = intel?.signaturePicks?.take(3) ?: emptyList()
    val soloQ = intel?.recentSoloQ7Days?.take(2) ?: emptyList()
    val spikes = intel?.practiceSpikes ?: emptyList()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(CardDark, RoundedCornerShape(6.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        // Player header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .background(BorderDark, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(role.name, color = GoldAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(playerName, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Spike indicator if any
            if (spikes.isNotEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .background(OrangeWarning, RoundedCornerShape(3.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text("⚡ SPIKE", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(3.dp))

        // Career Signatures
        if (signatures.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Career:", color = TextMuted, fontSize = 9.sp)
                signatures.forEach { sig ->
                    val wr = String.format(Locale.US, "%.0f%%", sig.winRate * 100)
                    Text("${sig.championId} (${sig.gamesPlayed}G, $wr)", color = TextSecondary, fontSize = 9.sp)
                }
            }
        }

        // SoloQ Heat
        if (soloQ.isNotEmpty() || spikes.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("SoloQ:", color = GoldAccent, fontSize = 9.sp)
                if (spikes.isNotEmpty()) {
                    val spk = spikes.first()
                    Text(
                        "${spk.championId} [${spk.recentGamesCount}G spike]",
                        color = OrangeWarning,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    soloQ.forEach { sq ->
                        val wr = String.format(Locale.US, "%.0f%%", sq.winRate * 100)
                        Text("${sq.championId} (${sq.gamesPlayed}G, $wr)", color = TextSecondary, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

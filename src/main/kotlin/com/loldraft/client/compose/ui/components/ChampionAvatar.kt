package com.loldraft.client.compose.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.loldraft.client.compose.image.rememberChampionBitmap
import com.loldraft.client.compose.ui.theme.BorderDark
import com.loldraft.client.compose.ui.theme.CardDark
import com.loldraft.client.compose.ui.theme.RedSideColor
import com.loldraft.client.compose.ui.theme.TextMuted
import com.loldraft.client.compose.ui.theme.TextPrimary
import com.loldraft.data.normalization.ChampionNormalizer

@Composable
fun ChampionAvatar(
    championNameOrId: String?,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 32.dp,
    shape: Shape = RoundedCornerShape(4.dp),
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    isBanned: Boolean = false,
    isPicked: Boolean = false,
    alpha: Float = 1.0f,
    contentDescription: String? = null,
) {
    val isEmpty = championNameOrId.isNullOrBlank() || ChampionNormalizer.isNoneOrEmpty(championNameOrId)
    val bitmap = rememberChampionBitmap(championNameOrId)

    val borderModifier =
        if (borderColor != null) {
            Modifier.border(borderWidth, borderColor, shape)
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .size(avatarSize)
                .alpha(if (isEmpty) 0.4f else alpha)
                .then(borderModifier)
                .clip(shape)
                .background(CardDark),
        contentAlignment = Alignment.Center,
    ) {
        if (!isEmpty && bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription ?: championNameOrId,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (!isEmpty) {
            // Initial letters fallback while image is loading or if offline
            val initials = championNameOrId.take(2).uppercase()
            val fontSize = (avatarSize.value * 0.38f).coerceIn(8f, 15f).sp
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(BorderDark),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    color = TextPrimary,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            // Empty placeholder
            Text(
                text = "—",
                color = TextMuted,
                fontSize = (avatarSize.value * 0.35f).sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Banned Overlay: dark overlay + red diagonal slash
        if (isBanned && !isEmpty) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.52f)),
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                drawLine(
                    color = RedSideColor,
                    start = Offset(0f, this.size.height),
                    end = Offset(this.size.width, 0f),
                    strokeWidth = stroke,
                )
            }
        }
    }
}

package com.loldraft.client.compose.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark = Color(0xFF0B0E14)
val SurfaceDark = Color(0xFF141A24)
val CardDark = Color(0xFF1B2332)
val BorderDark = Color(0xFF263245)

val BlueSideColor = Color(0xFF00D2FF)
val BlueSideDark = Color(0xFF005E82)
val RedSideColor = Color(0xFFFF4655)
val RedSideDark = Color(0xFF8A1E27)

val GoldAccent = Color(0xFFF5A623)
val GreenAccent = Color(0xFF00E676)
val OrangeWarning = Color(0xFFFF9100)

val TextPrimary = Color(0xFFF0F4F8)
val TextSecondary = Color(0xFF8E9BAE)
val TextMuted = Color(0xFF5A687D)

private val DarkColors =
    darkColorScheme(
        primary = BlueSideColor,
        secondary = RedSideColor,
        tertiary = GoldAccent,
        background = BgDark,
        surface = SurfaceDark,
        surfaceVariant = CardDark,
        onPrimary = Color.Black,
        onSecondary = Color.White,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
    )

@Composable
fun LolDraftAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}

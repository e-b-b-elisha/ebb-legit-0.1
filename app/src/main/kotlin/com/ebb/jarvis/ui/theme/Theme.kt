package com.ebb.jarvis.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/** Stark palette: cyan light on near-black glass, amber for anything that wants attention. */
object Jarvis {
    val Void = Color(0xFF03070C)
    val Deep = Color(0xFF071119)
    val Panel = Color(0x14FFFFFF)
    val PanelEdge = Color(0x3322D3EE)

    val Cyan = Color(0xFF22D3EE)
    val CyanBright = Color(0xFFA5F3FC)
    val CyanDim = Color(0xFF0E7490)
    val Amber = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)

    val Text = Color(0xFFDFF6FB)
    val TextDim = Color(0xFF7FA6B4)
}

private val JarvisColors = darkColorScheme(
    primary = Jarvis.Cyan,
    onPrimary = Jarvis.Void,
    secondary = Jarvis.CyanBright,
    background = Jarvis.Void,
    onBackground = Jarvis.Text,
    surface = Jarvis.Deep,
    onSurface = Jarvis.Text,
    error = Jarvis.Danger,
)

/** Monospace throughout: instrument panel, not a chat app. */
private val Mono = FontFamily.Monospace

val JarvisType = Typography(
    displayLarge = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Light,
        fontSize = 56.sp,
        letterSpacing = 2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = 1.5.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 2.sp,
        textAlign = TextAlign.Start,
    ),
)

@Composable
fun JarvisTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // There is no light mode. A HUD is a HUD.
    MaterialTheme(
        colorScheme = JarvisColors,
        typography = JarvisType,
        content = content,
    )
}

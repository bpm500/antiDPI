package com.antidpi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AntiDpiColors {
    val Background = Color(0xFF0A0A0A)
    val Surface = Color(0xFF141414)
    val SurfaceElevated = Color(0xFF1C1C1C)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextBody = Color(0xFFE0E0E0)
    val TextSecondary = Color(0xFF808080)
    val Border = Color(0xFF2A2A2A)
    val InvertedBg = Color(0xFFFFFFFF)
    val InvertedText = Color(0xFF0A0A0A)
}

private val MonochromeScheme = darkColorScheme(
    primary = AntiDpiColors.TextPrimary,
    onPrimary = AntiDpiColors.InvertedText,
    secondary = AntiDpiColors.TextBody,
    onSecondary = AntiDpiColors.InvertedText,
    background = AntiDpiColors.Background,
    onBackground = AntiDpiColors.TextBody,
    surface = AntiDpiColors.Surface,
    onSurface = AntiDpiColors.TextBody,
    surfaceVariant = AntiDpiColors.SurfaceElevated,
    onSurfaceVariant = AntiDpiColors.TextSecondary,
    outline = AntiDpiColors.Border
)

private val MonochromeTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = AntiDpiColors.TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = AntiDpiColors.TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = AntiDpiColors.TextBody
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = AntiDpiColors.TextBody
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        color = AntiDpiColors.TextSecondary
    )
)

@Composable
fun AntiDpiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MonochromeScheme,
        typography = MonochromeTypography,
        content = content
    )
}

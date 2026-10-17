package com.sewagetrace.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 视觉基调：深青绿（水体）+ 琥珀（预警），避免"环保绿"的廉价感
val Water900 = Color(0xFF06232B)
val Water800 = Color(0xFF0B1418)
val Water700 = Color(0xFF0E7490)
val Water500 = Color(0xFF0891B2)
val Water300 = Color(0xFF67E8F9)
val Amber = Color(0xFFF59E0B)
val Danger = Color(0xFFDC2626)
val SurfaceDark = Color(0xFF11212A)
val SurfaceDark2 = Color(0xFF172C36)

private val DarkScheme = darkColorScheme(
    primary = Water300,
    onPrimary = Water900,
    primaryContainer = Water700,
    onPrimaryContainer = Color.White,
    secondary = Amber,
    onSecondary = Color(0xFF1A1207),
    background = Water800,
    onBackground = Color(0xFFE6F1F4),
    surface = SurfaceDark,
    onSurface = Color(0xFFE6F1F4),
    surfaceVariant = SurfaceDark2,
    onSurfaceVariant = Color(0xFF9FB8C2),
    error = Danger,
    outline = Color(0xFF2C4653)
)

private val LightScheme = lightColorScheme(
    primary = Water700,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF3FB),
    onPrimaryContainer = Water900,
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    background = Color(0xFFF4F8F9),
    onBackground = Color(0xFF0B1A20),
    surface = Color.White,
    onSurface = Color(0xFF0B1A20),
    surfaceVariant = Color(0xFFE3EDF0),
    onSurfaceVariant = Color(0xFF4A6470),
    error = Danger,
    outline = Color(0xFFB6C8CF)
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.5.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.4.sp)
)

@Composable
fun SewageTraceTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = AppTypography,
        content = content
    )
}

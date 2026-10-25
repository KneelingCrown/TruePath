package ru.truepath.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF202925)
val Muted = Color(0xFF697168)
val Accent = Color(0xFF214D3E)
val Background = Color(0xFFF6F4EE)
val Paper = Color(0xFFFFFEFA)
val Line = Color(0xFFDDDCD3)
val Green = Color(0xFF28754D)
val Amber = Color(0xFF946922)
val Red = Color(0xFFAA493D)
val Lime = Color(0xFFDDEA9B)
val GraphBackground = Background

private val colors = lightColorScheme(
    primary = Accent, onPrimary = Paper,
    secondary = Green, background = Background, onBackground = Ink,
    surface = Paper, onSurface = Ink, surfaceVariant = Color(0xFFECEBE3),
    onSurfaceVariant = Muted, outline = Color(0xFFC6C9BE), outlineVariant = Line,
    surfaceTint = Accent, primaryContainer = Color(0xFFE4EADC), onPrimaryContainer = Accent,
    secondaryContainer = Color(0xFFECEFE3), onSecondaryContainer = Ink, error = Red,
)

@Composable
fun TruePathTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 38.sp, lineHeight = 41.sp, letterSpacing = (-1.2).sp),
            headlineMedium = TextStyle(fontFamily = FontFamily.Serif, fontSize = 29.sp, lineHeight = 34.sp, letterSpacing = (-.65).sp),
            titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-.35).sp),
            titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 23.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 25.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
            labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = .8.sp),
        ),
        content = content,
    )
}

package ru.truepath.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF172033)
val Muted = Color(0xFF727B8E)
val Accent = Color(0xFF6558E8)
val Background = Color(0xFFF6F7FB)
val Line = Color(0xFFE8EBF2)
val Green = Color(0xFF138A6F)
val Amber = Color(0xFFAA7017)
val Red = Color(0xFFCA5166)
val GraphBackground = Color(0xFF151B30)

private val colors = lightColorScheme(
    primary = Accent, onPrimary = Color.White,
    secondary = Green, background = Background, onBackground = Ink,
    surface = Color.White, onSurface = Ink, surfaceVariant = Color(0xFFF0F1F8),
    onSurfaceVariant = Muted, outline = Color(0xFFD9DDE8), error = Red,
)

@Composable
fun TruePathTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-1).sp),
            headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-.6).sp),
            titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 28.sp),
            titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
            bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
            labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        ),
        content = content,
    )
}

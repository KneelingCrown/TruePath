package ru.truepath.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class TruePathPalette(
    val ink: Color, val muted: Color, val accent: Color,
    val background: Color, val paper: Color, val line: Color,
    val green: Color, val amber: Color, val red: Color, val lime: Color,
    val onAccent: Color, val exampleSurface: Color, val graphEdge: Color,
) { val graphBackground get() = background }

private val light = TruePathPalette(
    Color(0xFF202925), Color(0xFF697168), Color(0xFF214D3E),
    Color(0xFFF6F4EE), Color(0xFFFFFEFA), Color(0xFFDDDCD3),
    Color(0xFF28754D), Color(0xFF946922), Color(0xFFAA493D), Color(0xFFDDEA9B),
    Color(0xFFFFFEFA), Color(0xFFECEFE3), Color(0xFFB8BEB0),
)
private val dark = TruePathPalette(
    Color(0xFFE7EAE0), Color(0xFFA5B0A3), Color(0xFFB0D5B8),
    Color(0xFF111914), Color(0xFF1A241D), Color(0xFF344137),
    Color(0xFF8CD4A5), Color(0xFFE8C27D), Color(0xFFF29A8B), Color(0xFFDDEA9B),
    Color(0xFF143424), Color(0xFF25362A), Color(0xFF516456),
)
val LocalTruePathPalette = staticCompositionLocalOf { light }
val LocalTruePathDark = staticCompositionLocalOf { false }

@Composable
private fun animatedPalette(target: TruePathPalette): TruePathPalette {
    @Composable fun color(value: Color) = animateColorAsState(value, tween(320), label = "theme-color").value
    return TruePathPalette(color(target.ink), color(target.muted), color(target.accent),
        color(target.background), color(target.paper), color(target.line), color(target.green),
        color(target.amber), color(target.red), color(target.lime), color(target.onAccent),
        color(target.exampleSurface), color(target.graphEdge))
}

@Composable
fun TruePathTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val palette = animatedPalette(if (darkTheme) dark else light)
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        primary = palette.accent, onPrimary = palette.onAccent,
        secondary = palette.green, onSecondary = palette.onAccent,
        background = palette.background, onBackground = palette.ink,
        surface = palette.paper, onSurface = palette.ink, surfaceVariant = palette.exampleSurface,
        onSurfaceVariant = palette.muted, outline = palette.muted, outlineVariant = palette.line,
        surfaceTint = palette.accent, primaryContainer = palette.exampleSurface, onPrimaryContainer = palette.ink,
        secondaryContainer = palette.exampleSurface, onSecondaryContainer = palette.ink,
        error = palette.red, onError = palette.onAccent,
        surfaceContainer = palette.paper, surfaceContainerHigh = palette.paper,
        surfaceContainerHighest = palette.exampleSurface, surfaceContainerLow = palette.background,
        surfaceContainerLowest = palette.background, inverseSurface = palette.ink, inverseOnSurface = palette.paper,
    )
    CompositionLocalProvider(LocalTruePathPalette provides palette, LocalTruePathDark provides darkTheme,
        LocalContentColor provides palette.ink) {
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
}

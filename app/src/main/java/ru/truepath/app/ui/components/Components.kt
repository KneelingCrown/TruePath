package ru.truepath.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.truepath.app.domain.TrustStatus
import ru.truepath.app.ui.theme.*

@Composable
fun TrustStatus.tint(): Color {
    val palette = LocalTruePathPalette.current
    return when (this) {
    TrustStatus.UNCHECKED -> palette.muted
    TrustStatus.SUPPORTED -> palette.green
    TrustStatus.UNCERTAIN -> palette.amber
    TrustStatus.CONTRADICTED -> palette.red
    }
}

fun TrustStatus.fill(): Color = when (this) {
    TrustStatus.SUPPORTED -> Color(0xFF28754D)
    TrustStatus.CONTRADICTED -> Color(0xFFAA493D)
    TrustStatus.UNCERTAIN -> Color(0xFF946922)
    TrustStatus.UNCHECKED -> Color(0xFF697168)
}

@Composable
fun Tag(text: String, tint: Color = LocalTruePathPalette.current.accent, icon: ImageVector? = null) {
    val palette = LocalTruePathPalette.current
    Surface(color = tint.copy(alpha = .07f), shape = RoundedCornerShape(4.dp)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(13.dp), tint = tint)
            Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

@Composable
fun DemoBanner(compact: Boolean = false) {
    val palette = LocalTruePathPalette.current
    Surface(color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp).size(5.dp).background(palette.amber, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Учебная версия", style = MaterialTheme.typography.labelLarge, color = palette.muted)
                if (!compact) Text("Проверки демонстрационные. Ваши материалы сохраняются на устройстве и не отправляются на исследование.", style = MaterialTheme.typography.bodySmall, color = palette.muted)
            }
        }
    }
}

@Composable
fun ScreenHeading(eyebrow: String, title: String, subtitle: String) {
    val palette = LocalTruePathPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.muted)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = palette.muted)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String, action: String, onAction: () -> Unit) {
    val palette = LocalTruePathPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(70.dp).background(palette.accent.copy(alpha = .06f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(30.dp), tint = palette.accent)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = palette.muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = onAction, shape = RoundedCornerShape(7.dp)) { Text(action) }
    }
}

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

fun TrustStatus.tint(): Color = when (this) {
    TrustStatus.UNCHECKED -> Muted
    TrustStatus.SUPPORTED -> Green
    TrustStatus.UNCERTAIN -> Amber
    TrustStatus.CONTRADICTED -> Red
}

@Composable
fun Tag(text: String, tint: Color = Accent, icon: ImageVector? = null) {
    Surface(color = tint.copy(alpha = .07f), shape = RoundedCornerShape(4.dp)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (icon != null) Icon(icon, null, Modifier.size(13.dp), tint = tint)
            Text(text, style = MaterialTheme.typography.labelSmall, color = tint)
        }
    }
}

@Composable
fun DemoBanner(compact: Boolean = false) {
    Surface(color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 5.dp).size(5.dp).background(Amber, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Учебная версия", style = MaterialTheme.typography.labelLarge, color = Muted)
                if (!compact) Text("Проверки демонстрационные. Ваши материалы сохраняются на устройстве и не отправляются на исследование.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

@Composable
fun ScreenHeading(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted)
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, description: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(70.dp).background(Accent.copy(alpha = .06f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(30.dp), tint = Accent)
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Button(onClick = onAction, shape = RoundedCornerShape(7.dp)) { Text(action) }
    }
}

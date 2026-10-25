package ru.truepath.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.truepath.app.domain.*
import ru.truepath.app.ui.components.*
import ru.truepath.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(session: ResearchSession?, onStop: () -> Unit, onResume: () -> Unit, onNew: () -> Unit) {
    if (session == null) {
        Column(Modifier.padding(20.dp)) {
            ScreenHeading("Основания на виду", "Визуализация", "От утверждения — к источнику и конкретной цитате.")
            EmptyState(Icons.Outlined.Hub, "Начните исследование", "Граф появится здесь сразу после запуска проверки и будет расти по мере поступления данных.", "Добавить материал", onNew)
        }
        return
    }
    var selectedId by remember(session.id) { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeading("Основания на виду", "Визуализация", session.title) }
        item {
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (session.status == RunStatus.RUNNING) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    else Icon(if (session.status == RunStatus.COMPLETE) Icons.Outlined.TaskAlt else Icons.Outlined.Info, null, tint = if (session.status == RunStatus.COMPLETE) Green else Muted)
                    Column(Modifier.weight(1f)) {
                        Text(session.stage, style = MaterialTheme.typography.labelLarge)
                        Text("${session.nodes.size} узлов · ${session.edges.size} связей", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    if (session.status == RunStatus.RUNNING) TextButton(onClick = onStop) { Text("Стоп") }
                    if (session.status == RunStatus.INTERRUPTED) TextButton(onClick = onResume) { Text("Продолжить") }
                }
            }
        }
        item { DemoBanner(compact = false) }
        item { key(session.id) { Column { GraphBoard(session) { selectedId = it } } } }
        if (session.summary.isNotBlank()) item {
            val tint = if (session.status == RunStatus.COMPLETE) Red else Accent
            Surface(color = tint.copy(alpha = .06f), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Tag(if (session.status == RunStatus.COMPLETE) "Вывод по учебному примеру" else "Проверка не выполнена", tint)
                    Text(session.summary, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (session.planB.isNotBlank()) item {
            Surface(color = Color(0xFFEEFAF5), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.AutoMirrored.Outlined.AltRoute, null, tint = Green)
                        Text("План Б", style = MaterialTheme.typography.titleMedium, color = Green)
                    }
                    Text(session.planB, style = MaterialTheme.typography.bodyMedium)
                    Text("Рекомендация из учебного сценария. Ваши задачи не изменяются автоматически.", style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        item { Text("Материалы графа", style = MaterialTheme.typography.titleMedium) }
        items(session.nodes, key = { it.id }) { node ->
            Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().clickable { selectedId = node.id }.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(when (node.kind) {
                        NodeKind.MATERIAL -> Icons.AutoMirrored.Outlined.Article
                        NodeKind.CLAIM -> Icons.Outlined.ChatBubbleOutline
                        NodeKind.SOURCE -> Icons.Outlined.Description
                        NodeKind.EVIDENCE -> Icons.Outlined.FormatQuote
                    }, null, tint = node.status.tint(), modifier = Modifier.size(22.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(node.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("${node.kind.label} · ${node.status.label}", style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                    Icon(Icons.Outlined.ChevronRight, "Открыть ${node.label}", tint = Muted)
                }
            }
        }
        if (session.attachments.isNotEmpty()) item {
            Text("Прикреплено: ${session.attachments.joinToString { it.name }}", style = MaterialTheme.typography.bodySmall, color = Muted)
        }
    }
    session.nodes.find { it.id == selectedId }?.let { node ->
        ModalBottomSheet(onDismissRequest = { selectedId = null }, containerColor = Color.White) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Tag(node.kind.label); Tag(node.status.label, node.status.tint()) }
                Text(node.label, style = MaterialTheme.typography.headlineMedium)
                Text(node.description.ifBlank { "Дополнительные сведения ещё не получены." }, style = MaterialTheme.typography.bodyLarge)
                if (node.quote.isNotBlank()) Surface(color = Background, shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.FormatQuote, null, tint = Accent)
                        Text(node.quote, style = MaterialTheme.typography.bodyMedium)
                        Text(node.sourceLabel, style = MaterialTheme.typography.labelSmall, color = Muted)
                    }
                }
                val connections = session.edges.filter { it.from == node.id || it.to == node.id }
                if (connections.isNotEmpty()) {
                    Text("Связи", style = MaterialTheme.typography.titleMedium)
                    connections.forEach { edge ->
                        val other = session.nodes.find { it.id == if (edge.from == node.id) edge.to else edge.from }
                        Text("${edge.label} → ${other?.label.orEmpty()}", style = MaterialTheme.typography.bodyMedium, color = Muted)
                    }
                }
                Text("Полученные сведения относятся к демонстрационному сценарию.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
    }
}

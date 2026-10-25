package ru.truepath.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.truepath.app.AppState
import ru.truepath.app.domain.*
import ru.truepath.app.ui.components.*
import ru.truepath.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(state: AppState, onQuery: (String) -> Unit, onOpen: (String) -> Unit, onDelete: (String) -> Unit, onNew: () -> Unit) {
    val palette = LocalTruePathPalette.current
    var deleting by remember { mutableStateOf<ResearchSession?>(null) }
    val sessions = state.sessions.filter { it.title.contains(state.historyQuery, ignoreCase = true) || it.input.contains(state.historyQuery, ignoreCase = true) }
    val dateFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru")) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenHeading("Память ваших исследований", "История", "Все основания сохранены. Возвращайтесь к ним в любой момент.") }
        if (state.sessions.isNotEmpty()) item {
            OutlinedTextField(state.historyQuery, onQuery, Modifier.fillMaxWidth(), placeholder = { Text("Найти исследование") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = RoundedCornerShape(6.dp))
        }
        if (state.sessions.isEmpty()) item {
            EmptyState(Icons.Outlined.History, "Здесь начнётся ваша история", "Сохранённые материалы и графы появятся после первого исследования.", "Добавить материал", onNew)
        } else if (sessions.isEmpty()) item {
            Text("По этому запросу ничего не найдено", style = MaterialTheme.typography.bodyMedium, color = palette.muted, modifier = Modifier.padding(vertical = 24.dp))
        }
        items(sessions, key = { it.id }) { session ->
            Surface(modifier = Modifier.animateItem(), shape = RoundedCornerShape(8.dp), color = palette.paper) {
                Column(Modifier.fillMaxWidth().clickable { onOpen(session.id) }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Tag(session.status.label, when (session.status) {
                            RunStatus.COMPLETE -> palette.green
                            RunStatus.RUNNING -> palette.accent
                            RunStatus.FAILED -> palette.red
                            else -> palette.muted
                        })
                        IconButton(onClick = { deleting = session }, modifier = Modifier.size(36.dp)) { Icon(Icons.Outlined.DeleteOutline, "Удалить ${session.title}", Modifier.size(19.dp), tint = palette.muted) }
                    }
                    Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 3)
                    Text("${dateFormat.format(Date(session.createdAt))} · ${session.nodes.size} узлов · ${if (session.scenarioId != null) "Учебный пример" else "Ваш материал"}", style = MaterialTheme.typography.bodySmall, color = palette.muted)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Hub, null, Modifier.size(16.dp), tint = palette.accent)
                        Text("Открыть граф", style = MaterialTheme.typography.labelLarge, color = palette.accent)
                    }
                }
            }
        }
    }
    deleting?.let { session -> AlertDialog(
        onDismissRequest = { deleting = null }, title = { Text("Удалить исследование?") },
        text = { Text("Материал и его граф будут удалены из истории на этом устройстве.") },
        confirmButton = { TextButton(onClick = { onDelete(session.id); deleting = null }) { Text("Удалить", color = palette.red) } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Оставить") } },
    ) }
}

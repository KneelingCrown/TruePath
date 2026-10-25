package ru.truepath.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.truepath.app.AppState
import ru.truepath.app.domain.Attachment
import ru.truepath.app.ui.components.*
import ru.truepath.app.ui.theme.*

@Composable
fun CheckScreen(state: AppState, onDraft: (String) -> Unit, onStart: () -> Unit, onAttach: () -> Unit, onRemove: (String) -> Unit, onExample: () -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.imePadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ScreenHeading("Начните с вопроса", "Что проверим?", "Прикрепите материал и добавьте то, в чём сомневаетесь.") }
        item { DemoBanner() }
        item { Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.size(20.dp), tint = Accent)
                    Text("Ваш материал", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(
                    value = state.draft, onValueChange = onDraft, modifier = Modifier.fillMaxWidth().heightIn(min = 185.dp),
                    placeholder = { Text("Вставьте новость, ссылку или утверждение.\n\nНапример: «Действительно ли мероприятие перенесли?»", color = Muted) },
                    shape = RoundedCornerShape(15.dp), maxLines = 12,
                    supportingText = { Text("${state.draft.length} / 12 000") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAttach, shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.AttachFile, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Файл")
                    }
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.let(onDraft)
                    }) { Icon(Icons.Outlined.ContentPaste, null, Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Вставить") }
                }
            }
        } }
        items(state.attachments, key = { it.uri }) { attachment -> AttachmentRow(attachment) { onRemove(attachment.uri) } }
        item {
            Button(onClick = onStart, enabled = state.draft.isNotBlank() || state.attachments.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(19.dp)); Spacer(Modifier.width(9.dp)); Text("Открыть в графе")
            }
        }
        item { Text("Материал сохранится на устройстве. Исследовательский сервис пока не подключён: приложение не будет придумывать результат проверки.", style = MaterialTheme.typography.bodySmall, color = Muted) }
        item { Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFEEFAF5)) {
            Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Tag("Попробуйте сейчас", Green, Icons.Outlined.PlayCircleOutline)
                Text("Посмотрите, как рождается граф", style = MaterialTheme.typography.titleMedium)
                Text("Учебный пример: источник, утверждения, цитата и изменение вывода — в реальном времени.", style = MaterialTheme.typography.bodyMedium, color = Muted)
                TextButton(onClick = onExample, contentPadding = PaddingValues(0.dp)) { Text("Запустить пример"); Spacer(Modifier.width(7.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(17.dp)) }
            }
        } }
    }
}

@Composable
private fun AttachmentRow(attachment: Attachment, onRemove: () -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (attachment.mime.startsWith("image/")) Icons.Outlined.Image else Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = Accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(attachment.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                Text("Только на устройстве", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Убрать ${attachment.name}", Modifier.size(19.dp)) }
        }
    }
}

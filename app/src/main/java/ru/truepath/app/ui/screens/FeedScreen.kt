package ru.truepath.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.truepath.app.AppState
import ru.truepath.app.data.*
import ru.truepath.app.ui.components.*
import ru.truepath.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(state: AppState, onTopic: (String) -> Unit, onCheck: (NewsArticle) -> Unit) {
    var opened by remember { mutableStateOf<NewsArticle?>(null) }
    val articles = DemoNews.articles.filter { state.topic == "Все" || it.topic == state.topic }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { ScreenHeading("Меньше шума. Больше контекста.", "Ваша картина дня", "Новости по темам. Исследование — когда вы решите.") }
        item { DemoBanner(compact = true) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Все", "События", "Наука", "Город").forEach { topic ->
                    FilterChip(selected = state.topic == topic, onClick = { onTopic(topic) }, label = { Text(topic) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Accent, selectedLabelColor = Color.White),
                        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = state.topic == topic, borderColor = Line),
                        shape = RoundedCornerShape(20.dp))
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("В фокусе", style = MaterialTheme.typography.titleMedium)
            Text("${articles.size} учебных события", style = MaterialTheme.typography.labelSmall, color = Muted)
        } }
        items(articles, key = { it.id }) { article ->
            NewsCard(article, { opened = article }, { onCheck(article) })
        }
        item { Text("Фактчекинг запускается только по вашему нажатию. Все публикации в этой версии вымышлены.", style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
    opened?.let { article -> ModalBottomSheet(onDismissRequest = { opened = null }, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 36.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Tag("Учебная публикация", icon = Icons.Outlined.Science)
            Text(article.title, style = MaterialTheme.typography.headlineMedium)
            Text(article.summary, style = MaterialTheme.typography.bodyLarge)
            Text("Это вымышленный материал для демонстрации. Кнопка запустит подготовленный пример исследования с постепенным появлением узлов и доказательств.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            Button(onClick = { opened = null; onCheck(article) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Outlined.Hub, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Посмотреть исследование")
            }
        }
    } }
}

@Composable
private fun NewsCard(article: NewsArticle, onOpen: () -> Unit, onCheck: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Tag(article.topic, if (article.topic == "Наука") Green else Accent)
                Text("${article.minutesAgo} мин назад · пример", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
            Column(Modifier.fillMaxWidth().clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(article.title, style = MaterialTheme.typography.titleLarge)
                Text(article.summary, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
            Row(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFFF2F0FF), Color(0xFFF5F8FA))), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Icon(Icons.Outlined.Layers, null, Modifier.size(18.dp), tint = Accent)
                Text("${article.sources} учебные публикации · одно событие", style = MaterialTheme.typography.labelSmall, color = Ink)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Ещё не проверено", style = MaterialTheme.typography.labelSmall, color = Muted)
                FilledTonalButton(onClick = onCheck, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Accent.copy(alpha = .09f), contentColor = Accent)) {
                    Icon(Icons.AutoMirrored.Outlined.FactCheck, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Демо-проверка", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

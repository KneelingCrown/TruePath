package ru.truepath.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.truepath.app.AppState
import ru.truepath.app.data.*
import ru.truepath.app.domain.ArticleBlock
import ru.truepath.app.ui.components.*
import ru.truepath.app.ui.theme.*

@Composable
fun FeedScreen(state: AppState, onTopic: (String) -> Unit, onCheck: (NewsArticle) -> Unit) {
    var opened by remember { mutableStateOf<NewsArticle?>(null) }
    val articles = DemoNews.articles.filter { state.topic == "Все" || it.topic == state.topic }
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 23.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("ВАША ПОДБОРКА / УЧЕБНЫЕ НОВОСТИ", style = MaterialTheme.typography.labelSmall, color = Muted)
                Text("Картина дня.", style = MaterialTheme.typography.headlineLarge)
                Text("Вы выбираете, что проверить.", style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(25.dp)) {
                listOf("Все", "События", "Наука", "Город").forEach { topic ->
                    Column(Modifier.clickable { onTopic(topic) }.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(topic, style = MaterialTheme.typography.labelLarge, color = if (state.topic == topic) Ink else Muted,
                            fontWeight = if (state.topic == topic) FontWeight.Bold else FontWeight.Normal)
                        Box(Modifier.width(if (state.topic == topic) 25.dp else 0.dp).height(2.dp).background(Accent))
                    }
                }
            }
        }
        itemsIndexed(articles, key = { _, it -> it.id }) { index, article ->
            EditorialNewsCard(article, hero = index == 0, onOpen = { opened = article }, onCheck = { onCheck(article) })
        }
        item { Text("Учебные публикации вымышлены. Фото иллюстрируют темы. Проверка начинается только по вашему нажатию.", style = MaterialTheme.typography.bodySmall, color = Muted) }
    }
    opened?.let { article ->
        Dialog(onDismissRequest = { opened = null }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            NewsReader(article, onClose = { opened = null }, onCheck = { opened = null; onCheck(article) })
        }
    }
}

@Composable
private fun EditorialNewsCard(article: NewsArticle, hero: Boolean, onOpen: () -> Unit, onCheck: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(article.topic.uppercase(), style = MaterialTheme.typography.labelSmall, color = Accent)
            Text("${article.minutesAgo} МИН НАЗАД · ПРИМЕР", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
        if (hero) {
            article.cover?.let { photo -> RemotePhoto(photo, Modifier.fillMaxWidth().aspectRatio(1.72f).clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpen)) }
            Text(article.title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable(onClick = onOpen))
            Text(article.summary, style = MaterialTheme.typography.bodyMedium, color = Muted)
        } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(article.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).clickable(onClick = onOpen), maxLines = 4, overflow = TextOverflow.Ellipsis)
            article.cover?.let { photo -> RemotePhoto(photo, Modifier.size(106.dp).clip(RoundedCornerShape(5.dp)).clickable(onClick = onOpen)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${article.sources} публикации / одно событие", style = MaterialTheme.typography.bodySmall, color = Muted)
            OutlinedButton(onClick = onCheck, shape = RoundedCornerShape(5.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp)) {
                Text("Проверить"); Spacer(Modifier.width(8.dp)); Icon(Icons.Outlined.NorthEast, null, Modifier.size(15.dp))
            }
        }
        HorizontalDivider(Modifier.padding(top = 9.dp), color = Line, thickness = .7.dp)
    }
}

@Composable
fun NewsReader(article: NewsArticle, onClose: () -> Unit, onCheck: () -> Unit) {
    var photo by remember { mutableStateOf<ArticleBlock.Photo?>(null) }
    Scaffold(
        containerColor = Paper,
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(58.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Закрыть новость") }
                Text("TRUE PATH / ЧТЕНИЕ", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 8.dp), color = Muted)
            }
        },
        bottomBar = {
            Column(Modifier.background(Paper).navigationBarsPadding().padding(horizontal = 22.dp, vertical = 12.dp)) {
                Button(onClick = onCheck, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(6.dp)) {
                    Text("Проверить публикацию"); Spacer(Modifier.weight(1f)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(19.dp))
                }
                Text("Запустится учебный пример исследования", style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(top = 7.dp))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(23.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
                    Text("${article.topic.uppercase()} / УЧЕБНЫЙ МАТЕРИАЛ", style = MaterialTheme.typography.labelSmall, color = Accent)
                    Text(article.title, style = MaterialTheme.typography.headlineLarge)
                    Text("${article.sources} публикации объединены в событие", style = MaterialTheme.typography.bodySmall, color = Muted)
                    HorizontalDivider(color = Line)
                }
            }
            val blocks = article.body.ifEmpty { listOf(ArticleBlock.Paragraph(article.summary)) }
            itemsIndexed(blocks) { _, block -> ArticleContentBlock(block) { photo = it } }
        }
    }
    photo?.let { PhotoViewer(it) { photo = null } }
}

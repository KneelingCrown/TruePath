package ru.truepath.app.ui

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.truepath.app.*
import ru.truepath.app.domain.Attachment
import ru.truepath.app.ui.screens.*
import ru.truepath.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TruePathApp(model: TruePathViewModel) {
    val state by model.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showAbout by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val attachment = runCatching {
                val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: "Прикреплённый файл"
                Attachment(uri.toString(), name.take(100), context.contentResolver.getType(uri) ?: "application/octet-stream")
            }.getOrNull()
            if (attachment != null) model.attach(attachment) else model.notify("Не удалось открыть выбранный файл")
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it, withDismissAction = true); model.dismissNotice() }
    }

    Scaffold(
        containerColor = Background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrandMark()
                    Text("True Path", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                } },
                actions = { IconButton(onClick = { showAbout = true }) { Icon(Icons.Outlined.Info, "О приложении", tint = Muted) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                AppTab.entries.forEach { tab ->
                    val icon = when (tab) {
                        AppTab.FEED -> Icons.Outlined.Newspaper
                        AppTab.CHECK -> Icons.AutoMirrored.Outlined.FactCheck
                        AppTab.GRAPH -> Icons.Outlined.Hub
                        AppTab.HISTORY -> Icons.Outlined.History
                    }
                    NavigationBarItem(
                        selected = state.tab == tab, onClick = { model.selectTab(tab) },
                        icon = { Icon(icon, null, Modifier.size(23.dp)) },
                        label = { Text(tab.title, fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Accent, selectedTextColor = Accent,
                            indicatorColor = Accent.copy(alpha = .09f), unselectedIconColor = Muted, unselectedTextColor = Muted),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else Box(Modifier.fillMaxHeight().widthIn(max = 720.dp)) {
                when (state.tab) {
                    AppTab.FEED -> FeedScreen(state, model::setTopic, model::startArticle)
                    AppTab.CHECK -> CheckScreen(
                        state, model::setDraft, model::startDraft,
                        onAttach = { picker.launch(arrayOf("image/*", "video/*", "audio/*", "text/*", "application/pdf")) },
                        onRemove = model::removeAttachment,
                        onExample = { model.startArticle(ru.truepath.app.data.DemoNews.articles.first()) },
                    )
                    AppTab.GRAPH -> GraphScreen(state.selectedSession, model::stop, model::resume, { model.selectTab(AppTab.CHECK) })
                    AppTab.HISTORY -> HistoryScreen(state, model::setHistoryQuery, model::openSession, model::deleteSession, { model.selectTab(AppTab.CHECK) })
                }
            }
        }
    }
    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        icon = { BrandMark() }, title = { Text("Ваш путь к основаниям") },
        text = { Text("True Path · 0.1.0\n\nСобирайте материалы, исследуйте связи и возвращайтесь к сохранённым результатам.\n\nСейчас доступен учебный пример с живым графом. Проверка собственных материалов появится после подключения исследовательского сервиса. История хранится на этом устройстве.") },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Понятно") } },
    )
}

@Composable
private fun BrandMark() {
    Canvas(Modifier.size(31.dp)) {
        drawRoundRect(Accent, cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()))
        val a = Offset(size.width * .28f, size.height * .73f)
        val b = Offset(size.width * .5f, size.height * .48f)
        val c = Offset(size.width * .74f, size.height * .27f)
        val d = Offset(size.width * .73f, size.height * .72f)
        drawLine(Color.White, a, c, 2.dp.toPx())
        drawLine(Color.White, b, d, 2.dp.toPx())
        listOf(a, c).forEach { drawCircle(Color.White, 3.dp.toPx(), it) }
        drawCircle(Color(0xFF7DE4C5), 3.dp.toPx(), d)
    }
}

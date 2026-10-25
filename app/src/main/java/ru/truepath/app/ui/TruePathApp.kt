package ru.truepath.app.ui

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import ru.truepath.app.domain.ThemeMode
import ru.truepath.app.ui.screens.*
import ru.truepath.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TruePathApp(model: TruePathViewModel) {
    val palette = LocalTruePathPalette.current
    val dark = LocalTruePathDark.current
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

    if (state.tab == AppTab.GRAPH && !state.loading) {
        Box(Modifier.fillMaxSize()) {
            GraphScreen(state.selectedSession, model::stop, model::resume,
                { model.selectTab(AppTab.CHECK) }, model::leaveGraph,
                onToggleTheme = { model.setTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) })
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
    } else AppShell(state.tab, model::selectTab, { showAbout = true }, snackbar,
        onToggleTheme = { model.setTheme(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) }) {
        if (state.loading) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else AnimatedContent(
            targetState = state.tab,
            transitionSpec = {
                (fadeIn(tween(200, delayMillis = 60)) + slideInVertically(tween(240)) { it / 35 })
                    .togetherWith(fadeOut(tween(100)))
            },
            label = "main-section",
        ) { tab -> when (tab) {
            AppTab.FEED -> FeedScreen(state, model::setTopic, model::startArticle)
            AppTab.CHECK -> CheckScreen(state, model::setDraft, model::startDraft,
                onAttach = { picker.launch(arrayOf("image/*", "video/*", "audio/*", "text/*", "application/pdf")) },
                onRemove = model::removeAttachment,
                onExample = { model.startArticle(ru.truepath.app.data.DemoNews.articles.first()) })
            AppTab.HISTORY -> HistoryScreen(state, model::setHistoryQuery, model::openSession, model::deleteSession, { model.selectTab(AppTab.CHECK) })
            AppTab.GRAPH -> Unit
        } }
    }
    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false },
        icon = { BrandMark() }, title = { Text("Ваш путь к основаниям") },
        text = { Text("True Path · 0.1.0\n\nСобирайте материалы, исследуйте связи и возвращайтесь к сохранённым результатам.\n\nСейчас доступен учебный пример с живым графом. Проверка собственных материалов появится после подключения исследовательского сервиса. История хранится на этом устройстве.") },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Понятно") } },
        dismissButton = { TextButton(onClick = { model.setTheme(ThemeMode.SYSTEM); showAbout = false }) { Text("Тема как в системе") } },
    )
}

@Composable
fun AppShell(
    tab: AppTab,
    onTab: (AppTab) -> Unit,
    onAbout: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onToggleTheme: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val palette = LocalTruePathPalette.current
    val dark = LocalTruePathDark.current
    Scaffold(
        containerColor = palette.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(59.dp).padding(start = 22.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BrandMark()
                        Text("true path", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = (-.7).sp)
                    }
                    Row {
                        IconButton(onClick = onToggleTheme) {
                            Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                                if (dark) "Включить светлую тему" else "Включить тёмную тему", Modifier.size(20.dp), tint = palette.muted)
                        }
                        IconButton(onClick = onAbout) { Icon(Icons.Outlined.Info, "О приложении", Modifier.size(20.dp), tint = palette.muted) }
                    }
                }
                HorizontalDivider(color = palette.line, thickness = .5.dp)
            }
        },
        bottomBar = {
            Column(Modifier.background(palette.paper).navigationBarsPadding()) {
                HorizontalDivider(color = palette.line, thickness = .5.dp)
                Row(Modifier.fillMaxWidth().height(68.dp)) {
                    AppTab.entries.forEach { item ->
                        Column(Modifier.weight(1f).fillMaxHeight().clickable { onTab(item) }.padding(top = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(when (item) {
                                AppTab.FEED -> Icons.Outlined.Newspaper
                                AppTab.CHECK -> Icons.AutoMirrored.Outlined.FactCheck
                                AppTab.GRAPH -> Icons.Outlined.Hub
                                AppTab.HISTORY -> Icons.Outlined.History
                            }, null, Modifier.size(22.dp), tint = if (item == tab) palette.accent else palette.muted)
                            Text(item.title, style = MaterialTheme.typography.labelSmall, color = if (item == tab) palette.accent else palette.muted)
                            if (item == tab) Box(Modifier.width(16.dp).height(2.dp).background(palette.accent))
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxHeight().widthIn(max = 720.dp)) { content() }
        }
    }
}

@Composable
private fun BrandMark() {
    val palette = LocalTruePathPalette.current
    Canvas(Modifier.size(26.dp)) {
        val start = Offset(size.width * .15f, size.height * .82f)
        val middle = Offset(size.width * .52f, size.height * .45f)
        val finish = Offset(size.width * .86f, size.height * .12f)
        drawLine(palette.accent, start, finish, 2.5.dp.toPx())
        drawLine(palette.accent, middle, Offset(size.width * .86f, size.height * .79f), 2.5.dp.toPx())
        drawCircle(palette.accent, 3.dp.toPx(), start)
        drawCircle(palette.accent, 3.dp.toPx(), finish)
        drawCircle(palette.accent, 3.dp.toPx(), Offset(size.width * .86f, size.height * .79f))
    }
}

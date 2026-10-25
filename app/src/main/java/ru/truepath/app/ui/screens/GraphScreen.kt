package ru.truepath.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.truepath.app.domain.*
import ru.truepath.app.ui.components.*
import ru.truepath.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GraphScreen(
    session: ResearchSession?, onStop: () -> Unit, onResume: () -> Unit, onNew: () -> Unit, onBack: () -> Unit,
    drawer: DrawerState = rememberDrawerState(DrawerValue.Closed),
) {
    val scope = rememberCoroutineScope()
    var selectedId by remember(session?.id) { mutableStateOf<String?>(null) }
    var infoTab by remember(session?.id) { mutableIntStateOf(0) }
    var focusId by remember(session?.id) { mutableStateOf<String?>(null) }
    var focusTick by remember(session?.id) { mutableIntStateOf(0) }
    val graph = session?.presentedGraph()
    BackHandler { if (drawer.isOpen) scope.launch { drawer.close() } else onBack() }
    fun openNode(id: String) {
        selectedId = id
        infoTab = if (id == PresentedGraph.CONCLUSION_ID) 0 else 1
        scope.launch { drawer.open() }
    }
    // ModalNavigationDrawer follows layout direction; only its placement is RTL.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = drawer.isOpen,
            scrimColor = Ink.copy(alpha = .25f),
            drawerContent = {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        // Keep the sheet hidden while its initial anchors are measured.
                        modifier = Modifier.fillMaxWidth(.91f).widthIn(max = 420.dp).graphicsLayer {
                            alpha = if (drawer.isClosed && drawer.targetValue == DrawerValue.Closed) 0f else 1f
                        },
                        drawerContainerColor = Paper, drawerShape = RoundedCornerShape(0.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Исследование", style = MaterialTheme.typography.headlineMedium)
                            IconButton(onClick = { scope.launch { drawer.close() } }) { Icon(Icons.Outlined.Close, "Закрыть материалы") }
                        }
                        TabRow(selectedTabIndex = infoTab, containerColor = Paper, contentColor = Accent) {
                            listOf("Итог", "Материалы", "Сведения").forEachIndexed { index, title ->
                                Tab(selected = infoTab == index, onClick = { infoTab = index; selectedId = null }, text = { Text(title, style = MaterialTheme.typography.labelLarge) })
                            }
                        }
                        if (session != null && graph != null) {
                            GraphInformation(session, graph, infoTab, selectedId, onSelect = { selectedId = it }, onFocus = {
                                focusId = it; focusTick++; scope.launch { drawer.close() }
                            }, onStop = onStop, onResume = onResume)
                        }
                    }
                }
            },
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.fillMaxSize().background(Background)) {
                    if (session != null && graph != null) {
                        GraphBoard(session, graph, selectedId, focusId, focusTick,
                            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(top = 90.dp, bottom = 65.dp), ::openNode)
                    } else Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                        EmptyState(Icons.Outlined.Hub, "Начните с материала", "Здесь появится путь от новости к её источникам и выводу.", "Добавить материал", onNew)
                    }
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        WorkspaceButton("Вернуться", Icons.AutoMirrored.Outlined.ArrowBack, onBack)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("TRUE PATH", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(if (session?.scenarioId != null) "УЧЕБНОЕ ИССЛЕДОВАНИЕ" else "ИССЛЕДОВАНИЕ", style = MaterialTheme.typography.labelSmall, color = Muted)
                        }
                        WorkspaceButton("Материалы и сведения", Icons.Outlined.Menu, { scope.launch { drawer.open() } })
                    }
                    if (session != null) {
                        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            WorkspaceButton("Показать весь путь", Icons.Outlined.CenterFocusStrong, { focusId = null; focusTick++ })
                            if (session.status == RunStatus.RUNNING) {
                                CircularProgressIndicator(Modifier.size(12.dp), color = Accent, strokeWidth = 1.5.dp)
                                Text(session.stage, style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.weight(1f), maxLines = 2)
                            } else {
                                Text(if (session.status == RunStatus.COMPLETE) "Вывод готов" else session.status.label,
                                    style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.weight(1f))
                            }
                            if (graph?.conclusion != null) TextButton(onClick = { openNode(PresentedGraph.CONCLUSION_ID) }) {
                                Text("Читать вывод", color = Accent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(9.dp), color = Paper, border = BorderStroke(1.dp, Line)) {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) { Icon(icon, label, Modifier.size(20.dp), tint = Ink) }
    }
}

@Composable
private fun GraphInformation(
    session: ResearchSession, graph: PresentedGraph, tab: Int, selectedId: String?,
    onSelect: (String) -> Unit, onFocus: (String) -> Unit, onStop: () -> Unit, onResume: () -> Unit,
) {
    val selected = graph.nodes.find { it.id == selectedId }
    val scroll = rememberLazyListState()
    LaunchedEffect(tab, selectedId) { scroll.scrollToItem(0) }
    LazyColumn(state = scroll, contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (tab == 0) {
            item {
                Text("ОТВЕТ ПО НОВОСТИ", style = MaterialTheme.typography.labelSmall, color = Muted)
                Spacer(Modifier.height(12.dp))
                val conclusion = graph.conclusion
                Text(conclusion?.label ?: when (session.status) {
                    RunStatus.RUNNING -> "Проверка продолжается"
                    RunStatus.AWAITING_API -> "Вывод пока не сделан"
                    else -> "Проверка не завершена"
                }, style = MaterialTheme.typography.headlineMedium, color = conclusion?.status?.tint() ?: Ink)
                Spacer(Modifier.height(14.dp))
                Text(session.summary.ifBlank { "Здесь появится итоговый ответ, когда исследование получит достаточно оснований и завершится." }, style = MaterialTheme.typography.bodyLarge)
            }
            if (graph.conclusion != null) item { OutlinedButton(onClick = { onFocus(PresentedGraph.CONCLUSION_ID) }, shape = RoundedCornerShape(6.dp)) { Text("Показать вывод на графе") } }
            val evidence = graph.nodes.filter { it.kind == NodeKind.EVIDENCE }
            if (evidence.isNotEmpty()) item { SectionLabel("На чём основан вывод") }
            items(evidence, key = { "result:${it.id}" }) { NodeDetails(it) }
            if (session.planB.isNotBlank()) item {
                HorizontalDivider(color = Line)
                Spacer(Modifier.height(18.dp))
                SectionLabel("Что можно сделать")
                Spacer(Modifier.height(10.dp))
                Text(session.planB, style = MaterialTheme.typography.bodyMedium)
            }
        } else if (tab == 1) {
            if (selected != null) item {
                NodeDetails(selected)
                TextButton(onClick = { onFocus(selected.id) }) { Text("Найти на графе") }
                HorizontalDivider(color = Line)
            }
            item { SectionLabel("Все материалы · ${graph.nodes.size}") }
            items(graph.nodes, key = { it.id }) { node ->
                Column(Modifier.fillMaxWidth().clickable { onSelect(node.id) }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(node.kind.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(node.label, style = MaterialTheme.typography.titleMedium)
                    Text(node.status.label, style = MaterialTheme.typography.bodySmall, color = node.status.tint())
                    Spacer(Modifier.height(4.dp)); HorizontalDivider(color = Line)
                }
            }
            if (session.attachments.isNotEmpty()) item {
                SectionLabel("Вложения")
                session.attachments.forEach { Text(it.name, style = MaterialTheme.typography.bodyMedium) }
            }
        } else {
            item {
                SectionLabel("Состояние")
                Spacer(Modifier.height(10.dp)); Text(session.stage, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp)); Text("${graph.nodes.size} узлов · ${graph.edges.size} связей", style = MaterialTheme.typography.bodySmall, color = Muted)
                if (session.status == RunStatus.RUNNING) TextButton(onClick = onStop) { Text("Остановить пример", color = Red) }
                if (session.status == RunStatus.INTERRUPTED) TextButton(onClick = onResume) { Text("Продолжить пример") }
            }
            item {
                SectionLabel("Статусы утверждений")
                Spacer(Modifier.height(12.dp))
                TrustStatus.entries.forEach { status ->
                    Row(Modifier.padding(vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(status.tint(), RoundedCornerShape(2.dp)))
                        Text(status.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item { Text("Учебный пример интерфейса. Материалы подготовлены заранее; результаты не являются реальным исследованием. Цвет отражает статус конкретного утверждения, а не абсолютную истинность источника.", style = MaterialTheme.typography.bodySmall, color = Muted) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) { Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted) }

@Composable
private fun NodeDetails(node: GraphNode) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(node.kind.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted)
        Text(node.label, style = MaterialTheme.typography.titleLarge)
        Text(node.status.label, style = MaterialTheme.typography.labelLarge, color = node.status.tint())
        Text(node.description.ifBlank { "Дополнительные сведения ещё не получены." }, style = MaterialTheme.typography.bodyMedium)
        if (node.quote.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(2.dp).height(54.dp).background(Accent))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(node.quote, style = MaterialTheme.typography.bodyLarge)
                    Text(node.sourceLabel, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
            }
        }
        if (isSafeWebUrl(node.sourceUrl)) TextButton(onClick = { openWebLink(context, node.sourceUrl) }) {
            Text("Открыть источник"); Spacer(Modifier.width(7.dp)); Icon(Icons.Outlined.NorthEast, null, Modifier.size(16.dp))
        }
    }
}

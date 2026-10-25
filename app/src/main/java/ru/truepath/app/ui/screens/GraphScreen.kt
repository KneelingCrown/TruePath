package ru.truepath.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.truepath.app.domain.*
import ru.truepath.app.ui.components.*
import ru.truepath.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun GraphScreen(
    session: ResearchSession?, onStop: () -> Unit, onResume: () -> Unit, onNew: () -> Unit, onBack: () -> Unit,
    initialMenuOpen: Boolean = false,
    onToggleTheme: () -> Unit = {},
    initialInfoTab: Int = 0,
) {
    val palette = LocalTruePathPalette.current
    var menuOpen by remember(session?.id) { mutableStateOf(initialMenuOpen) }
    var menuDrag by remember(session?.id) { mutableFloatStateOf(0f) }
    var menuWidth by remember { mutableFloatStateOf(1f) }
    var draggingMenu by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val dragTranslation by animateFloatAsState(menuDrag,
        animationSpec = if (draggingMenu) snap() else tween(200), label = "panel-drag")
    var selectedId by remember(session?.id) { mutableStateOf<String?>(null) }
    var infoTab by remember(session?.id) { mutableIntStateOf(initialInfoTab) }
    var focusId by remember(session?.id) { mutableStateOf<String?>(null) }
    var focusTick by remember(session?.id) { mutableIntStateOf(0) }
    val inspection = LocalInspectionMode.current
    val baseGraph = remember(session?.id, session?.nodes, session?.edges, session?.status, session?.summary, session?.title) {
        session?.presentedGraph(relaxLayout = inspection)
    }
    val topology = baseGraph?.nodes?.map { it.id }?.toSet() to ((baseGraph?.edges?.size ?: 0) / 6)
    val relaxedPositions by produceState<Map<String, Pair<Float, Float>>>(emptyMap(), session?.id, topology) {
        if (!inspection && baseGraph != null && baseGraph.minimumWidthDp > 0f) {
            delay(100)
            value = withContext(Dispatchers.Default) {
                baseGraph.organicLayout().nodes.associate { it.id to (it.x to it.y) }
            }
        }
    }
    val graph = if (inspection || baseGraph == null || baseGraph.minimumWidthDp == 0f) baseGraph else baseGraph.copy(
        nodes = baseGraph.nodes.map { node -> relaxedPositions[node.id]?.let { node.copy(x = it.first, y = it.second) } ?: node })
    BackHandler { if (menuOpen) menuOpen = false else onBack() }
    fun openNode(id: String) {
        menuDrag = 0f
        selectedId = id
        infoTab = if (id == PresentedGraph.CONCLUSION_ID) 0 else 1
        menuOpen = true
    }
    Box(Modifier.fillMaxSize().background(palette.background)) {
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
                Text(if (session?.scenarioId != null) "УЧЕБНОЕ ИССЛЕДОВАНИЕ" else "ИССЛЕДОВАНИЕ", style = MaterialTheme.typography.labelSmall, color = palette.muted)
            }
            WorkspaceButton("Материалы и сведения", Icons.Outlined.Menu, { menuDrag = 0f; menuOpen = true })
        }
        if (session != null) {
            Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WorkspaceButton("Показать весь путь", Icons.Outlined.CenterFocusStrong, { focusId = null; focusTick++ })
                if (session.status == RunStatus.RUNNING) {
                    CircularProgressIndicator(Modifier.size(12.dp), color = palette.accent, strokeWidth = 1.5.dp)
                    Text(session.stage, style = MaterialTheme.typography.bodySmall, color = palette.muted, modifier = Modifier.weight(1f), maxLines = 2)
                } else Text(if (session.status == RunStatus.COMPLETE) "Вывод готов" else session.status.label,
                    style = MaterialTheme.typography.bodySmall, color = palette.muted, modifier = Modifier.weight(1f))
                if (graph?.conclusion != null) TextButton(onClick = { openNode(PresentedGraph.CONCLUSION_ID) }) { Text("Читать вывод", color = palette.accent) }
            }
        }

        // Closed panels leave no invisible layer over the interactive graph.
        AnimatedVisibility(menuOpen, enter = fadeIn(tween(220)), exit = fadeOut(tween(180))) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .25f * (1f - menuDrag / menuWidth).coerceIn(0f, 1f))).clickable { menuOpen = false })
        }
        AnimatedVisibility(
            visible = menuOpen, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(180)),
            exit = slideOutHorizontally(tween(230)) { it } + fadeOut(tween(180)),
        ) {
            Surface(Modifier.fillMaxHeight().widthIn(max = 420.dp).fillMaxWidth(.91f)
                .onSizeChanged { menuWidth = it.width.toFloat().coerceAtLeast(1f) }
                .graphicsLayer { translationX = dragTranslation }
                .pointerInput(session?.id) {
                    val velocity = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = { velocity.resetTracking(); draggingMenu = true },
                        onHorizontalDrag = { change, amount ->
                            velocity.addPosition(change.uptimeMillis, change.position)
                            change.consume()
                            menuDrag = (menuDrag + amount).coerceIn(0f, menuWidth)
                        },
                        onDragEnd = {
                            val fastSwipe = velocity.calculateVelocity().x > with(density) { 650.dp.toPx() } && menuDrag > with(density) { 16.dp.toPx() }
                            if (menuDrag > menuWidth * .22f || fastSwipe) menuOpen = false else menuDrag = 0f
                            draggingMenu = false
                        },
                        onDragCancel = { draggingMenu = false; menuDrag = 0f },
                    )
                }, color = palette.paper, shadowElevation = 12.dp) {
                Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 12.dp, top = 10.dp, bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Исследование", style = MaterialTheme.typography.headlineMedium)
                        IconButton(onClick = { menuOpen = false }) { Icon(Icons.Outlined.Close, "Закрыть материалы") }
                    }
                    TabRow(selectedTabIndex = infoTab, containerColor = palette.paper, contentColor = palette.accent) {
                        listOf("Итог", "Материалы", "Сведения").forEachIndexed { index, title ->
                            Tab(selected = infoTab == index, onClick = { infoTab = index; selectedId = null }, text = { Text(title, style = MaterialTheme.typography.labelLarge) })
                        }
                    }
                    if (session != null && graph != null) {
                        GraphInformation(session, graph, infoTab, selectedId, onSelect = { selectedId = it }, onFocus = {
                            focusId = it; focusTick++; menuOpen = false
                        }, onStop = onStop, onResume = onResume, onToggleTheme = onToggleTheme)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val palette = LocalTruePathPalette.current
    Surface(shape = RoundedCornerShape(9.dp), color = palette.paper, border = BorderStroke(1.dp, palette.line)) {
        IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) { Icon(icon, label, Modifier.size(20.dp), tint = palette.ink) }
    }
}

@Composable
private fun GraphInformation(
    session: ResearchSession, graph: PresentedGraph, tab: Int, selectedId: String?,
    onSelect: (String) -> Unit, onFocus: (String) -> Unit, onStop: () -> Unit, onResume: () -> Unit,
    onToggleTheme: () -> Unit,
) {
    val palette = LocalTruePathPalette.current
    val selected = graph.nodes.find { it.id == selectedId }
    val scroll = rememberLazyListState()
    LaunchedEffect(tab, selectedId) { scroll.scrollToItem(0) }
    LazyColumn(state = scroll, contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (tab == 0) {
            item {
                Text("ОТВЕТ ПО НОВОСТИ", style = MaterialTheme.typography.labelSmall, color = palette.muted)
                Spacer(Modifier.height(12.dp))
                val conclusion = graph.conclusion
                Text(conclusion?.label ?: when (session.status) {
                    RunStatus.RUNNING -> "Проверка продолжается"
                    RunStatus.AWAITING_API -> "Вывод пока не сделан"
                    else -> "Проверка не завершена"
                }, style = MaterialTheme.typography.headlineMedium, color = conclusion?.status?.tint() ?: palette.ink)
                Spacer(Modifier.height(14.dp))
                Text(session.summary.ifBlank { "Здесь появится итоговый ответ, когда исследование получит достаточно оснований и завершится." }, style = MaterialTheme.typography.bodyLarge)
            }
            if (graph.conclusion != null) item { OutlinedButton(onClick = { onFocus(PresentedGraph.CONCLUSION_ID) }, shape = RoundedCornerShape(6.dp)) { Text("Показать вывод на графе") } }
            val evidence = graph.nodes.filter { it.kind == NodeKind.EVIDENCE }
            if (evidence.isNotEmpty()) item { SectionLabel("На чём основан вывод") }
            items(evidence, key = { "result:${it.id}" }) { NodeDetails(it) }
            if (session.planB.isNotBlank()) item {
                HorizontalDivider(color = palette.line)
                Spacer(Modifier.height(18.dp))
                SectionLabel("Что можно сделать")
                Spacer(Modifier.height(10.dp))
                Text(session.planB, style = MaterialTheme.typography.bodyMedium)
            }
        } else if (tab == 1) {
            if (selected != null) item {
                NodeDetails(selected)
                TextButton(onClick = { onFocus(selected.id) }) { Text("Найти на графе") }
                val related = graph.edges.filter { it.from == selected.id || it.to == selected.id }
                if (related.isNotEmpty()) {
                    SectionLabel("Связи материала")
                    related.forEach { edge ->
                        val otherId = if (edge.from == selected.id) edge.to else edge.from
                        val other = graph.nodes.find { it.id == otherId }
                        if (other != null) TextButton(onClick = { onSelect(otherId) }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(if (edge.to == selected.id) "${other.kind.label} → этот материал" else "Этот материал → ${other.kind.label}",
                                    style = MaterialTheme.typography.labelSmall, color = palette.muted)
                                Text("${edge.label}: ${other.label}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                HorizontalDivider(color = palette.line)
            }
            item { SectionLabel("Все материалы · ${graph.nodes.size}") }
            items(graph.orderedMaterials, key = { it.id }) { node ->
                Column(Modifier.animateItem().fillMaxWidth().clickable { onSelect(node.id) }, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(node.kind.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.muted)
                    Text(node.label, style = MaterialTheme.typography.titleMedium)
                    Text(node.status.label, style = MaterialTheme.typography.bodySmall, color = node.status.tint())
                    Spacer(Modifier.height(4.dp)); HorizontalDivider(color = palette.line)
                }
            }
            if (session.attachments.isNotEmpty()) item {
                SectionLabel("Вложения")
                session.attachments.forEach { Text(it.name, style = MaterialTheme.typography.bodyMedium) }
            }
        } else {
            item {
                val dark = LocalTruePathDark.current
                SectionLabel("Оформление")
                TextButton(onClick = onToggleTheme) {
                    Icon(if (dark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text(if (dark) "Включить светлую тему" else "Включить тёмную тему")
                }
            }
            item {
                SectionLabel("Состояние")
                Spacer(Modifier.height(10.dp)); Text(session.stage, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp)); Text("${graph.nodes.size} узлов · ${graph.edges.size} связей", style = MaterialTheme.typography.bodySmall, color = palette.muted)
                if (session.status == RunStatus.RUNNING) TextButton(onClick = onStop) { Text("Остановить пример", color = palette.red) }
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
            item { Text("Учебный пример интерфейса. Материалы подготовлены заранее; результаты не являются реальным исследованием. Цвет отражает статус конкретного утверждения, а не абсолютную истинность источника.", style = MaterialTheme.typography.bodySmall, color = palette.muted) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val palette = LocalTruePathPalette.current
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.muted)
}

@Composable
private fun NodeDetails(node: GraphNode) {
    val palette = LocalTruePathPalette.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(node.kind.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = palette.muted)
        Text(node.label, style = MaterialTheme.typography.titleLarge)
        Text(node.status.label, style = MaterialTheme.typography.labelLarge, color = node.status.tint())
        Text(node.description.ifBlank { "Дополнительные сведения ещё не получены." }, style = MaterialTheme.typography.bodyMedium)
        if (node.quote.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(2.dp).height(54.dp).background(palette.accent))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(node.quote, style = MaterialTheme.typography.bodyLarge)
                    Text(node.sourceLabel, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
            }
        }
        if (isSafeWebUrl(node.sourceUrl)) TextButton(onClick = { openWebLink(context, node.sourceUrl) }) {
            Text("Открыть источник"); Spacer(Modifier.width(7.dp)); Icon(Icons.Outlined.NorthEast, null, Modifier.size(16.dp))
        }
    }
}

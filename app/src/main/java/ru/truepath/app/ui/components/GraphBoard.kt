package ru.truepath.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ru.truepath.app.domain.*
import ru.truepath.app.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun GraphBoard(
    session: ResearchSession,
    graph: PresentedGraph,
    selectedId: String?,
    focusNodeId: String?,
    focusTick: Int,
    modifier: Modifier = Modifier,
    onNode: (String) -> Unit,
) {
    val palette = LocalTruePathPalette.current
    var zoom by remember(session.id) { mutableFloatStateOf(1f) }
    var pan by remember(session.id) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    var autoFit by remember(session.id) { mutableStateOf(true) }
    var handledFocus by remember(session.id) { mutableIntStateOf(-1) }
    var initializedCamera by remember(session.id) { mutableStateOf(false) }
    var cameraMotion by remember(session.id) { mutableStateOf<Job?>(null) }
    val edgesProgress = remember(session.id) { mutableStateMapOf<String, Float>() }
    val displayPositions = remember(session.id) { mutableStateMapOf<String, Offset>() }
    val nodeMotions = remember(session.id) { mutableMapOf<String, Job>() }
    val density = LocalDensity.current
    val inspection = LocalInspectionMode.current
    LaunchedEffect(graph.edges.map { it.id }) {
        graph.edges.filter { it.id !in edgesProgress }.forEach { edge ->
            edgesProgress[edge.id] = if (inspection) 1f else 0f
            if (!inspection) scope.launch {
                animate(0f, 1f, animationSpec = tween(360)) { value, _ -> edgesProgress[edge.id] = value }
            }
        }
    }
    BoxWithConstraints(modifier.background(palette.graphBackground).clipToBounds()) {
        val viewportWidth = with(density) { maxWidth.toPx() }
        val width = maxOf(viewportWidth, with(density) { graph.minimumWidthDp.dp.toPx() })
        // Keep endpoint cards apart on short screens; the overview fits the world.
        val availableHeight = with(density) { maxHeight.toPx() }
        val height = maxOf(availableHeight, with(density) { graph.minimumHeightDp.dp.toPx() })
        val overviewZoom = minOf(viewportWidth / width, availableHeight / height, 1f)
        val endpointWidth = minOf(maxWidth - 38.dp, 318.dp)
        val layoutKey = graph.nodes.associate { it.id to (it.x to it.y) }
        LaunchedEffect(layoutKey) {
            graph.nodes.forEach { node ->
                val target = Offset(node.x, node.y)
                val previous = displayPositions[node.id]
                if (previous == target) return@forEach
                nodeMotions.remove(node.id)?.cancel()
                if (inspection || node.kind == NodeKind.MATERIAL) displayPositions[node.id] = target
                else {
                    val from = previous ?: displayPositions.values.minByOrNull { (it - target).getDistanceSquared() } ?: target
                    displayPositions[node.id] = from
                    nodeMotions[node.id] = scope.launch {
                        Animatable(from, Offset.VectorConverter).animateTo(target, tween(600)) {
                            displayPositions[node.id] = value
                        }
                    }
                }
            }
        }
        val nodeBounds = remember(layoutKey, width, height, density, endpointWidth) {
            graph.nodes.map { node ->
                val endpoint = node.kind == NodeKind.MATERIAL || node.kind == NodeKind.CONCLUSION
                val nodeWidth = with(density) { (if (endpoint) endpointWidth else 112.dp).toPx() }
                val nodeHeight = with(density) { (when (node.kind) { NodeKind.MATERIAL -> 106.dp; NodeKind.CONCLUSION -> 138.dp; else -> 90.dp }).toPx() }
                floatArrayOf(node.x * width - nodeWidth / 2, node.y * height - nodeHeight / 2,
                    node.x * width + nodeWidth / 2, node.y * height + nodeHeight / 2)
            }
        }
        val left = nodeBounds.minOfOrNull { it[0] } ?: 0f
        val top = nodeBounds.minOfOrNull { it[1] } ?: 0f
        val right = nodeBounds.maxOfOrNull { it[2] } ?: width
        val bottom = nodeBounds.maxOfOrNull { it[3] } ?: height
        val margin = with(density) { 24.dp.toPx() }
        val panBounds by rememberUpdatedState(floatArrayOf(left - margin, top - margin, right + margin, bottom + margin))
        fun bounded(value: Offset, scale: Float) = Offset(
            boundedGraphPanWithin(value.x, panBounds[0], panBounds[2], viewportWidth, scale),
            boundedGraphPanWithin(value.y, panBounds[1], panBounds[3], availableHeight, scale),
        )
        LaunchedEffect(session.id, focusTick, width, availableHeight, layoutKey, autoFit) {
            val requestedFocus = handledFocus != focusTick
            if (requestedFocus) { handledFocus = focusTick; autoFit = focusNodeId == null }
            if (!autoFit && !requestedFocus) return@LaunchedEffect
            cameraMotion?.cancel()
            val target = graph.nodes.find { it.id == focusNodeId }.takeIf { requestedFocus && !autoFit }
            val desired = if (target != null) {
                val scale = 1.1f
                GraphCameraTarget(scale, viewportWidth / 2 - target.x * width * scale, availableHeight / 2 - target.y * height * scale)
            } else if (graph.nodes.isNotEmpty()) {
                fitGraphCamera(left, top, right, bottom, viewportWidth, availableHeight, margin)
            } else GraphCameraTarget(overviewZoom, (viewportWidth - width * overviewZoom) / 2, 0f)
            val targetZoom = desired.zoom
            val targetPan = bounded(Offset(desired.x, desired.y), targetZoom)
            if (inspection || !initializedCamera) {
                zoom = targetZoom; pan = targetPan; initializedCamera = true
            } else {
                val fromZoom = zoom; val fromPan = pan
                cameraMotion = scope.launch {
                    animate(0f, 1f, animationSpec = tween(if (requestedFocus) 400 else 650)) { fraction, _ ->
                        zoom = fromZoom + (targetZoom - fromZoom) * fraction
                        pan = bounded(fromPan + (targetPan - fromPan) * fraction, zoom)
                    }
                }
            }
        }
        Box(Modifier.fillMaxSize().pointerInput(session.id, width, availableHeight) {
            detectTransformGestures { centroid, delta, scale, _ ->
                cameraMotion?.cancel()
                autoFit = false
                val nextZoom = (zoom * scale).coerceIn(minOf(.55f, overviewZoom), 2.6f)
                pan = bounded(centroid - (centroid - pan) * (nextZoom / zoom) + delta, nextZoom)
                zoom = nextZoom
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val step = 48.dp.toPx()
                var x = step / 2
                while (x < size.width) {
                    var y = step / 2
                    while (y < size.height) {
                        drawLine(palette.line.copy(alpha = .65f), Offset(x - 2.dp.toPx(), y), Offset(x + 2.dp.toPx(), y), .6.dp.toPx())
                        drawLine(palette.line.copy(alpha = .65f), Offset(x, y - 2.dp.toPx()), Offset(x, y + 2.dp.toPx()), .6.dp.toPx())
                        y += step
                    }
                    x += step
                }
            }
            // The transformed world must have real layout bounds as well as drawing
            // coordinates, so nodes outside the initial viewport remain tappable.
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                .requiredSize(with(density) { width.toDp() }, with(density) { height.toDp() }).graphicsLayer {
                scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y
                transformOrigin = TransformOrigin(0f, 0f)
            }) {
                Canvas(Modifier.fillMaxSize().semantics {
                    contentDescription = "Путь от исходной новости к выводу. Все материалы доступны в меню справа сверху."
                }) {
                    val points = graph.nodes.associate { node ->
                        val position = displayPositions[node.id] ?: Offset(node.x, node.y)
                        node.id to Offset(position.x * width, position.y * height)
                    }
                    graph.edges.forEach { edge ->
                        val start = points[edge.from] ?: return@forEach
                        val end = points[edge.to] ?: return@forEach
                        val highlighted = edge.from == selectedId || edge.to == selectedId || edge.to == PresentedGraph.CONCLUSION_ID
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            val bend = (end.y - start.y) * .52f
                            cubicTo(start.x, start.y + bend, end.x, end.y - bend, end.x, end.y)
                        }
                        val progress = edgesProgress[edge.id] ?: if (inspection) 1f else 0f
                        val visiblePath = if (progress >= 1f) path else Path().also {
                            val measure = PathMeasure().apply { setPath(path, false) }
                            measure.getSegment(0f, measure.length * progress, it, true)
                        }
                        drawPath(visiblePath, if (highlighted) palette.accent.copy(alpha = .72f) else palette.graphEdge, style = Stroke(if (highlighted) 1.8.dp.toPx() else 1.dp.toPx()))
                        val middle = (start + end) / 2f
                        if (progress >= .5f) drawCircle(if (highlighted) palette.accent else palette.graphEdge, 2.dp.toPx(), middle)
                    }
                }
                graph.nodes.forEach { node -> key(node.id) {
                    val endpoint = node.kind == NodeKind.MATERIAL || node.kind == NodeKind.CONCLUSION
                    val nodeWidth = if (endpoint) endpointWidth else 112.dp
                    val nodeHeight = when (node.kind) { NodeKind.MATERIAL -> 106.dp; NodeKind.CONCLUSION -> 138.dp; else -> 90.dp }
                    val appearance = remember { MutableTransitionState(inspection).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = appearance,
                        enter = fadeIn(tween(240)) + scaleIn(tween(320), initialScale = .88f),
                        modifier = Modifier.offset {
                            val position = displayPositions[node.id] ?: Offset(node.x, node.y)
                            with(density) { IntOffset((position.x * width - nodeWidth.toPx() / 2).roundToInt(),
                                (position.y * height - nodeHeight.toPx() / 2).roundToInt()) }
                        }.width(nodeWidth).height(nodeHeight),
                    ) {
                        when (node.kind) {
                            NodeKind.MATERIAL -> StartNode(node, onClick = { onNode(node.id) })
                            NodeKind.CONCLUSION -> ConclusionNode(node, onClick = { onNode(node.id) })
                            else -> EvidenceNode(node, node.id == selectedId, onClick = { onNode(node.id) })
                        }
                    }
                } }
            }
        }
    }
}

@Composable
private fun StartNode(node: GraphNode, onClick: () -> Unit) {
    val palette = LocalTruePathPalette.current
    Surface(Modifier.fillMaxSize().clickable(onClick = onClick), shape = RoundedCornerShape(10.dp), color = palette.accent) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = palette.lime, shape = RoundedCornerShape(3.dp)) { Text("01", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF202925)) }
                Text("ИСХОДНАЯ НОВОСТЬ", style = MaterialTheme.typography.labelSmall, color = palette.onAccent.copy(alpha = .7f))
            }
            Text(node.label, color = palette.onAccent, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ConclusionNode(node: GraphNode, onClick: () -> Unit) {
    val palette = LocalTruePathPalette.current
    Surface(Modifier.fillMaxSize().clickable(onClick = onClick), shape = RoundedCornerShape(10.dp), color = palette.paper, border = BorderStroke(1.4.dp, node.status.tint())) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ВЫВОД", style = MaterialTheme.typography.labelSmall, color = node.status.tint(), fontWeight = FontWeight.Bold)
                Icon(Icons.Outlined.NorthEast, "Открыть итог и основания", Modifier.size(16.dp), tint = node.status.tint())
            }
            Text(node.label, style = MaterialTheme.typography.titleMedium, color = palette.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(node.description, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EvidenceNode(node: GraphNode, selected: Boolean, onClick: () -> Unit) {
    val palette = LocalTruePathPalette.current
    val statusColor by animateColorAsState(node.status.fill(), tween(360), label = "node-status")
    Column(Modifier.fillMaxSize().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = CircleShape, color = statusColor, border = if (selected) BorderStroke(2.dp, palette.ink) else null) {
            Box(Modifier.size(43.dp), contentAlignment = Alignment.Center) {
                Icon(when (node.kind) {
                    NodeKind.CLAIM -> Icons.Outlined.QuestionAnswer
                    NodeKind.SOURCE -> Icons.Outlined.Description
                    else -> Icons.Outlined.FormatQuote
                }, null, Modifier.size(18.dp), tint = Color.White)
            }
        }
        Text(node.label, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = palette.ink)
    }
}

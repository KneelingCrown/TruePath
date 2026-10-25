package ru.truepath.app.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    var zoom by remember(session.id) { mutableFloatStateOf(1f) }
    var pan by remember(session.id) { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.background(GraphBackground)) {
        val width = with(density) { maxWidth.toPx() }
        // Keep endpoint cards apart on short screens; the overview fits the world.
        val availableHeight = with(density) { maxHeight.toPx() }
        val height = maxOf(availableHeight, with(density) { 680.dp.toPx() })
        val overviewZoom = (availableHeight / height).coerceAtMost(1f)
        val endpointWidth = minOf(maxWidth - 38.dp, 318.dp)
        LaunchedEffect(session.id, focusTick, width, availableHeight) {
            val target = graph.nodes.find { it.id == focusNodeId }
            if (target == null) { zoom = overviewZoom; pan = Offset(width * (1 - zoom) / 2, 0f) }
            else {
                zoom = 1.1f
                pan = Offset(width / 2 - target.x * width * zoom, availableHeight / 2 - target.y * height * zoom)
            }
        }
        Box(Modifier.fillMaxSize().pointerInput(session.id) {
            detectTransformGestures { centroid, delta, scale, _ ->
                val nextZoom = (zoom * scale).coerceIn(.55f, 2.6f)
                pan = centroid - (centroid - pan) * (nextZoom / zoom) + delta
                zoom = nextZoom
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val step = 48.dp.toPx()
                var x = step / 2
                while (x < size.width) {
                    var y = step / 2
                    while (y < size.height) {
                        drawLine(Line.copy(alpha = .65f), Offset(x - 2.dp.toPx(), y), Offset(x + 2.dp.toPx(), y), .6.dp.toPx())
                        drawLine(Line.copy(alpha = .65f), Offset(x, y - 2.dp.toPx()), Offset(x, y + 2.dp.toPx()), .6.dp.toPx())
                        y += step
                    }
                    x += step
                }
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y
                transformOrigin = TransformOrigin(0f, 0f)
            }) {
                Canvas(Modifier.fillMaxSize().semantics {
                    contentDescription = "Путь от исходной новости к выводу. Все материалы доступны в меню справа сверху."
                }) {
                    val points = graph.nodes.associate { it.id to Offset(it.x * width, it.y * height) }
                    graph.edges.forEach { edge ->
                        val start = points[edge.from] ?: return@forEach
                        val end = points[edge.to] ?: return@forEach
                        val highlighted = edge.from == selectedId || edge.to == selectedId || edge.to == PresentedGraph.CONCLUSION_ID
                        val path = Path().apply {
                            moveTo(start.x, start.y)
                            val bend = (end.y - start.y) * .52f
                            cubicTo(start.x, start.y + bend, end.x, end.y - bend, end.x, end.y)
                        }
                        drawPath(path, if (highlighted) Accent.copy(alpha = .72f) else Color(0xFFB8BEB0), style = Stroke(if (highlighted) 1.8.dp.toPx() else 1.dp.toPx()))
                        val middle = (start + end) / 2f
                        drawCircle(if (highlighted) Accent else Color(0xFFB8BEB0), 2.dp.toPx(), middle)
                    }
                }
                graph.nodes.forEach { node ->
                    val endpoint = node.kind == NodeKind.MATERIAL || node.kind == NodeKind.CONCLUSION
                    val nodeWidth = if (endpoint) endpointWidth else 112.dp
                    val nodeHeight = when (node.kind) { NodeKind.MATERIAL -> 106.dp; NodeKind.CONCLUSION -> 138.dp; else -> 90.dp }
                    val left = with(density) { (node.x * width - nodeWidth.toPx() / 2).roundToInt() }
                    val top = with(density) { (node.y * height - nodeHeight.toPx() / 2).roundToInt() }
                    Box(Modifier.offset { IntOffset(left, top) }.width(nodeWidth).height(nodeHeight)) {
                        when (node.kind) {
                            NodeKind.MATERIAL -> StartNode(node, onClick = { onNode(node.id) })
                            NodeKind.CONCLUSION -> ConclusionNode(node, onClick = { onNode(node.id) })
                            else -> EvidenceNode(node, node.id == selectedId, onClick = { onNode(node.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StartNode(node: GraphNode, onClick: () -> Unit) {
    Surface(Modifier.fillMaxSize().clickable(onClick = onClick), shape = RoundedCornerShape(10.dp), color = Accent) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(color = Lime, shape = RoundedCornerShape(3.dp)) { Text("01", Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Ink) }
                Text("ИСХОДНАЯ НОВОСТЬ", style = MaterialTheme.typography.labelSmall, color = Paper.copy(alpha = .7f))
            }
            Text(node.label, color = Paper, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ConclusionNode(node: GraphNode, onClick: () -> Unit) {
    Surface(Modifier.fillMaxSize().clickable(onClick = onClick), shape = RoundedCornerShape(10.dp), color = Paper, border = BorderStroke(1.4.dp, node.status.tint())) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("ВЫВОД", style = MaterialTheme.typography.labelSmall, color = node.status.tint(), fontWeight = FontWeight.Bold)
                Icon(Icons.Outlined.NorthEast, "Открыть итог и основания", Modifier.size(16.dp), tint = node.status.tint())
            }
            Text(node.label, style = MaterialTheme.typography.titleMedium, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(node.description, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EvidenceNode(node: GraphNode, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = CircleShape, color = if (selected) Accent else Paper, border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Accent else Line)) {
            Box(Modifier.size(43.dp), contentAlignment = Alignment.Center) {
                Icon(when (node.kind) {
                    NodeKind.CLAIM -> Icons.Outlined.QuestionAnswer
                    NodeKind.SOURCE -> Icons.Outlined.Description
                    else -> Icons.Outlined.FormatQuote
                }, null, Modifier.size(18.dp), tint = if (selected) Paper else node.status.tint())
            }
        }
        Text(node.label, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = Ink)
    }
}

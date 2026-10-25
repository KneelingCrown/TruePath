package ru.truepath.app.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.truepath.app.domain.*
import ru.truepath.app.ui.theme.*
import kotlin.math.hypot

@Composable
fun GraphBoard(session: ResearchSession, onNode: (String) -> Unit) {
    var zoom by remember(session.id) { mutableFloatStateOf(1f) }
    var pan by remember(session.id) { mutableStateOf(Offset.Zero) }
    val currentSession by rememberUpdatedState(session)
    val currentOnNode by rememberUpdatedState(onNode)
    val density = LocalDensity.current
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    val pulse = if (session.status == RunStatus.RUNNING) {
        val transition = rememberInfiniteTransition(label = "Research pulse")
        transition.animateFloat(.25f, .6f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "Node glow").value
    } else .2f
    Box(Modifier.fillMaxWidth().height(430.dp).clip(RoundedCornerShape(24.dp)).background(GraphBackground)) {
        Canvas(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "Граф: ${session.nodes.size} узлов, ${session.edges.size} связей. Элементы также доступны в списке под графом." }
                .pointerInput(session.id) {
                    detectTransformGestures { centroid, delta, scale, _ ->
                        val nextZoom = (zoom * scale).coerceIn(.65f, 2.5f)
                        pan = centroid - (centroid - pan) * (nextZoom / zoom) + delta
                        zoom = nextZoom
                    }
                }
                .pointerInput(session.id) {
                    detectTapGestures(
                        onDoubleTap = { zoom = 1f; pan = Offset.Zero },
                        onTap = { tap ->
                            val point = (tap - pan) / zoom
                            val hit = currentSession.nodes.minByOrNull { node ->
                                hypot(point.x - node.x * size.width, point.y - node.y * size.height)
                            }
                            if (hit != null && hypot(point.x - hit.x * size.width, point.y - hit.y * size.height) < with(density) { 34.dp.toPx() }) currentOnNode(hit.id)
                        },
                    )
                },
        ) {
            val grid = 22.dp.toPx()
            var gx = grid
            while (gx < size.width) {
                var gy = grid
                while (gy < size.height) { drawCircle(Color.White.copy(alpha = .07f), 1.dp.toPx(), Offset(gx, gy)); gy += grid }
                gx += grid
            }
            withTransform({ translate(pan.x, pan.y); scale(zoom, zoom, pivot = Offset.Zero) }) {
                val points = session.nodes.associate { it.id to Offset(it.x * size.width, it.y * size.height) }
                session.edges.forEach { edge ->
                    val from = points[edge.from] ?: return@forEach
                    val to = points[edge.to] ?: return@forEach
                    // Предполагаемые связи (основание не установлено) заметно слабее.
                    val alpha = if (edge.isEstablished) .75f else .35f
                    drawLine(Color(0xFF5A6384).copy(alpha = alpha), from, to, 1.5.dp.toPx())
                    val mid = (from + to) / 2f
                    drawCircle(Color(0xFF8E97BE), 2.dp.toPx(), mid)
                }
                session.nodes.forEach { node ->
                    val center = points.getValue(node.id)
                    val tint = when (node.status) {
                        TrustStatus.UNCHECKED -> if (node.kind == NodeKind.MATERIAL) Color(0xFFA79BFF) else Color(0xFFA2ACC9)
                        TrustStatus.SUPPORTED -> Color(0xFF66DCB5)
                        TrustStatus.UNCERTAIN -> Color(0xFFF3CA72)
                        TrustStatus.CONTRADICTED -> Color(0xFFFF8B9D)
                    }
                    val radius = if (node.kind == NodeKind.MATERIAL) 19.dp.toPx() else 15.dp.toPx()
                    drawCircle(tint.copy(alpha = pulse * .25f), radius + 9.dp.toPx(), center)
                    drawCircle(tint.copy(alpha = .18f), radius, center)
                    drawCircle(tint, 6.dp.toPx(), center)
                    val label = node.label.let { if (it.length > 21) it.take(19) + "…" else it }
                    textPaint.textSize = 10.dp.toPx()
                    val labelWidth = textPaint.measureText(label) + 14.dp.toPx()
                    drawRoundRect(GraphBackground.copy(alpha = .96f), Offset(center.x - labelWidth / 2, center.y + radius + 5.dp.toPx()), Size(labelWidth, 21.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                    textPaint.color = android.graphics.Color.rgb(231, 234, 248)
                    drawContext.canvas.nativeCanvas.drawText(label, center.x, center.y + radius + 19.dp.toPx(), textPaint)
                }
            }
        }
        Surface(Modifier.align(Alignment.TopStart).padding(15.dp), color = Color.White.copy(alpha = .08f), shape = RoundedCornerShape(9.dp)) {
            Text(if (session.status == RunStatus.RUNNING) "ЖИВОЙ ГРАФ · ПРИМЕР" else "ГРАФ ИССЛЕДОВАНИЯ", Modifier.padding(horizontal = 9.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFCCCAFF))
        }
        IconButton(onClick = { zoom = 1f; pan = Offset.Zero }, modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)) {
            Icon(Icons.Outlined.CenterFocusStrong, "Показать весь граф", tint = Color(0xFFCCD3EB))
        }
        Text("Раздвиньте пальцы для масштаба · нажмите на узел", Modifier.align(Alignment.BottomCenter).padding(13.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFFADB5D1))
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag("Подтверждается", Green)
        Tag("Неопределённость", Amber)
        Tag("Противоречие", Red)
        Tag("Не проверено", Muted)
    }
}

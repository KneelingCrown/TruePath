package ru.truepath.app.domain

import kotlin.math.*

/** Deterministic spring layout. Edges pull related nodes together; rectangles repel.
 * Seeds keep branches recognizable as new information arrives. Endpoints stay anchored.
 */
internal fun PresentedGraph.organicLayout(relax: Boolean = true): PresentedGraph {
    if (nodes.size < 2) return this
    val width = minimumWidthDp.coerceAtLeast(1080f)
    val height = minimumHeightDp.coerceAtLeast(1280f)
    val ordered = nodes.sortedBy { it.id }
    val index = ordered.mapIndexed { i, node -> node.id to i }.toMap()
    val hubs = listOf(.20f to .29f, .49f to .24f, .79f to .32f, .19f to .62f, .51f to .62f, .81f to .68f)
    val anchors = ordered.map { it.kind == NodeKind.MATERIAL || it.kind == NodeKind.CONCLUSION }
    val seeds = ordered.map { node ->
        when (node.kind) {
            NodeKind.MATERIAL -> .5f * width to .055f * height
            NodeKind.CONCLUSION -> .5f * width to .94f * height
            else -> {
                val branch = node.id.substringAfterLast(':').toIntOrNull()?.mod(hubs.size) ?: 0
                val hub = hubs[branch]
                val kind = node.id.split(':').getOrNull(1)
                val angle = branch * 1.13 + when (kind) {
                    "document" -> .1
                    "retelling" -> 1.8
                    "evidence" -> 3.4
                    "limit" -> 4.9
                    else -> 0.0
                }
                val radius = if (kind == "claim") 0f else if (kind == "limit") 145f else 125f
                hub.first * width + cos(angle).toFloat() * radius to hub.second * height + sin(angle).toFloat() * radius
            }
        }
    }
    if (!relax) {
        val positions = ordered.mapIndexed { i, node -> node.id to seeds[i] }.toMap()
        return copy(nodes = nodes.map { node -> positions.getValue(node.id).let { node.copy(x = it.first / width, y = it.second / height) } })
    }
    val x = FloatArray(ordered.size) { seeds[it].first }
    val y = FloatArray(ordered.size) { seeds[it].second }
    val vx = FloatArray(ordered.size)
    val vy = FloatArray(ordered.size)
    val halfWidths = ordered.map { if (it.kind == NodeKind.MATERIAL || it.kind == NodeKind.CONCLUSION) 159f else 56f }
    val halfHeights = ordered.map { when (it.kind) { NodeKind.MATERIAL -> 53f; NodeKind.CONCLUSION -> 69f; else -> 45f } }
    repeat(150) {
        val fx = FloatArray(ordered.size) { (seeds[it].first - x[it]) * .027f }
        val fy = FloatArray(ordered.size) { (seeds[it].second - y[it]) * .027f }
        for (a in ordered.indices) for (b in a + 1 until ordered.size) {
            val dx = x[a] - x[b]
            val dy = y[a] - y[b]
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val repel = 1800f / (distance * distance).coerceAtLeast(100f)
            fx[a] += dx / distance * repel; fx[b] -= dx / distance * repel
            fy[a] += dy / distance * repel; fy[b] -= dy / distance * repel
            val overlapX = halfWidths[a] + halfWidths[b] + 26f - abs(dx)
            val overlapY = halfHeights[a] + halfHeights[b] + 26f - abs(dy)
            if (overlapX > 0 && overlapY > 0) {
                if (overlapX < overlapY) {
                    val push = overlapX * .5f * if (dx >= 0) 1f else -1f
                    fx[a] += push; fx[b] -= push
                } else {
                    val push = overlapY * .5f * if (dy >= 0) 1f else -1f
                    fy[a] += push; fy[b] -= push
                }
            }
        }
        edges.forEach { edge ->
            val a = index[edge.from] ?: return@forEach
            val b = index[edge.to] ?: return@forEach
            val dx = x[b] - x[a]; val dy = y[b] - y[a]
            val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val sameBranch = edge.from.substringAfterLast(':') == edge.to.substringAfterLast(':')
            val rest = if (sameBranch) 155f else 285f
            val force = (distance - rest) * if (anchors[a] || anchors[b]) .003f else .009f
            fx[a] += dx / distance * force; fx[b] -= dx / distance * force
            fy[a] += dy / distance * force; fy[b] -= dy / distance * force
        }
        ordered.indices.forEach { i ->
            if (!anchors[i]) {
                vx[i] = ((vx[i] + fx[i]) * .72f).coerceIn(-14f, 14f)
                vy[i] = ((vy[i] + fy[i]) * .72f).coerceIn(-14f, 14f)
                x[i] = (x[i] + vx[i]).coerceIn(halfWidths[i] + 24, width - halfWidths[i] - 24)
                y[i] = (y[i] + vy[i]).coerceIn(180f, height - 190f)
            }
        }
    }
    // Finish with collision projection so labels do not overlap once motion settles.
    repeat(70) {
        for (a in ordered.indices) for (b in a + 1 until ordered.size) {
            val dx = x[a] - x[b]; val dy = y[a] - y[b]
            val overlapX = halfWidths[a] + halfWidths[b] + 18f - abs(dx)
            val overlapY = halfHeights[a] + halfHeights[b] + 18f - abs(dy)
            if (overlapX > 0f && overlapY > 0f && !(anchors[a] && anchors[b])) {
                val shareA = if (anchors[a]) 0f else if (anchors[b]) 1f else .5f
                if (overlapX < overlapY) {
                    val delta = (overlapX + .1f) * if (dx >= 0f) 1f else -1f
                    x[a] += delta * shareA; x[b] -= delta * (1f - shareA)
                } else {
                    val delta = (overlapY + .1f) * if (dy >= 0f) 1f else -1f
                    y[a] += delta * shareA; y[b] -= delta * (1f - shareA)
                }
            }
        }
        ordered.indices.filterNot { anchors[it] }.forEach { i ->
            x[i] = x[i].coerceIn(halfWidths[i] + 24, width - halfWidths[i] - 24)
            y[i] = y[i].coerceIn(180f, height - 190f)
        }
    }
    val positions = ordered.mapIndexed { i, node -> node.id to (x[i] / width to y[i] / height) }.toMap()
    return copy(nodes = nodes.map { node -> positions.getValue(node.id).let { node.copy(x = it.first, y = it.second) } })
}

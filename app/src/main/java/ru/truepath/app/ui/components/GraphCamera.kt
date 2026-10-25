package ru.truepath.app.ui.components

/** Center an axis when it fits; otherwise keep both viewport edges within the world. */
internal fun boundedGraphPan(proposed: Float, world: Float, viewport: Float, zoom: Float): Float {
    return boundedGraphPanWithin(proposed, 0f, world, viewport, zoom)
}

internal fun boundedGraphPanWithin(proposed: Float, start: Float, end: Float, viewport: Float, zoom: Float): Float {
    val extent = (end - start) * zoom
    return if (extent <= viewport) { (viewport - extent) / 2f - start * zoom }
    else proposed.coerceIn(viewport - end * zoom, -start * zoom)
}

internal data class GraphCameraTarget(val zoom: Float, val x: Float, val y: Float)

/** Fit the revealed content, not the future empty world, without enlarging tiny graphs. */
internal fun fitGraphCamera(left: Float, top: Float, right: Float, bottom: Float,
    viewportWidth: Float, viewportHeight: Float, padding: Float): GraphCameraTarget {
    val zoom = minOf(viewportWidth / (right - left + padding * 2).coerceAtLeast(1f),
        viewportHeight / (bottom - top + padding * 2).coerceAtLeast(1f), 1f)
    return GraphCameraTarget(zoom, viewportWidth / 2 - (left + right) / 2 * zoom,
        viewportHeight / 2 - (top + bottom) / 2 * zoom)
}

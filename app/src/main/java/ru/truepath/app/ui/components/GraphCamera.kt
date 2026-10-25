package ru.truepath.app.ui.components

/** Center an axis when it fits; otherwise keep both viewport edges within the world. */
internal fun boundedGraphPan(proposed: Float, world: Float, viewport: Float, zoom: Float): Float {
    val extent = world * zoom
    return if (extent <= viewport) (viewport - extent) / 2f
    else proposed.coerceIn(viewport - extent, 0f)
}

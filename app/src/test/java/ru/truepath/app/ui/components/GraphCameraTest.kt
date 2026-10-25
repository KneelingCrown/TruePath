package ru.truepath.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphCameraTest {
    @Test fun `revealed content can be centered even far from the world origin`() {
        assertEquals(-350f, boundedGraphPanWithin(5000f, 400f, 700f, 400f, 1f), .001f)
        assertEquals(-800f, boundedGraphPanWithin(5000f, 400f, 700f, 400f, 2f), .001f)
        assertEquals(-1000f, boundedGraphPanWithin(-5000f, 400f, 700f, 400f, 2f), .001f)
    }
    @Test fun `growing graph gradually needs a smaller overview and keeps endpoints visible`() {
        val small = fitGraphCamera(380f, 20f, 700f, 130f, 400f, 700f, 24f)
        val wide = fitGraphCamera(20f, 20f, 1060f, 350f, 400f, 700f, 24f)
        val complete = fitGraphCamera(20f, 20f, 1060f, 1270f, 400f, 700f, 24f)
        assertTrue(small.zoom > wide.zoom)
        assertTrue(complete.zoom <= wide.zoom)
        assertTrue(20 * complete.zoom + complete.x >= 0)
        assertTrue(1060 * complete.zoom + complete.x <= 400)
        assertTrue(1270 * complete.zoom + complete.y <= 700)
    }

    @Test fun `small graphs are not enlarged beyond natural scale`() {
        assertEquals(1f, fitGraphCamera(0f, 0f, 100f, 100f, 400f, 700f, 24f).zoom, .001f)
    }
    @Test fun `overview stays centered despite a drag`() {
        assertEquals(100f, boundedGraphPan(-800f, 400f, 400f, .5f), .001f)
        assertEquals(100f, boundedGraphPan(800f, 400f, 400f, .5f), .001f)
        assertEquals(0f, boundedGraphPan(800f, 400f, 400f, 1f), .001f)
    }

    @Test fun `zoomed graph cannot be dragged outside either viewport edge`() {
        assertEquals(0f, boundedGraphPan(500f, 400f, 400f, 2f), .001f)
        assertEquals(-400f, boundedGraphPan(-900f, 400f, 400f, 2f), .001f)
        assertEquals(-150f, boundedGraphPan(-150f, 400f, 400f, 2f), .001f)
    }

    @Test fun `short viewport constrains vertical pan independently`() {
        assertEquals(-180f, boundedGraphPan(-900f, 680f, 500f, 1f), .001f)
        assertEquals(0f, boundedGraphPan(900f, 680f, 500f, 1f), .001f)
    }
}

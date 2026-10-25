package ru.truepath.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class GraphCameraTest {
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

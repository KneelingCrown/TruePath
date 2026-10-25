package ru.truepath.app.domain

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
import ru.truepath.app.data.LargeDemoScenario

class OrganicGraphLayoutTest {
    private fun graph(): PresentedGraph {
        val start = ResearchSession("s", "Сюжет", "Материал", 0, scenarioId = LargeDemoScenario.ID,
            nodes = listOf(GraphNode("material", "Материал", NodeKind.MATERIAL)))
        return LargeDemoScenario.events().fold(start, GraphReducer::apply).presentedGraph()
    }

    @Test fun `organic network is bounded and no two final label rectangles overlap`() {
        val graph = graph()
        assertTrue(graph.nodes.all { it.x in 0f..1f && it.y in 0f..1f })
        fun halfWidth(n: GraphNode) = if (n.kind in listOf(NodeKind.MATERIAL, NodeKind.CONCLUSION)) 159f else 56f
        fun halfHeight(n: GraphNode) = when (n.kind) { NodeKind.MATERIAL -> 53f; NodeKind.CONCLUSION -> 69f; else -> 45f }
        graph.nodes.forEachIndexed { i, a -> graph.nodes.drop(i + 1).forEach { b ->
            val overlaps = abs(a.x - b.x) * graph.minimumWidthDp < halfWidth(a) + halfWidth(b) &&
                abs(a.y - b.y) * graph.minimumHeightDp < halfHeight(a) + halfHeight(b)
            assertFalse("Overlapping labels: ${a.id}, ${b.id}", overlaps)
        } }
    }

    @Test fun `layout is independent of arrival order and does not retain strict rows`() {
        val graph = graph()
        val again = graph.copy(nodes = graph.nodes.reversed()).organicLayout()
        val positions = again.nodes.associateBy { it.id }
        graph.nodes.forEach { node ->
            assertEquals(node.x, positions.getValue(node.id).x, .0001f)
            assertEquals(node.y, positions.getValue(node.id).y, .0001f)
        }
        assertTrue(graph.nodes.map { (it.y * 1000).toInt() }.distinct().size > 25)
    }

    @Test fun `source and conclusion remain fixed endpoints`() {
        val graph = graph()
        val source = graph.nodes.single { it.kind == NodeKind.MATERIAL }
        assertEquals(.5f, source.x, .001f)
        assertEquals(.055f, source.y, .001f)
        assertEquals(.5f, graph.conclusion!!.x, .001f)
        assertEquals(.94f, graph.conclusion!!.y, .001f)
    }
}

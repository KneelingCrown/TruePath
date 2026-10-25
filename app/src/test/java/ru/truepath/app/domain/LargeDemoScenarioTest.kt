package ru.truepath.app.domain

import org.junit.Assert.*
import org.junit.Test
import ru.truepath.app.data.LargeDemoScenario

class LargeDemoScenarioTest {
    private fun initial() = ResearchSession("large", LargeDemoScenario.article.title, "Учебный материал", 0,
        scenarioId = LargeDemoScenario.ID, nodes = listOf(GraphNode("material", "Материал", NodeKind.MATERIAL)))

    @Test fun `large stream finishes with connected graph and mixed evidence`() {
        val result = LargeDemoScenario.events().fold(initial(), GraphReducer::apply)
        val graph = result.presentedGraph()
        assertEquals(RunStatus.COMPLETE, result.status)
        assertEquals(32, graph.nodes.size)
        assertTrue(graph.edges.size > 50)
        assertTrue(result.pendingEdges.isEmpty())
        val ids = graph.nodes.map { it.id }.toSet()
        assertTrue(graph.edges.all { it.from in ids && it.to in ids })
        assertTrue(graph.nodes.all { it.x in 0f..1f && it.y in 0f..1f })
        val claims = result.nodes.filter { it.kind == NodeKind.CLAIM }
        assertEquals(6, claims.size)
        assertEquals(setOf(TrustStatus.SUPPORTED, TrustStatus.CONTRADICTED, TrustStatus.UNCERTAIN), claims.map { it.status }.toSet())
        assertEquals(LargeDemoScenario.article.conclusion, graph.conclusion?.description)
    }

    @Test fun `resuming a partial stream does not duplicate branches or links`() {
        val events = LargeDemoScenario.events()
        val partial = events.take(30).fold(initial(), GraphReducer::apply).copy(status = RunStatus.INTERRUPTED)
        val resumed = events.fold(partial, GraphReducer::apply)
        val once = events.fold(initial(), GraphReducer::apply)
        assertEquals(once, resumed)
        assertEquals(once, events.fold(resumed, GraphReducer::apply))
    }

    @Test fun `large world size stays stable while events arrive`() {
        val start = initial().presentedGraph()
        val complete = LargeDemoScenario.events().fold(initial(), GraphReducer::apply).presentedGraph()
        assertEquals(1080f, start.minimumWidthDp)
        assertEquals(1280f, start.minimumHeightDp)
        assertEquals(start.minimumWidthDp, complete.minimumWidthDp)
        assertEquals(start.minimumHeightDp, complete.minimumHeightDp)
        assertNull(start.conclusion)
    }
}

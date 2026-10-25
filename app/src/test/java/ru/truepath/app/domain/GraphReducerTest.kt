package ru.truepath.app.domain

import org.junit.Assert.*
import org.junit.Test

class GraphReducerTest {
    private fun session() = ResearchSession("s", "Пример", "Текст", 1L)
    private fun node(id: String, status: TrustStatus = TrustStatus.UNCHECKED) = GraphNode(id, id, NodeKind.CLAIM, status)

    @Test fun `replayed events are idempotent and revisions retain node identity`() {
        val first = ResearchEvent.UpsertNode("event:1", node("claim"))
        val once = GraphReducer.apply(session(), first)
        assertEquals(once, GraphReducer.apply(once, first))
        val revised = GraphReducer.apply(once, ResearchEvent.UpsertNode("event:2", node("claim", TrustStatus.CONTRADICTED)))
        assertEquals(1, revised.nodes.size)
        assertEquals("claim", revised.nodes.single().id)
        assertEquals(TrustStatus.CONTRADICTED, revised.nodes.single().status)
    }

    @Test fun `out of order edges wait until both nodes arrive`() {
        val edge = GraphEdge("edge", "a", "b", "подтверждает")
        var s = GraphReducer.apply(session(), ResearchEvent.UpsertEdge("event:edge", edge))
        assertTrue(s.edges.isEmpty())
        assertEquals(listOf(edge), s.pendingEdges)
        s = GraphReducer.apply(s, ResearchEvent.UpsertNode("event:a", node("a")))
        assertTrue(s.edges.isEmpty())
        s = GraphReducer.apply(s, ResearchEvent.UpsertNode("event:b", node("b")))
        assertEquals(listOf(edge), s.edges)
        assertTrue(s.pendingEdges.isEmpty())
    }

    @Test fun `failed stream preserves partial graph and rejects a late completion`() {
        var s = GraphReducer.apply(session(), ResearchEvent.UpsertNode("event:a", node("a")))
        s = GraphReducer.apply(s, ResearchEvent.Failed("event:error", "Соединение прервано"))
        val late = GraphReducer.apply(s, ResearchEvent.Finished("event:late", "Успех", ""))
        assertEquals(RunStatus.FAILED, late.status)
        assertEquals(1, late.nodes.size)
        assertTrue(late.summary.isEmpty())
    }

    @Test fun `interrupted sessions can continue with saved event ids`() {
        var s = GraphReducer.apply(session(), ResearchEvent.UpsertNode("event:a", node("a")))
        s = s.copy(status = RunStatus.INTERRUPTED)
        s = GraphReducer.apply(s, ResearchEvent.UpsertNode("event:a", node("a")))
        assertEquals(1, s.nodes.size)
        s = GraphReducer.apply(s, ResearchEvent.Finished("event:done", "Учебный итог", ""))
        assertEquals(RunStatus.COMPLETE, s.status)
    }

    @Test fun `custom material stays unverified when no API is configured`() {
        val s = GraphReducer.apply(session().copy(nodes = listOf(node("a"))), ResearchEvent.NeedsApi("event:api"))
        assertEquals(RunStatus.AWAITING_API, s.status)
        assertTrue(s.nodes.all { it.status == TrustStatus.UNCHECKED })
    }
}

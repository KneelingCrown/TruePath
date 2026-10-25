package ru.truepath.app.domain

import org.junit.Assert.*
import org.junit.Test

class GraphPresentationTest {
    @Test fun `colored materials precede unchecked items without reordering within groups`() {
        val nodes = listOf(
            GraphNode("grey-1", "", NodeKind.SOURCE),
            GraphNode("green", "", NodeKind.EVIDENCE, TrustStatus.SUPPORTED),
            GraphNode("grey-2", "", NodeKind.MATERIAL),
            GraphNode("red", "", NodeKind.CLAIM, TrustStatus.CONTRADICTED),
            GraphNode("yellow", "", NodeKind.CLAIM, TrustStatus.UNCERTAIN),
        )
        assertEquals(listOf("green", "red", "yellow", "grey-1", "grey-2"), PresentedGraph(nodes, emptyList()).orderedMaterials.map { it.id })
    }
    private val material = GraphNode("material", "Материал", NodeKind.MATERIAL)
    private val claim = GraphNode("claim", "Тезис", NodeKind.CLAIM, TrustStatus.CONTRADICTED)
    private val evidence = GraphNode("evidence", "Цитата", NodeKind.EVIDENCE, TrustStatus.SUPPORTED)
    private fun session() = ResearchSession("s", "Исходная новость", "Текст", 0,
        nodes = listOf(material, claim, evidence), status = RunStatus.COMPLETE, summary = "Оговорка потеряна")

    @Test fun `existing saved result receives visible endpoints without changing stored data`() {
        val saved = session()
        val graph = saved.presentedGraph()
        assertEquals(saved.title, graph.nodes.single { it.kind == NodeKind.MATERIAL }.label)
        assertEquals(saved.summary, graph.conclusion?.description)
        assertEquals(TrustStatus.CONTRADICTED, graph.conclusion?.status)
        assertTrue(graph.edges.any { it.from == evidence.id && it.to == graph.conclusion?.id })
        assertEquals(3, saved.nodes.size)
        assertEquals(graph, saved.presentedGraph())
    }

    @Test fun `unfinished or unavailable research never receives a verdict node`() {
        RunStatus.entries.filter { it != RunStatus.COMPLETE }.forEach { status ->
            assertNull(session().copy(status = status).presentedGraph().conclusion)
        }
        assertNull(session().copy(summary = "").presentedGraph().conclusion)
    }

    @Test fun `support requires at least one claim and no unknown claims`() {
        val supported = claim.copy(status = TrustStatus.SUPPORTED)
        assertEquals(TrustStatus.SUPPORTED, session().copy(nodes = listOf(material, supported)).presentedGraph().conclusion?.status)
        assertEquals(TrustStatus.UNCERTAIN, session().copy(nodes = listOf(material, evidence)).presentedGraph().conclusion?.status)
        assertEquals(TrustStatus.UNCERTAIN, session().copy(nodes = listOf(supported, claim.copy(id = "other", status = TrustStatus.UNCHECKED))).presentedGraph().conclusion?.status)
    }

    @Test fun `claim connects to conclusion when no separate evidence exists`() {
        val graph = session().copy(nodes = listOf(material, claim)).presentedGraph()
        assertEquals(claim.id, graph.edges.single().from)
        assertEquals(graph.conclusion?.id, graph.edges.single().to)
        assertTrue(graph.nodes.single { it.kind == NodeKind.MATERIAL }.y < graph.conclusion!!.y)
    }
}

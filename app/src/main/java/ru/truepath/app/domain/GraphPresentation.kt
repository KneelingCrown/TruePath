package ru.truepath.app.domain

data class PresentedGraph(val nodes: List<GraphNode>, val edges: List<GraphEdge>) {
    val conclusion get() = nodes.find { it.id == CONCLUSION_ID }
    companion object { const val CONCLUSION_ID = "presentation:conclusion" }
}

/** Derive endpoints from saved results, including previously saved history. */
fun ResearchSession.presentedGraph(): PresentedGraph {
    val resultStatus = when {
        nodes.any { it.kind == NodeKind.CLAIM && it.status == TrustStatus.CONTRADICTED } -> TrustStatus.CONTRADICTED
        nodes.filter { it.kind == NodeKind.CLAIM }.let { it.isNotEmpty() && it.all { n -> n.status == TrustStatus.SUPPORTED } } -> TrustStatus.SUPPORTED
        else -> TrustStatus.UNCERTAIN
    }
    val mapped = nodes.filterNot { it.id == PresentedGraph.CONCLUSION_ID }.map { node ->
        when (node.id) {
            "material" -> node.copy(label = title, x = .5f, y = .10f)
            "claim" -> node.copy(x = .28f, y = .32f)
            "question" -> node.copy(x = .74f, y = .33f)
            "source" -> node.copy(x = .24f, y = .55f)
            "retelling" -> node.copy(x = .78f, y = .55f)
            "evidence" -> node.copy(x = .50f, y = .71f)
            else -> node
        }
    }
    if (status != RunStatus.COMPLETE || summary.isBlank()) return PresentedGraph(mapped, edges)
    val conclusion = GraphNode(
        PresentedGraph.CONCLUSION_ID,
        when (resultStatus) {
            TrustStatus.CONTRADICTED -> "В новости есть искажение"
            TrustStatus.SUPPORTED -> "Утверждение подтверждается"
            else -> "Данных пока недостаточно"
        },
        NodeKind.CONCLUSION, resultStatus, description = summary, x = .5f, y = .87f,
    )
    val evidenceIds = mapped.filter { it.kind == NodeKind.EVIDENCE }.map { it.id }
        .ifEmpty { mapped.filter { it.kind == NodeKind.CLAIM }.map { it.id } }
    return PresentedGraph(mapped + conclusion, edges + evidenceIds.map {
        GraphEdge("presentation:$it:conclusion", it, conclusion.id, "основание вывода")
    })
}

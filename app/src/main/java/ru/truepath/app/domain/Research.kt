package ru.truepath.app.domain

enum class TrustStatus(val label: String) {
    UNCHECKED("Не проверено"), SUPPORTED("Подтверждается"),
    UNCERTAIN("Недостаточно данных"), CONTRADICTED("Есть противоречие")
}

enum class NodeKind(val label: String) {
    MATERIAL("Материал"), CLAIM("Утверждение"), SOURCE("Источник"), EVIDENCE("Доказательство")
}

enum class RunStatus(val label: String) {
    RUNNING("Проверяем"), COMPLETE("Завершено"), INTERRUPTED("Прервано"),
    AWAITING_API("Ожидает подключения"), FAILED("Ошибка")
}

data class Attachment(val uri: String, val name: String, val mime: String)

data class GraphNode(
    val id: String,
    val label: String,
    val kind: NodeKind,
    val status: TrustStatus = TrustStatus.UNCHECKED,
    val description: String = "",
    val quote: String = "",
    val sourceLabel: String = "",
    val sourceUrl: String = "",
    val x: Float = .5f,
    val y: Float = .2f,
)

data class GraphEdge(val id: String, val from: String, val to: String, val label: String)

data class ResearchSession(
    val id: String,
    val title: String,
    val input: String,
    val createdAt: Long,
    val scenarioId: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
    val pendingEdges: List<GraphEdge> = emptyList(),
    val status: RunStatus = RunStatus.RUNNING,
    val stage: String = "Готовим исследование",
    val summary: String = "",
    val planB: String = "",
    val appliedEventIds: Set<String> = emptySet(),
)

sealed interface ResearchEvent {
    val id: String
    data class Stage(override val id: String, val text: String) : ResearchEvent
    data class UpsertNode(override val id: String, val node: GraphNode) : ResearchEvent
    data class UpsertEdge(override val id: String, val edge: GraphEdge) : ResearchEvent
    data class Finished(override val id: String, val summary: String, val planB: String) : ResearchEvent
    data class NeedsApi(override val id: String) : ResearchEvent
    data class Failed(override val id: String, val message: String) : ResearchEvent
}

/** Pure reducer shared by the demo stream and a future remote API adapter. */
object GraphReducer {
    fun apply(session: ResearchSession, event: ResearchEvent): ResearchSession {
        if (event.id in session.appliedEventIds) return session
        // Late events never turn a finalized or failed run into a successful one.
        if (session.status in setOf(RunStatus.COMPLETE, RunStatus.FAILED, RunStatus.AWAITING_API)) return session
        val changed = when (event) {
            is ResearchEvent.Stage -> session.copy(stage = event.text)
            is ResearchEvent.UpsertNode -> session.copy(
                nodes = session.nodes.filterNot { it.id == event.node.id } + event.node,
            )
            is ResearchEvent.UpsertEdge -> session.copy(
                edges = session.edges.filterNot { it.id == event.edge.id },
                pendingEdges = session.pendingEdges.filterNot { it.id == event.edge.id } + event.edge,
            )
            is ResearchEvent.Finished -> session.copy(
                status = RunStatus.COMPLETE, stage = "Учебное исследование завершено",
                summary = event.summary, planB = event.planB,
            )
            is ResearchEvent.NeedsApi -> session.copy(
                status = RunStatus.AWAITING_API, stage = "Материал сохранён",
                summary = "Для проверки этого материала нужно подключить исследовательский сервис. Вывод о достоверности пока не сделан.",
            )
            is ResearchEvent.Failed -> session.copy(status = RunStatus.FAILED, stage = event.message)
        }
        val nodeIds = changed.nodes.map { it.id }.toSet()
        val (ready, waiting) = changed.pendingEdges.partition { it.from in nodeIds && it.to in nodeIds }
        return changed.copy(
            edges = (changed.edges + ready).distinctBy { it.id },
            pendingEdges = waiting,
            appliedEventIds = changed.appliedEventIds + event.id,
        )
    }
}

package ru.truepath.app.data

import org.json.JSONArray
import org.json.JSONObject
import ru.truepath.app.domain.*
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object SessionCodec {
    fun encode(sessions: List<ResearchSession>): String = JSONArray().apply {
        sessions.forEach { s -> put(JSONObject().apply {
            put("id", s.id); put("title", s.title); put("input", s.input)
            put("createdAt", s.createdAt); put("scenarioId", s.scenarioId ?: JSONObject.NULL)
            put("status", s.status.name); put("stage", s.stage); put("summary", s.summary); put("planB", s.planB)
            put("appliedEventIds", JSONArray(s.appliedEventIds.toList()))
            put("attachments", JSONArray().apply { s.attachments.forEach { a -> put(JSONObject().apply {
                put("uri", a.uri); put("name", a.name); put("mime", a.mime)
            }) } })
            put("nodes", JSONArray().apply { s.nodes.forEach { n -> put(JSONObject().apply {
                put("id", n.id); put("label", n.label); put("kind", n.kind.name); put("status", n.status.name)
                put("description", n.description); put("quote", n.quote); put("sourceLabel", n.sourceLabel)
                put("sourceUrl", n.sourceUrl); put("x", n.x.toDouble()); put("y", n.y.toDouble())
            }) } })
            put("edges", edges(s.edges)); put("pendingEdges", edges(s.pendingEdges))
        }) }
    }.toString()

    fun decode(text: String): List<ResearchSession> {
        val all = JSONArray(text)
        return (0 until all.length()).map { i ->
            val s = all.getJSONObject(i)
            ResearchSession(
                id = s.getString("id"), title = s.getString("title"), input = s.getString("input"),
                createdAt = s.getLong("createdAt"), scenarioId = if (s.isNull("scenarioId")) null else s.getString("scenarioId"),
                status = RunStatus.valueOf(s.getString("status")), stage = s.getString("stage"),
                summary = s.optString("summary"), planB = s.optString("planB"),
                attachments = s.getJSONArray("attachments").let { a -> (0 until a.length()).map { j ->
                    a.getJSONObject(j).let { Attachment(it.getString("uri"), it.getString("name"), it.getString("mime")) }
                } },
                nodes = s.getJSONArray("nodes").let { a -> (0 until a.length()).map { j ->
                    a.getJSONObject(j).let { n -> GraphNode(
                        n.getString("id"), n.getString("label"), NodeKind.valueOf(n.getString("kind")),
                        TrustStatus.valueOf(n.getString("status")), n.optString("description"), n.optString("quote"),
                        n.optString("sourceLabel"), n.optString("sourceUrl"), n.getDouble("x").toFloat(), n.getDouble("y").toFloat(),
                    ) }
                } },
                edges = readEdges(s.getJSONArray("edges")), pendingEdges = readEdges(s.getJSONArray("pendingEdges")),
                appliedEventIds = s.getJSONArray("appliedEventIds").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() },
            )
        }
    }

    private fun edges(values: List<GraphEdge>) = JSONArray().apply { values.forEach { e -> put(JSONObject().apply {
        put("id", e.id); put("from", e.from); put("to", e.to); put("label", e.label)
    }) } }
    private fun readEdges(a: JSONArray) = (0 until a.length()).map { i -> a.getJSONObject(i).let {
        GraphEdge(it.getString("id"), it.getString("from"), it.getString("to"), it.getString("label"))
    } }
}

class SessionStore(private val file: File) {
    fun read(): List<ResearchSession> = if (file.exists()) SessionCodec.decode(file.readText()) else emptyList()

    fun write(sessions: List<ResearchSession>) {
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(SessionCodec.encode(sessions))
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

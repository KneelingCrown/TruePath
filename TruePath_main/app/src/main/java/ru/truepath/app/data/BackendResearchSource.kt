package ru.truepath.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import ru.truepath.app.domain.GraphEdge
import ru.truepath.app.domain.GraphNode
import ru.truepath.app.domain.NodeKind
import ru.truepath.app.domain.ResearchEvent
import ru.truepath.app.domain.ResearchSession
import ru.truepath.app.domain.TrustStatus
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Адаптер сервера True Path (FastAPI, см. backend/ и docs/API_CONTRACT.md).
 * Реализует тот же контракт [ResearchSource], что и демо-источник: опрашивает
 * /research/{id}/events и переводит события сервера в доменные события графа.
 *
 * Восстановление после перезапуска: локальный id сессии передаётся как
 * client_task_id, поэтому повторный запуск возвращает существующую задачу,
 * а повторная доставка событий отсекается редуктором по id события.
 *
 * Координаты узлов назначает клиент — контракт прямо не требует их от поставщика.
 */
class BackendResearchSource(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val pollIntervalMs: Long = 500,
) : ResearchSource {

    override fun events(session: ResearchSession): Flow<ResearchEvent> = flow {
        val translator = BackendEventTranslator()
        val http = BackendHttp(baseUrl)
        val cursor = Cursor()
        var emittedAny = false
        while (true) {
            val batch = try {
                if (cursor.researchId == null) {
                    val materialId = http.createMaterial(session.input)
                    cursor.researchId = http.startResearch(materialId, session.id)
                }
                http.fetchEvents(cursor.researchId!!, cursor.lastSeq)
            } catch (io: IOException) {
                null
            }
            if (batch == null) {
                // Материал сохранён локально, проверка не проводилась — не выдумываем результат.
                if (!emittedAny) {
                    emit(ResearchEvent.NeedsApi("needs-api"))
                    return@flow
                }
                throw IOException("Соединение с сервером прервано во время исследования")
            }
            cursor.lastSeq = batch.cursor
            for (event in batch.events) {
                val mapped = translator.translate(event) ?: continue
                emittedAny = true
                emit(mapped)
                if (mapped is ResearchEvent.Finished || mapped is ResearchEvent.Failed) return@flow
            }
            delay(pollIntervalMs)
        }
    }.flowOn(Dispatchers.IO)

    private class Cursor {
        var researchId: Long? = null
        var lastSeq: Int = 0
    }

    companion object {
        // 10.0.2.2 — loopback машины-хоста из эмулятора Android.
        // Для реального устройства укажите адрес компьютера в локальной сети.
        const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"
    }
}

/** HTTP-доступ к серверу без внешних зависимостей. */
internal class BackendHttp(private val baseUrl: String) {

    data class Batch(val cursor: Int, val events: List<JSONObject>)

    fun createMaterial(text: String): Long {
        val body = JSONObject().put("kind", "text").put("content", text)
        return JSONObject(send("POST", "/api/v1/materials", body)).getLong("id")
    }

    fun startResearch(materialId: Long, clientTaskId: String): Long {
        val body = JSONObject()
            .put("material_id", materialId)
            .put("client_task_id", clientTaskId)
        return JSONObject(send("POST", "/api/v1/research", body)).getLong("id")
    }

    fun fetchEvents(researchId: Long, after: Int): Batch {
        val json = JSONObject(send("GET", "/api/v1/research/$researchId/events?after=$after", null))
        val array = json.getJSONArray("events")
        val events = buildList {
            for (i in 0 until array.length()) add(array.getJSONObject(i))
        }
        return Batch(cursor = json.optInt("cursor", after), events = events)
    }

    private fun send(method: String, path: String, body: JSONObject?): String {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        connection.disconnect()
        if (code !in 200..299) {
            throw IOException("Сервер вернул $code: ${text.take(200)}")
        }
        return text
    }
}

/**
 * Чистый переводчик событий сервера в доменные события; не делает запросов,
 * поэтому покрывается unit-тестами. Узел материала сервера отображается на
 * локальный узел "material", который ViewModel создаёт при запуске, — иначе
 * в графе оказалось бы два узла материала.
 */
class BackendEventTranslator {
    private val nodeIds = mutableMapOf<String, String>()
    private val nodeCoordinates = mutableMapOf<String, Pair<Float, Float>>()
    private val kindCounters = mutableMapOf<NodeKind, Int>()

    fun translate(event: JSONObject): ResearchEvent? = when (event.optString("type")) {
        "stage" -> ResearchEvent.Stage(event.getString("id"), event.getString("text"))
        "upsert_node" -> event.optJSONObject("node")?.let { nodeEvent(event.getString("id"), it) }
        "upsert_edge" -> event.optJSONObject("edge")?.let { edgeEvent(event.getString("id"), it) }
        "finished" -> ResearchEvent.Finished(
            event.getString("id"), event.optString("summary"), event.optString("plan_b"),
        )
        "failed" -> ResearchEvent.Failed(event.getString("id"), event.optString("message"))
        else -> null
    }

    private fun nodeEvent(eventId: String, node: JSONObject): ResearchEvent.UpsertNode? {
        val kind = nodeKind(node.optString("kind")) ?: return null
        val ref = node.getString("ref")
        val frontendId = if (kind == NodeKind.MATERIAL) LOCAL_MATERIAL_ID else ref
        nodeIds[ref] = frontendId
        val index = kindCounters.getOrDefault(kind, 0)
        kindCounters[kind] = index + 1
        // Координаты назначаются один раз на узел: повторная доставка события
        // и обновления статуса не должны двигать узел на графе.
        val (x, y) = nodeCoordinates.getOrPut(ref) { coordinates(kind, index) }
        return ResearchEvent.UpsertNode(
            eventId,
            GraphNode(
                id = frontendId,
                label = node.optString("label"),
                kind = kind,
                status = trustStatus(node.optString("status")),
                description = node.optString("description"),
                quote = node.optString("quote"),
                sourceLabel = node.optString("source_label"),
                sourceUrl = node.optString("source_url"),
                x = x,
                y = y,
            ),
        )
    }

    private fun edgeEvent(eventId: String, edge: JSONObject): ResearchEvent.UpsertEdge =
        ResearchEvent.UpsertEdge(
            eventId,
            GraphEdge(
                id = edge.getString("ref"),
                from = nodeIds[edge.getString("from_ref")] ?: edge.getString("from_ref"),
                to = nodeIds[edge.getString("to_ref")] ?: edge.getString("to_ref"),
                label = edge.getString("label"),
                explanation = edge.optString("explanation"),
                isEstablished = edge.optBoolean("is_established", true),
            ),
        )

    private fun nodeKind(raw: String): NodeKind? = when (raw) {
        "material" -> NodeKind.MATERIAL
        "claim" -> NodeKind.CLAIM
        "source" -> NodeKind.SOURCE
        "evidence" -> NodeKind.EVIDENCE
        else -> null
    }

    private fun trustStatus(raw: String): TrustStatus = when (raw) {
        "supported" -> TrustStatus.SUPPORTED
        "insufficient" -> TrustStatus.UNCERTAIN
        "refuted" -> TrustStatus.CONTRADICTED
        else -> TrustStatus.UNCHECKED
    }

    // Детерминированная раскладка по типу узла; камера и масштаб остаются за клиентом.
    private fun coordinates(kind: NodeKind, index: Int): Pair<Float, Float> = when (kind) {
        NodeKind.MATERIAL -> .5f to .12f
        NodeKind.CLAIM -> .3f to (.38f + index * .07f)
        NodeKind.SOURCE -> (.2f + (index % 3) * .25f) to .67f
        NodeKind.EVIDENCE -> (.35f + (index % 2) * .3f) to .84f
    }

    companion object {
        const val LOCAL_MATERIAL_ID = "material"
    }
}

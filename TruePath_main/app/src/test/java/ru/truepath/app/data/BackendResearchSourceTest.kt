package ru.truepath.app.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import ru.truepath.app.domain.NodeKind
import ru.truepath.app.domain.ResearchEvent
import ru.truepath.app.domain.TrustStatus


class BackendResearchSourceTest {
    private val translator = BackendEventTranslator()

    @Test fun `stage event maps to domain stage`() {
        val event = translator.translate(JSONObject("""{"seq": 1, "id": "stage-1", "type": "stage", "text": "Разбор материала"}"""))
        assertEquals(ResearchEvent.Stage("stage-1", "Разбор материала"), event)
    }

    @Test fun `material node maps to local material node id`() {
        val event = translator.translate(
            JSONObject(
                """{"seq": 4, "id": "node-1", "type": "upsert_node",
                   "node": {"ref": "node-1", "kind": "material", "label": "Материал: текст"}}""",
            ),
        ) as ResearchEvent.UpsertNode
        assertEquals(BackendEventTranslator.LOCAL_MATERIAL_ID, event.node.id)
        assertEquals(NodeKind.MATERIAL, event.node.kind)
        assertEquals(TrustStatus.UNCHECKED, event.node.status)
    }

    @Test fun `claim node carries status quote and source fields`() {
        val event = translator.translate(
            JSONObject(
                """{"seq": 9, "id": "claim-status-3", "type": "upsert_node",
                   "node": {"ref": "node-2", "kind": "claim", "entity_id": 3,
                            "label": "Утверждение", "status": "refuted",
                            "description": "Источник противоречит",
                            "source_label": "Распоряжение", "source_url": "https://demo.gov/1"}}""",
            ),
        ) as ResearchEvent.UpsertNode
        assertEquals("node-2", event.node.id)
        assertEquals(TrustStatus.CONTRADICTED, event.node.status)
        assertEquals("Источник противоречит", event.node.description)
        assertEquals("Распоряжение", event.node.sourceLabel)
        assertEquals("https://demo.gov/1", event.node.sourceUrl)
    }

    @Test fun `insufficient maps to uncertain and out_of_scope to unchecked`() {
        fun statusOf(raw: String): TrustStatus {
            val event = translator.translate(
                JSONObject(
                    """{"id": "e", "type": "upsert_node",
                       "node": {"ref": "node-9", "kind": "claim", "status": "$raw"}}""",
                ),
            ) as ResearchEvent.UpsertNode
            return event.node.status
        }
        assertEquals(TrustStatus.UNCERTAIN, statusOf("insufficient"))
        assertEquals(TrustStatus.UNCHECKED, statusOf("out_of_scope"))
        assertEquals(TrustStatus.SUPPORTED, statusOf("supported"))
    }

    @Test fun `edge endpoints follow renamed material node`() {
        // Узел материала должен быть переведён до связи, как в реальном потоке событий.
        translator.translate(
            JSONObject(
                """{"id": "node-1", "type": "upsert_node",
                   "node": {"ref": "node-1", "kind": "material", "label": "Материал"}}""",
            ),
        )
        val edge = translator.translate(
            JSONObject(
                """{"id": "edge-1", "type": "upsert_edge",
                   "edge": {"ref": "edge-1", "from_ref": "node-1", "to_ref": "node-2", "label": "содержит",
                            "explanation": "Утверждение содержится в материале", "is_established": true}}""",
            ),
        ) as ResearchEvent.UpsertEdge
        assertEquals(BackendEventTranslator.LOCAL_MATERIAL_ID, edge.edge.from)
        assertEquals("node-2", edge.edge.to)
        assertEquals("содержит", edge.edge.label)
        assertEquals("Утверждение содержится в материале", edge.edge.explanation)
        assertTrue(edge.edge.isEstablished)
    }

    @Test fun `unestablished edge keeps flag and explanation`() {
        val edge = translator.translate(
            JSONObject(
                """{"id": "edge-9", "type": "upsert_edge",
                   "edge": {"ref": "edge-9", "from_ref": "node-1", "to_ref": "node-2", "label": "то же событие",
                            "explanation": "Заголовки совпадают", "is_established": false}}""",
            ),
        ) as ResearchEvent.UpsertEdge
        assertFalse(edge.edge.isEstablished)
        assertEquals("Заголовки совпадают", edge.edge.explanation)
    }

    @Test fun `finished and failed map with summary and message`() {
        val finished = translator.translate(
            JSONObject("""{"id": "finished-1", "type": "finished", "summary": "Итог", "plan_b": ""}"""),
        )
        assertEquals(ResearchEvent.Finished("finished-1", "Итог", ""), finished)

        val failed = translator.translate(
            JSONObject("""{"id": "failed-1", "type": "failed", "message": "Ошибка этапа"}"""),
        )
        assertEquals(ResearchEvent.Failed("failed-1", "Ошибка этапа"), failed)
    }

    @Test fun `unknown type and unknown node kind are skipped`() {
        assertNull(translator.translate(JSONObject("""{"id": "x", "type": "token", "text": "..." }""")))
        assertNull(
            translator.translate(
                JSONObject(
                    """{"id": "node-x", "type": "upsert_node",
                       "node": {"ref": "node-x", "kind": "plan", "label": "План"}}""",
                ),
            ),
        )
    }

    @Test fun `translation is deterministic for repeated delivery`() {
        val json = JSONObject(
            """{"id": "node-2", "type": "upsert_node",
               "node": {"ref": "node-2", "kind": "claim", "label": "Утверждение", "status": "supported"}}""",
        )
        assertEquals(translator.translate(json), translator.translate(json))
    }
}

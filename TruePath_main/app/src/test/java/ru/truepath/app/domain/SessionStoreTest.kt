package ru.truepath.app.domain

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.truepath.app.data.SessionCodec
import ru.truepath.app.data.SessionStore
import java.io.File

class SessionStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `partial graph round trip retains pending edges attachments and event cursor`() {
        val session = ResearchSession(
            "s", "Пример", "Скриншот и ссылка", 42L,
            attachments = listOf(Attachment("content://test/one", "скрин.png", "image/png")),
            nodes = listOf(GraphNode("a", "Утверждение", NodeKind.CLAIM, quote = "Точная цитата")),
            pendingEdges = listOf(GraphEdge("ab", "a", "b", "подтверждает")),
            appliedEventIds = setOf("e1", "e2"),
        )
        assertEquals(listOf(session), SessionCodec.decode(SessionCodec.encode(listOf(session))))
    }

    @Test fun `history replaces existing snapshot without losing the latest state`() {
        val file = File(temporary.root, "history.json")
        val store = SessionStore(file)
        val first = ResearchSession("s", "Проверка", "Текст", 5L)
        store.write(listOf(first))
        val last = first.copy(status = RunStatus.COMPLETE, summary = "Завершено")
        store.write(listOf(last))
        assertEquals(listOf(last), store.read())
        assertFalse(File(temporary.root, "history.json.tmp").exists())
    }
}

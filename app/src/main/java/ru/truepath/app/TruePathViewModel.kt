package ru.truepath.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.core.content.edit
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.truepath.app.data.DemoResearchSource
import ru.truepath.app.data.NewsArticle
import ru.truepath.app.data.SessionStore
import ru.truepath.app.domain.*
import java.io.File
import java.util.UUID

enum class AppTab(val title: String) {
    FEED("Лента"), CHECK("Проверить"), GRAPH("Граф"), HISTORY("История")
}

data class AppState(
    val tab: AppTab = AppTab.FEED,
    val draft: String = "",
    val attachments: List<Attachment> = emptyList(),
    val topic: String = "Все",
    val sessions: List<ResearchSession> = emptyList(),
    val selectedSessionId: String? = null,
    val notice: String? = null,
    val historyQuery: String = "",
    val loading: Boolean = true,
    val graphReturnTab: AppTab = AppTab.FEED,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    val selectedSession get() = sessions.find { it.id == selectedSessionId }
    val runningSession get() = sessions.find { it.status == RunStatus.RUNNING }
}

class TruePathViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableState = MutableStateFlow(AppState())
    val state = mutableState.asStateFlow()
    private val source = DemoResearchSource()
    private val storeFile = File(application.filesDir, "research-history.json")
    private val store = SessionStore(storeFile)
    private val preferences = application.getSharedPreferences("truepath", 0)
    private val saves = Channel<List<ResearchSession>>(Channel.CONFLATED)
    private var researchJob: Job? = null

    init {
        mutableState.value = mutableState.value.copy(themeMode = ThemeMode.restore(preferences.getString("theme", null)))
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { store.read() } }
            val restored = result.getOrDefault(emptyList()).map {
                if (it.status == RunStatus.RUNNING) it.copy(status = RunStatus.INTERRUPTED, stage = "Проверка прервана. Граф сохранён") else it
            }
            val topic = preferences.getString("topic", "Все") ?: "Все"
            val draft = preferences.getString("draft", "") ?: ""
            mutableState.value = mutableState.value.copy(
                sessions = restored, selectedSessionId = restored.firstOrNull()?.id,
                topic = topic, draft = draft, loading = false,
                notice = if (result.isFailure) "Не удалось открыть историю. Исходный файл сохранён для восстановления." else null,
            )
            if (result.isFailure && storeFile.exists()) {
                withContext(Dispatchers.IO) { runCatching {
                    storeFile.copyTo(File(storeFile.parentFile, "research-history-${System.currentTimeMillis()}.recovery"))
                } }
            }
            launch(Dispatchers.IO) {
                for (sessions in saves) {
                    try { store.write(sessions) }
                    catch (_: Exception) { withContext(Dispatchers.Main) { notify("Историю не удалось сохранить. Проверьте свободное место.") } }
                }
            }
            if (restored.isNotEmpty()) saves.trySend(restored)
        }
    }

    fun selectTab(tab: AppTab) {
        mutableState.value = mutableState.value.copy(tab = tab,
            graphReturnTab = if (tab == AppTab.GRAPH && mutableState.value.tab != AppTab.GRAPH) mutableState.value.tab else mutableState.value.graphReturnTab)
    }
    fun leaveGraph() { mutableState.value = mutableState.value.copy(tab = mutableState.value.graphReturnTab) }
    fun setTheme(mode: ThemeMode) {
        mutableState.value = mutableState.value.copy(themeMode = mode)
        preferences.edit { putString("theme", mode.name) }
    }
    fun setDraft(text: String) {
        mutableState.value = mutableState.value.copy(draft = text.take(12000))
        preferences.edit { putString("draft", mutableState.value.draft) }
    }
    fun setTopic(topic: String) {
        mutableState.value = mutableState.value.copy(topic = topic)
        preferences.edit { putString("topic", topic) }
    }
    fun setHistoryQuery(value: String) { mutableState.value = mutableState.value.copy(historyQuery = value) }
    fun notify(text: String) { mutableState.value = mutableState.value.copy(notice = text) }
    fun dismissNotice() { mutableState.value = mutableState.value.copy(notice = null) }
    fun attach(attachment: Attachment) {
        val old = mutableState.value.attachments
        if (old.any { it.uri == attachment.uri }) return
        if (old.size >= 5) { notify("Можно прикрепить до пяти файлов"); return }
        mutableState.value = mutableState.value.copy(attachments = old + attachment)
    }
    fun removeAttachment(uri: String) {
        mutableState.value = mutableState.value.copy(attachments = mutableState.value.attachments.filterNot { it.uri == uri })
    }
    fun startArticle(article: NewsArticle) = start(article.title, article.title, article.id, emptyList())
    fun startDraft() {
        val s = mutableState.value
        if (s.draft.isBlank() && s.attachments.isEmpty()) return
        start(s.draft.ifBlank { s.attachments.first().name }.take(100), s.draft, null, s.attachments)
    }
    private fun start(title: String, input: String, scenarioId: String?, attachments: List<Attachment>) {
        if (mutableState.value.loading) return
        mutableState.value.runningSession?.let {
            openSession(it.id)
            notify("Сначала завершите или остановите текущий пример")
            return
        }
        val session = ResearchSession(
            UUID.randomUUID().toString(), title, input, System.currentTimeMillis(), scenarioId, attachments,
            nodes = listOf(GraphNode("material", "Исходный материал", NodeKind.MATERIAL,
                description = input.ifBlank { attachments.joinToString { it.name } }, x = .5f, y = .12f)),
        )
        mutableState.value = mutableState.value.copy(
            sessions = listOf(session) + mutableState.value.sessions,
            selectedSessionId = session.id, tab = AppTab.GRAPH,
            graphReturnTab = mutableState.value.tab.takeUnless { it == AppTab.GRAPH } ?: mutableState.value.graphReturnTab,
        )
        saves.trySend(mutableState.value.sessions)
        collect(session)
    }
    private fun collect(session: ResearchSession) {
        researchJob = viewModelScope.launch {
            try {
                source.events(session).collect { event ->
                    val current = mutableState.value.sessions.find { it.id == session.id } ?: return@collect
                    update(GraphReducer.apply(current, event))
                }
                val latest = mutableState.value.sessions.find { it.id == session.id }
                if (latest?.status == RunStatus.RUNNING) {
                    update(latest.copy(status = RunStatus.INTERRUPTED, stage = "Поток завершился без итогового результата"))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutableState.value.sessions.find { it.id == session.id }?.let {
                    update(it.copy(status = RunStatus.FAILED, stage = "Не удалось продолжить проверку. Полученные данные сохранены"))
                }
            }
        }
    }
    private fun update(session: ResearchSession) {
        mutableState.value = mutableState.value.copy(sessions = mutableState.value.sessions.map { if (it.id == session.id) session else it })
        saves.trySend(mutableState.value.sessions)
    }
    fun openSession(id: String) {
        if (mutableState.value.sessions.any { it.id == id }) mutableState.value = mutableState.value.copy(
            selectedSessionId = id, tab = AppTab.GRAPH,
            graphReturnTab = mutableState.value.tab.takeUnless { it == AppTab.GRAPH } ?: mutableState.value.graphReturnTab)
    }
    fun stop() {
        val current = mutableState.value.selectedSession ?: return
        if (current.status != RunStatus.RUNNING) return
        researchJob?.cancel()
        update(current.copy(status = RunStatus.INTERRUPTED, stage = "Вы остановили пример. Полученные данные сохранены"))
    }
    fun resume() {
        val current = mutableState.value.selectedSession ?: return
        if (current.status != RunStatus.INTERRUPTED || mutableState.value.runningSession != null) return
        val continued = current.copy(status = RunStatus.RUNNING, stage = "Продолжаем учебный пример")
        update(continued)
        collect(continued)
    }
    fun deleteSession(id: String) {
        if (mutableState.value.sessions.find { it.id == id }?.status == RunStatus.RUNNING) {
            notify("Перед удалением остановите текущую проверку"); return
        }
        val remaining = mutableState.value.sessions.filterNot { it.id == id }
        mutableState.value = mutableState.value.copy(
            sessions = remaining,
            selectedSessionId = if (mutableState.value.selectedSessionId == id) remaining.firstOrNull()?.id else mutableState.value.selectedSessionId,
        )
        saves.trySend(remaining)
    }
}

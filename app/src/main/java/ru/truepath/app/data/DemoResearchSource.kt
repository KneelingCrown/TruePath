package ru.truepath.app.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import ru.truepath.app.domain.*

data class NewsArticle(
    val id: String, val topic: String, val title: String, val summary: String,
    val sources: Int, val minutesAgo: Int, val sourceText: String,
    val claim: String, val evidence: String, val conclusion: String, val planB: String,
)

/** Fictional editorial fixtures. No network requests or genuine fact-checking take place. */
object DemoNews {
    val articles = listOf(
        NewsArticle(
            "event", "События", "Хакатон перенесли на воскресенье. Что изменилось?",
            "В учебной подборке три публикации по-разному описывают изменение расписания.",
            3, 12, "Учебное объявление организаторов: на воскресенье переносится только финал трека «Дизайн». Остальные треки проходят по исходному расписанию.",
            "Перенесли весь хакатон", "На воскресенье переносится только финал трека «Дизайн».",
            "Обобщённый заголовок противоречит учебному объявлению: перенос касается одного трека.",
            "Уточните свой трек по расписанию. До уточнения сохраните исходное время выезда.",
        ),
        NewsArticle(
            "science", "Наука", "Новый аккумулятор заряжается за минуту?",
            "Разбираем разницу между лабораторным образцом и готовой технологией.",
            2, 34, "Учебная заметка лаборатории: тестовый образец достиг 10% заряда за минуту в контролируемых условиях. Испытания полной зарядки не проводились.",
            "Полная зарядка за минуту", "Тестовый образец достиг 10% заряда за минуту в контролируемых условиях.",
            "В учебном источнике речь о 10% заряда. Утверждение о полной зарядке им не подтверждается.",
            "Дождитесь результатов испытаний полного цикла и независимого повторения эксперимента.",
        ),
        NewsArticle(
            "city", "Город", "В городе запускают бесплатный транспорт",
            "Какие условия исчезли в коротком пересказе учебного объявления.",
            4, 56, "Учебное городское объявление: бесплатный проезд действует на одном выставочном маршруте в субботу с 10:00 до 18:00.",
            "Весь транспорт бесплатный", "Бесплатный проезд действует на одном выставочном маршруте в субботу с 10:00 до 18:00.",
            "Учебное объявление относится к одному маршруту и ограниченному времени.",
            "Проверьте маршрут и время поездки, прежде чем рассчитывать на бесплатный проезд.",
        ),
    )
}

interface ResearchSource {
    fun events(session: ResearchSession): Flow<ResearchEvent>
}

class DemoResearchSource : ResearchSource {
    override fun events(session: ResearchSession): Flow<ResearchEvent> = flow {
        emit(ResearchEvent.Stage("stage:prepare", "Разбираем материал"))
        delay(900)
        val article = DemoNews.articles.find { it.id == session.scenarioId }
        if (article == null) {
            emit(ResearchEvent.NeedsApi("needs-api"))
            return@flow
        }
        emit(ResearchEvent.UpsertNode("node:claim", GraphNode(
            "claim", article.claim, NodeKind.CLAIM, description = "Проверяем область действия утверждения из учебной публикации.", x = .3f, y = .38f,
        )))
        emit(ResearchEvent.UpsertEdge("edge:material-claim", GraphEdge("material-claim", "material", "claim", "содержит")))
        delay(1100)
        emit(ResearchEvent.UpsertNode("node:question", GraphNode(
            "question", "Что говорит первоисточник?", NodeKind.CLAIM,
            TrustStatus.UNCERTAIN, "Ищем полный контекст и ограничения.", x = .74f, y = .38f,
        )))
        emit(ResearchEvent.UpsertEdge("edge:question", GraphEdge("claim-question", "claim", "question", "нужно уточнить")))
        emit(ResearchEvent.Stage("stage:source", "Добавляем учебные источники"))
        delay(1100)
        emit(ResearchEvent.UpsertNode("node:source", GraphNode(
            "source", "Полный документ", NodeKind.SOURCE, description = article.sourceText,
            sourceLabel = "Учебный первоисточник", x = .23f, y = .67f,
        )))
        emit(ResearchEvent.UpsertNode("node:retelling", GraphNode(
            "retelling", "Короткий пересказ", NodeKind.SOURCE,
            description = "Учебная публикация повторяет заголовок. Независимого подтверждения не добавляет.",
            sourceLabel = "Учебная публикация", x = .78f, y = .68f,
        )))
        emit(ResearchEvent.UpsertEdge("edge:retelling", GraphEdge("retelling-claim", "retelling", "claim", "повторяет")))
        delay(1200)
        emit(ResearchEvent.Stage("stage:compare", "Сопоставляем формулировки"))
        emit(ResearchEvent.UpsertNode("node:evidence", GraphNode(
            "evidence", "Важное ограничение", NodeKind.EVIDENCE, TrustStatus.SUPPORTED,
            "Цитата относится к подготовленному учебному источнику.", article.evidence,
            "Учебный первоисточник", x = .5f, y = .82f,
        )))
        emit(ResearchEvent.UpsertEdge("edge:quote", GraphEdge("source-evidence", "source", "evidence", "цитата")))
        emit(ResearchEvent.UpsertEdge("edge:contradiction", GraphEdge("evidence-claim", "evidence", "claim", "противоречит")))
        delay(1200)
        emit(ResearchEvent.UpsertNode("node:claim-final", GraphNode(
            "claim", article.claim, NodeKind.CLAIM, TrustStatus.CONTRADICTED,
            article.conclusion, article.evidence, "Учебный первоисточник", x = .3f, y = .38f,
        )))
        emit(ResearchEvent.UpsertNode("node:question-final", GraphNode(
            "question", "Контекст найден", NodeKind.CLAIM, TrustStatus.SUPPORTED,
            article.sourceText, x = .74f, y = .38f,
        )))
        emit(ResearchEvent.UpsertEdge("edge:context", GraphEdge("source-question", "source", "question", "уточняет")))
        delay(900)
        emit(ResearchEvent.Finished("finished", article.conclusion, article.planB))
    }
}

package ru.truepath.app.data

import ru.truepath.app.domain.*

/** Deliberately fictional stress scenario: six claims, twelve sources and twelve checks. */
object LargeDemoScenario {
    const val ID = "energy"
    val article = NewsArticle(
        ID, "Наука", "Город будущего: энергия без счетов и транспорт без билетов?",
        "Шесть громких обещаний в одной учебной публикации. Большой граф показывает, как они связаны с документами, пересказами и ограничениями.",
        12, 4, "Учебное досье: пилотный городской квартал, шесть направлений, разные сроки и условия участия.",
        "Все технологии уже доступны каждому", "Каждое направление ограничено сроками, территорией или условиями пилота.",
        "В публикации смешаны подтверждённые условия пилота, преувеличения и обещания без достаточных данных. Бесплатный маршрут существует только внутри квартала; зарядка за минуту относится к 10%, а не к полному аккумулятору. Общегородской запуск и нулевые счета не установлены.",
        "Проверяйте условия своего маршрута и адреса по отдельности. Не меняйте тарифы и планы поездки на основании общего заголовка.",
    )

    private data class Branch(val title: String, val document: String, val quote: String, val question: String, val status: TrustStatus)
    private val branches = listOf(
        Branch("Зарядка за минуту", "Протокол лаборатории", "За минуту образец получил 10% заряда.", "Полный цикл не испытан", TrustStatus.CONTRADICTED),
        Branch("Бесплатный проезд", "Условия маршрута", "Пилотный шаттл внутри квартала бесплатен.", "За пределами пилота — обычный тариф", TrustStatus.SUPPORTED),
        Branch("Энергия без счетов", "Паспорт энергопроекта", "Расчётная экономия до 20% зависит от нагрузки.", "Нулевой счёт не доказан", TrustStatus.UNCERTAIN),
        Branch("Чистый воздух", "Отчёт датчиков", "Снижение показателя измерено у одного перекрёстка.", "Город целиком не измерялся", TrustStatus.CONTRADICTED),
        Branch("Запуск уже завтра", "Календарь испытаний", "Завтра открывается один демонстрационный маршрут.", "Даты остальных запусков отличаются", TrustStatus.SUPPORTED),
        Branch("Доступно всем", "Правила участия", "Участие по предварительной регистрации жителей квартала.", "Расширение программы не подтверждено", TrustStatus.UNCERTAIN),
    )

    fun events(): List<ResearchEvent> = buildList {
        add(ResearchEvent.Stage("large:claims", "Выделяем шесть утверждений"))
        branches.forEachIndexed { i, branch ->
            val x = .085f + i * .166f
            add(ResearchEvent.UpsertNode("large:claim:$i", GraphNode("energy:claim:$i", branch.title, NodeKind.CLAIM,
                description = "Отдельное утверждение из вымышленной публикации.", x = x, y = .20f)))
            add(ResearchEvent.UpsertEdge("large:contains:$i", GraphEdge("energy:contains:$i", "material", "energy:claim:$i", "содержит")))
        }
        add(ResearchEvent.Stage("large:sources", "Собираем досье из двенадцати учебных источников"))
        branches.forEachIndexed { i, branch ->
            val x = .085f + i * .166f
            add(ResearchEvent.UpsertNode("large:document:$i", GraphNode("energy:document:$i", branch.document, NodeKind.SOURCE,
                description = "Подготовленный для демонстрации документ. ${branch.quote}", sourceLabel = "Учебный документ ${i + 1}", x = x, y = .36f)))
            add(ResearchEvent.UpsertNode("large:retelling:$i", GraphNode("energy:retelling:$i", "Пересказ ${i + 1}", NodeKind.SOURCE,
                description = "Повторяет исходное обещание без новых независимых оснований.", sourceLabel = "Учебный пересказ", x = x, y = .47f)))
            add(ResearchEvent.UpsertEdge("large:repeats:$i", GraphEdge("energy:repeats:$i", "energy:retelling:$i", "energy:claim:$i", "пересказывает")))
            add(ResearchEvent.UpsertEdge("large:quotes:$i", GraphEdge("energy:quotes:$i", "energy:document:$i", "energy:retelling:$i", "исходный контекст")))
        }
        add(ResearchEvent.Stage("large:checks", "Сопоставляем цитаты и ограничения по каждой ветке"))
        branches.forEachIndexed { i, branch ->
            val x = .085f + i * .166f
            val evidenceTitle = listOf("10% вместо 100%", "Один квартал", "Экономия до 20%", "Один перекрёсток", "Демонстрационный рейс", "Нужна регистрация")[i]
            add(ResearchEvent.UpsertNode("large:evidence:$i", GraphNode("energy:evidence:$i", evidenceTitle, NodeKind.EVIDENCE,
                TrustStatus.SUPPORTED, "Цитата из подготовленного учебного документа.", branch.quote, branch.document, x = x, y = .62f)))
            add(ResearchEvent.UpsertNode("large:limit:$i", GraphNode("energy:limit:$i", branch.question, NodeKind.EVIDENCE,
                TrustStatus.UNCERTAIN, "Ограничение, которое нужно учитывать отдельно от общего заголовка.", sourceLabel = branch.document, x = x, y = .74f)))
            add(ResearchEvent.UpsertEdge("large:document-evidence:$i", GraphEdge("energy:document-evidence:$i", "energy:document:$i", "energy:evidence:$i", "цитата")))
            add(ResearchEvent.UpsertEdge("large:document-limit:$i", GraphEdge("energy:document-limit:$i", "energy:document:$i", "energy:limit:$i", "ограничение")))
            add(ResearchEvent.UpsertEdge("large:evidence-claim:$i", GraphEdge("energy:evidence-claim:$i", "energy:evidence:$i", "energy:claim:$i", "проверяет")))
            add(ResearchEvent.UpsertEdge("large:limit-claim:$i", GraphEdge("energy:limit-claim:$i", "energy:limit:$i", "energy:claim:$i", "уточняет границы")))
            if (i > 0) add(ResearchEvent.UpsertEdge("large:cross:$i", GraphEdge("energy:cross:$i", "energy:limit:${i - 1}", "energy:claim:$i", "общий контекст пилота")))
        }
        add(ResearchEvent.Stage("large:verdicts", "Уточняем статусы шести утверждений"))
        branches.forEachIndexed { i, branch ->
            add(ResearchEvent.UpsertNode("large:verdict:$i", GraphNode("energy:claim:$i", branch.title, NodeKind.CLAIM,
                branch.status, "${branch.quote} ${branch.question}.", branch.quote, branch.document, x = .085f + i * .166f, y = .20f)))
        }
        add(ResearchEvent.Finished("large:finished", article.conclusion, article.planB))
    }
}

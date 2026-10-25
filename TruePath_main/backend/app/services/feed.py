"""Лента новостей без внешних API (по ТЗ текущего этапа): фикстурные публикации
загружаются в таблицы source/article/event, кластеризуются в события без
эмбеддингов и LLM — по пересечению ключевых слов заголовков. Позже загрузчик
фикстур заменяется RSS-загрузчиком с той же кластеризацией."""

import datetime as dt
from dataclasses import dataclass

from sqlalchemy.orm import Session

from app.adapters.local_search import tokens, tokens_match
from app.models import Article, Event, Source

# Минимум совпадающих ключевых слов заголовков, чтобы считать публикации
# рассказами об одном событии (план шага 8: без эмбеддингов).
_CLUSTER_OVERLAP = 2


@dataclass
class FixtureArticle:
    source_name: str
    source_url: str
    url: str
    title: str
    content: str
    minutes_ago: int


FIXTURE_ARTICLES = [
    FixtureArticle(
        source_name="Городские новости",
        source_url="https://demo-news.example.ru",
        url="https://demo-news.example.ru/festival-perenos",
        title="Городской фестиваль перенесён на воскресенье",
        content=(
            "Начало городского фестиваля перенесено на воскресенье 25 октября. "
            "Организаторы сообщили об изменении расписания."
        ),
        minutes_ago=12,
    ),
    FixtureArticle(
        source_name="Официальный вестник",
        source_url="https://demo.gov",
        url="https://demo.gov/news/festival-schedule",
        title="Расписание фестиваля изменилось: старт в воскресенье",
        content=(
            "Распоряжение №114-У: старт фестиваля для направления «Робототехника» "
            "перенесён на воскресенье 25 октября. Остальные направления — по прежнему расписанию."
        ),
        minutes_ago=30,
    ),
    FixtureArticle(
        source_name="Афиша города",
        source_url="https://demo-afisha.example.ru",
        url="https://demo-afisha.example.ru/festival",
        title="Фестиваль стартует на день позже",
        content=(
            "Организаторы фестиваля сообщили: церемония открытия пройдёт по расписанию, "
            "старт направлений — на день позже."
        ),
        minutes_ago=55,
    ),
    FixtureArticle(
        source_name="Городские новости",
        source_url="https://demo-news.example.ru",
        url="https://demo-news.example.ru/park",
        title="Центральный парк откроют после реконструкции",
        content="Центральный парк откроют для посетителей в ноябре после завершения реконструкции.",
        minutes_ago=90,
    ),
    FixtureArticle(
        source_name="Официальный вестник",
        source_url="https://demo.gov",
        url="https://demo.gov/news/tickets",
        title="Билеты на фестиваль поступили в кассы",
        content="Билеты на фестиваль поступили в кассы и на официальный сайт организаторов.",
        minutes_ago=200,
    ),
]

# Тема для ранжирования выводится из заголовка детерминированно (без LLM).
_TOPIC_LEXICON = {
    "События": ("фестивал", "билет", "расписание", "перенос", "отмен", "мероприят"),
    "Город": ("парк", "улиц", "транспорт", "строй", "ремонт"),
}


def classify_topic(text: str) -> str:
    text_tokens = tokens(text)
    for topic, markers in _TOPIC_LEXICON.items():
        if any(any(tokens_match(marker, token) for token in text_tokens) for marker in markers):
            return topic
    return "Общее"


def cluster_indexes(titles: list[str]) -> list[list[int]]:
    """Кластеризация заголовков: публикация присоединяется к кластеру, если у неё
    не меньше двух общих ключевых слов с любой публикацией кластера."""
    title_tokens = [tokens(title) for title in titles]
    clusters: list[list[int]] = []
    for index, current in enumerate(title_tokens):
        target = next(
            (
                cluster
                for cluster in clusters
                if any(_overlap(current, title_tokens[other]) >= _CLUSTER_OVERLAP for other in cluster)
            ),
            None,
        )
        if target is None:
            clusters.append([index])
        else:
            target.append(index)
    return clusters


def _overlap(a: list[str], b: list[str]) -> int:
    return sum(1 for token in a if any(tokens_match(token, other) for other in b))


def event_summary(titles_freshest_first: list[str]) -> str:
    """Шаблонное саммари события: самый свежий заголовок + число публикаций +
    новые подробности относительно самой ранней публикации (дифф ключевых слов)."""
    latest, oldest = titles_freshest_first[0], titles_freshest_first[-1]
    summary = f"«{latest}». Публикаций: {len(titles_freshest_first)}."
    if len(titles_freshest_first) > 1:
        new_details = [
            token
            for token in tokens(latest)
            if not any(tokens_match(token, other) for other in tokens(oldest))
        ]
        if new_details:
            summary += f" Новое относительно первой публикации: {', '.join(new_details)}."
    return summary


def seed_feed(session: Session) -> None:
    """Загружает фикстурные публикации и кластеры. Идемпотентно: повторный вызов
    не создаёт дубликатов."""
    if session.query(Source).count():
        return

    sources: dict[str, Source] = {}
    articles: list[Article] = []
    now = dt.datetime.now(dt.UTC).replace(tzinfo=None)
    for item in FIXTURE_ARTICLES:
        source = sources.get(item.source_name)
        if source is None:
            source = Source(name=item.source_name, url=item.source_url)
            session.add(source)
            sources[item.source_name] = source
        article = Article(
            source=source,
            url=item.url,
            title=item.title,
            content=item.content,
            published_at=now - dt.timedelta(minutes=item.minutes_ago),
        )
        session.add(article)
        articles.append(article)
    session.flush()

    for cluster in cluster_indexes([item.title for item in FIXTURE_ARTICLES]):
        ordered = sorted(cluster, key=lambda i: FIXTURE_ARTICLES[i].minutes_ago)
        event = Event(
            title=FIXTURE_ARTICLES[ordered[0]].title,
            summary=event_summary([FIXTURE_ARTICLES[i].title for i in ordered]),
        )
        session.add(event)
        session.flush()
        for index in ordered:
            articles[index].event_id = event.id
    session.commit()

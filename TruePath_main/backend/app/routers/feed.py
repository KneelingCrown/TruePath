"""Лента событий: кластеры публикаций с шаблонным саммари, темой и отметкой
«событие уже проверялось». Ранжирование — свежесть; просмотренные события
клиент исключает параметром exclude (персональные отзывы появятся вместе
с моделью пользователя)."""

import datetime as dt

from fastapi import APIRouter, Depends, Query
from sqlalchemy.orm import Session

from app.db import get_session
from app.models import Event, Research, ResearchStatus
from app.schemas import FeedArticleOut, FeedCheckOut, FeedItemOut
from app.services.feed import classify_topic, seed_feed

router = APIRouter(prefix="/feed", tags=["feed"])


@router.get("", response_model=list[FeedItemOut])
def get_feed(
    q: str | None = Query(default=None),
    topic: str | None = Query(default=None),
    exclude: str | None = Query(default=None, description="event_id просмотренных событий через запятую"),
    session: Session = Depends(get_session),
) -> list[FeedItemOut]:
    seed_feed(session)
    excluded = {int(value) for value in (exclude or "").split(",") if value.strip().isdigit()}

    items: list[FeedItemOut] = []
    for event in session.query(Event).order_by(Event.id).all():
        if event.id in excluded:
            continue
        articles = sorted(event.articles, key=lambda a: a.published_at or dt.datetime.min, reverse=True)
        latest = articles[0]
        event_topic = classify_topic(event.title)
        if topic and event_topic != topic:
            continue
        if q and not _matches(event, q):
            continue
        items.append(
            FeedItemOut(
                event_id=event.id,
                title=event.title,
                summary=event.summary or "",
                topic=event_topic,
                sources=len(articles),
                minutes_ago=_minutes_ago(latest.published_at),
                articles=[
                    FeedArticleOut(
                        id=article.id,
                        title=article.title,
                        url=article.url,
                        source=article.source.name,
                        minutes_ago=_minutes_ago(article.published_at),
                    )
                    for article in articles
                ],
                checked=_checked(session, articles),
            )
        )
    return items


def _minutes_ago(published_at: dt.datetime | None) -> int:
    if published_at is None:
        return 0
    delta = dt.datetime.now(dt.UTC).replace(tzinfo=None) - published_at
    return max(0, int(delta.total_seconds() // 60))


def _matches(event: Event, q: str) -> bool:
    needle = q.lower().strip()
    texts = [event.title, event.summary or "", *(article.title for article in event.articles)]
    return any(needle in text.lower() for text in texts)


def _checked(session: Session, articles) -> FeedCheckOut | None:
    research = (
        session.query(Research)
        .filter(
            Research.article_id.in_([article.id for article in articles]),
            Research.status == ResearchStatus.done,
        )
        .order_by(Research.id.desc())
        .first()
    )
    if research is None:
        return None
    return FeedCheckOut(
        research_id=research.id,
        checked_at=research.finished_at.isoformat() if research.finished_at else None,
    )

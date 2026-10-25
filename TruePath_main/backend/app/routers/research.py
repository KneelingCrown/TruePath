import json

import datetime as dt

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query, Response, status
from sqlalchemy.orm import Session

from app import db
from app.adapters.local_search import LocalSearch
from app.adapters.rule_llm import RuleBasedLLM
from app.config import settings
from app.db import get_session
from app.models import (
    Article,
    Claim,
    Edge,
    Evidence,
    ExtSource,
    Material,
    MaterialKind,
    Node,
    Research,
    ResearchStatus,
    ResearchStep,
)
from app.schemas import (
    EDGE_KIND_LABELS,
    ClaimOut,
    EventOut,
    EventsOut,
    EvidenceOut,
    GraphEdgeOut,
    GraphNodeOut,
    GraphOut,
    ResearchCreate,
    ResearchDetailOut,
    ResearchOut,
    ResearchStepOut,
    ResultOut,
    parse_questions,
)
from app.services.events import build_events, node_payload
from app.services.pipeline import ResearchPipeline

router = APIRouter(prefix="/research", tags=["research"])

# Фоновые задачи умирают при перезапуске сервера; дольше этого срока
# queued/running считаются прерванными (план, раздел «Риски»).
STALE_TIMEOUT_MINUTES = 10


def _fail_stale(session: Session) -> None:
    cutoff = dt.datetime.now(dt.UTC).replace(tzinfo=None) - dt.timedelta(minutes=STALE_TIMEOUT_MINUTES)
    stale = (
        session.query(Research)
        .filter(
            Research.status.in_([ResearchStatus.queued, ResearchStatus.running]),
            Research.created_at < cutoff,
        )
        .all()
    )
    for research in stale:
        research.status = ResearchStatus.failed
        research.error = "Прервано: сервер был перезапущен во время проверки. Запустите обновление."
    if stale:
        session.commit()


def get_pipeline() -> ResearchPipeline:
    return ResearchPipeline(
        db.SessionLocal,
        RuleBasedLLM(),
        LocalSearch(),
        extra_search_max_queries=settings.extra_search_max_queries,
    )


@router.post("", response_model=ResearchOut, status_code=status.HTTP_201_CREATED)
def start_research(
    data: ResearchCreate,
    response: Response,
    background_tasks: BackgroundTasks,
    session: Session = Depends(get_session),
    pipeline: ResearchPipeline = Depends(get_pipeline),
) -> ResearchOut:
    _fail_stale(session)
    # Идемпотентность: повторный тап с тем же client_task_id возвращает существующую задачу.
    if data.client_task_id:
        existing = session.query(Research).filter_by(client_task_id=data.client_task_id).one_or_none()
        if existing:
            response.status_code = status.HTTP_200_OK
            return ResearchOut(id=existing.id, status=existing.status, material_id=existing.material_id)

    material_id, article_id = _resolve_source(session, data)

    research = Research(
        material_id=material_id,
        article_id=article_id,
        context=data.context,
        client_task_id=data.client_task_id,
        status=ResearchStatus.queued,
    )
    session.add(research)
    session.flush()
    for stage in range(1, len(ResearchPipeline.STAGE_NAMES) + 1):
        session.add(ResearchStep(research_id=research.id, stage=stage))
    session.commit()

    background_tasks.add_task(pipeline.run, research.id)
    return ResearchOut(id=research.id, status=research.status, material_id=research.material_id)


def _resolve_source(session: Session, data: ResearchCreate) -> tuple[int, int | None]:
    """Возвращает (material_id, article_id). Для публикации из ленты материал
    создаётся из её текста."""
    if data.article_id is not None:
        article = session.get(Article, data.article_id)
        if article is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Публикация не найдена")
        text = (article.content or article.title).strip()
        if not text:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail="У публикации нет текста — проверка невозможна",
            )
        material = Material(kind=MaterialKind.text, raw_content=article.url, extracted_text=text)
        session.add(material)
        session.flush()
        return material.id, article.id

    material = session.get(Material, data.material_id)
    if material is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Материал не найден")
    if not material.extracted_text:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="У материала нет извлечённого текста — проверка невозможна",
        )
    return material.id, None


@router.get("/{research_id}", response_model=ResearchDetailOut)
def get_research(research_id: int, session: Session = Depends(get_session)) -> ResearchDetailOut:
    research = session.get(Research, research_id)
    if research is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Исследование не найдено")
    return _detail(research)


@router.get("/{research_id}/result", response_model=ResultOut)
def get_result(research_id: int, session: Session = Depends(get_session)) -> ResultOut:
    research = session.get(Research, research_id)
    if research is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Исследование не найдено")
    return ResultOut(
        research_id=research.id,
        status=research.status,
        summary=research.summary,
        error=research.error,
        claims=_claims_out(research),
    )


@router.get("/{research_id}/graph", response_model=GraphOut)
def get_graph(research_id: int, session: Session = Depends(get_session)) -> GraphOut:
    research = session.get(Research, research_id)
    if research is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Исследование не найдено")

    nodes: list[GraphNodeOut] = []
    for node in sorted(research.nodes, key=lambda n: n.id):
        nodes.append(node_payload(session, node, f"node-{node.id}"))

    edges = [
        GraphEdgeOut(
            ref=f"edge-{edge.id}",
            from_ref=f"node-{edge.src_node_id}",
            to_ref=f"node-{edge.dst_node_id}",
            kind=edge.kind,
            label=EDGE_KIND_LABELS[edge.kind],
            explanation=edge.explanation,
            is_established=edge.is_established,
        )
        for edge in sorted(session.query(Edge).filter_by(research_id=research.id), key=lambda e: e.id)
    ]
    return GraphOut(research_id=research.id, status=research.status, nodes=nodes, edges=edges)


@router.get("/{research_id}/events", response_model=EventsOut)
def get_events(
    research_id: int,
    after: int = Query(default=0, ge=0),
    session: Session = Depends(get_session),
) -> EventsOut:
    research = session.get(Research, research_id)
    if research is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Исследование не найдено")
    events = build_events(session, research)
    pending = [event for event in events if event.seq > after]
    cursor = events[-1].seq if events else after
    return EventsOut(research_id=research.id, status=research.status, cursor=cursor, events=pending)


@router.post("/{research_id}/refresh", response_model=ResearchOut, status_code=status.HTTP_201_CREATED)
def refresh_research(
    research_id: int,
    background_tasks: BackgroundTasks,
    session: Session = Depends(get_session),
    pipeline: ResearchPipeline = Depends(get_pipeline),
) -> ResearchOut:
    research = session.get(Research, research_id)
    if research is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Исследование не найдено")
    if research.material is None:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="У исследования нет материала для повторной проверки",
        )

    # Обновление создаёт отдельную запись: прежний результат сохраняется.
    refreshed = Research(
        material_id=research.material_id,
        article_id=research.article_id,
        context=research.context,
        status=ResearchStatus.queued,
    )
    session.add(refreshed)
    session.flush()
    for stage in range(1, len(ResearchPipeline.STAGE_NAMES) + 1):
        session.add(ResearchStep(research_id=refreshed.id, stage=stage))
    session.commit()

    background_tasks.add_task(pipeline.run, refreshed.id)
    return ResearchOut(id=refreshed.id, status=refreshed.status, material_id=refreshed.material_id)


def _detail(research: Research) -> ResearchDetailOut:
    return ResearchDetailOut(
        id=research.id,
        status=research.status,
        error=research.error,
        summary=research.summary,
        created_at=research.created_at.isoformat() if research.created_at else None,
        steps=[
            ResearchStepOut(
                stage=step.stage,
                name=ResearchPipeline.STAGE_NAMES[step.stage],
                status=step.status,
                detail=step.detail,
            )
            for step in sorted(research.steps, key=lambda s: s.stage)
        ],
        claims=_claims_out(research),
    )


def _claims_out(research: Research) -> list[ClaimOut]:
    claims_out: list[ClaimOut] = []
    for claim in sorted(research.claims, key=lambda c: c.id):
        evidences = [
            EvidenceOut(
                quote=evidence.quote,
                relation=evidence.relation,
                explanation=evidence.explanation,
                source_url=evidence.ext_source.url if evidence.ext_source else None,
                source_title=evidence.ext_source.title if evidence.ext_source else None,
            )
            for evidence in sorted(claim.evidences, key=lambda e: e.id)
        ]
        claims_out.append(
            ClaimOut(
                id=claim.id,
                text=claim.text,
                status=claim.status,
                questions=parse_questions(claim.questions),
                explanation=claim.explanation,
                evidences=evidences,
            )
        )
    return claims_out

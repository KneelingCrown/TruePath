"""Поток событий исследования для клиента. События вычисляются из состояния БД,
поэтому список детерминированный: повторный опрос с тем же курсором возвращает
те же события, новые события получают следующие seq. Идентификаторы событий
стабильные (node-<id>, stage-<n>), повторная доставка безопасна."""

from sqlalchemy.orm import Session

from app.models import (
    Claim,
    Edge,
    Evidence,
    ExtSource,
    Node,
    NodeKind,
    Research,
    ResearchStatus,
    StepStatus,
)
from app.schemas import EDGE_KIND_LABELS, EventOut, GraphEdgeOut, GraphNodeOut
from app.services.pipeline import ResearchPipeline


def build_events(session: Session, research: Research) -> list[EventOut]:
    events: list[EventOut] = []

    for step in sorted(research.steps, key=lambda s: s.stage):
        if step.status == StepStatus.pending:
            continue
        text = ResearchPipeline.STAGE_NAMES[step.stage]
        if step.status == StepStatus.done and step.detail:
            text = f"{text}: {step.detail}"
        events.append(EventOut(seq=0, id=f"stage-{step.stage}", type="stage", text=text))

    claim_by_node_ref: dict[str, Claim] = {}
    for node in sorted(research.nodes, key=lambda n: n.id):
        ref = f"node-{node.id}"
        payload = node_payload(session, node, ref)
        events.append(EventOut(seq=0, id=f"node-{node.id}", type="upsert_node", node=payload))
        if node.kind == NodeKind.claim and node.entity_id:
            claim = session.get(Claim, node.entity_id)
            if claim:
                claim_by_node_ref[ref] = claim

    for edge in sorted(session.query(Edge).filter_by(research_id=research.id), key=lambda e: e.id):
        events.append(
            EventOut(seq=0, id=f"edge-{edge.id}", type="upsert_edge", edge=_edge_payload(edge))
        )

    if research.status == ResearchStatus.done:
        # Финальные статусы утверждений: тот же узел, новый id события.
        for ref, claim in claim_by_node_ref.items():
            events.append(
                EventOut(
                    seq=0,
                    id=f"claim-status-{claim.id}",
                    type="upsert_node",
                    node=GraphNodeOut(
                        ref=ref,
                        kind=NodeKind.claim,
                        entity_id=claim.id,
                        label=claim.text[:200],
                        status=claim.status,
                        description=claim.explanation,
                    ),
                )
            )
        events.append(
            EventOut(
                seq=0,
                id=f"finished-{research.id}",
                type="finished",
                summary=research.summary or "",
                plan_b=_plan_b_text(research),
            )
        )
    elif research.status == ResearchStatus.failed:
        events.append(
            EventOut(seq=0, id=f"failed-{research.id}", type="failed", message=research.error or "Ошибка исследования")
        )

    for index, event in enumerate(events, start=1):
        event.seq = index
    return events


def node_payload(session: Session, node: Node, ref: str) -> GraphNodeOut:
    """Данные узла для клиента. Цитаты и источники берутся из связанных сущностей:
    у доказательства — цитата и её источник, у источника — название и URL,
    у утверждения — итоговое объяснение."""
    status = None
    description = None
    quote = None
    source_label = None
    source_url = None
    if node.kind == NodeKind.claim and node.entity_id:
        claim = session.get(Claim, node.entity_id)
        if claim:
            status = claim.status
            description = claim.explanation
    elif node.kind == NodeKind.source and node.entity_id:
        source = session.get(ExtSource, node.entity_id)
        if source:
            source_label = source.title
            source_url = source.url
            description = (source.text or "")[:200] or None
    elif node.kind == NodeKind.evidence and node.entity_id:
        evidence = session.get(Evidence, node.entity_id)
        if evidence:
            quote = evidence.quote
            description = evidence.explanation
            if evidence.ext_source:
                source_label = evidence.ext_source.title
                source_url = evidence.ext_source.url
    return GraphNodeOut(
        ref=ref,
        kind=node.kind,
        entity_id=node.entity_id,
        label=node.label,
        status=status,
        description=description,
        quote=quote,
        source_label=source_label,
        source_url=source_url,
    )


def _plan_b_text(research: Research) -> str:
    """Текст «Плана Б» для события finished: по одному предложению на карточку."""
    parts: list[str] = []
    for claim in sorted(research.claims, key=lambda c: c.id):
        card = next(iter(claim.plan_b_cards), None)
        if card and card.act_now:
            parts.append(f"«{_truncate(claim.text, 60)}» — {card.act_now.lower()}")
    if not parts:
        return ""
    return "План Б: " + "; ".join(parts) + "."


def _truncate(text: str, limit: int) -> str:
    text = " ".join(text.split())
    return text if len(text) <= limit else text[: limit - 1] + "…"


def _edge_payload(edge: Edge) -> GraphEdgeOut:
    return GraphEdgeOut(
        ref=f"edge-{edge.id}",
        from_ref=f"node-{edge.src_node_id}",
        to_ref=f"node-{edge.dst_node_id}",
        kind=edge.kind,
        label=EDGE_KIND_LABELS[edge.kind],
        explanation=edge.explanation,
        is_established=edge.is_established,
    )

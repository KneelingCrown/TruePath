import json

from pydantic import BaseModel, HttpUrl, model_validator

from app.models import (
    ClaimStatus,
    EdgeKind,
    EvidenceRelation,
    Material,
    MaterialKind,
    NodeKind,
    PlanTaskStatus,
    ResearchStatus,
    StepStatus,
)

# Русские подписи типов связей — для label на графе клиента.
EDGE_KIND_LABELS = {
    EdgeKind.contains_claim: "содержит",
    EdgeKind.supports: "подтверждает",
    EdgeKind.refutes: "противоречит",
    EdgeKind.limits: "ограничивает",
    EdgeKind.references_source: "цитата",
    EdgeKind.same_event: "то же событие",
    EdgeKind.depends_on_claim: "зависит от утверждения",
}


class MaterialCreate(BaseModel):
    kind: MaterialKind
    content: str | None = None
    url: HttpUrl | None = None

    @model_validator(mode="after")
    def check_payload(self) -> "MaterialCreate":
        if self.kind == MaterialKind.text and not (self.content and self.content.strip()):
            raise ValueError("Для текстового материала нужно непустое поле content")
        if self.kind == MaterialKind.link and not self.url:
            raise ValueError("Для материала-ссылки нужно поле url")
        if self.kind in (MaterialKind.image, MaterialKind.document):
            raise ValueError("Файлы загружаются через POST /api/v1/materials/file")
        return self


class MaterialOut(BaseModel):
    id: int
    kind: MaterialKind
    has_extracted_text: bool
    warning: str | None = None


def to_out(material: Material, warning: str | None = None) -> MaterialOut:
    return MaterialOut(
        id=material.id,
        kind=material.kind,
        has_extracted_text=bool(material.extracted_text),
        warning=warning,
    )


class ResearchCreate(BaseModel):
    material_id: int | None = None
    # Запуск проверки публикации из ленты: материал создаётся из её текста.
    article_id: int | None = None
    context: str | None = None
    # Идемпотентность: повторный запуск с тем же client_task_id не создаёт вторую задачу.
    client_task_id: str | None = None

    @model_validator(mode="after")
    def check_source(self) -> "ResearchCreate":
        if (self.material_id is None) == (self.article_id is None):
            raise ValueError("Укажите material_id или article_id — ровно один источник")
        return self


class ResearchOut(BaseModel):
    id: int
    status: ResearchStatus
    material_id: int | None


class ResearchStepOut(BaseModel):
    stage: int
    name: str
    status: StepStatus
    detail: str | None = None


class EvidenceOut(BaseModel):
    quote: str
    relation: EvidenceRelation
    explanation: str
    source_url: str | None = None
    source_title: str | None = None


class ClaimOut(BaseModel):
    id: int
    text: str
    status: ClaimStatus
    questions: list[str] | None = None
    explanation: str | None = None
    evidences: list[EvidenceOut] = []


class ResearchDetailOut(BaseModel):
    id: int
    status: ResearchStatus
    error: str | None = None
    summary: str | None = None
    created_at: str | None = None
    steps: list[ResearchStepOut]
    claims: list[ClaimOut]


class ResultOut(BaseModel):
    research_id: int
    status: ResearchStatus
    summary: str | None = None
    error: str | None = None
    claims: list[ClaimOut]


class GraphNodeOut(BaseModel):
    ref: str
    kind: NodeKind
    entity_id: int | None = None
    label: str | None = None
    # Статус заполняется только для узлов-утверждений.
    status: ClaimStatus | None = None
    description: str | None = None
    quote: str | None = None
    source_label: str | None = None
    source_url: str | None = None


class GraphEdgeOut(BaseModel):
    ref: str
    from_ref: str
    to_ref: str
    kind: EdgeKind
    label: str
    explanation: str
    is_established: bool


class GraphOut(BaseModel):
    research_id: int
    status: ResearchStatus
    nodes: list[GraphNodeOut]
    edges: list[GraphEdgeOut]


class EventOut(BaseModel):
    """Событие потока исследования. Семантики совпадают с контрактом клиента:
    stage / upsert_node / upsert_edge / finished / failed. seq — курсор."""

    seq: int
    id: str
    type: str
    text: str | None = None
    node: GraphNodeOut | None = None
    edge: GraphEdgeOut | None = None
    summary: str | None = None
    plan_b: str | None = None
    message: str | None = None


class EventsOut(BaseModel):
    research_id: int
    status: ResearchStatus
    cursor: int
    events: list[EventOut]


class HistoryItemOut(BaseModel):
    id: int
    status: ResearchStatus
    created_at: str | None = None
    summary: str | None = None
    snippet: str | None = None


class PlanTaskOut(BaseModel):
    id: int
    claim_id: int
    text: str
    status: PlanTaskStatus


class PlanClaimOut(BaseModel):
    id: int
    text: str
    status: ClaimStatus
    explanation: str | None = None


class PlanBCardOut(BaseModel):
    act_now: str | None = None
    postpone: str | None = None
    evidence_needed: str | None = None
    alternatives: str | None = None


class PlanItemOut(BaseModel):
    id: int
    text: str
    status: PlanTaskStatus
    claim: PlanClaimOut
    # Карточка «План Б» есть у утверждений в неопределённости или с опровержением.
    plan_b: PlanBCardOut | None = None
    # Утверждение изменило статус при обновлении исследования.
    changed: bool = False
    # Статус того же утверждения в последнем исследовании того же материала.
    current_status: ClaimStatus | None = None


class FeedArticleOut(BaseModel):
    id: int
    title: str
    url: str
    source: str
    minutes_ago: int


class FeedCheckOut(BaseModel):
    research_id: int
    checked_at: str | None = None


class FeedItemOut(BaseModel):
    event_id: int
    title: str
    summary: str
    topic: str
    sources: int
    minutes_ago: int
    articles: list[FeedArticleOut]
    # Событие уже проверялось: переход к готовому исследованию без нового запуска.
    checked: FeedCheckOut | None = None


def parse_questions(raw: str | None) -> list[str] | None:
    if not raw:
        return None
    return json.loads(raw)

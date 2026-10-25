import enum
import datetime as dt

from sqlalchemy import Boolean, DateTime, Enum, ForeignKey, Integer, String, Text, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base


class MaterialKind(str, enum.Enum):
    text = "text"
    link = "link"
    image = "image"
    document = "document"


class ResearchStatus(str, enum.Enum):
    queued = "queued"
    running = "running"
    done = "done"
    failed = "failed"


class StepStatus(str, enum.Enum):
    pending = "pending"
    running = "running"
    done = "done"
    failed = "failed"


# Статусы утверждений из раздела 7 концепции:
# supported — зелёный, insufficient — жёлтый, refuted — красный, out_of_scope — серый.
# Отсутствие подтверждения — это insufficient, а не refuted.
class ClaimStatus(str, enum.Enum):
    supported = "supported"
    insufficient = "insufficient"
    refuted = "refuted"
    out_of_scope = "out_of_scope"


class EvidenceRelation(str, enum.Enum):
    supports = "supports"
    refutes = "refutes"
    limits = "limits"


class ExtSourceKind(str, enum.Enum):
    primary = "primary"
    retelling = "retelling"


class NodeKind(str, enum.Enum):
    material = "material"
    claim = "claim"
    source = "source"
    evidence = "evidence"
    event = "event"
    plan = "plan"


class EdgeKind(str, enum.Enum):
    contains_claim = "contains_claim"
    supports = "supports"
    refutes = "refutes"
    limits = "limits"
    references_source = "references_source"
    same_event = "same_event"
    depends_on_claim = "depends_on_claim"


class PlanTaskStatus(str, enum.Enum):
    open = "open"
    resolved = "resolved"


class Source(Base):
    """Источник новостной ленты."""

    __tablename__ = "source"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(200))
    url: Mapped[str] = mapped_column(String(500))
    rss_url: Mapped[str | None] = mapped_column(String(500))
    enabled: Mapped[bool] = mapped_column(Boolean, default=True)

    articles: Mapped[list["Article"]] = relationship(back_populates="source")


class Event(Base):
    """Событие — кластер публикаций об одном факте."""

    __tablename__ = "event"

    id: Mapped[int] = mapped_column(primary_key=True)
    title: Mapped[str] = mapped_column(String(500))
    summary: Mapped[str | None] = mapped_column(Text)
    updated_at: Mapped[dt.datetime] = mapped_column(DateTime, server_default=func.now())

    articles: Mapped[list["Article"]] = relationship(back_populates="event")


class Article(Base):
    """Публикация из ленты."""

    __tablename__ = "article"

    id: Mapped[int] = mapped_column(primary_key=True)
    source_id: Mapped[int] = mapped_column(ForeignKey("source.id"))
    event_id: Mapped[int | None] = mapped_column(ForeignKey("event.id"))
    url: Mapped[str] = mapped_column(String(500))
    title: Mapped[str] = mapped_column(String(500))
    content: Mapped[str | None] = mapped_column(Text)
    content_hash: Mapped[str | None] = mapped_column(String(64), index=True)
    published_at: Mapped[dt.datetime | None] = mapped_column(DateTime)

    source: Mapped["Source"] = relationship(back_populates="articles")
    event: Mapped["Event | None"] = relationship(back_populates="articles")
    researches: Mapped[list["Research"]] = relationship(back_populates="article")


class Material(Base):
    """Загруженный пользователем материал для проверки."""

    __tablename__ = "material"

    id: Mapped[int] = mapped_column(primary_key=True)
    kind: Mapped[MaterialKind] = mapped_column(Enum(MaterialKind))
    raw_content: Mapped[str | None] = mapped_column(Text)
    file_path: Mapped[str | None] = mapped_column(String(500))
    extracted_text: Mapped[str | None] = mapped_column(Text)
    meta: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, server_default=func.now())

    researches: Mapped[list["Research"]] = relationship(back_populates="material")


class Research(Base):
    """Исследование (фактчекинг). Ошибка API — это failed, а не результат."""

    __tablename__ = "research"

    id: Mapped[int] = mapped_column(primary_key=True)
    material_id: Mapped[int | None] = mapped_column(ForeignKey("material.id"))
    article_id: Mapped[int | None] = mapped_column(ForeignKey("article.id"))
    status: Mapped[ResearchStatus] = mapped_column(Enum(ResearchStatus), default=ResearchStatus.queued)
    context: Mapped[str | None] = mapped_column(Text)
    summary: Mapped[str | None] = mapped_column(Text)
    error: Mapped[str | None] = mapped_column(Text)
    # Идемпотентность запуска: повторный тап в клиенте не создаёт вторую задачу.
    client_task_id: Mapped[str | None] = mapped_column(String(100), unique=True, index=True)
    created_at: Mapped[dt.datetime] = mapped_column(DateTime, server_default=func.now())
    finished_at: Mapped[dt.datetime | None] = mapped_column(DateTime)

    material: Mapped["Material | None"] = relationship(back_populates="researches")
    article: Mapped["Article | None"] = relationship(back_populates="researches")
    steps: Mapped[list["ResearchStep"]] = relationship(back_populates="research", cascade="all, delete-orphan")
    claims: Mapped[list["Claim"]] = relationship(back_populates="research", cascade="all, delete-orphan")
    ext_sources: Mapped[list["ExtSource"]] = relationship(back_populates="research", cascade="all, delete-orphan")
    nodes: Mapped[list["Node"]] = relationship(back_populates="research", cascade="all, delete-orphan")


class ResearchStep(Base):
    """Этап пайплайна (1–6) с прогрессом для клиента."""

    __tablename__ = "research_step"

    id: Mapped[int] = mapped_column(primary_key=True)
    research_id: Mapped[int] = mapped_column(ForeignKey("research.id"))
    stage: Mapped[int] = mapped_column(Integer)
    status: Mapped[StepStatus] = mapped_column(Enum(StepStatus), default=StepStatus.pending)
    detail: Mapped[str | None] = mapped_column(Text)
    error: Mapped[str | None] = mapped_column(Text)

    research: Mapped["Research"] = relationship(back_populates="steps")


class Claim(Base):
    """Проверяемое утверждение. Статус относится к утверждению, не к материалу."""

    __tablename__ = "claim"

    id: Mapped[int] = mapped_column(primary_key=True)
    research_id: Mapped[int] = mapped_column(ForeignKey("research.id"))
    text: Mapped[str] = mapped_column(Text)
    status: Mapped[ClaimStatus] = mapped_column(Enum(ClaimStatus), default=ClaimStatus.out_of_scope)
    explanation: Mapped[str | None] = mapped_column(Text)
    # Вопросы этапа 2 пайплайна (JSON-список строк).
    questions: Mapped[str | None] = mapped_column(Text)

    research: Mapped["Research"] = relationship(back_populates="claims")
    evidences: Mapped[list["Evidence"]] = relationship(back_populates="claim", cascade="all, delete-orphan")
    plan_tasks: Mapped[list["PlanTask"]] = relationship(back_populates="claim")
    plan_b_cards: Mapped[list["PlanBCard"]] = relationship(back_populates="claim", cascade="all, delete-orphan")


class ExtSource(Base):
    """Внешний источник, найденный при исследовании."""

    __tablename__ = "ext_source"

    id: Mapped[int] = mapped_column(primary_key=True)
    research_id: Mapped[int] = mapped_column(ForeignKey("research.id"))
    url: Mapped[str] = mapped_column(String(500))
    title: Mapped[str | None] = mapped_column(String(500))
    kind: Mapped[ExtSourceKind] = mapped_column(Enum(ExtSourceKind), default=ExtSourceKind.retelling)
    accessible: Mapped[bool] = mapped_column(Boolean, default=True)
    # Текст источника: без него цитаты доказательств нельзя проверить.
    text: Mapped[str | None] = mapped_column(Text)

    research: Mapped["Research"] = relationship(back_populates="ext_sources")
    evidences: Mapped[list["Evidence"]] = relationship(back_populates="ext_source")


class Evidence(Base):
    """Доказательство: цитата или фрагмент с привязкой к источнику и объяснением связи."""

    __tablename__ = "evidence"

    id: Mapped[int] = mapped_column(primary_key=True)
    claim_id: Mapped[int] = mapped_column(ForeignKey("claim.id"))
    ext_source_id: Mapped[int | None] = mapped_column(ForeignKey("ext_source.id"))
    quote: Mapped[str] = mapped_column(Text)
    relation: Mapped[EvidenceRelation] = mapped_column(Enum(EvidenceRelation))
    explanation: Mapped[str] = mapped_column(Text)

    claim: Mapped["Claim"] = relationship(back_populates="evidences")
    ext_source: Mapped["ExtSource | None"] = relationship(back_populates="evidences")


class Node(Base):
    """Узел графа доказательств; ссылается на сущность по типу."""

    __tablename__ = "node"

    id: Mapped[int] = mapped_column(primary_key=True)
    research_id: Mapped[int] = mapped_column(ForeignKey("research.id"))
    kind: Mapped[NodeKind] = mapped_column(Enum(NodeKind))
    entity_id: Mapped[int | None] = mapped_column(Integer)
    label: Mapped[str | None] = mapped_column(String(500))

    research: Mapped["Research"] = relationship(back_populates="nodes")
    outgoing_edges: Mapped[list["Edge"]] = relationship(
        back_populates="src_node", foreign_keys="Edge.src_node_id", cascade="all, delete-orphan"
    )
    incoming_edges: Mapped[list["Edge"]] = relationship(
        back_populates="dst_node", foreign_keys="Edge.dst_node_id", cascade="all, delete-orphan"
    )


class Edge(Base):
    """Связь графа. Каждая содержательная связь имеет объяснение;
    предполагаемые связи отличаются флагом is_established=False."""

    __tablename__ = "edge"

    id: Mapped[int] = mapped_column(primary_key=True)
    research_id: Mapped[int] = mapped_column(ForeignKey("research.id"))
    src_node_id: Mapped[int] = mapped_column(ForeignKey("node.id"))
    dst_node_id: Mapped[int] = mapped_column(ForeignKey("node.id"))
    kind: Mapped[EdgeKind] = mapped_column(Enum(EdgeKind))
    explanation: Mapped[str] = mapped_column(Text)
    is_established: Mapped[bool] = mapped_column(Boolean, default=False)

    src_node: Mapped["Node"] = relationship(back_populates="outgoing_edges", foreign_keys=[src_node_id])
    dst_node: Mapped["Node"] = relationship(back_populates="incoming_edges", foreign_keys=[dst_node_id])


class PlanTask(Base):
    """Задача пользователя, зависящая от утверждения."""

    __tablename__ = "plan_task"

    id: Mapped[int] = mapped_column(primary_key=True)
    claim_id: Mapped[int] = mapped_column(ForeignKey("claim.id"))
    text: Mapped[str] = mapped_column(Text)
    status: Mapped[PlanTaskStatus] = mapped_column(Enum(PlanTaskStatus), default=PlanTaskStatus.open)

    claim: Mapped["Claim"] = relationship(back_populates="plan_tasks")


class PlanBCard(Base):
    """Карточка «План Б»: действия при неопределённости, предлагаются, но не выполняются автоматически."""

    __tablename__ = "plan_b_card"

    id: Mapped[int] = mapped_column(primary_key=True)
    claim_id: Mapped[int] = mapped_column(ForeignKey("claim.id"))
    act_now: Mapped[str | None] = mapped_column(Text)
    postpone: Mapped[str | None] = mapped_column(Text)
    evidence_needed: Mapped[str | None] = mapped_column(Text)
    alternatives: Mapped[str | None] = mapped_column(Text)

    claim: Mapped["Claim"] = relationship(back_populates="plan_b_cards")

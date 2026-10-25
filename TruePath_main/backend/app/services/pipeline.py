"""Оркестратор исследования. Этапы 1–6 из плана. Ошибка этапа — это failed,
а не результат. Все обращения к внешним сервисам заменены заглушками (по ТЗ)."""

import datetime as dt
import json

from sqlalchemy.orm import Session, sessionmaker

from app.adapters.base import LLMClient, SearchClient, SourceDocument
from app.models import (
    Claim,
    ClaimStatus,
    Edge,
    EdgeKind,
    Evidence,
    EvidenceRelation,
    ExtSource,
    ExtSourceKind,
    Node,
    NodeKind,
    PlanBCard,
    Research,
    ResearchStatus,
    ResearchStep,
    StepStatus,
)

_LABEL_LIMIT = 200


class PipelineError(Exception):
    """Остановка пайплайна с понятным сообщением пользователю."""


class ResearchPipeline:
    STAGE_NAMES = {
        1: "Разбор материала",
        2: "Определение вопросов",
        3: "Поиск оснований",
        4: "Сопоставление",
        5: "Дополнительный поиск",
        6: "Формирование результата",
    }

    def __init__(
        self,
        session_factory: sessionmaker,
        llm: LLMClient,
        search: SearchClient,
        *,
        extra_search_max_queries: int = 3,
    ):
        self._session_factory = session_factory
        self._llm = llm
        self._search = search
        self._extra_search_max_queries = extra_search_max_queries

    def run(self, research_id: int) -> None:
        with self._session_factory() as session:
            research = session.get(Research, research_id)
            if research is None:
                return
            research.status = ResearchStatus.running
            session.commit()
            try:
                self._run_stage(session, research, 1, self._parse_material)
                self._run_stage(session, research, 2, self._form_questions)
                self._run_stage(session, research, 3, self._search_sources)
                self._run_stage(session, research, 4, self._match_evidence)
                self._run_stage(session, research, 5, self._extra_search)
                self._run_stage(session, research, 6, self._build_result)
            except Exception:
                # Статус failed и причина уже записаны в _run_stage.
                return

    def _run_stage(self, session: Session, research: Research, stage: int, stage_fn) -> None:
        step = next(s for s in research.steps if s.stage == stage)
        step.status = StepStatus.running
        session.commit()
        try:
            detail = stage_fn(session, research)
        except Exception as exc:
            step.status = StepStatus.failed
            step.error = str(exc)
            research.status = ResearchStatus.failed
            research.error = f"Этап {stage} ({self.STAGE_NAMES[stage]}): {exc}"
            session.commit()
            raise
        step.status = StepStatus.done
        step.detail = detail
        session.commit()

    # Этап 1: разбор материала и выделение утверждений.
    def _parse_material(self, session: Session, research: Research) -> str:
        text = research.material.extracted_text if research.material else None
        if not text:
            raise PipelineError("У материала нет извлечённого текста")
        claims = self._llm.extract_claims(text)
        if not claims:
            raise PipelineError("Не удалось выделить проверяемые утверждения — уточните предмет проверки")
        for claim_text in claims:
            session.add(Claim(research_id=research.id, text=claim_text))
        session.flush()
        return f"Выделено утверждений: {len(claims)}"

    # Этап 2: вопросы, на которые должен ответить поиск.
    def _form_questions(self, session: Session, research: Research) -> str:
        claims = self._claims(session, research)
        for claim in claims:
            claim.questions = json.dumps(self._llm.form_questions(claim.text), ensure_ascii=False)
        return f"Сформированы вопросы для {len(claims)} утверждений"

    # Этап 3: поиск оснований по вопросам каждого утверждения.
    def _search_sources(self, session: Session, research: Research) -> str:
        claims = self._claims(session, research)
        found_urls: dict[str, SourceDocument] = {}
        for claim in claims:
            queries = _parse_questions(claim.questions) or [claim.text]
            for query in queries:
                for doc in self._search.search(query, limit=3):
                    found_urls.setdefault(doc.url, doc)
        for doc in found_urls.values():
            session.add(
                ExtSource(
                    research_id=research.id,
                    url=doc.url,
                    title=doc.title,
                    kind=doc.kind,
                    text=doc.text,
                )
            )
        session.flush()
        return f"Найдено источников: {len(found_urls)}"

    # Этап 4: сопоставление утверждений с найденными источниками, создание доказательств.
    def _match_evidence(self, session: Session, research: Research) -> str:
        sources = self._sources_for(session, research)
        total = 0
        for claim in self._claims(session, research):
            for source in sources:
                for match in self._llm.match_evidence(claim.text, source.title + ". " + (source.text or "")):
                    session.add(
                        Evidence(
                            claim_id=claim.id,
                            ext_source_id=source.id,
                            quote=match.quote,
                            relation=match.relation,
                            explanation=match.explanation,
                        )
                    )
                    total += 1
        return f"Создано доказательств: {total}"

    # Этап 5: дополнительный поиск по утверждениям без подтверждения, с бюджетом.
    def _extra_search(self, session: Session, research: Research) -> str:
        budget_used = 0
        new_sources = 0
        known_urls = {source.url for source in self._sources_for(session, research)}
        for claim in self._claims(session, research):
            if budget_used >= self._extra_search_max_queries:
                break
            if _has_relation(claim, {EvidenceRelation.supports, EvidenceRelation.refutes}):
                continue
            budget_used += 1
            for doc in self._search.search(claim.text, limit=3):
                if doc.url in known_urls:
                    continue
                known_urls.add(doc.url)
                source = ExtSource(
                    research_id=research.id, url=doc.url, title=doc.title, kind=doc.kind, text=doc.text
                )
                session.add(source)
                session.flush()
                new_sources += 1
                for match in self._llm.match_evidence(claim.text, source.title + ". " + doc.text):
                    session.add(
                        Evidence(
                            claim_id=claim.id,
                            ext_source_id=source.id,
                            quote=match.quote,
                            relation=match.relation,
                            explanation=match.explanation,
                        )
                    )
        return f"Дополнительных запросов: {budget_used}, новых источников: {new_sources}"

    # Этап 6: статусы утверждений, краткий вывод, карточки «План Б», сборка графа.
    def _build_result(self, session: Session, research: Research) -> str:
        claims = self._claims(session, research)
        cards = 0
        for claim in claims:
            claim.status, claim.explanation = _verdict(claim)
            # Карточка «План Б» — только для неопределённости и опровержения:
            # подтверждённому утверждению альтернативный план не нужен (раздел 8 концепции).
            if claim.status in (ClaimStatus.insufficient, ClaimStatus.refuted):
                session.add(_plan_b_card(claim))
                cards += 1
        self._build_graph(session, research)
        research.status = ResearchStatus.done
        research.finished_at = dt.datetime.now()
        research.summary = _summary(claims)
        return f"Статусы назначены для {len(claims)} утверждений, карточек «План Б»: {cards}, граф собран"

    def _build_graph(self, session: Session, research: Research) -> None:
        material_text = research.material.extracted_text if research.material else ""
        material_node = Node(
            research_id=research.id,
            kind=NodeKind.material,
            entity_id=research.material_id,
            label=f"Материал: {_truncate(material_text)}",
        )
        session.add(material_node)
        session.flush()

        claim_nodes: dict[int, Node] = {}
        for claim in self._claims(session, research):
            node = Node(
                research_id=research.id,
                kind=NodeKind.claim,
                entity_id=claim.id,
                label=_truncate(claim.text),
            )
            session.add(node)
            claim_nodes[claim.id] = node
        session.flush()

        for claim_id, claim_node in claim_nodes.items():
            session.add(
                Edge(
                    research_id=research.id,
                    src_node_id=material_node.id,
                    dst_node_id=claim_node.id,
                    kind=EdgeKind.contains_claim,
                    explanation="Утверждение содержится в исходном материале",
                    is_established=True,
                )
            )

        source_nodes: dict[int, Node] = {}
        for source in session.query(ExtSource).filter_by(research_id=research.id):
            node = Node(
                research_id=research.id,
                kind=NodeKind.source,
                entity_id=source.id,
                label=_truncate(source.title or source.url),
            )
            session.add(node)
            source_nodes[source.id] = node
        session.flush()

        for claim_id, claim_node in claim_nodes.items():
            claim = session.get(Claim, claim_id)
            for evidence in claim.evidences:
                evidence_node = Node(
                    research_id=research.id,
                    kind=NodeKind.evidence,
                    entity_id=evidence.id,
                    label=_truncate(evidence.quote),
                )
                session.add(evidence_node)
                session.flush()
                session.add(
                    Edge(
                        research_id=research.id,
                        src_node_id=source_nodes[evidence.ext_source_id].id,
                        dst_node_id=evidence_node.id,
                        kind=EdgeKind.references_source,
                        explanation="Цитата взята из этого источника",
                        is_established=True,
                    )
                )
                session.add(
                    Edge(
                        research_id=research.id,
                        src_node_id=evidence_node.id,
                        dst_node_id=claim_node.id,
                        kind=EdgeKind(evidence.relation.value),
                        explanation=evidence.explanation,
                        is_established=True,
                    )
                )

    def _claims(self, session: Session, research: Research) -> list[Claim]:
        return session.query(Claim).filter_by(research_id=research.id).order_by(Claim.id).all()

    def _sources_for(self, session: Session, research: Research) -> list[ExtSource]:
        return session.query(ExtSource).filter_by(research_id=research.id).order_by(ExtSource.id).all()


def _plan_b_card(claim: Claim) -> PlanBCard:
    """Карточка альтернативных действий. Предлагается пользователю, но не
    выполняется автоматически (требование раздела 8 концепции)."""
    if claim.status == ClaimStatus.refuted:
        return PlanBCard(
            claim_id=claim.id,
            act_now="Не опираться на это утверждение в решениях",
            postpone=f"Перепроверить «{_truncate(claim.text, 80)}» по первичному документу позже",
            evidence_needed="Первоисточник, который прямо подтверждает или опровергает утверждение",
            alternatives="Использовать проверенную формулировку из первоисточника",
        )
    return PlanBCard(
        claim_id=claim.id,
        act_now="Считать утверждение неподтверждённым",
        postpone=f"Дождаться проверки «{_truncate(claim.text, 80)}» по первичным источникам",
        evidence_needed="Официальный документ или заявление с датой и областью изменения",
        alternatives="Действовать по наиболее консервативному сценарию",
    )


def _verdict(claim: Claim) -> tuple[ClaimStatus, str]:
    relations = {evidence.relation for evidence in claim.evidences}
    if EvidenceRelation.refutes in relations:
        refuting = next(e for e in claim.evidences if e.relation == EvidenceRelation.refutes)
        return ClaimStatus.refuted, refuting.explanation
    if EvidenceRelation.limits in relations:
        limiting = next(e for e in claim.evidences if e.relation == EvidenceRelation.limits)
        return (
            ClaimStatus.insufficient,
            f"Утверждение требует уточнения: {limiting.explanation} "
            "Отсутствие полного подтверждения — не опровержение.",
        )
    if EvidenceRelation.supports in relations:
        supporting = next(e for e in claim.evidences if e.relation == EvidenceRelation.supports)
        return ClaimStatus.supported, supporting.explanation
    # Отсутствие подтверждения — это insufficient, а не refuted (раздел 7 концепции).
    return ClaimStatus.insufficient, "Подтверждающих или опровергающих источников не найдено"


def _summary(claims: list[Claim]) -> str:
    counts = {status: 0 for status in ClaimStatus}
    for claim in claims:
        counts[claim.status] += 1
    return (
        f"Проверено утверждений: {len(claims)}. Подтверждено: {counts[ClaimStatus.supported]}, "
        f"недостаточно данных: {counts[ClaimStatus.insufficient]}, "
        f"опровергнуто: {counts[ClaimStatus.refuted]}."
    )


def _has_relation(claim: Claim, relations: set[EvidenceRelation]) -> bool:
    return any(evidence.relation in relations for evidence in claim.evidences)


def _truncate(text: str, limit: int = _LABEL_LIMIT) -> str:
    text = " ".join(text.split())
    return text if len(text) <= limit else text[: limit - 1] + "…"


def _parse_questions(raw: str | None) -> list[str] | None:
    if not raw:
        return None
    return json.loads(raw)

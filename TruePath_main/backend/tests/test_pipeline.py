"""Юнит-тесты этапов 3–6 пайплайна на управляемом поиске (без API)."""

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from app.adapters.base import SourceDocument
from app.adapters.local_search import LocalSearch
from app.adapters.rule_llm import RuleBasedLLM
from app.db import Base
from app.models import (
    ClaimStatus,
    Edge,
    EdgeKind,
    EvidenceRelation,
    ExtSourceKind,
    Material,
    MaterialKind,
    Research,
    ResearchStatus,
    ResearchStep,
    StepStatus,
)
from app.services.pipeline import ResearchPipeline

REFUTING_DOC = SourceDocument(
    url="https://demo.gov/benefits-order",
    title="Распоряжение о мерах поддержки",
    kind=ExtSourceKind.primary,
    text="Отдел выплат подтвердил: льготы не отменены, распоряжение об отмене не подписано.",
)

MATERIAL_TEXT = "Заявление мэрии: льготы отменили с 1 января 2026 года. Парк открыт ежедневно."


@pytest.fixture
def db_session(tmp_path):
    engine = create_engine(f"sqlite:///{tmp_path}/pipeline.db", connect_args={"check_same_thread": False})
    Base.metadata.create_all(engine)
    factory = sessionmaker(bind=engine, autoflush=False, expire_on_commit=False)
    with factory() as session:
        yield session, factory


def _make_research(session) -> Research:
    material = Material(kind=MaterialKind.text, raw_content=MATERIAL_TEXT, extracted_text=MATERIAL_TEXT)
    research = Research(material=material, status=ResearchStatus.queued)
    session.add(research)
    session.flush()
    for stage in range(1, 7):
        session.add(ResearchStep(research_id=research.id, stage=stage))
    session.commit()
    return research


def _run_pipeline(db_session, search, **kwargs) -> Research:
    session, factory = db_session
    research = _make_research(session)
    ResearchPipeline(factory, RuleBasedLLM(), search, **kwargs).run(research.id)
    session.expire_all()
    return session.get(Research, research.id)


def _edges(research) -> list[Edge]:
    edges: dict[int, Edge] = {}
    for node in research.nodes:
        for edge in node.outgoing_edges:
            edges[edge.id] = edge
        for edge in node.incoming_edges:
            edges[edge.id] = edge
    return sorted(edges.values(), key=lambda e: e.id)


def test_full_pipeline_assigns_statuses_and_builds_graph(db_session):
    research = _run_pipeline(db_session, LocalSearch(sources=[REFUTING_DOC]))

    assert research.status == ResearchStatus.done
    assert research.finished_at is not None

    benefits, park = research.claims
    assert "льготы" in benefits.text
    # Прямое противоречие в первоисточнике — красный статус.
    assert benefits.status == ClaimStatus.refuted
    assert any(e.relation == EvidenceRelation.refutes for e in benefits.evidences)
    # Отсутствие найденных источников — insufficient, а не refuted (раздел 7 концепции).
    assert park.status == ClaimStatus.insufficient
    assert park.evidences == []

    assert research.summary == "Проверено утверждений: 2. Подтверждено: 0, недостаточно данных: 1, опровергнуто: 1."

    kinds = [node.kind for node in research.nodes]
    assert kinds.count("material") == 1
    assert kinds.count("claim") == 2
    assert kinds.count("source") == 1
    assert kinds.count("evidence") == 1

    edge_kinds = [edge.kind for edge in _edges(research)]
    assert edge_kinds.count(EdgeKind.contains_claim) == 2
    assert edge_kinds.count(EdgeKind.references_source) == 1
    assert edge_kinds.count(EdgeKind.refutes) == 1
    assert all(edge.explanation for edge in _edges(research))
    assert all(edge.is_established for edge in _edges(research))


def test_stage_details_are_recorded(db_session):
    research = _run_pipeline(db_session, LocalSearch(sources=[REFUTING_DOC]))
    details = {step.stage: (step.status, step.detail) for step in research.steps}
    assert all(status == StepStatus.done for status, _ in details.values())
    assert "Выделено утверждений: 2" in details[1][1]
    assert "Найдено источников: 1" in details[3][1]
    assert "Создано доказательств: 1" in details[4][1]


def test_extra_search_respects_budget(db_session):
    park_only = SourceDocument(
        url="https://demo.gov/park",
        title="Парк закрыт на реконструкцию",
        text="Содержимое не совпадает с утверждениями материала.",
    )
    # Бюджет 0: дополнительный поиск не выполняется.
    research = _run_pipeline(db_session, LocalSearch(sources=[park_only]), extra_search_max_queries=0)
    assert research.status == ResearchStatus.done
    stage5 = next(step for step in research.steps if step.stage == 5)
    assert "Дополнительных запросов: 0" in stage5.detail

    # Оба утверждения без подтверждения — по одному запросу в пределах бюджета.
    research_with_budget = _run_pipeline(db_session, LocalSearch(sources=[park_only]))
    stage5 = next(step for step in research_with_budget.steps if step.stage == 5)
    assert "Дополнительных запросов: 2" in stage5.detail


def test_failed_stage_marks_research_failed_not_done(db_session):
    session, factory = db_session
    material = Material(kind=MaterialKind.text, extracted_text="Привет! Как дела?")
    research = Research(material=material, status=ResearchStatus.queued)
    session.add(research)
    session.flush()
    for stage in range(1, 7):
        session.add(ResearchStep(research_id=research.id, stage=stage))
    session.commit()

    ResearchPipeline(factory, RuleBasedLLM(), LocalSearch(sources=[])).run(research.id)
    session.expire_all()
    failed = session.get(Research, research.id)
    assert failed.status == ResearchStatus.failed
    assert "уточните предмет проверки" in failed.error
    assert failed.summary is None

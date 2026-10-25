from pathlib import Path

from sqlalchemy import create_engine, inspect
from sqlalchemy.orm import sessionmaker

from alembic import command
from alembic.config import Config

from app.db import Base
from app.models import (
    Claim,
    ClaimStatus,
    Edge,
    EdgeKind,
    Evidence,
    EvidenceRelation,
    ExtSource,
    ExtSourceKind,
    Material,
    MaterialKind,
    Node,
    NodeKind,
    Research,
    ResearchStatus,
)


def _make_session(tmp_path):
    engine = create_engine(f"sqlite:///{tmp_path}/models.db", connect_args={"check_same_thread": False})
    Base.metadata.create_all(engine)
    return sessionmaker(bind=engine, expire_on_commit=False)()


def test_research_with_claim_and_evidence_roundtrip(tmp_path):
    session = _make_session(tmp_path)

    material = Material(kind=MaterialKind.text, raw_content="Мероприятие перенесено на воскресенье")
    research = Research(material=material, status=ResearchStatus.done, summary="Перенос касается одного направления")
    session.add(research)
    session.flush()

    ext_source = ExtSource(
        research_id=research.id,
        url="https://example.gov/doc",
        title="Распоряжение №1",
        kind=ExtSourceKind.primary,
    )
    claim = Claim(
        research_id=research.id,
        text="Мероприятие перенесено на воскресенье",
        status=ClaimStatus.refuted,
        explanation="Документ переносит только направление X",
    )
    session.add_all([ext_source, claim])
    session.flush()

    evidence = Evidence(
        claim_id=claim.id,
        ext_source_id=ext_source.id,
        quote="Перенос осуществляется для направления X",
        relation=EvidenceRelation.refutes,
        explanation="Цитата ограничивает область действия переноса",
    )
    session.add(evidence)
    session.commit()

    loaded = session.query(Research).one()
    assert loaded.status == ResearchStatus.done
    assert loaded.material.kind == MaterialKind.text
    assert len(loaded.claims) == 1
    assert loaded.claims[0].status == ClaimStatus.refuted
    assert loaded.claims[0].evidences[0].ext_source.kind == ExtSourceKind.primary


def test_graph_nodes_and_edges_with_explanation(tmp_path):
    session = _make_session(tmp_path)

    research = Research(status=ResearchStatus.done)
    session.add(research)
    session.flush()

    claim = Claim(research_id=research.id, text="Утверждение", status=ClaimStatus.insufficient)
    session.add(claim)
    session.flush()

    node_claim = Node(research_id=research.id, kind=NodeKind.claim, entity_id=claim.id)
    node_source = Node(research_id=research.id, kind=NodeKind.source, label="Первичный документ")
    session.add_all([node_claim, node_source])
    session.flush()

    edge = Edge(
        research_id=research.id,
        src_node_id=node_source.id,
        dst_node_id=node_claim.id,
        kind=EdgeKind.limits,
        explanation="Документ ограничивает область действия утверждения",
        is_established=True,
    )
    session.add(edge)
    session.commit()

    loaded_edge = session.query(Edge).one()
    assert loaded_edge.src_node.kind == NodeKind.source
    assert loaded_edge.dst_node.entity_id == claim.id
    assert loaded_edge.is_established is True


def test_migration_applies_to_clean_db(tmp_path, monkeypatch):
    monkeypatch.chdir(Path(__file__).resolve().parents[1])
    db_path = tmp_path / "migrated.db"
    cfg = Config("alembic.ini")
    cfg.set_main_option("sqlalchemy.url", f"sqlite:///{db_path}")
    command.upgrade(cfg, "head")

    engine = create_engine(f"sqlite:///{db_path}")
    tables = set(inspect(engine).get_table_names())
    expected = {
        "source", "event", "article", "material", "research", "research_step",
        "claim", "ext_source", "evidence", "node", "edge", "plan_task", "plan_b_card",
    }
    assert expected <= tables

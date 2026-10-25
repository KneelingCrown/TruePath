"""Тесты шага 9 «План Б»: карточки в пайплайне, задачи, подсветка изменений статуса."""

import pytest

from app.adapters.base import SourceDocument
from app.adapters.local_search import LocalSearch
from app.models import ExtSourceKind

DEMO_TEXT = "Оргкомитет сообщил: начало фестиваля перенесено на воскресенье 25 октября."

SUPPORT_DOC = SourceDocument(
    url="https://demo.gov/confirmation",
    title="Официальное подтверждение переноса",
    kind=ExtSourceKind.primary,
    text="Организаторы подтвердили: начало фестиваля перенесено на воскресенье 25 октября.",
)

REFUTE_DOC = SourceDocument(
    url="https://demo.gov/refutation",
    title="Опровержение слухов о переносе",
    kind=ExtSourceKind.primary,
    text="Пресс-служба сообщила: начало фестиваля не переносилось, мероприятие пройдёт 24 октября.",
)


class SwitchingSearch(LocalSearch):
    """Первый запуск находит подтверждающий документ, повторный — опровергающий."""

    runs = 0

    def __init__(self):
        SwitchingSearch.runs += 1
        super().__init__(sources=[SUPPORT_DOC if SwitchingSearch.runs == 1 else REFUTE_DOC])


@pytest.fixture
def switching_search(monkeypatch):
    monkeypatch.setattr("app.routers.research.LocalSearch", SwitchingSearch)
    SwitchingSearch.runs = 0


@pytest.fixture
def refute_search(monkeypatch):
    class RefuteOnly(LocalSearch):
        def __init__(self):
            super().__init__(sources=[REFUTE_DOC])

    monkeypatch.setattr("app.routers.research.LocalSearch", RefuteOnly)


def _run_research(client):
    material_id = client.post("/api/v1/materials", json={"kind": "text", "content": DEMO_TEXT}).json()["id"]
    research_id = client.post("/api/v1/research", json={"material_id": material_id}).json()["id"]
    return research_id, client.get(f"/api/v1/research/{research_id}").json()


def test_finished_event_carries_plan_b_text(client, refute_search):
    research_id, detail = _run_research(client)
    # Опровергающий источник → статус refuted → карточка «План Б» создана.
    assert detail["claims"][0]["status"] == "refuted"

    events = client.get(f"/api/v1/research/{research_id}/events").json()["events"]
    finished = next(event for event in events if event["type"] == "finished")
    assert finished["plan_b"].startswith("План Б:")
    assert "утверждение" in finished["plan_b"]


def test_supported_claim_gets_no_card(client, switching_search):
    _research_id, detail = _run_research(client)
    assert detail["claims"][0]["status"] == "supported"
    assert client.get("/api/v1/plans").json() == []


def test_create_task_and_see_it_with_card(client, refute_search):
    research_id, detail = _run_research(client)
    claim = detail["claims"][0]

    created = client.post("/api/v1/plans", json={"claim_id": claim["id"], "text": "Купить билеты заранее"})
    assert created.status_code == 201
    assert created.json()["status"] == "open"

    items = client.get("/api/v1/plans", params={"research_id": research_id}).json()
    assert len(items) == 1
    assert items[0]["claim"]["id"] == claim["id"]
    assert items[0]["plan_b"] is not None
    assert items[0]["plan_b"]["act_now"]
    assert items[0]["changed"] is False


def test_create_task_validates_input(client, switching_search):
    assert client.post("/api/v1/plans", json={"claim_id": 999, "text": "Задача"}).status_code == 404

    _research_id, detail = _run_research(client)
    claim_id = detail["claims"][0]["id"]
    assert client.post("/api/v1/plans", json={"claim_id": claim_id, "text": "   "}).status_code == 422


def test_refresh_changes_status_and_highlights_task(client, switching_search):
    # Приёмка шага 9: связали задачу с утверждением, статус изменился при
    # обновлении исследования — GET /plans показывает зависимость и карточку.
    research_id, detail = _run_research(client)
    claim = detail["claims"][0]
    assert claim["status"] == "supported"

    client.post("/api/v1/plans", json={"claim_id": claim["id"], "text": "Купить билеты заранее"})

    refreshed_id = client.post(f"/api/v1/research/{research_id}/refresh").json()["id"]
    refreshed = client.get(f"/api/v1/research/{refreshed_id}").json()
    assert refreshed["claims"][0]["status"] == "refuted"

    items = client.get("/api/v1/plans", params={"research_id": research_id}).json()
    assert len(items) == 1
    assert items[0]["changed"] is True
    assert items[0]["current_status"] == "refuted"
    # Карточка подставляется из обновлённого исследования: что делать теперь.
    assert items[0]["plan_b"] is not None
    assert "Не опираться" in items[0]["plan_b"]["act_now"]

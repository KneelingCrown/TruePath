"""Тесты истории: поиск, повторное открытие без нового запуска, обновление исследования."""

DEMO_TEXT = (
    "Оргкомитет сообщил: начало фестиваля перенесено на воскресенье 25 октября. "
    "Билеты продаются в кассах."
)


def _run_research(client, content=DEMO_TEXT):
    material = client.post("/api/v1/materials", json={"kind": "text", "content": content}).json()["id"]
    return client.post("/api/v1/research", json={"material_id": material}).json()["id"]


def test_history_lists_completed_research(client):
    research_id = _run_research(client)
    items = client.get("/api/v1/history").json()

    assert len(items) == 1
    assert items[0]["id"] == research_id
    assert items[0]["status"] == "done"
    assert "фестиваля" in items[0]["snippet"]


def test_history_search_filters(client):
    _run_research(client)
    assert len(client.get("/api/v1/history", params={"q": "фестивал"}).json()) == 1
    assert client.get("/api/v1/history", params={"q": "борщ"}).json() == []


def test_reopening_from_history_returns_same_result_without_new_research(client):
    research_id = _run_research(client)
    first = client.get(f"/api/v1/history/{research_id}").json()
    second = client.get(f"/api/v1/history/{research_id}").json()

    assert first == second
    assert first["status"] == "done"
    assert first["summary"]
    assert len(client.get("/api/v1/history").json()) == 1, "повторное открытие не создаёт исследование"


def test_refresh_creates_new_research_and_preserves_old(client):
    old_id = _run_research(client)
    response = client.post(f"/api/v1/research/{old_id}/refresh")
    assert response.status_code == 201
    new_id = response.json()["id"]
    assert new_id != old_id

    items = client.get("/api/v1/history").json()
    assert {item["id"] for item in items} == {old_id, new_id}
    # Прежний результат сохраняется без изменений.
    assert client.get(f"/api/v1/research/{old_id}").json()["status"] == "done"
    assert client.get(f"/api/v1/research/{new_id}").json()["status"] == "done"


def test_refresh_unknown_research_404(client):
    assert client.post("/api/v1/research/999/refresh").status_code == 404

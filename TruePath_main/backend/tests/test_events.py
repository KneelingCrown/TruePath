"""Тесты потока событий для клиента: курсор, стабильные id, семантики событий."""

DEMO_TEXT = (
    "Оргкомитет сообщил: начало фестиваля перенесено на воскресенье 25 октября. "
    "Билеты продаются в кассах."
)


def _run_research(client, content=DEMO_TEXT):
    material = client.post("/api/v1/materials", json={"kind": "text", "content": content}).json()["id"]
    research_id = client.post("/api/v1/research", json={"material_id": material}).json()["id"]
    return research_id


def test_events_cover_full_pipeline_with_stable_ids(client):
    research_id = _run_research(client)
    data = client.get(f"/api/v1/research/{research_id}/events").json()

    assert data["status"] == "done"
    seqs = [event["seq"] for event in data["events"]]
    assert seqs == list(range(1, len(seqs) + 1))
    ids = [event["id"] for event in data["events"]]
    assert len(ids) == len(set(ids)), "id событий должны быть уникальными"

    types = [event["type"] for event in data["events"]]
    # Этапы идут первыми, завершение — последним.
    assert types[:6] == ["stage"] * 6
    assert types[-1] == "finished"
    assert "upsert_node" in types and "upsert_edge" in types
    assert "failed" not in types

    finished = data["events"][-1]
    assert "недостаточно данных" in finished["summary"]

    # Финальный статус утверждения приходит отдельным событием с тем же узлом.
    claim_updates = [e for e in data["events"] if e["type"] == "upsert_node" and e["node"]["kind"] == "claim"]
    assert len(claim_updates) == 2, "первичный upsert и финальный статус"
    assert claim_updates[0]["node"]["ref"] == claim_updates[1]["node"]["ref"]
    assert claim_updates[1]["node"]["status"] == "insufficient"


def test_events_cursor_returns_only_new_events(client):
    research_id = _run_research(client)
    full = client.get(f"/api/v1/research/{research_id}/events").json()

    tail = client.get(f"/api/v1/research/{research_id}/events", params={"after": full["cursor"]}).json()
    assert tail["events"] == []
    assert tail["cursor"] == full["cursor"]

    head = client.get(f"/api/v1/research/{research_id}/events", params={"after": 2}).json()
    assert [event["seq"] for event in head["events"]] == list(range(3, len(full["events"]) + 1))
    assert head["events"] == full["events"][2:], "повторный опрос возвращает те же события"


def test_failed_research_ends_with_failed_event(client):
    research_id = _run_research(client, "Привет! Как дела?")
    data = client.get(f"/api/v1/research/{research_id}/events").json()

    assert data["status"] == "failed"
    assert data["events"][-1]["type"] == "failed"
    assert "уточните предмет проверки" in data["events"][-1]["message"]
    assert all(event["type"] != "finished" for event in data["events"])

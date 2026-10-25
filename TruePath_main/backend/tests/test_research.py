from app.models import Research, ResearchStatus

DEMO_TEXT = (
    "Оргкомитет сообщил: начало фестиваля перенесено на воскресенье 25 октября. "
    "Билеты продаются в кассах."
)


def _create_text_material(client, content):
    response = client.post("/api/v1/materials", json={"kind": "text", "content": content})
    assert response.status_code == 201
    return response.json()["id"]


def test_full_pipeline_reaches_result_with_graph(client):
    material_id = _create_text_material(client, DEMO_TEXT)
    response = client.post("/api/v1/research", json={"material_id": material_id})
    assert response.status_code == 201
    research_id = response.json()["id"]

    detail = client.get(f"/api/v1/research/{research_id}").json()
    assert detail["status"] == "done"
    assert {step["status"] for step in detail["steps"]} == {"done"}

    claim = detail["claims"][0]
    # Первоисточник ограничивает область переноса — жёлтый статус, не красный.
    assert claim["status"] == "insufficient"
    assert claim["explanation"]
    assert claim["evidences"], "доказательства должны ссылаться на источники"
    assert all(evidence["source_url"] for evidence in claim["evidences"])

    result = client.get(f"/api/v1/research/{research_id}/result").json()
    assert result["status"] == "done"
    assert "недостаточно данных: 1" in result["summary"]

    graph = client.get(f"/api/v1/research/{research_id}/graph").json()
    kinds = {node["kind"] for node in graph["nodes"]}
    assert {"material", "claim", "source", "evidence"} <= kinds
    assert graph["edges"], "граф должен содержать связи"
    for edge in graph["edges"]:
        assert edge["explanation"], "каждая содержательная связь имеет объяснение"
    labels = {edge["label"] for edge in graph["edges"]}
    assert "содержит" in labels
    claim_node = next(node for node in graph["nodes"] if node["kind"] == "claim")
    assert claim_node["status"] == "insufficient"


def test_research_unknown_material_404(client):
    response = client.post("/api/v1/research", json={"material_id": 999})
    assert response.status_code == 404


def test_research_rejects_material_without_text(client):
    upload = client.post("/api/v1/materials/file", files={"file": ("shot.png", b"\x89PNG", "image/png")})
    assert upload.status_code == 201
    material_id = upload.json()["id"]

    response = client.post("/api/v1/research", json={"material_id": material_id})
    assert response.status_code == 422
    assert "нет извлечённого текста" in response.json()["detail"]


def test_ambiguous_material_fails_with_clarification_request(client):
    material_id = _create_text_material(client, "Привет! Как дела?")
    response = client.post("/api/v1/research", json={"material_id": material_id})
    research_id = response.json()["id"]

    detail = client.get(f"/api/v1/research/{research_id}").json()
    assert detail["status"] == "failed"
    assert "уточните предмет проверки" in detail["error"]
    assert detail["claims"] == []


def test_repeated_tap_with_same_client_task_id_does_not_create_second_task(client):
    material_id = _create_text_material(client, DEMO_TEXT)
    payload = {"material_id": material_id, "client_task_id": "client-run-1"}

    first = client.post("/api/v1/research", json=payload)
    assert first.status_code == 201
    second = client.post("/api/v1/research", json=payload)
    assert second.status_code == 200
    assert second.json()["id"] == first.json()["id"]

    third = client.post("/api/v1/research", json={"material_id": material_id, "client_task_id": "client-run-2"})
    assert third.status_code == 201
    assert third.json()["id"] != first.json()["id"]


def test_research_get_unknown_404(client):
    assert client.get("/api/v1/research/999").status_code == 404
    assert client.get("/api/v1/research/999/result").status_code == 404
    assert client.get("/api/v1/research/999/graph").status_code == 404
    assert client.get("/api/v1/research/999/events").status_code == 404


def test_stale_running_research_marked_failed_on_next_start(client):
    """Фоновая задача умерла при перезапуске сервера: следующий запуск
    помечает застрявшее исследование failed, а не оставляет его висеть."""
    import datetime as dt

    import app.db as db

    material_id = _create_text_material(client, DEMO_TEXT)
    with db.SessionLocal() as session:
        stale = Research(
            material_id=material_id,
            status=ResearchStatus.running,
            created_at=dt.datetime.now(dt.UTC).replace(tzinfo=None) - dt.timedelta(minutes=30),
        )
        session.add(stale)
        session.commit()
        stale_id = stale.id

    client.post("/api/v1/research", json={"material_id": material_id})

    with db.SessionLocal() as session:
        marked = session.get(Research, stale_id)
        assert marked.status == ResearchStatus.failed
        assert "перезапущен" in marked.error

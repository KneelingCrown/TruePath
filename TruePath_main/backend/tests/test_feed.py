"""Тесты шага 8 «Лента»: кластеризация фикстур, саммари, поиск, отметка проверки."""

from app.services.feed import cluster_indexes, event_summary

TITLES = [
    "Городской фестиваль перенесён на воскресенье",
    "Расписание фестиваля изменилось: старт в воскресенье",
    "Фестиваль стартует на день позже",
    "Центральный парк откроют после реконструкции",
    "Билеты на фестиваль поступили в кассы",
]


def test_cluster_groups_three_articles_about_one_event():
    clusters = cluster_indexes(TITLES)
    # Три публикации об одном событии — один кластер; остальные — отдельные события.
    assert sorted(sorted(cluster) for cluster in clusters) == [[0, 1, 2], [3], [4]]


def test_cluster_is_deterministic():
    assert cluster_indexes(TITLES) == cluster_indexes(TITLES)


def test_summary_mentions_count_and_new_details():
    summary = event_summary([TITLES[0], TITLES[1], TITLES[2]])
    assert TITLES[0] in summary
    assert "Публикаций: 3" in summary
    assert "Новое" in summary

    single = event_summary([TITLES[3]])
    assert "Публикаций: 1" in single
    assert "Новое" not in single


def test_feed_lists_clusters_without_duplicates(client):
    items = client.get("/api/v1/feed").json()
    # Пять публикаций → три события; повторы не дублируются.
    assert len(items) == 3
    assert len({item["title"] for item in items}) == 3

    festival = next(item for item in items if "фестиваль перенесён" in item["title"].lower())
    assert festival["sources"] == 3
    assert len(festival["articles"]) == 3
    assert "Новое" in festival["summary"]
    # Самое свежее — 12 минут назад.
    assert festival["minutes_ago"] <= 15
    assert {article["source"] for article in festival["articles"]} == {
        "Городские новости",
        "Официальный вестник",
        "Афиша города",
    }


def test_feed_search_and_topic_filter(client):
    by_q = client.get("/api/v1/feed", params={"q": "фестивал"}).json()
    assert len(by_q) == 2, "кластер фестиваля и новость о билетах"

    city = client.get("/api/v1/feed", params={"topic": "Город"}).json()
    assert len(city) == 1
    assert "парк" in city[0]["title"].lower()

    assert client.get("/api/v1/feed", params={"topic": "Спорт"}).json() == []


def test_feed_excludes_viewed_events(client):
    all_items = client.get("/api/v1/feed").json()
    first_id = all_items[0]["event_id"]
    rest = client.get("/api/v1/feed", params={"exclude": str(first_id)}).json()
    assert len(rest) == 2
    assert all(item["event_id"] != first_id for item in rest)


def test_research_from_article_marks_event_checked(client):
    feed = client.get("/api/v1/feed").json()
    festival = next(item for item in feed if item["sources"] == 3)
    article_id = festival["articles"][0]["id"]

    started = client.post("/api/v1/research", json={"article_id": article_id})
    assert started.status_code == 201
    research_id = started.json()["id"]
    assert client.get(f"/api/v1/research/{research_id}").json()["status"] == "done"

    feed_after = client.get("/api/v1/feed").json()
    festival_after = next(item for item in feed_after if item["event_id"] == festival["event_id"])
    assert festival_after["checked"] is not None
    assert festival_after["checked"]["research_id"] == research_id


def test_research_source_validation(client):
    assert client.post("/api/v1/research", json={"article_id": 999}).status_code == 404
    assert client.post("/api/v1/research", json={}).status_code == 422
    both = client.post("/api/v1/research", json={"material_id": 1, "article_id": 1})
    assert both.status_code == 422

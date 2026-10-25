from app.adapters.fixtures import FIXTURE_SOURCES
from app.adapters.local_search import LocalSearch
from app.adapters.rule_llm import RuleBasedLLM
from app.models import EvidenceRelation

DEMO_TEXT = (
    "Оргкомитет сообщил: начало фестиваля перенесено на воскресенье 25 октября. "
    "Как добраться до площадки? "
    "Билеты продаются в кассах. "
    "Количество участников выросло до 500 человек."
)


def test_extract_claims_finds_event_and_date_sentences():
    claims = RuleBasedLLM().extract_claims(DEMO_TEXT)
    assert any("перенесено" in claim for claim in claims)
    assert all(not claim.endswith("?") for claim in claims)
    # Число без даты и событийной лексики — не утверждение.
    assert not any("500 человек" in claim and "перенесено" not in claim for claim in claims)


def test_extract_claims_ignores_smalltalk():
    assert RuleBasedLLM().extract_claims("Привет! Как дела?") == []


def test_extract_claims_deduplicates():
    text = "Начало перенесено на воскресенье. Начало перенесено на воскресенье."
    assert len(RuleBasedLLM().extract_claims(text)) == 1


def test_extract_claims_limits_count():
    text = " ".join(f"Событие номер {n} перенесено на {n} октября." for n in range(1, 20))
    assert len(RuleBasedLLM().extract_claims(text)) == 10


def test_form_questions_by_features():
    llm = RuleBasedLLM()
    questions = llm.form_questions("Начало перенесено на воскресенье 25 октября")
    assert any("первичные документы" in question for question in questions)
    assert any("дата" in question for question in questions)
    assert any("объявил" in question for question in questions)

    plain = llm.form_questions("В кассах продаются билеты")
    assert len(plain) == 1


def test_match_evidence_finds_support_and_limits():
    llm = RuleBasedLLM()
    claim = "Начало фестиваля перенесено на воскресенье 25 октября"
    primary = FIXTURE_SOURCES[0]
    matches = llm.match_evidence(claim, primary.title + ". " + primary.text)

    relations = {match.relation for match in matches}
    assert EvidenceRelation.supports in relations
    assert EvidenceRelation.limits in relations
    # Объяснение обязательно и называет связь словами.
    for match in matches:
        assert match.explanation
        assert "утверждение" in match.explanation


def test_match_evidence_detects_refutation():
    llm = RuleBasedLLM()
    claim = "Начало фестиваля перенесено на воскресенье 25 октября"
    refuting_source = (
        "Пресс-служба сообщила: начало фестиваля не переносилось, "
        "мероприятие пройдёт 24 октября по исходному расписанию."
    )
    matches = llm.match_evidence(claim, refuting_source)
    assert any(match.relation == EvidenceRelation.refutes for match in matches)


def test_local_search_finds_fixture_source():
    docs = LocalSearch().search("перенесли ли начало фестиваля на воскресенье")
    assert docs
    assert any(doc.kind.value == "primary" for doc in docs)
    # Первоисточник о переносе (распоряжение) релевантнее пересказа и выходит первым.
    assert docs[0].url.endswith("order-2026-114")
    assert any(doc.kind.value == "retelling" for doc in docs)


def test_local_search_empty_for_irrelevant_query():
    assert LocalSearch().search("рецепт борща со сметаной") == []


def test_local_search_custom_sources_and_limit():
    docs = FIXTURE_SOURCES[:1]
    search = LocalSearch(sources=docs)
    results = search.search("расписание фестиваля", limit=1)
    assert len(results) <= 1

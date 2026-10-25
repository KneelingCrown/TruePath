"""Детерминированная замена LLM: правила и эвристики вместо нейросети (по ТЗ).
Контракт совпадает с будущим GigaChat-адаптером, поэтому пайплайн не изменится."""

import re

from app.adapters.base import EvidenceMatch, LLMClient
from app.adapters.local_search import overlapping_tokens
from app.models import EvidenceRelation

_EVENT_LEXICON = (
    "перенес", "перенос", "отмен", "отлож", "начал", "запуск", "запуст",
    "закрыт", "открыт", "введен", "введён", "запрещ", "разреш", "объяв",
    "состоит", "повышен", "снижен", "измен", "приостанов", "старт",
)
_MONTHS = r"январ|феврал|март|апрел|ма[йя]|июн|июл|август|сентябр|октябр|ноябр|декабр"
_DATE_RE = re.compile(
    rf"\d{{1,2}}\s+({_MONTHS})"
    r"|\d{1,2}[./-]\d{1,2}(?:[./-]\d{2,4})?"
    r"|\b20\d{2}\b"
)
_NUMBER_RE = re.compile(r"\b\d+(?:[.,]\d+)?\b")
_SENTENCE_SPLIT_RE = re.compile(r"(?<=[.!?…])\s+")

_MIN_CLAIM_LEN = 20
_MAX_CLAIMS = 10

# Эвристики этапа 4 (сопоставление).
_MIN_OVERLAP = 2
# Маркеры ограничения области: фрагмент сужает утверждение, не опровергая его.
_LIMIT_MARKERS = ("только", "лишь", "не касается", "кроме", "за исключением", "остаётся без изменений")
# Маркеры прямого противоречия: отрицание того же факта.
_REFUTE_MARKERS = ("не перенос", "не состоялся", "не отмен", "отменён", "опроверг", "не подтверждается")
_RELATION_LABELS = {
    EvidenceRelation.supports: "подтверждает",
    EvidenceRelation.refutes: "противоречит",
    EvidenceRelation.limits: "ограничивает",
}


class RuleBasedLLM(LLMClient):
    def extract_claims(self, text: str) -> list[str]:
        claims: list[str] = []
        for sentence in _SENTENCE_SPLIT_RE.split(text.replace("\n", " ")):
            sentence = sentence.strip()
            if not self._is_claim(sentence):
                continue
            if sentence not in claims:
                claims.append(sentence)
            if len(claims) >= _MAX_CLAIMS:
                break
        return claims

    def form_questions(self, claim: str) -> list[str]:
        questions = [
            f"Какие первичные документы подтверждают или опровергают утверждение: «{claim}»?"
        ]
        features = self._features(claim)
        if features["has_date"]:
            questions.append("Соответствует ли дата в утверждении официальным документам и расписаниям?")
        if features["has_number"]:
            questions.append("Подтверждаются ли указанные в утверждении числа первичными источниками?")
        if features["has_event"]:
            questions.append("Кто и когда официально объявил об изменении и на какую область оно распространяется?")
        return questions

    def match_evidence(self, claim: str, source_text: str) -> list[EvidenceMatch]:
        matches: list[EvidenceMatch] = []
        for sentence in _SENTENCE_SPLIT_RE.split(source_text.replace("\n", " ")):
            sentence = sentence.strip()
            overlap = overlapping_tokens(claim, sentence)
            if len(overlap) < _MIN_OVERLAP:
                continue
            relation = self._classify(sentence)
            matches.append(
                EvidenceMatch(
                    quote=sentence,
                    relation=relation,
                    explanation=(
                        f"Фрагмент { _RELATION_LABELS[relation] } утверждение: "
                        f"совпадают ключевые слова ({', '.join(sorted(overlap))})."
                    ),
                )
            )
        return matches

    def _classify(self, sentence: str) -> EvidenceRelation:
        lowered = sentence.lower()
        if any(marker in lowered for marker in _REFUTE_MARKERS):
            return EvidenceRelation.refutes
        if any(marker in lowered for marker in _LIMIT_MARKERS):
            return EvidenceRelation.limits
        return EvidenceRelation.supports

    def _is_claim(self, sentence: str) -> bool:
        if len(sentence) < _MIN_CLAIM_LEN or sentence.endswith("?"):
            return False
        features = self._features(sentence)
        # Число само по себе шумно: утверждение требует даты или событийной лексики.
        return features["has_date"] or features["has_event"]

    def _features(self, text: str) -> dict[str, bool]:
        lowered = text.lower()
        return {
            "has_date": bool(_DATE_RE.search(lowered)),
            "has_number": bool(_NUMBER_RE.search(lowered)),
            "has_event": any(word in lowered for word in _EVENT_LEXICON),
        }

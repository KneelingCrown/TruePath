"""Поиск оснований без сети: по локальной фикстурной базе источников (по ТЗ).
Позже заменяется реализацией поверх поискового API с тем же контрактом."""

import os.path
import re

from app.adapters.base import SearchClient, SourceDocument
from app.adapters.fixtures import FIXTURE_SOURCES

_STOPWORDS = {
    "и", "в", "на", "с", "по", "о", "об", "что", "как", "какие", "какой", "какая",
    "ли", "или", "для", "к", "от", "до", "при", "это", "не", "у", "за", "из", "же",
    "то", "бы", "а", "но", "да", "был", "была", "будет", "есть",
}
_MIN_TOKEN_LEN = 3
_PREFIX_MATCH_LEN = 4


def tokens(text: str) -> list[str]:
    return [
        token
        for token in re.findall(r"[а-яёa-z0-9]+", text.lower())
        if len(token) >= _MIN_TOKEN_LEN and token not in _STOPWORDS
    ]


def tokens_match(a: str, b: str) -> bool:
    if a == b:
        return True
    return len(os.path.commonprefix([a, b])) >= _PREFIX_MATCH_LEN


def overlapping_tokens(claim: str, sentence: str) -> set[str]:
    """Ключевые слова, общие для утверждения и предложения источника."""
    claim_tokens = tokens(claim)
    sentence_tokens = tokens(sentence)
    return {
        claim_token
        for claim_token in claim_tokens
        if any(tokens_match(claim_token, sentence_token) for sentence_token in sentence_tokens)
    }


class LocalSearch(SearchClient):
    def __init__(self, sources: list[SourceDocument] | None = None):
        self._sources = list(sources if sources is not None else FIXTURE_SOURCES)
        self._doc_tokens = [set(tokens(doc.title + " " + doc.text)) for doc in self._sources]

    def search(self, query: str, *, limit: int = 5) -> list[SourceDocument]:
        query_tokens = tokens(query)
        scored: list[tuple[int, int, SourceDocument]] = []
        for position, (doc, doc_tokens) in enumerate(zip(self._sources, self._doc_tokens)):
            matched = {
                query_token
                for query_token in query_tokens
                if any(tokens_match(query_token, doc_token) for doc_token in doc_tokens)
            }
            if matched:
                # position сохраняет порядок фикстур при равном score — детерминизм.
                scored.append((len(matched), -position, doc))
        scored.sort(key=lambda item: (item[0], item[1]), reverse=True)
        return [doc for _, _, doc in scored[:limit]]

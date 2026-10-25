"""Адаптеры внешних способностей. По ТЗ текущего этапа внешние API и нейросети
не подключаются — реализации детерминированные; контракты сохранены, чтобы позже
подключить GigaChat и поисковый API без изменения пайплайна."""

from app.adapters.base import EvidenceMatch, LLMClient, SearchClient, SourceDocument
from app.adapters.local_search import LocalSearch, tokens_match
from app.adapters.rule_llm import RuleBasedLLM

__all__ = [
    "EvidenceMatch",
    "LLMClient",
    "SearchClient",
    "SourceDocument",
    "LocalSearch",
    "RuleBasedLLM",
    "tokens_match",
]

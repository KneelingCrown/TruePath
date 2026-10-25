import abc
from dataclasses import dataclass

from app.models import EvidenceRelation, ExtSourceKind


@dataclass
class SourceDocument:
    """Найденный источник: страница или документ с текстом."""

    url: str
    title: str
    text: str
    kind: ExtSourceKind = ExtSourceKind.retelling


@dataclass
class EvidenceMatch:
    """Фрагмент источника, связанный с утверждением, с объяснением связи."""

    quote: str
    relation: EvidenceRelation
    explanation: str


class LLMClient(abc.ABC):
    """Роли LLM в пайплайне: этапы 1, 2 и 4 (сопоставление)."""

    @abc.abstractmethod
    def extract_claims(self, text: str) -> list[str]:
        """Этап 1: выделить проверяемые утверждения."""

    @abc.abstractmethod
    def form_questions(self, claim: str) -> list[str]:
        """Этап 2: сформулировать, что требуется установить по утверждению."""

    @abc.abstractmethod
    def match_evidence(self, claim: str, source_text: str) -> list[EvidenceMatch]:
        """Этап 4: найти во фрагментах источника связи с утверждением."""


class SearchClient(abc.ABC):
    """Этапы 3 и 5: поиск оснований по вопросу."""

    @abc.abstractmethod
    def search(self, query: str, *, limit: int = 5) -> list[SourceDocument]: ...

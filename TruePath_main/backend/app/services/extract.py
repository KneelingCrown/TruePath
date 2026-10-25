"""Извлечение текста из материалов. Внешние API (OCR, LLM) на текущем этапе не
подключаются: недостающие возможности возвращают явную ошибку или предупреждение,
а не выдаются за результат."""

import io
import re
from html import unescape

import httpx
from pypdf import PdfReader


class MaterialFetchError(Exception):
    """Ссылка недоступна или сервер вернул ошибку."""


class PdfExtractError(Exception):
    """Файл не распознан как читаемый PDF."""


def fetch(url: str, *, transport: httpx.BaseTransport | None = None) -> httpx.Response:
    try:
        with httpx.Client(timeout=10.0, follow_redirects=True, transport=transport) as client:
            response = client.get(url)
            response.raise_for_status()
    except httpx.HTTPError as exc:
        raise MaterialFetchError(f"Не удалось скачать страницу по ссылке: {exc}") from exc
    return response


def html_to_text(html: str) -> str:
    without_scripts = re.sub(r"(?is)<(script|style)\b.*?</\1>", " ", html)
    without_tags = re.sub(r"(?s)<[^>]+>", " ", without_scripts)
    return re.sub(r"\s+", " ", unescape(without_tags)).strip()


def pdf_to_text(data: bytes) -> str:
    try:
        reader = PdfReader(io.BytesIO(data))
        pages = [page.extract_text() or "" for page in reader.pages]
    except Exception as exc:
        raise PdfExtractError(f"Не удалось извлечь текст из PDF: {exc}") from exc
    return "\n".join(text for text in pages if text).strip()

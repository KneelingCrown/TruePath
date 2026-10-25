from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import or_
from sqlalchemy.orm import Session

from app.db import get_session
from app.models import Material, Research
from app.routers.research import _detail
from app.schemas import HistoryItemOut, ResearchDetailOut

router = APIRouter(prefix="/history", tags=["history"])

_SNIPPET_LIMIT = 120


@router.get("", response_model=list[HistoryItemOut])
def list_history(q: str | None = None, session: Session = Depends(get_session)) -> list[HistoryItemOut]:
    query = session.query(Research).order_by(Research.id.desc())
    if q:
        # Поиск по теме или тексту: материал, контекст запроса и итог.
        pattern = f"%{q}%"
        query = query.join(Research.material).filter(
            or_(
                Material.extracted_text.ilike(pattern),
                Research.context.ilike(pattern),
                Research.summary.ilike(pattern),
            )
        )
    items: list[HistoryItemOut] = []
    for research in query.all():
        material_text = research.material.extracted_text if research.material else None
        items.append(
            HistoryItemOut(
                id=research.id,
                status=research.status,
                created_at=research.created_at.isoformat() if research.created_at else None,
                summary=research.summary,
                snippet=(material_text or "")[:_SNIPPET_LIMIT] or None,
            )
        )
    return items


@router.get("/{research_id}", response_model=ResearchDetailOut)
def get_history_item(research_id: int, session: Session = Depends(get_session)) -> ResearchDetailOut:
    research = session.get(Research, research_id)
    if research is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Исследование не найдено")
    # Повторное открытие возвращает сохранённый результат без нового запуска.
    return _detail(research)

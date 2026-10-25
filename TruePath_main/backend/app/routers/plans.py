"""Задачи пользователя, зависящие от утверждений, и карточки «План Б».

Никаких автоматических переносов задач: сервер только предлагает действия
и подсвечивает задачи, чьи утверждения изменили статус при обновлении
исследования (требование раздела 8 концепции).
"""

from fastapi import APIRouter, Depends, HTTPException, Query, status
from pydantic import BaseModel
from sqlalchemy.orm import Session

from app.db import get_session
from app.models import Claim, ClaimStatus, PlanTask, PlanTaskStatus, Research, ResearchStatus
from app.schemas import PlanBCardOut, PlanClaimOut, PlanItemOut, PlanTaskOut

router = APIRouter(prefix="/plans", tags=["plans"])


class PlanTaskCreate(BaseModel):
    claim_id: int
    text: str


@router.post("", response_model=PlanTaskOut, status_code=status.HTTP_201_CREATED)
def create_plan_task(data: PlanTaskCreate, session: Session = Depends(get_session)) -> PlanTaskOut:
    claim = session.get(Claim, data.claim_id)
    if claim is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Утверждение не найдено")
    if not data.text.strip():
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Текст задачи не может быть пустым"
        )
    task = PlanTask(claim_id=claim.id, text=data.text.strip(), status=PlanTaskStatus.open)
    session.add(task)
    session.commit()
    return PlanTaskOut(id=task.id, claim_id=task.claim_id, text=task.text, status=task.status)


@router.get("", response_model=list[PlanItemOut])
def list_plans(
    research_id: int | None = Query(default=None),
    session: Session = Depends(get_session),
) -> list[PlanItemOut]:
    query = session.query(PlanTask).order_by(PlanTask.id)
    if research_id is not None:
        query = query.join(Claim).filter(Claim.research_id == research_id)
    return [_to_item(session, task) for task in query.all()]


def _to_item(session: Session, task: PlanTask) -> PlanItemOut:
    claim = task.claim
    twin = _latest_twin(session, claim)
    # Карточка берётся у своего утверждения; если его статус был «supported»
    # (карточка не создаётся) — у того же утверждения в последнем исследовании.
    card = next(iter(claim.plan_b_cards), None) or (next(iter(twin.plan_b_cards), None) if twin else None)
    changed, current_status = _status_changed(session, claim, twin)
    return PlanItemOut(
        id=task.id,
        text=task.text,
        status=task.status,
        claim=PlanClaimOut(id=claim.id, text=claim.text, status=claim.status, explanation=claim.explanation),
        plan_b=PlanBCardOut(
            act_now=card.act_now,
            postpone=card.postpone,
            evidence_needed=card.evidence_needed,
            alternatives=card.alternatives,
        )
        if card
        else None,
        changed=changed,
        current_status=current_status,
    )


def _latest_twin(session: Session, claim: Claim) -> Claim | None:
    """То же утверждение (по тексту) в последнем исследовании того же материала."""
    research = claim.research
    if research.material_id is None:
        return None
    latest = (
        session.query(Research)
        .filter(
            Research.material_id == research.material_id,
            Research.id > research.id,
            Research.status == ResearchStatus.done,
        )
        .order_by(Research.id.desc())
        .first()
    )
    if latest is None:
        return None
    return next((c for c in latest.claims if c.text == claim.text), None)


def _status_changed(session: Session, claim: Claim, twin: Claim | None) -> tuple[bool, ClaimStatus | None]:
    """Задача подсвечивается, если в более позднем исследовании того же материала
    утверждение с тем же текстом получила другой статус."""
    if twin is None or twin.status == claim.status:
        return False, None
    return True, twin.status

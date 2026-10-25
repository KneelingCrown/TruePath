from fastapi import APIRouter, Depends, HTTPException, UploadFile, status
from sqlalchemy.orm import Session

from app.db import get_session
from app.models import Material, MaterialKind
from app.schemas import MaterialCreate, MaterialOut, to_out
from app.services import extract
from app.services.storage import save_upload

router = APIRouter(prefix="/materials", tags=["materials"])

MAX_UPLOAD_SIZE = 10 * 1024 * 1024

_IMAGE_SUFFIXES = (".png", ".jpg", ".jpeg", ".webp", ".gif")

OCR_NOT_CONNECTED = "OCR не подключён: текст не извлечён, материал не отправлять на проверку"


@router.post("", response_model=MaterialOut, status_code=status.HTTP_201_CREATED)
def create_material(data: MaterialCreate, session: Session = Depends(get_session)) -> MaterialOut:
    material = Material(kind=data.kind)
    if data.kind == MaterialKind.text:
        material.raw_content = data.content
        material.extracted_text = data.content
    elif data.kind == MaterialKind.link:
        material.raw_content = str(data.url)
        try:
            response = extract.fetch(str(data.url))
        except extract.MaterialFetchError as exc:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exc))
        material.extracted_text = _text_from_response(response)
    session.add(material)
    session.commit()
    return to_out(material)


@router.post("/file", response_model=MaterialOut, status_code=status.HTTP_201_CREATED)
async def upload_material(file: UploadFile, session: Session = Depends(get_session)) -> MaterialOut:
    kind = _infer_kind(file)
    data = await file.read()
    if len(data) > MAX_UPLOAD_SIZE:
        raise HTTPException(status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE, detail="Файл больше 10 МБ")

    material = Material(kind=kind)
    session.add(material)
    session.flush()
    material.file_path = save_upload(data, file.filename, material.id)

    warning: str | None = None
    if kind == MaterialKind.image:
        warning = OCR_NOT_CONNECTED
    elif kind == MaterialKind.document:
        try:
            text = extract.pdf_to_text(data)
        except extract.PdfExtractError as exc:
            warning = str(exc)
        else:
            if text:
                material.extracted_text = text
            else:
                warning = "В PDF не найден извлекаемый текст"
    elif kind == MaterialKind.text:
        material.extracted_text = data.decode("utf-8", errors="replace")

    session.commit()
    return to_out(material, warning)


def _text_from_response(response) -> str | None:
    content_type = response.headers.get("content-type", "")
    if "html" in content_type:
        return extract.html_to_text(response.text) or None
    if content_type.startswith("application/pdf"):
        try:
            return extract.pdf_to_text(response.content) or None
        except extract.PdfExtractError:
            return None
    return response.text.strip() or None


def _infer_kind(file: UploadFile) -> MaterialKind:
    content_type = file.content_type or ""
    name = (file.filename or "").lower()
    if content_type == "application/pdf" or name.endswith(".pdf"):
        return MaterialKind.document
    if content_type.startswith("image/") or name.endswith(_IMAGE_SUFFIXES):
        return MaterialKind.image
    if content_type == "text/plain" or name.endswith(".txt"):
        return MaterialKind.text
    raise HTTPException(
        status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
        detail="Поддерживаются PDF, изображения и текстовые файлы",
    )

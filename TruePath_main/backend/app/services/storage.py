from pathlib import Path

from app.config import settings


def save_upload(data: bytes, filename: str | None, material_id: int) -> str:
    upload_dir = Path(settings.uploads_dir)
    upload_dir.mkdir(parents=True, exist_ok=True)
    path = upload_dir / f"material_{material_id}_{_safe_filename(filename)}"
    path.write_bytes(data)
    return str(path)


def _safe_filename(filename: str | None) -> str:
    base = Path(filename or "file").name
    cleaned = "".join(c if c.isalnum() or c in "._-" else "_" for c in base)
    return cleaned or "file"

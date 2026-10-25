import httpx
import pytest

from app.services import extract

TEXT_URL = "/api/v1/materials"
FILE_URL = "/api/v1/materials/file"


def _minimal_pdf(text: str) -> bytes:
    stream = f"BT /F1 12 Tf 72 720 Td ({text}) Tj ET".encode()
    objs = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        b"<< /Length " + str(len(stream)).encode() + b" >>\nstream\n" + stream + b"\nendstream",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    ]
    out = b"%PDF-1.4\n"
    offsets = []
    for i, body in enumerate(objs, start=1):
        offsets.append(len(out))
        out += f"{i} 0 obj\n".encode() + body + b"\nendobj\n"
    xref_pos = len(out)
    out += f"xref\n0 {len(objs)+1}\n".encode() + b"0000000000 65535 f \n"
    for off in offsets:
        out += f"{off:010d} 00000 n \n".encode()
    out += f"trailer\n<< /Size {len(objs)+1} /Root 1 0 R >>\nstartxref\n{xref_pos}\n%%EOF".encode()
    return out


def test_create_text_material(client):
    response = client.post(TEXT_URL, json={"kind": "text", "content": "Мероприятие перенесено на воскресенье"})
    assert response.status_code == 201
    body = response.json()
    assert body["kind"] == "text"
    assert body["has_extracted_text"] is True
    assert body["warning"] is None


def test_create_text_material_requires_content(client):
    response = client.post(TEXT_URL, json={"kind": "text", "content": "   "})
    assert response.status_code == 422


def test_create_link_material_success(client, monkeypatch):
    monkeypatch.setattr(
        "app.routers.materials.extract.fetch",
        lambda url, transport=None: httpx.Response(
            200, headers={"content-type": "text/html"}, text="<html><script>x()</script><p>Перенос подтверждён</p></html>"
        ),
    )
    response = client.post(TEXT_URL, json={"kind": "link", "url": "https://example.com/news"})
    assert response.status_code == 201
    assert response.json()["has_extracted_text"] is True


def test_create_link_material_unavailable_url(client):
    # Порт 1 на localhost закрыт — реальная ошибка соединения без внешней сети.
    response = client.post(TEXT_URL, json={"kind": "link", "url": "http://127.0.0.1:1/page"})
    assert response.status_code == 502
    assert "Не удалось скачать страницу" in response.json()["detail"]


def test_fetch_url_with_mock_transport():
    handler = lambda request: httpx.Response(200, text="<html><style>b{}</style><b>Привет мир</b></html>")
    response = extract.fetch("http://test/example", transport=httpx.MockTransport(handler))
    assert extract.html_to_text(response.text) == "Привет мир"


def test_fetch_url_error_on_http_status():
    handler = lambda request: httpx.Response(404)
    with pytest.raises(extract.MaterialFetchError):
        extract.fetch("http://test/missing", transport=httpx.MockTransport(handler))


def test_upload_txt_file(client):
    response = client.post(FILE_URL, files={"file": ("note.txt", "Текст заметки".encode("utf-8"), "text/plain")})
    assert response.status_code == 201
    body = response.json()
    assert body["kind"] == "text"
    assert body["has_extracted_text"] is True


def test_upload_pdf_extracts_text(client):
    response = client.post(FILE_URL, files={"file": ("doc.pdf", _minimal_pdf("Hello PDF test"), "application/pdf")})
    assert response.status_code == 201
    body = response.json()
    assert body["kind"] == "document"
    assert body["has_extracted_text"] is True
    assert body["warning"] is None


def test_upload_broken_pdf_warns_instead_of_failing(client):
    response = client.post(FILE_URL, files={"file": ("bad.pdf", b"not a pdf", "application/pdf")})
    assert response.status_code == 201
    body = response.json()
    assert body["has_extracted_text"] is False
    assert "Не удалось извлечь текст из PDF" in body["warning"]


def test_upload_image_warns_ocr_not_connected(client):
    response = client.post(FILE_URL, files={"file": ("shot.png", b"\x89PNG fake", "image/png")})
    assert response.status_code == 201
    body = response.json()
    assert body["kind"] == "image"
    assert body["has_extracted_text"] is False
    assert "OCR не подключён" in body["warning"]


def test_upload_rejects_unsupported_type(client):
    response = client.post(FILE_URL, files={"file": ("prog.exe", b"MZ", "application/octet-stream")})
    assert response.status_code == 422


def test_upload_rejects_oversized_file(client):
    big = b"x" * (10 * 1024 * 1024 + 1)
    response = client.post(FILE_URL, files={"file": ("big.txt", big, "text/plain")})
    assert response.status_code == 413

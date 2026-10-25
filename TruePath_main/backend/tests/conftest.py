import pytest
from fastapi.testclient import TestClient

import app.db as db
from app.config import settings
from app.db import Base, get_session
from app.main import create_app


@pytest.fixture
def client(tmp_path, monkeypatch):
    url = f"sqlite:///{tmp_path}/test.db"
    monkeypatch.setenv("DATABASE_URL", url)
    monkeypatch.setattr(settings, "uploads_dir", str(tmp_path / "uploads"))

    db.engine = db.create_engine(url, connect_args={"check_same_thread": False})
    db.SessionLocal = db.sessionmaker(bind=db.engine, autoflush=False, expire_on_commit=False)
    Base.metadata.create_all(db.engine)

    app = create_app()

    def override():
        session = db.SessionLocal()
        try:
            yield session
        finally:
            session.close()

    app.dependency_overrides[get_session] = override
    with TestClient(app) as c:
        yield c

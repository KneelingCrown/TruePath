from fastapi import FastAPI

from app.config import settings
from app.routers import feed, health, history, materials, plans, research


def create_app() -> FastAPI:
    app = FastAPI(title=settings.app_name)
    app.include_router(health.router, prefix="/api/v1")
    app.include_router(materials.router, prefix="/api/v1")
    app.include_router(research.router, prefix="/api/v1")
    app.include_router(history.router, prefix="/api/v1")
    app.include_router(plans.router, prefix="/api/v1")
    app.include_router(feed.router, prefix="/api/v1")
    return app


app = create_app()

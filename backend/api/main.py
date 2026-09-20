import base64
import logging
import uuid
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware

from api.catalog import get_wine, search_wines
from api.contracts import (
    ScanAcceptedResponse,
    ScanRequest,
    ScanStatusResponse,
    SearchResponse,
    SommelierChatRequest,
    SommelierChatResponse,
    WineDetailResponse,
)
from api.infra import init_infra, publish_scan, upload_scan_image
from api.scans import create_pending, get_job, mark_failed, to_status_response
from api.sommelier import reply

log = logging.getLogger("api")

TAGS = [
    {"name": "health", "description": "Проверка, что API поднялся (воркер и Docker healthcheck)."},
    {"name": "wines", "description": "Скан этикетки, текстовый поиск и карточка. Контракт Android ApiService."},
    {"name": "sommelier", "description": "Цифровой сомелье после поиска (retention)."},
]


@asynccontextmanager
async def lifespan(_app: FastAPI):
    init_infra()
    yield


app = FastAPI(
    title="Своё вино API",
    description=(
        "Backend для Android-сканера этикеток. JSON в camelCase, как в `ApiDtos.kt`. "
        "Интерактивная документация: `/docs` (Swagger UI), `/redoc`, схема `/openapi.json`."
    ),
    version="1.0.0",
    openapi_tags=TAGS,
    docs_url="/docs",
    redoc_url="/redoc",
    openapi_url="/openapi.json",
    servers=[
        {"url": "http://localhost:3000", "description": "Docker DEV (хост → контейнер :8000)"},
        {"url": "http://10.0.2.2:3000", "description": "Android-эмулятор → хост"},
    ],
    lifespan=lifespan,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get(
    "/health",
    tags=["health"],
    summary="Готовность сервиса",
    response_description="API принимает запросы",
)
def health() -> dict[str, str]:
    return {"status": "ready", "service": "api"}


@app.post(
    "/v1/wines/scan",
    tags=["wines"],
    summary="Поставить скан этикетки в очередь",
    response_model=ScanAcceptedResponse,
    response_description="scanId для опроса GET /v1/wines/scan/{scanId}",
)
def scan_label(body: ScanRequest) -> ScanAcceptedResponse:
    try:
        image = _decode_image(body.imageBase64)
    except Exception:
        return ScanAcceptedResponse(
            success=False,
            scanId=None,
            status="failed",
            error="Некорректное изображение",
        )

    scan_id = str(uuid.uuid4())
    try:
        image_key = upload_scan_image(scan_id, image)
    except Exception as exc:  # noqa: BLE001
        log.warning("scan image upload failed: %s", exc)
        image_key = None

    job = create_pending(body.includeAlternatives, image_key=image_key, scan_id=scan_id)
    try:
        publish_scan(
            {
                "scanId": job.scan_id,
                "imageKey": job.image_key,
                "includeAlternatives": job.include_alternatives,
            }
        )
    except Exception as exc:  # noqa: BLE001
        log.warning("scan queue publish failed: %s", exc)
        mark_failed(job.scan_id, "Не удалось поставить задачу в очередь")
        return ScanAcceptedResponse(
            success=False,
            scanId=job.scan_id,
            status="failed",
            error="Не удалось поставить задачу в очередь",
        )
    return ScanAcceptedResponse(
        success=True,
        scanId=job.scan_id,
        status="pending",
        error=None,
    )


@app.get(
    "/v1/wines/scan/{scan_id}",
    tags=["wines"],
    summary="Результат сканирования (polling)",
    response_model=ScanStatusResponse,
)
def scan_status(scan_id: str) -> ScanStatusResponse:
    job = get_job(scan_id)
    if job is None:
        raise HTTPException(
            status_code=404,
            detail={"success": False, "error": "Сканирование не найдено"},
        )
    return to_status_response(job)


@app.get(
    "/v1/wines/search",
    tags=["wines"],
    summary="Поиск вин по строке",
    response_model=SearchResponse,
)
def wines_search(
    q: str = Query(default="", description="Подстрока: название, регион, сорт, винодельня"),
    page: int = Query(default=1, ge=1, description="Страница, нумерация с 1"),
    per_page: int = Query(default=20, ge=1, le=100, description="Размер страницы"),
) -> SearchResponse:
    wines, total, has_more = search_wines(q, page, per_page)
    return SearchResponse(wines=wines, totalCount=total, page=page, hasMore=has_more)


@app.get(
    "/v1/wines/{wine_id}",
    tags=["wines"],
    summary="Карточка вина",
    response_model=WineDetailResponse,
)
def wine_detail(wine_id: str) -> WineDetailResponse:
    return WineDetailResponse(wine=get_wine(wine_id))


@app.post(
    "/v1/sommelier/chat",
    tags=["sommelier"],
    summary="Сообщение цифровому сомелье",
    response_model=SommelierChatResponse,
)
def sommelier_chat(body: SommelierChatRequest) -> SommelierChatResponse:
    if not body.messages:
        return SommelierChatResponse(success=False, message=None, error="Пустой диалог")
    content = reply(body.messages, body.wineContext)
    return SommelierChatResponse(
        success=True,
        message={"role": "assistant", "content": content},
        error=None,
    )


def _decode_image(image_base64: str) -> bytes:
    payload = image_base64.strip()
    if not payload:
        raise ValueError("empty")
    if "," in payload and payload.lower().startswith("data:"):
        payload = payload.split(",", 1)[1]
    data = base64.b64decode(payload, validate=False)
    if not data:
        raise ValueError("empty")
    return data

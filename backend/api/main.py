import base64
import logging
import uuid
import os
import time
import asyncio
from io import BytesIO
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Query, File, UploadFile
from fastapi.responses import FileResponse
from PIL import Image
from fastapi.middleware.cors import CORSMiddleware

from api.catalog import get_wine, search_wines, image_path, catalog_data
from api.contracts import (
    ScanAcceptedResponse,
    ScanConfirmationRequest,
    ScanRequest,
    ScanStatusResponse,
    SearchResponse,
    SommelierChatRequest,
    SommelierChatResponse,
    WineDetailResponse,
)
from api.infra import init_infra, publish_scan, upload_scan_image, recognition_ready
from api.scans import create_pending, get_job, mark_failed, to_status_response, confirm_job
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
    if _catalog_import_on_start():
        asyncio.create_task(asyncio.to_thread(_run_startup_catalog_load))
    yield


def _catalog_import_on_start() -> bool:
    return os.environ.get("IMPORT_CATALOG_ON_START", "0").strip().lower() in {"1", "true", "yes"}


def _run_startup_catalog_load() -> None:
    from api.import_vino_svoe import startup_catalog_load

    try:
        stats = startup_catalog_load()
        log.info("startup catalog load: %s", stats)
    except Exception as exc:  # noqa: BLE001
        log.warning("startup catalog load failed: %s", exc)


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
        {"url": "/", "description": "Текущий сервер API"},
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


@app.get("/ready", tags=["health"])
def ready() -> dict:
    if not recognition_ready():
        raise HTTPException(503, "Recognizer is not ready or catalogue versions differ")
    return {"status":"ready", "catalogCount":len(catalog_data()[0]), "catalogSha256":catalog_data()[1]}


MAX_IMAGE_BYTES = 30*1024*1024


def validate_image(data: bytes) -> None:
    if not data or len(data) > MAX_IMAGE_BYTES:
        raise ValueError("Empty image or upload exceeds 30 MiB")
    with Image.open(BytesIO(data)) as image:
        if image.width*image.height > 25_000_000 or getattr(image, "n_frames", 1) != 1:
            raise ValueError("Expected a single image up to 25 megapixels")
        image.verify()


def submit_scan(image: bytes, include_alternatives: bool, *, apply_catalog_refusal: bool = True,
                use_reviewed_sweetness: bool = True) -> ScanAcceptedResponse:
    scan_id = str(uuid.uuid4())
    try:
        image_key = upload_scan_image(scan_id, image)
    except Exception as exc:
        log.exception("scan image upload failed")
        raise HTTPException(503, "Image storage unavailable") from exc
    job = create_pending(include_alternatives, image_key=image_key, scan_id=scan_id)
    try:
        publish_scan({"scanId":job.scan_id, "imageKey":job.image_key, "includeAlternatives":include_alternatives,
                      "applyCatalogRefusal":apply_catalog_refusal, "useReviewedSweetness":use_reviewed_sweetness})
    except Exception as exc:
        mark_failed(job.scan_id, "Не удалось поставить задачу в очередь")
        raise HTTPException(503, "Queue unavailable") from exc
    return ScanAcceptedResponse(success=True, scanId=job.scan_id, status="pending")


@app.post("/v1/eval/predict", tags=["wines"], summary="Синхронный Top-1 для participant_test.sh")
def eval_predict(image: UploadFile = File(...)) -> dict[str, str]:
    deadline = time.monotonic()+float(os.environ.get("EVAL_WAIT_SECONDS", "9"))
    if not recognition_ready():
        raise HTTPException(503, "Wait for GET /ready before evaluation")
    data = image.file.read(MAX_IMAGE_BYTES+1)
    try:
        validate_image(data)
    except Exception as exc:
        raise HTTPException(422, "Invalid image") from exc
    # Evaluation uses reviewed sugar to resolve wine variants but never rejects
    # retrieval candidates. Unknown-wine refusal remains a separate mobile policy.
    accepted = submit_scan(data, False, apply_catalog_refusal=False, use_reviewed_sweetness=True)
    while time.monotonic() < deadline:
        job = get_job(accepted.scanId)
        if job and job.status == "done":
            # Retain catalogue/contract validation without loading recommendations:
            # their availability must not block an already recognized evaluation slug.
            result = to_status_response(job, include_recommendations=False)
            return {"slug":result.slug or "unknown"}
        if job and job.status == "failed":
            raise HTTPException(500, "Recognition failed")
        time.sleep(.1)
    raise HTTPException(504, "Recognition timed out; check GPU availability and queue load")


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
        validate_image(image)
    except Exception:
        return ScanAcceptedResponse(
            success=False,
            scanId=None,
            status="failed",
            error="Некорректное изображение",
        )

    return submit_scan(image, body.includeAlternatives)


@app.get(
    "/v1/wines/{wine_id}/image", tags=["wines"], response_class=FileResponse,
)
def wine_image(wine_id: str):
    path = image_path(wine_id)
    if path is None:
        raise HTTPException(404, "Catalogue image unavailable")
    # Minimal container images may lack MIME mappings for WebP. Use the file's
    # actual format so catalogue photos are never served as text/plain.
    with Image.open(path) as photo:
        media_type = Image.MIME.get(photo.format, "application/octet-stream")
    return FileResponse(path, media_type=media_type)


@app.post(
    "/v1/wines/scan/{scan_id}/confirmation",
    tags=["wines"],
    summary="Пользователь подтвердил кандидата кнопкой «Это моё вино»",
    response_model=ScanStatusResponse,
)
def confirm_scan(scan_id: uuid.UUID, body: ScanConfirmationRequest) -> ScanStatusResponse:
    try:
        job = confirm_job(str(scan_id), body.slug)
    except ValueError as exc:
        raise HTTPException(409, str(exc)) from exc
    if job is None:
        raise HTTPException(404, "Scan not found")
    return to_status_response(job)


@app.get(
    "/v1/wines/scan/{scan_id}",
    tags=["wines"],
    summary="Результат сканирования (polling)",
    response_model=ScanStatusResponse,
)
def scan_status(scan_id: uuid.UUID) -> ScanStatusResponse:
    job = get_job(str(scan_id))
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
    if len(payload) > (MAX_IMAGE_BYTES+2)//3*4:
        raise ValueError("Image is too large")
    data = base64.b64decode(payload, validate=True)
    if not data:
        raise ValueError("empty")
    return data

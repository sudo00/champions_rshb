import uuid
from dataclasses import dataclass
from typing import Any

import psycopg

from api.catalog import get_wine, catalog_data
from api.config import DATABASE_URL
from api.contracts import ScanStatusResponse, WineDto

STATUS_PENDING = "pending"
STATUS_PROCESSING = "processing"
STATUS_DONE = "done"
STATUS_FAILED = "failed"


@dataclass
class ScanJob:
    scan_id: str
    status: str
    include_alternatives: bool
    image_key: str | None = None
    result: dict[str, Any] | None = None
    error: str | None = None


def create_pending(
    include_alternatives: bool,
    image_key: str | None,
    scan_id: str | None = None,
) -> ScanJob:
    job = ScanJob(
        scan_id=scan_id or str(uuid.uuid4()),
        status=STATUS_PENDING,
        include_alternatives=include_alternatives,
        image_key=image_key,
    )
    with psycopg.connect(DATABASE_URL) as conn:
        conn.execute(
            """
            INSERT INTO scans (id, status, include_alternatives, image_key)
            VALUES (%s, %s, %s, %s)
            """,
            (job.scan_id, job.status, job.include_alternatives, job.image_key),
        )
        conn.commit()
    return job


def get_job(scan_id: str) -> ScanJob | None:
    with psycopg.connect(DATABASE_URL) as conn:
        row = conn.execute(
            """
            SELECT id, status, include_alternatives, image_key, result, error
            FROM scans WHERE id = %s
            """,
            (scan_id,),
        ).fetchone()
    if row is None:
        return None
    return ScanJob(
        scan_id=str(row[0]),
        status=row[1],
        include_alternatives=row[2],
        image_key=row[3],
        result=row[4],
        error=row[5],
    )


def mark_failed(scan_id: str, error: str) -> None:
    with psycopg.connect(DATABASE_URL) as conn:
        conn.execute(
            """
            UPDATE scans
            SET status = %s, error = %s, updated_at = NOW()
            WHERE id = %s
            """,
            (STATUS_FAILED, error, scan_id),
        )
        conn.commit()


def to_status_response(job: ScanJob) -> ScanStatusResponse:
    wine: WineDto | None = None
    alternatives: list[WineDto] = []
    details = {}
    if job.status == STATUS_DONE:
        if job.result:
            if job.result.get("catalogSha256") != catalog_data()[1]:
                raise RuntimeError("API and recognizer catalogues differ")
            details = {k:v for k,v in job.result.items() if k in ScanStatusResponse.model_fields and k not in ("success", "scanId", "status", "error", "wine", "alternatives", "confidence")}
            candidates = []
            for item in job.result.get("candidates", [])[:5 if job.include_alternatives else 1]:
                card = get_wine(item["slug"])
                if card is None:
                    raise RuntimeError("Recognizer returned a slug missing from catalogue")
                candidates.append({**item, "wine":card.model_dump()})
            details["candidates"] = candidates
            wine = get_wine(candidates[0]["slug"]) if candidates else None
            alternatives = [get_wine(c["slug"]) for c in candidates[1:]]
        else:
            raise RuntimeError("Completed scan has no recognition result")
    return ScanStatusResponse(
        success=True,
        scanId=job.scan_id,
        status=job.status,
        wine=wine,
        confidence=None,
        alternatives=alternatives,
        error=job.error,
        **details,
    )

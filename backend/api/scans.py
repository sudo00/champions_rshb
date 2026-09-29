import uuid
from dataclasses import dataclass
from typing import Any

import psycopg
from psycopg.types.json import Jsonb

from api.catalog import get_wine, catalog_data, result_catalog_compatible
from api.config import DATABASE_URL
from api.contracts import ScanStatusResponse, WineDto
from api.recommendations import recommendation_index

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


def validated_confirmation(job: ScanJob, slug: str) -> dict:
    """Only an actually returned candidate may be confirmed; keep ML evidence intact."""
    result = job.result or {}
    candidates = result.get("candidates", [])[:5 if job.include_alternatives else 1]
    if (job.status != STATUS_DONE or result.get("recognitionStatus") in ("not_in_catalog", "no_target")
            or slug not in {c.get("slug") for c in candidates}):
        raise ValueError("Confirm one of the candidates returned by a completed scan")
    return {"slug": slug, "source": "user"}


def confirm_job(scan_id: str, slug: str) -> ScanJob | None:
    with psycopg.connect(DATABASE_URL) as conn:
        row = conn.execute(
            "SELECT status, include_alternatives, image_key, result, error FROM scans WHERE id = %s FOR UPDATE",
            (scan_id,),
        ).fetchone()
        if row is None:
            return None
        job = ScanJob(scan_id, row[0], row[1], row[2], row[3], row[4])
        confirmation = validated_confirmation(job, slug)
        job.result = {**job.result, "userConfirmation": confirmation}
        conn.execute("UPDATE scans SET result = %s, updated_at = NOW() WHERE id = %s",
                     (Jsonb(job.result), scan_id))
        conn.commit()
        return job


def to_status_response(job: ScanJob, *, include_recommendations: bool = True) -> ScanStatusResponse:
    wine: WineDto | None = None
    alternatives: list[WineDto] = []
    details = {}
    if job.status == STATUS_DONE:
        if job.result:
            if not result_catalog_compatible(job.result.get("catalogSha256")):
                raise RuntimeError("API and recognizer catalogues differ")
            details = {k:v for k,v in job.result.items() if k in ScanStatusResponse.model_fields and k not in ("success", "scanId", "status", "error", "wine", "alternatives", "confidence", "recommendations", "recommendationContext")}
            candidates = []
            for item in job.result.get("candidates", [])[:5 if job.include_alternatives else 1]:
                card = get_wine(item["slug"])
                if card is None:
                    raise RuntimeError("Recognizer returned a slug missing from catalogue")
                candidates.append({**item, "wine":card.model_dump()})
            details["candidates"] = candidates
            wine = get_wine(candidates[0]["slug"]) if candidates else None
            alternatives = [get_wine(c["slug"]) for c in candidates[1:]]
            if include_recommendations and job.result.get("recognitionStatus") in ("not_in_catalog", "candidates_unverified", "confirmed"):
                recommendation_source = job.result
                confirmation = job.result.get("userConfirmation")
                if confirmation:
                    validated_confirmation(job, confirmation.get("slug"))
                    selected = next(c for c in candidates if c["slug"] == confirmation["slug"])
                    recommendation_source = {**job.result, "recognitionStatus": "confirmed",
                                             "candidates": [selected], "observations": []}
                recommended = recommendation_index().recommend(recommendation_source, limit=5 if job.include_alternatives else 1)
                details["recommendationContext"] = {k: v for k, v in recommended.items() if k != "items"}
                details["recommendations"] = []
                for item in recommended["items"]:
                    card = get_wine(item["slug"])
                    if card is None:
                        raise RuntimeError("Recommended slug missing from catalogue")
                    details["recommendations"].append({**item, "wine": card.model_dump()})
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

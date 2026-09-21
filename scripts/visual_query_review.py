"""Manual query review used only after retrieval, never for region selection or ranking."""
from __future__ import annotations

REVIEWS = {
    "query_019c68d0": {
        "source_sha256": "c975b31e13bfa77dbc402d7ae4cd3889609cf85a9dadda45778a63232b4c6acf",
        "status": "absent_from_current_catalog_per_user_review",
        "slug": None,
        "note": "Табия Пино Нуар: пользователь проверил отсутствие позиции в текущем каталоге и на сайте. Ближайший кандидат не является правильным ответом.",
    },
    "query_02eef911": {
        "source_sha256": "8c760f87c8940934bfd19020d90aa949f843267c47a99180bdd41242a5033a36",
        "status": "locally_confirmed_product_match",
        "slug": "massandra-muskatel-belyy-belye-sorta-vinograda-beloe-sladkoe-16",
        "note": "Массандра Мускатель белый: соответствие карточке установлено по этикетке и подтверждено пользователем. Год не учитывается. Это локальная проверка одного примера, не официальный ответ организатора.",
    },
    "query_096ca74e": {
        "source_sha256": "f84ac48acf05212cb213db4540b68a0afdb1a16308797d4fa1d5edeb4df8725e",
        "status": "absent_from_current_catalog_per_user_review",
        "slug": None,
        "note": "Aristov Donum 24: пользователь проверил отсутствие позиции в текущем каталоге и на сайте. Не подменять карточкой Aristov Anima.",
    },
}


def reviewed_query(identifier: str, source_sha256: str, rankings: dict[str, list[dict]]) -> dict | None:
    review = REVIEWS.get(identifier)
    if review is None:
        return None
    if review["source_sha256"] != source_sha256:
        raise ValueError("Reviewed query image changed: " + identifier)
    ranks = {
        mode: next((i + 1 for i, candidate in enumerate(candidates)
                    if candidate["slug"] == review["slug"]), None)
        for mode, candidates in rankings.items()
    } if review["slug"] else {}
    return {**review, "ranks": ranks, "evidence": "docs/CATALOG_QUERY_GAPS.md"}

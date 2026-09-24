from unittest.mock import patch

from api.recommendations import RecommendationIndex, find_values, SWEETNESS
from api.scans import ScanJob, to_status_response
from api.catalog import catalog_data, wine_from_file


def card(slug, color="Белое", sugar="Сухое", grapes="Рислинг", producer="Denisov Winery"):
    return dict(slug=slug, title=slug, category=color, sweetness=sugar, grapes=grapes, winery=producer)


def observed(*texts):
    return {"observations": [dict(text=t, confidence=.99, on_target_bottle=True) for t in texts]}


def test_full_attribute_match_beats_same_producer_with_different_grape():
    cards = [card("producer-only", grapes="Шардоне"), card("other-winery", producer="Фанагория")]
    result = RecommendationIndex({c["slug"]: c for c in cards}).recommend(observed("DENISOV", "белое сухое рислинг"))
    assert result["items"][0]["slug"] == "other-winery"
    assert result["items"][0]["tier"] == "color_sweetness_grapes"
    assert result["items"][1]["unmatchedGrapes"] == ["Рислинг"]


def test_known_color_and_sweetness_never_relaxed_for_producer():
    cards = [card("red", color="Красное"), card("sweet", sugar="Сладкое"), card("unknown", sugar=None),
             card("same", grapes="Шардоне")]
    result = RecommendationIndex({c["slug"]: c for c in cards}).recommend(observed("DENISOV", "white dry riesling"))
    assert [r["slug"] for r in result["items"]] == ["same"]


def test_read_producer_is_fallback_and_anchor_is_not_observed_evidence():
    ix = RecommendationIndex({"a": card("a"), "b": card("b", producer="Фанагория")})
    assert ix.recommend(observed("DENISOV"))["items"][0]["tier"] == "same_producer"
    result = ix.recommend(observed("unreadable"))
    assert result["items"] == [] and result["status"] == "insufficient_evidence"
    result = ix.recommend({**observed("unreadable"), "candidateScoring": {"candidates": [{"slug": "a", "matchScore": .01}]}})
    assert result["anchor"]["slug"] == "a" and result["anchor"]["isRecognizedWine"] is False
    assert result["profile"]["color"]["value"] is None
    assert result["criteriaSources"]["color"] == "retrieval_top1_reference"
    assert [r["slug"] for r in result["items"]] == ["b"]


def test_neighbors_low_confidence_and_ambiguous_colors_cannot_drive_recommendations():
    ix = RecommendationIndex({"a": card("a")})
    assert ix.recommend({"observations": [dict(text="DENISOV white dry", confidence=.99, on_target_bottle=False)]})["items"] == []
    assert ix.recommend({"observations": [dict(text="DENISOV", confidence=.6)]})["items"] == []
    result = ix.recommend(observed("white dry riesling", "red"))
    assert result["status"] == "ambiguous_evidence" and result["items"] == []


def test_sweetness_uses_complete_words_and_reviewed_catalogue_values():
    assert find_values("полусухое semi-dry", SWEETNESS) == ["Полусухое"]
    assert find_values("extra brut", SWEETNESS) == ["Экстра брют"]
    raw = card("wine-bryut-1", sugar=None)
    raw["metadata_review"] = dict(status="confirmed", confirmed_attributes={"sweetness": "Полусухое"})
    ix = RecommendationIndex({"reviewed": raw, "suffix": card("wine-2", sugar=None)})
    assert ix.cards["reviewed"]["sweetness"] == "Полусухое"
    assert ix.cards["reviewed"]["sweetnessSource"] == "reviewed"
    assert ix.cards["suffix"]["sweetness"] is None


def test_grape_aliases_match_and_grape_color_is_not_wine_color():
    ix = RecommendationIndex({"a": card("a", grapes="Пино Гри"), "b": card("b", grapes="Мускат Белый")})
    assert ix.recommend(observed("pinot grigio"))["items"][0]["slug"] == "a"
    profile = ix.observed(observed("Мускат Белый сухое")["observations"])
    assert profile["color"]["value"] is None
    assert profile["grapes"]["value"] == ["Мускат Белый"]


def test_incomplete_profile_and_empty_pool_are_explained():
    ix = RecommendationIndex({"a": card("a", sugar="Сладкое")})
    assert ix.recommend(observed("белое"))["status"] == "insufficient_evidence"
    assert ix.recommend(observed("white dry riesling"))["status"] == "no_suitable_analogs"
    result = ix.recommend(observed("riesling"))
    assert result["items"][0]["unknownQueryFields"] == ["color", "sweetness", "producer"]
    assert "matchScore" not in result["items"][0]


def test_api_keeps_unknown_and_exposes_separate_analog_cards_without_gpu():
    data = dict(slug="unknown", candidates=[], recognitionStatus="not_in_catalog",
                catalogSha256=catalog_data()[1], **observed("белое полусухое"))
    with patch("api.scans.get_wine", side_effect=wine_from_file):
        top5 = to_status_response(ScanJob("id", "done", True, result=data))
        top1 = to_status_response(ScanJob("id", "done", False, result=data))
    assert top5.slug == "unknown" and top5.wine is None and top5.candidates == [] and top5.alternatives == []
    assert len(top5.recommendations) == 5 and top1.recommendations == top5.recommendations[:1]
    assert top5.recommendationContext == top1.recommendationContext
    assert all(c["attributes"]["color"] == "Белое" and c["attributes"]["sweetness"] == "Полусухое" for c in top5.recommendations)
    assert top5.recommendations[0]["wine"]["slug"] == top5.recommendations[0]["slug"]
    with patch("api.scans.recommendation_index") as index:
        for status in ("no_target", "candidates_unverified"):
            body = to_status_response(ScanJob("id", "done", True, result={**data, "recognitionStatus": status}))
            assert body.recommendations == []
        index.assert_not_called()


def test_text_similarity_orders_compatible_wines_and_cannot_override_ocr():
    from types import SimpleNamespace
    semantic = SimpleNamespace(model="test", metadata={}, scores=lambda slug: {"wrong": .99, "near": .9, "far": .7})
    cards = {c["slug"]: c for c in [card("anchor", color="Красное"), card("wrong", color="Красное"),
                                    card("near", producer="Other"), card("far")]}
    ix = RecommendationIndex(cards, semantic=semantic)
    result = ix.recommend({**observed("белое сухое рислинг"), "candidateScoring": {"candidates": [{"slug": "anchor"}]}})
    assert [i["slug"] for i in result["items"]] == ["near", "far"]
    assert result["criteria"]["color"] == "Белое" and result["criteriaSources"]["color"] == "photo_ocr"
    assert result["items"][0]["textSimilarity"] == .9
    assert result["semanticStatus"] == "available"


def test_text_index_rejects_wrong_catalogue_or_corrupt_vectors(tmp_path, monkeypatch):
    import hashlib
    import json
    import struct
    import pytest
    from api.text_neighbors import TextIndex
    metadata = tmp_path / "recommendation_metadata.json"
    metadata.write_text('{"items": {}}')
    monkeypatch.setenv("WINE_CATALOG_PATH", str(tmp_path / "catalog.jsonl"))
    values = [1.] + [0.] * 383 + [0., 1.] + [0.] * 382
    raw = struct.pack("<768f", *values)
    (tmp_path / "vectors.f32").write_bytes(raw)
    manifest = dict(version="wine-text-embeddings-v1", catalog_sha256="catalog", dimensions=384, model="fixture",
                    slugs=["a", "b"], vectors_sha256=hashlib.sha256(raw).hexdigest(),
                    metadata_sha256=hashlib.sha256(metadata.read_bytes()).hexdigest())
    (tmp_path / "manifest.json").write_text(json.dumps(manifest))
    index = TextIndex(tmp_path, {"a": {}, "b": {}}, "catalog")
    assert index.scores("a") == {"a": 1., "b": 0.}
    with pytest.raises(ValueError, match="catalogue mismatch"):
        TextIndex(tmp_path, {"a": {}, "b": {}}, "wrong")
    (tmp_path / "vectors.f32").write_bytes(raw[:-1] + b"1")
    with pytest.raises(ValueError, match="checksum"):
        TextIndex(tmp_path, {"a": {}, "b": {}}, "catalog")

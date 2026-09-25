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
    result = ix.recommend({**observed("unreadable"), "candidates": [{"slug": "a", "matchScore": .01}]})
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
        for status in ("no_target",):
            body = to_status_response(ScanJob("id", "done", True, result={**data, "recognitionStatus": status}))
            assert body.recommendations == []
        index.assert_not_called()


def test_text_similarity_orders_compatible_wines_and_cannot_override_ocr():
    from types import SimpleNamespace
    semantic = SimpleNamespace(model="test", metadata={}, scores=lambda slug: {"wrong": .99, "near": .9, "far": .7})
    cards = {c["slug"]: c for c in [card("anchor", color="Красное"), card("wrong", color="Красное"),
                                    card("near", producer="Other"), card("far")]}
    ix = RecommendationIndex(cards, semantic=semantic)
    result = ix.recommend({**observed("белое сухое рислинг"), "candidates": [{"slug": "anchor"}]})
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


def test_absent_wine_never_uses_rejected_top1_metadata_or_embeddings():
    from types import SimpleNamespace
    from unittest.mock import Mock
    semantic = SimpleNamespace(model="test", metadata={}, scores=Mock(return_value={"white": .99}))
    ix = RecommendationIndex({"red": card("red", color="Красное"), "white": card("white")}, semantic)
    data = dict(recognitionStatus="not_in_catalog", candidates=[],
                candidateScoring={"candidates": [{"slug": "red"}]}, **observed("unreadable"))
    output = ix.recommend(data)
    assert output["items"] == [] and output["anchor"] is None
    assert output["basis"] == "photo_ocr"
    assert all(value == "missing" for value in output["criteriaSources"].values())
    semantic.scores.assert_not_called()
    output = ix.recommend({**data, **observed("white dry riesling")})
    assert [item["slug"] for item in output["items"]] == ["white"]
    assert all(value == "photo_ocr" for key, value in output["criteriaSources"].items() if key != "producer")


def test_unverified_scan_recommends_from_top1_without_confirming_it():
    data = dict(slug="a", recognitionStatus="candidates_unverified", candidates=[{"slug": "a"}],
                catalogSha256=catalog_data()[1], **observed("unreadable"))
    ix = RecommendationIndex({"a": card("a"), "b": card("b")})
    with patch("api.scans.get_wine", side_effect=lambda slug: wine_from_file(next(iter(catalog_data()[0])))), \
            patch("api.scans.recommendation_index", return_value=ix):
        body = to_status_response(ScanJob("id", "done", True, result=data))
    assert body.recognitionStatus == "candidates_unverified"
    assert body.userConfirmation is None
    assert body.recommendationContext["basis"] == "retrieval_top1"
    assert body.recommendationContext["anchor"]["isRecognizedWine"] is False


def test_user_confirmation_anchors_selected_candidate_without_reranking():
    from api.scans import validated_confirmation
    slugs = list(catalog_data()[0])[:2]
    data = dict(slug=slugs[0], recognitionStatus="candidates_unverified",
                candidates=[{"slug": slug, "rank": i + 1, "matchScore": .9 - i * .1} for i, slug in enumerate(slugs)],
                catalogSha256=catalog_data()[1], **observed("wrong OCR"))
    job = ScanJob("id", "done", True, result=data)
    job.result = {**data, "userConfirmation": validated_confirmation(job, slugs[1])}
    with patch("api.scans.get_wine", side_effect=wine_from_file):
        body = to_status_response(job)
    assert body.slug == slugs[0] and [c["slug"] for c in body.candidates] == slugs
    assert body.candidates[1]["matchScore"] == .8
    assert body.recognitionStatus == "candidates_unverified"
    assert body.userConfirmation == {"slug": slugs[1], "source": "user"}
    assert body.recommendationContext["anchor"]["slug"] == slugs[1]
    assert body.recommendationContext["anchor"]["isRecognizedWine"] is True
    assert slugs[1] not in [item["slug"] for item in body.recommendations]


def test_confirmation_rejects_unknown_hidden_and_unfinished_candidates():
    import pytest
    from api.scans import validated_confirmation
    data = {"recognitionStatus": "candidates_unverified", "candidates": [{"slug": "a"}, {"slug": "b"}]}
    for job, slug in [(ScanJob("id", "pending", True, result=data), "a"),
                      (ScanJob("id", "done", False, result=data), "b"),
                      (ScanJob("id", "done", True, result=data), "unknown"),
                      (ScanJob("id", "done", True, result={**data, "recognitionStatus": "not_in_catalog"}), "a")]:
        with pytest.raises(ValueError):
            validated_confirmation(job, slug)


def test_confirmation_persists_in_scan_json_and_keeps_original_candidate_scores():
    from unittest.mock import MagicMock
    from api.scans import confirm_job
    original = {"recognitionStatus": "candidates_unverified", "candidates": [{"slug": "a", "matchScore": .87}]}
    connection = MagicMock()
    connection.execute.return_value.fetchone.return_value = ("done", True, "image", original, None)
    with patch("api.scans.psycopg.connect") as connect:
        connect.return_value.__enter__.return_value = connection
        result = confirm_job("id", "a")
    assert result.result["userConfirmation"]["slug"] == "a"
    assert result.result["candidates"] == original["candidates"]
    saved_json = connection.execute.call_args_list[1].args[1][0].obj
    assert saved_json == result.result
    connection.commit.assert_called_once()

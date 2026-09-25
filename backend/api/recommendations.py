"""Explainable catalogue alternatives from image evidence; no recognition reranking."""
from __future__ import annotations

from collections import defaultdict
from functools import lru_cache
import math
import re
import unicodedata

VERSION = "attribute-recommendations-v3"
COLORS = {"Белое": ["белое", "белый", "white"], "Красное": ["красное", "красный", "red"],
          "Розовое": ["розовое", "розовый", "rose", "rosé"], "Оранжевое": ["оранжевое", "orange"]}
SWEETNESS = {
    "Экстра брют": ["экстра брют", "extra brut", "ekstra bryut", "экстрабрют"],
    "Полусладкое": ["полусладкое", "полусладкий", "полу сладкое", "semi sweet", "semisweet", "polusladkoe"],
    "Полусухое": ["полусухое", "полусухой", "полу сухое", "semi dry", "semidry", "polusuhoe", "polusukhoe"],
    "Брют": ["брют", "brut", "bryut"], "Сладкое": ["сладкое", "сладкий", "sweet", "sladkoe"],
    "Сухое": ["сухое", "сухой", "dry", "suhoe", "sukhoe"],
}
# Conservative vocabulary: no colour inference from the grape or bottle pixels.
GRAPES = {
    "Каберне Совиньон": ["cabernet sauvignon"], "Каберне Фран": ["cabernet franc"],
    "Совиньон Блан": ["sauvignon blanc"], "Шардоне": ["chardonnay"], "Рислинг": ["riesling"],
    "Ркацители": ["rkatsiteli"], "Вионье": ["viognier"], "Мерло": ["merlot"],
    "Пино Нуар": ["pinot noir"], "Пино Блан": ["pinot blanc"], "Пино Гри": ["pinot gris", "pinot grigio", "пино гриджио"],
    "Мускат": ["muscat", "moscato"], "Саперави": ["saperavi"], "Зинфандель": ["zinfandel"],
    "Траминер": ["traminer"], "Гевюрцтраминер": ["gewurztraminer"], "Алиготе": ["aligote"],
    "Сира": ["syrah", "shiraz", "шираз", "сира / шираз"], "Мальбек": ["malbec"],
    "Глера": ["glera"], "Темпранильо": ["tempranillo"], "Санджовезе": ["sangiovese"],
    "Монтепульчано": ["montepulciano"], "Пино Менье": ["pinot meunier"],
}
PRODUCER_ALIASES = {"Фанагория": ["fanagoria", "phanagoria"], "Мысхако": ["myskhako", "myshako"],
                    "Массандра": ["massandra"], "Абрау-Дюрсо": ["abrau durso"],
                    "Denisov Winery": ["denisov", "денисов"], "ESSE": ["эссе"]}
GENERIC_PRODUCER = {"winery", "wine", "wines", "vineyards", "estate", "chateau", "шато", "винодельня",
                    "винодельни", "вино", "вина", "дом", "завод", "усадьба", "ооо", "the", "and", "de", "le"}


def normalize(text: str) -> str:
    text = unicodedata.normalize("NFKD", str(text).casefold().replace("ё", "е"))
    text = "".join(c for c in text if not unicodedata.combining(c))
    return " ".join(re.findall(r"[^\W_]+", text))


# OCR can read Russian capitals as Latin glyphs (КРАСНОЕ -> KPACHOE).
# V/Y are allowed only inside a complete known attribute word, never as a
# general transliteration of names, grapes, catalogue metadata or raw evidence.
OCR_ATTRIBUTE_GLYPHS = str.maketrans({
    "a": "а", "b": "в", "c": "с", "e": "е", "h": "н", "k": "к",
    "m": "м", "o": "о", "p": "р", "t": "т", "x": "х", "y": "у", "v": "у",
})


@lru_cache(maxsize=1)
def attribute_words() -> frozenset[str]:
    return frozenset(word for vocabulary in (COLORS, SWEETNESS)
                     for value, aliases in vocabulary.items() for alias in [value, *aliases]
                     for word in normalize(alias).split() if re.fullmatch(r"[а-я]+", word))


def normalize_ocr_attributes(text: str) -> str:
    words = []
    for word in normalize(text).split():
        decoded = word.translate(OCR_ATTRIBUTE_GLYPHS)
        words.append(decoded if decoded in attribute_words() else word)
    return " ".join(words)


@lru_cache(maxsize=64)
def patterns(entries: tuple) -> tuple:
    vocabulary = dict(entries)
    options = sorted(((normalize(alias), value) for value, aliases in vocabulary.items()
                      for alias in [value, *aliases]), key=lambda item: -len(item[0]))
    return tuple((re.compile(r"(?<!\w)" + re.escape(phrase) + r"(?!\w)"), value) for phrase, value in options)


def find_values(text: str, vocabulary: dict[str, list[str]]) -> list[str]:
    """Longest phrase wins at overlapping spans (semi-dry must not also mean dry)."""
    normalized = normalize(text)
    spans = []; values = set()
    for expression, value in patterns(tuple((k, tuple(v)) for k, v in vocabulary.items())):
        for match in expression.finditer(normalized):
            if any(match.start() < end and match.end() > start for start, end in spans):
                continue
            spans.append(match.span()); values.add(value)
    return sorted(values)


def single(values: list[str]) -> str | None:
    return values[0] if len(values) == 1 else None


class RecommendationIndex:
    def __init__(self, cards: dict[str, dict], semantic=None):
        self.semantic = semantic
        self.grapes = {name: list(aliases) for name, aliases in GRAPES.items()}
        aliases = {normalize(a) for k, aa in self.grapes.items() for a in [k, *aa]}
        for card in cards.values():
            for name in str(card.get("grapes") or "").split(","):
                name = name.strip()
                if name and normalize(name) not in aliases and "сорта винограда" not in name and not re.search(r"[()]| и ", name):
                    self.grapes.setdefault(name, [])
        self.cards = {slug: self.attributes(card) for slug, card in cards.items()}
        self.producers = {}
        owners = defaultdict(set)
        excluded = GENERIC_PRODUCER | {word for name in self.grapes for word in normalize(name).split()}
        for name in {c["producer"] for c in self.cards.values() if c["producer"]}:
            for alias in [name, *PRODUCER_ALIASES.get(name, [])]:
                self.producers.setdefault(name, []).append(alias)
            for word in normalize(name).split():
                if len(word) >= 5 and word not in excluded:
                    owners[word].add(name)
        for word, names in owners.items():
            if len(names) == 1:
                self.producers[next(iter(names))].append(word)
        alias_owners = defaultdict(set)
        for name, aliases in self.producers.items():
            for alias in aliases:
                alias_owners[normalize(alias)].add(name)
        self.producers = {name: [a for a in aliases if len(alias_owners[normalize(a)]) == 1]
                          for name, aliases in self.producers.items()}

    def attributes(self, card: dict) -> dict:
        review = card.get("metadata_review") or {}
        confirmed = review.get("confirmed_attributes", {}) if review.get("status") in ("confirmed", "applied") else {}
        color = single(find_values(confirmed.get("category") or card.get("category") or "", COLORS))
        sweetness = None; source = None
        for source_name, text in [("reviewed", confirmed.get("sweetness")), ("metadata", card.get("sweetness")),
                                  ("title", card.get("title")), ("slug", card.get("slug"))]:
            values = find_values(text or "", SWEETNESS)
            if values:
                sweetness = single(values); source = source_name
                break
        return dict(color=color, sweetness=sweetness, sweetnessSource=source,
                    grapes=find_values(card.get("grapes") or "", self.grapes), producer=card.get("winery"))

    def observed(self, observations: list[dict]) -> dict:
        groups = {key: defaultdict(list) for key in ("color", "sweetness", "grapes", "producer")}
        for line in observations:
            try:
                confidence = float(line.get("confidence", 0))
            except (ValueError, TypeError):
                continue
            if line.get("on_target_bottle") is False or not math.isfinite(confidence) or not .8 <= confidence <= 1:
                continue
            text = str(line.get("text") or "")
            evidence = {k: line.get(k) for k in ("text", "confidence", "source", "polygon_original")}
            # "Мускат Белый" is a grape name, not evidence that this wine is white.
            attribute_text = normalize_ocr_attributes(text)
            color_text = attribute_text
            for grape in find_values(attribute_text, self.grapes):
                for alias in [grape, *self.grapes[grape]]:
                    color_text = re.sub(r"(?<!\w)" + re.escape(normalize(alias)) + r"(?!\w)", " ", color_text)
            for key, vocabulary in [("color", COLORS), ("sweetness", SWEETNESS), ("grapes", self.grapes), ("producer", self.producers)]:
                search_text = color_text if key == "color" else attribute_text if key == "sweetness" else text
                for value in find_values(search_text, vocabulary):
                    if len(groups[key][value]) < 3:
                        groups[key][value].append(evidence)
        return {key: dict(value=sorted(values) if key == "grapes" else single(sorted(values)),
                          status="read" if values and (key == "grapes" or len(values) == 1) else "ambiguous" if values else "missing",
                          readings=[dict(value=value, evidence=evidence) for value, evidence in values.items()])
                for key, values in groups.items()}

    def recommend(self, result: dict, limit: int = 5) -> dict:
        profile = self.observed(result.get("observations", []))
        status = result.get("recognitionStatus")
        # An absent wine has no catalogue anchor: diagnostic Top-5 is not evidence.
        observed_only = status == "not_in_catalog"
        confirmed = status == "confirmed"
        shortlist = [] if observed_only else result.get("candidates") or []
        anchor_slug = shortlist[0].get("slug") if shortlist else None
        anchor = self.cards.get(anchor_slug)
        semantic_scores = self.semantic.scores(anchor_slug) if self.semantic and anchor else {}
        output = dict(version=VERSION, status="insufficient_evidence", profile=profile, items=[],
                      basis="photo_ocr" if observed_only else "confirmed_wine" if confirmed else "retrieval_top1",
                      anchor=dict(slug=anchor_slug, source="confirmed_wine" if confirmed else "retrieval_top1",
                                  isRecognizedWine=confirmed) if anchor else None,
                      semanticStatus="available" if semantic_scores else "unavailable",
                      semanticModel=self.semantic.model if self.semantic else None)
        if any(profile[k]["status"] == "ambiguous" for k in ("color", "sweetness")):
            output["status"] = "ambiguous_evidence"
            return output
        query = {k: v["value"] for k, v in profile.items()}
        sources = {k: "photo_ocr" if v else "missing" for k, v in query.items()}
        # Only a working reference: never copy anchor metadata into observedFields.
        if anchor:
            for key in query:
                if not query[key] and profile[key]["status"] == "missing" and anchor[key]:
                    query[key] = anchor[key]
                    sources[key] = "confirmed_wine" if confirmed else "retrieval_top1_reference"
        output["criteria"] = query
        output["criteriaSources"] = sources
        if not (query["grapes"] or query["producer"] or query["color"] and query["sweetness"]):
            return output
        pool = []
        for slug, card in self.cards.items():
            if slug == anchor_slug:
                continue
            # Known colour/sugar never relaxed, including producer fallback.
            if any(query[k] and query[k] != card[k] for k in ("color", "sweetness")):
                continue
            common = sorted(set(query["grapes"]) & set(card["grapes"]))
            matched = [k for k in ("color", "sweetness") if query[k] and query[k] == card[k]]
            if common:
                matched.append("grapes")
            same_producer = bool(query["producer"] and query["producer"] == card["producer"])
            if same_producer:
                matched.append("producer")
            if not (common or {"color", "sweetness"}.issubset(matched) or same_producer):
                continue
            tier = ("color_sweetness_grapes" if {"color", "sweetness", "grapes"}.issubset(matched)
                    else "color_sweetness" if {"color", "sweetness"}.issubset(matched)
                    else "grape_overlap" if common else "same_producer")
            tier_order = ["color_sweetness_grapes", "color_sweetness", "grape_overlap", "same_producer"].index(tier)
            coverage = len(common) / len(query["grapes"]) if query["grapes"] else 0.
            exact_grapes = bool(common and set(card["grapes"]) == set(query["grapes"]))
            reasons = []
            if "color" in matched: reasons.append("Совпадает цвет: " + query["color"])
            if "sweetness" in matched: reasons.append("Совпадает сладость: " + query["sweetness"])
            if common: reasons.append("Общие сорта: " + ", ".join(common))
            if same_producer: reasons.append("Тот же производитель: " + query["producer"])
            reasons = [reason + (" (по надписи на фото)" if sources[key] == "photo_ocr" else " (по карточке-ориентиру)")
                       for key, reason in zip(matched, reasons)]
            missing = [k for k, v in profile.items() if v["status"] != "read"]
            unmatched_grapes = sorted(set(query["grapes"]) - set(common))
            item = dict(slug=slug, tier=tier, matchedFields=matched, reasons=reasons, attributes=card,
                        commonGrapes=common, unmatchedGrapes=unmatched_grapes, unknownQueryFields=missing)
            similarity = semantic_scores.get(slug)
            item.update(textSimilarity=similarity, similarityIsProbability=False,
                        criteriaSources={k: sources[k] for k in matched})
            if self.semantic:
                extra = self.semantic.metadata.get(slug, {})
                item["servingAdvice"] = dict(foodPairing=extra.get("food_pairing", []),
                                             temperature=extra.get("serving_temperature"), source=extra.get("source"))
            if similarity is not None:
                reasons.append("Близкое текстовое описание к карточке-ориентиру Top-1")
            pool.append(((tier_order, -coverage, -int(exact_grapes), -(similarity if similarity is not None else -1.), -int(same_producer), slug), item))
        pool.sort(key=lambda pair: pair[0])
        output["items"] = [{"rank": i + 1, **item} for i, (_, item) in enumerate(pool[:limit])]
        output["status"] = "available" if pool else "no_suitable_analogs"
        return output


@lru_cache(maxsize=1)
def recommendation_index() -> RecommendationIndex:
    from api.catalog import catalog_data
    from api.text_neighbors import load_text_index
    cards, checksum = catalog_data()
    return RecommendationIndex(cards, semantic=load_text_index(cards, checksum))

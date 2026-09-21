"""Resolve distinctive OCR producer names without using query annotations."""
from __future__ import annotations

from collections import defaultdict

from rapidfuzz import process
from rapidfuzz.distance import DamerauLevenshtein

from .text_search import COLORS, SWEETNESS, TECHNICAL_TERMS, WINERY_ALIASES, raw_words, tokens, word_variants

# The short brand is present in catalogue titles; the winery field uses its legal name.
PRODUCER_ALIASES = {**WINERY_ALIASES, "Инкерманский ЗМВ": ["Инкерман", "INKERMAN"]}
GENERIC = {"vineyards", "vineyard", "winemaking", "company", "zavod", "zmv", "ooo", "zao", "oao", "dom",
           "grand", "gran", "cru", "selection", "select", "special"}


class ProducerIndex:
    def __init__(self, cards: list[dict]):
        excluded = COLORS | SWEETNESS | TECHNICAL_TERMS | GENERIC
        for card in cards:
            excluded |= set(tokens(str(card.get("grapes") or "")))
            excluded |= set(tokens(str(card.get("region") or "")))
        owners = defaultdict(set)
        for winery in {str(card.get("winery") or "") for card in cards}:
            for name in [winery, *PRODUCER_ALIASES.get(winery, [])]:
                words = tokens(name)
                terms = set(words)
                for i in range(len(words)-1):
                    if all(len(w) >= 3 and w not in excluded for w in words[i:i+2]):
                        terms.add("".join(words[i:i+2]))
                for term in terms - excluded:
                    if len(term) >= 4 or term == "kd":
                        owners[term].add(winery)
        self.owners = {term: next(iter(names)) for term, names in owners.items() if len(names) == 1}
        self.terms = sorted(self.owners)
        self.cache = {}

    def matches(self, word: str) -> list[dict]:
        if word in self.cache:
            return self.cache[word]
        best = {}
        for query, method, prior in word_variants(word):
            cutoff = 2 if len(query) >= 8 else 0
            for term, distance, _ in process.extract(query, self.terms, scorer=DamerauLevenshtein.distance,
                                                      score_cutoff=cutoff, limit=4):
                similarity = (1-distance/max(len(query), len(term))) * prior
                if similarity < .8:
                    continue
                winery = self.owners[term]
                if similarity > best.get(winery, {}).get("similarity", 0):
                    best[winery] = dict(winery=winery, term=term, similarity=similarity,
                                        distance=distance, normalization=method)
        matches = sorted(best.values(), key=lambda e: (-e["similarity"], e["winery"]))
        # A fuzzy word equally compatible with several producers is not an identity.
        result = matches[:1] if matches and (len(matches) == 1 or matches[0]["similarity"]-matches[1]["similarity"] >= .08) else []
        self.cache[word] = result
        return result

    def search(self, observations: list[dict]) -> dict:
        evidence = {}
        for observation in observations:
            confidence = float(observation.get("confidence", 1.))
            if observation.get("on_target_bottle") is False or not .85 <= confidence <= 1.:
                continue
            for word in raw_words(observation["text"]):
                for match in self.matches(word):
                    strength = match["similarity"] * confidence
                    winery = match["winery"]
                    if strength > evidence.get(winery, {}).get("strength", 0):
                        evidence[winery] = dict(**match, strength=strength, raw_word=word,
                                               raw_line=observation["text"], confidence=confidence,
                                               source=observation.get("source"),
                                               polygon_original=observation.get("polygon_original"))
        ranked = sorted(evidence.values(), key=lambda e: (-e["strength"], e["winery"]))
        if not ranked:
            return dict(status="unreadable", winery=None, evidence=[])
        if len(ranked) > 1 and ranked[0]["strength"]-ranked[1]["strength"] < .08:
            return dict(status="ambiguous", winery=None, evidence=ranked)
        return dict(status="recognized", winery=ranked[0]["winery"], evidence=ranked[:1])

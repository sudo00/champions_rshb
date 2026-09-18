from pipeline.embed import embed
from pipeline.normalize import normalize
from pipeline.search import search


def run(image: bytes) -> dict:
    # тут код Лехи
    prepared = normalize(image)
    vector = embed(prepared)
    return search(vector)

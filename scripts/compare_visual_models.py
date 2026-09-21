"""Compare completed visual runs and fuse model ranks without mixing feature spaces."""
from __future__ import annotations
import argparse
import html
import json
import os
from pathlib import Path
import sys

from label_rectification_pilot import ROOT, read, write, digest
from visual_query_review import reviewed_query
sys.path.insert(0, str(ROOT / 'worker'))
from pipeline.visual_search import fuse_rankings

BASE = ROOT / 'data/audit/visual_search'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--models', nargs='+', default=['dinov3_timm', 'dinov3_large', 'siglip2'])
    parser.add_argument('--output', type=Path, default=BASE / 'comparison_v1')
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    inputs = {model: read(BASE / (model + '_v1') / 'results.json') for model in args.models}
    if len({v['catalog_sha256'] for v in inputs.values()}) != 1:
        raise ValueError('Models used different catalogues')
    catalog_path = ROOT / 'data/catalog/curated/catalog.jsonl'
    if digest(catalog_path) != next(iter(inputs.values()))['catalog_sha256']:
        raise ValueError('Catalogue changed after retrieval')
    signatures = []
    for model in args.models:
        base = BASE / (model + '_v1')
        if inputs[model]['encoding_sha256'] != digest(base / 'encoding.json'):
            raise ValueError('Encoding changed after report')
        encoding = read(base / 'encoding.json')
        if digest(base / 'views.json') != encoding['views_sha256']:
            raise ValueError('View manifest changed')
        signatures.append([(v['record_id'], v['name'], v['family'], v['sha256']) for v in read(base / 'views.json')])
    if any(s != signatures[0] for s in signatures[1:]):
        raise ValueError('Models encoded different visual views')
    grouped = {model: {r['id']: r for r in doc['images']} for model, doc in inputs.items()}
    ids = list(next(iter(grouped.values())))
    if any(set(rows) != set(ids) for rows in grouped.values()):
        raise ValueError('Query sets differ')
    catalogue = {r['slug']: r for r in (json.loads(l) for l in (ROOT / 'data/catalog/curated/catalog.jsonl').read_text().splitlines())}
    text_file = ROOT / 'data/audit/text_search/pilot14_v1/results.json'
    text_doc = read(text_file)
    if text_doc['provenance']['catalog_sha256'] != next(iter(inputs.values()))['catalog_sha256']:
        raise ValueError('OCR catalogue differs')
    text = {r['id']: r for r in text_doc['images']}
    results, sections = [], []
    def image(path):
        return '<img loading="lazy" src="' + html.escape(os.path.relpath(path, args.output), quote=True) + '">'
    for identifier in ids:
        entries = {model: rows[identifier] for model, rows in grouped.items()}
        if len({e['source_sha256'] for e in entries.values()}) != 1:
            raise ValueError('Query source changed between models')
        modes = {model: row['results']['augmented']['candidates'] for model, row in entries.items()}
        modes['ensemble'] = fuse_rankings(modes)
        ocr = text.get(identifier.removeprefix('query_'), {}).get('searches', {}).get('combined', {}).get('candidates', [])
        if ocr and modes['ensemble']:
            modes['ensemble_plus_ocr'] = fuse_rankings({'visual': modes['ensemble'], 'ocr': ocr})
        source = next(iter(entries.values()))
        expected = source['source_card_for_diagnostic_only']
        ranks = {mode: next((i+1 for i, c in enumerate(candidates) if c['slug'] == expected), None)
                 for mode, candidates in modes.items()} if expected else {}
        review = reviewed_query(identifier, source['source_sha256'], modes)
        results.append({'id': identifier, 'source_sha256': source['source_sha256'],
                        'manual_review': review,
                        'expected_for_self_image_diagnostic_only': expected, 'self_image_ranks': ranks,
                        'candidates': modes, 'score_is_probability': False})
        gallery = read(BASE / 'gallery_v2/records' / (identifier + '.json'))
        preview = BASE / 'gallery_v2' / next(v['path'] for v in gallery['views'] if v['family'] == 'reference')
        panels = []
        for mode, candidates in modes.items():
            cards = []
            for i, c in enumerate(candidates, 1):
                card = catalogue[c['slug']]
                cards.append(f'<article><b>{i}. {html.escape(card["title"])}</b>{image(ROOT / card["reference_path"])}<small>{html.escape(c["slug"])}</small></article>')
            rank_label = f'место исходной карточки {ranks.get(mode,"—")}'
            if review and review['slug']:
                rank_label = f'место проверенной карточки {review["ranks"].get(mode) or "вне Top-10"}'
            panels.append(f'<details {"open" if mode == "ensemble" else ""}><summary>{html.escape(mode)} · {rank_label}</summary><div class="cards">{"".join(cards) or "Нет целевого объекта"}</div></details>')
        warning = review['note'] if review else ('Свой каталожный исходник, не независимая точность' if expected else 'Подтверждённого ответа нет. Сходство не подтверждает наличие товара в каталоге')
        sections.append(f'<section><h2>{html.escape(identifier)}</h2>{image(preview)}<p>{html.escape(warning)}</p>{"".join(panels)}</section>')
    stats = {}
    for mode in [*args.models, 'ensemble', 'ensemble_plus_ocr']:
        known = [r for r in results if r['expected_for_self_image_diagnostic_only'] and mode in r['candidates']]
        stats[mode] = {'self_images': len(known), 'top1': sum(r['self_image_ranks'][mode] == 1 for r in known),
                       'top10': sum(r['self_image_ranks'][mode] is not None for r in known)}
    write(args.output / 'results.json', {'inputs_sha256': {model: digest(BASE / (model + '_v1/results.json')) for model in args.models},
        'script_sha256': digest(Path(__file__)), 'ocr_results_sha256': digest(text_file),
        'manual_review_code_sha256': digest(ROOT / 'scripts/visual_query_review.py'),
        'self_image_diagnostics_not_accuracy': stats, 'images': results})
    links = ' · '.join(f'<a href="../{html.escape(model)}_v1/review.html">{html.escape(model)}: виды и baseline</a>' for model in args.models)
    (args.output / 'review.html').write_text('<!doctype html><html lang="ru"><meta charset="utf-8"><title>Сравнение визуального поиска</title><style>body{font:16px system-ui;background:#f5f4ef;margin:30px}section{border-top:2px solid #aaa;padding:24px 0}img{height:220px;max-width:300px;object-fit:contain}.cards{display:flex;gap:10px;overflow:auto}article{background:white;min-width:180px;width:180px;padding:10px;overflow-wrap:anywhere}article img{width:170px}small{font-size:11px}summary{cursor:pointer;padding:12px}</style><h1>Визуальный поиск — сравнение моделей</h1><p>Top-10 уникальных slug. Объединяются ранги, а не векторы разных моделей. Оценки не являются вероятностями; отказ от неизвестного вина ещё не откалиброван.</p>' + links + ''.join(sections) + '</html>')
    print(json.dumps(stats, indent=2))


if __name__ == '__main__':
    main()

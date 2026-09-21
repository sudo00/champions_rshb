"""Encode prepared catalogue views and review exact Top-10 visual retrieval."""
from __future__ import annotations

import argparse
from collections import Counter
import html
import importlib.metadata
import json
import os
import shutil
from pathlib import Path
import sys
import time

import numpy as np
from PIL import Image, ImageOps

from label_rectification_pilot import ROOT, read, write, digest, immutable, rgb_oriented

sys.path.insert(0, str(ROOT / 'worker'))
from pipeline.visual_search import VisualIndex, fuse_rankings, normalize_vectors

BASE = ROOT / 'data/audit/visual_search'
ARCHITECTURES = {'dinov3_timm': 'vit_base_patch16_dinov3_qkvb', 'dinov3_large': 'vit_large_patch16_dinov3'}


def capture_evidence() -> tuple[dict, dict]:
    """Reuse image-only, multi-prompt checks; never infer query packaging from its filename."""
    by_sha, provenance = {}, {}
    mapping = {'bottle': 'bottle', 'wine box': 'box', 'can': 'can', 'beverage carton': 'carton', 'pouch': 'pouch'}
    for name in ('pilot20_v1', 'user_examples2_v1'):
        base = ROOT / 'data/audit/capture_quality' / name
        path = base / 'assessment.json'
        if not path.exists():
            continue
        assessment = read(path)
        if assessment['config_sha256'] != digest(base / 'config.json'):
            raise ValueError('Stale capture assessment')
        if read(base / 'config.json')['manifest_sha256'] != digest(base / 'manifest.json'):
            raise ValueError('Capture source manifest changed')
        provenance[str(path)] = digest(path)
        manifest = {row['id']: row for row in read(base / 'manifest.json')['images']}
        for row in assessment['images']:
            coarse = {}
            for item in row['packaging_hypotheses']:
                if item['concept'] in mapping:
                    kind = mapping[item['concept']]
                    coarse[kind] = max(coarse.get(kind, 0.), item['score'])
            ordered = sorted(coarse.items(), key=lambda item: -item[1])
            kind, score = ordered[0] if ordered else ('unknown', 0.)
            reliable = score >= .8 and len(ordered) == 1
            target = row.get('target')
            if target and digest(base / target['mask_path']) != target['mask_sha256']:
                raise ValueError('Capture target mask changed')
            by_sha[manifest[row['id']]['source_sha256']] = {
                'kind': kind, 'reliable': reliable, 'score': score, 'source': 'SAM3_multi_prompt_assessment',
                'assessment_path': str(path), 'target': target}
    return by_sha, provenance


def records(gallery: Path) -> list[dict]:
    manifest = read(gallery / 'manifest.json')
    config_sha = digest(gallery / 'config.json')
    result = []
    for entry in manifest['entries']:
        path = gallery / 'records' / (entry['id'] + '.json')
        if not path.exists():
            raise ValueError('Gallery preparation incomplete: ' + entry['id'])
        row = read(path)
        if row['config_sha256'] != config_sha or row['source_sha256'] != entry['source_sha256']:
            raise ValueError('Stale gallery record')
        result.append(row)
    return result


def encode(args: argparse.Namespace) -> None:
    import torch
    rows = records(args.gallery)
    captures, capture_provenance = capture_evidence()
    model_dir = ROOT / 'weights/research/visual_search' / args.model
    download = model_dir.with_name(args.model + '_download.json')
    for name, checksum in read(download)['files_sha256'].items():
        if digest(model_dir / name) != checksum:
            raise ValueError('Model file changed: ' + name)
    body_cache_path = BASE / 'dinov3_timm_v1/views.json'
    body_cache = {v['record_id']: v for v in read(body_cache_path) if v['family'] == 'body'} if args.model != 'dinov3_timm' and body_cache_path.exists() else {}
    views = []
    for row in rows:
        has_body = any(v['family'] == 'body' for v in row['views'])
        for view in row['views']:
            if view['family'] == 'reference' and has_body:
                continue
            if row['role'] == 'query' and view['family'] == 'augmented':
                continue
            view = dict(view)
            if view['family'] == 'body':
                # SAM can omit a wrap/foil from the bottle mask. Preserve all RGB inside its box.
                source = Path(row['path'])
                if digest(source) != row['source_sha256']:
                    raise ValueError('Source changed before body crop')
                capture = captures.get(row['source_sha256']) if row['role'] == 'query' else None
                if capture and capture['reliable'] and capture['kind'] != 'bottle' and capture['target']:
                    # A generic bottle prompt can select the bottle *pictured* on a bag-in-box.
                    box = capture['target']['box_xyxy']
                    margin = max(3, round(min(box[2] - box[0], box[3] - box[1]) * .04))
                    view['crop_box_original'] = [max(0, int(box[0]) - margin), max(0, int(box[1]) - margin),
                        min(row['source_size'][0], int(box[2]) + margin), min(row['source_size'][1], int(box[3]) + margin)]
                    view['localization_source'] = capture['assessment_path']
                path = args.output / 'body_rgb' / (row['id'] + '.png')
                path.parent.mkdir(parents=True, exist_ok=True)
                cached = body_cache.get(row['id'])
                if cached and cached['source_sha256'] == row['source_sha256'] and cached['crop_box_original'] == view['crop_box_original']:
                    if digest(Path(cached['path'])) != cached['sha256']:
                        raise ValueError('Cached RGB body changed')
                    shutil.copyfile(cached['path'], path)
                    size = cached['size']
                else:
                    image, _ = rgb_oriented(source)
                    image = image.crop(view['crop_box_original'])
                    image.thumbnail((768, 768), Image.Resampling.LANCZOS)
                    image.save(path)
                    size = list(image.size)
                view.update(name='object_rgb', path=str(path.resolve()), sha256=digest(path),
                            size=size, source_sha256=row['source_sha256'])
            views.append({**view, 'record_id': row['id'], 'role': row['role'], 'slugs': row['slugs']})
    # Query rotations are not indexed or used for the baseline; query gets raw + flat views.
    config = {'model': args.model, 'model_download_sha256': digest(download),
              'gallery_config_sha256': digest(args.gallery / 'config.json'),
              'capture_provenance': capture_provenance,
              'side': 384, 'resize': 'letterbox_white_bicubic_no_crop', 'precision': 'bf16',
              'pooling': 'CLS' if args.model in ARCHITECTURES else 'SigLIP_projection',
              'rope_periods': 'bf16_roundtrip' if args.model in ARCHITECTURES else None,
              'batch_size': args.batch_size, 'script_sha256': digest(Path(__file__)),
              'versions': {name: importlib.metadata.version(name) for name in ('torch', 'timm', 'transformers', 'Pillow')}}
    immutable(args.output / 'config.json', config)
    immutable(args.output / 'views.json', views)
    (args.output / 'provenance').mkdir(exist_ok=True)
    (args.output / 'provenance/encoder_script.py').write_bytes(Path(__file__).read_bytes())
    if not torch.cuda.is_available():
        raise RuntimeError('CUDA required for this benchmark')
    torch.set_num_threads(4)
    torch.backends.cuda.matmul.allow_tf32 = False
    if args.model in ARCHITECTURES:
        import timm
        from safetensors.torch import load_file
        model = timm.create_model(ARCHITECTURES[args.model], pretrained=False,
                                  num_classes=0, global_pool='token')
        model.load_state_dict(load_file(str(model_dir / 'model.safetensors')), strict=True)
        model.rope.periods = model.rope.periods.to(torch.bfloat16).float()
        model = model.eval().cuda()
        mean = torch.tensor([.485, .456, .406]).view(3, 1, 1)
        std = torch.tensor([.229, .224, .225]).view(3, 1, 1)
        def forward(batch):
            return model(batch)
    else:
        # Fixed-resolution SigLIP 2 checkpoints use the Siglip architecture; Siglip2 is NaFlex.
        from transformers import SiglipVisionModel
        if read(model_dir / 'config.json')['model_type'] != 'siglip':
            raise ValueError('Expected fixed-resolution SigLIP 2 checkpoint')
        model, loading = SiglipVisionModel.from_pretrained(model_dir, local_files_only=True, output_loading_info=True)
        if loading.get('missing_keys') or loading.get('mismatched_keys') or loading.get('error_msgs'):
            raise ValueError('Incomplete SigLIP vision weights: ' + str(loading))
        model = model.eval().cuda()
        mean = std = torch.tensor([.5, .5, .5]).view(3, 1, 1)
        def forward(batch):
            return model(pixel_values=batch).pooler_output
    def tensor(view):
        path = args.gallery / view['path']
        if digest(path) != view['sha256']:
            raise ValueError('Prepared image changed')
        with Image.open(path) as opened:
            image = ImageOps.pad(opened.convert('RGB'), (384, 384), method=Image.Resampling.BICUBIC, color='white')
            values = torch.from_numpy(np.array(image).copy()).permute(2, 0, 1).float() / 255
        return (values - mean) / std
    with torch.inference_mode(), torch.autocast('cuda', dtype=torch.bfloat16):
        forward(torch.stack([tensor(views[0])]).cuda())
    torch.cuda.synchronize()
    torch.cuda.reset_peak_memory_stats()
    started = time.perf_counter()
    measured = []
    chunks = []
    for offset in range(0, len(views), args.batch_size):
        path = args.output / 'chunks' / f'{offset:06d}.npy'
        receipt = path.with_suffix('.json')
        if path.exists() and receipt.exists():
            saved = read(receipt)
            if saved['sha256'] != digest(path) or saved['config_sha256'] != digest(args.output / 'config.json'):
                raise ValueError('Stale feature chunk')
            chunks.append(np.load(path, allow_pickle=False))
            continue
        selected = views[offset:offset + args.batch_size]
        batch = torch.stack([tensor(v) for v in selected]).cuda()
        torch.cuda.synchronize()
        start = time.perf_counter()
        with torch.inference_mode(), torch.autocast('cuda', dtype=torch.bfloat16):
            features = forward(batch).float()
        torch.cuda.synchronize()
        elapsed = time.perf_counter() - start
        array = normalize_vectors(features.cpu().numpy())
        path.parent.mkdir(parents=True, exist_ok=True)
        np.save(path, array, allow_pickle=False)
        write(receipt, {'sha256': digest(path), 'config_sha256': digest(args.output / 'config.json'),
                        'offset': offset, 'count': len(array), 'gpu_forward_seconds': elapsed})
        chunks.append(array)
        measured.append({'count': len(array), 'gpu_forward_seconds': elapsed})
        if offset % (args.batch_size * 25) == 0:
            print(offset, '/', len(views), 'encoded', round(time.perf_counter() - started, 1), 's', flush=True)
    combined = np.concatenate(chunks)
    if len(combined) != len(views):
        raise ValueError('Incomplete feature matrix')
    np.save(args.output / 'features.npy', combined, allow_pickle=False)
    write(args.output / 'encoding.json', {'features_sha256': digest(args.output / 'features.npy'),
        'config_sha256': digest(args.output / 'config.json'), 'views_sha256': digest(args.output / 'views.json'),
        'views': len(views), 'dimension': combined.shape[1], 'elapsed_seconds_this_run': time.perf_counter() - started,
        'gpu': torch.cuda.get_device_name(), 'peak_allocated_bytes': torch.cuda.max_memory_allocated(),
        'measured_batches_this_run': measured, 'note': 'Batched warm forward excludes CPU preparation; not request latency.'})
    print('Encoded', combined.shape, flush=True)


def report(args: argparse.Namespace) -> None:
    from visual_query_review import reviewed_query

    rows = records(args.gallery)
    captures, capture_provenance = capture_evidence()
    catalog_path = ROOT / 'data/catalog/curated/catalog.jsonl'
    if digest(catalog_path) != read(args.gallery / 'manifest.json')['catalog_sha256']:
        raise ValueError('Catalogue changed')
    cards = [json.loads(line) for line in catalog_path.read_text().splitlines()]
    lookup = {r['slug']: r for r in cards}
    encoding = read(args.output / 'encoding.json')
    for filename, key in [('features.npy', 'features_sha256'), ('views.json', 'views_sha256'), ('config.json', 'config_sha256')]:
        if digest(args.output / filename) != encoding[key]:
            raise ValueError('Feature provenance mismatch: ' + filename)
    if read(args.output / 'config.json')['gallery_config_sha256'] != digest(args.gallery / 'config.json'):
        raise ValueError('Wrong gallery')
    if read(args.output / 'config.json')['capture_provenance'] != capture_provenance:
        raise ValueError('Capture evidence changed')
    views = read(args.output / 'views.json')
    for view in views:
        if digest(args.gallery / view['path']) != view['sha256']:
            raise ValueError('Displayed view differs from encoded view')
    vectors = np.load(args.output / 'features.npy', allow_pickle=False)
    gallery_ids = [i for i, v in enumerate(views) if v['role'] == 'gallery']
    index = VisualIndex(vectors[gallery_ids], [views[i] for i in gallery_ids], cards)
    text_path = ROOT / 'data/audit/text_search/pilot14_v1/results.json'
    text_rows = {}
    if text_path.exists():
        text_results = read(text_path)
        if text_results['provenance']['catalog_sha256'] != digest(catalog_path):
            raise ValueError('Text index catalogue differs')
        text_rows = {r['id']: r for r in text_results['images']}
    entries = []
    sections = []
    def img(path, label):
        return f'<figure><img loading="lazy" src="{html.escape(os.path.relpath(path, args.output), quote=True)}"><figcaption>{html.escape(label)}</figcaption></figure>'
    for row in rows:
        if row['role'] != 'query':
            continue
        indices = [i for i, v in enumerate(views) if v['record_id'] == row['id']]
        query_views = [views[i] for i in indices]
        results = {}
        start = time.perf_counter()
        for name, augment in [('baseline', False), ('augmented', True)]:
            results[name] = index.search(vectors[indices], query_views, augment=augment, target_detected=row['target_detected'])
        for branch, candidates in results['baseline']['branches'].items():
            results['only_' + branch] = {'status': 'candidates_unverified', 'score_is_probability': False,
                'candidates': [{'slug': c['slug'], 'score': c['cosine'], 'sources': {branch: c}} for c in candidates]}
        # Packaging evidence here is a hypothesis only. Keep a separate explicit ablation.
        ev = row['evidence'].get('object', {})
        sel = ev.get('selected_index')
        score = ev.get('candidates', [])[sel]['score'] if sel is not None else 0.
        prompt_kind = {'bottle': 'bottle', 'wine box': 'box', 'can': 'can'}
        hypothesis = {'kind': prompt_kind.get(row['object_prompt'], 'unknown'), 'reliable': score >= .8,
                      'source': 'SAM3_selected_object', 'score': score}
        # Generic region-selection prompts alone are not a reliable packaging classifier.
        hypothesis = captures.get(row['source_sha256'], {**hypothesis, 'reliable': False})
        results['packaging'] = index.search(vectors[indices], query_views, augment=True,
            target_detected=row['target_detected'], packaging=hypothesis)
        text_row = text_rows.get(row['id'].removeprefix('query_'))
        if text_row and row['target_detected']:
            ocr = text_row['searches'].get('combined', {}).get('candidates', [])
            results['visual_plus_ocr'] = {'status': 'candidates_unverified', 'candidates': fuse_rankings({
                'visual': results['augmented']['candidates'], 'ocr': ocr}), 'score_is_probability': False}
        expected = row.get('source_card_for_diagnostic_only')
        ranks = {mode: next((i + 1 for i, r in enumerate(value['candidates']) if r['slug'] == expected), None)
                 for mode, value in results.items()} if expected else {}
        review = reviewed_query(row['id'], row['source_sha256'],
                                {mode: value['candidates'] for mode, value in results.items()})
        entries.append({'id': row['id'], 'source_sha256': row['source_sha256'], 'group': row['group'],
                        'source_card_for_diagnostic_only': expected, 'self_image_ranks': ranks,
                        'manual_review': review,
                        'packaging_hypothesis': hypothesis, 'results': results,
                        'all_modes_search_ms': (time.perf_counter() - start) * 1000})
        original = next(v for v in row['views'] if v['family'] == 'reference')
        image_html = img(args.gallery / original['path'], 'Исходный кадр')
        image_html += ''.join(img(args.gallery / v['path'], v['name']) for v in query_views if v['family'] != 'reference')
        mode_html = []
        for mode, value in results.items():
            candidates = []
            for rank, candidate in enumerate(value['candidates'], 1):
                card = lookup[candidate['slug']]
                source = max((s for s in candidate['sources'].values() if s.get('gallery_view')),
                             key=lambda s: s['cosine'], default=None)
                reference = source['gallery_view'] if source else None
                picture = img(args.gallery / reference['path'], reference['name']) if reference else img(ROOT / card['reference_path'], 'Эталон')
                evidence = '' if not source else f"{source['query_view']['name']} → {reference['name']}; cosine {source['cosine']:.3f}"
                candidates.append(f'<article><b>{rank}. {html.escape(card["title"])}</b><br>{html.escape(str(card.get("winery", "")))}{picture}<small>{html.escape(candidate["slug"])}</small><p>{html.escape(evidence)}</p></article>')
            rank_label = f'место исходной карточки: {ranks.get(mode, "—")}'
            if review and review['slug']:
                rank_label = f'место проверенной карточки: {review["ranks"].get(mode) or "вне Top-10"}'
            mode_html.append(f'<details {"open" if mode == "augmented" else ""}><summary>{mode} · {rank_label}</summary><div class="candidates">{"".join(candidates) or "Целевой объект не обнаружен"}</div></details>')
        warning = 'Совпадение с собственным каталожным фото — не независимая точность.' if expected else 'Подтверждённого правильного slug нет; показаны только кандидаты. Не подтверждает наличие вина в каталоге.'
        if review:
            warning = review['note']
        sections.append(f'<section><h2>{html.escape(row["id"])}</h2><p>{warning}</p><div class="views">{image_html}</div>{"".join(mode_html)}</section>')
    counts = Counter(r['cylinder']['status'] for r in rows if r['role'] == 'gallery')
    stats = {}
    for mode in ('baseline', 'augmented', 'packaging', 'visual_plus_ocr'):
        selected = [r for r in entries if r['source_card_for_diagnostic_only'] and mode in r['results']]
        stats[mode] = {'self_image_count': len(selected), 'self_image_top1': sum(r['self_image_ranks'][mode] == 1 for r in selected),
                       'self_image_top10': sum(r['self_image_ranks'][mode] is not None for r in selected)}
    write(args.output / 'results.json', {'catalog_sha256': digest(catalog_path), 'encoding_sha256': digest(args.output / 'encoding.json'),
        'search_code_sha256': digest(ROOT / 'worker/pipeline/visual_search.py'), 'report_code_sha256': digest(Path(__file__)),
        'manual_review_code_sha256': digest(ROOT / 'scripts/visual_query_review.py'),
        'ocr_results_sha256': digest(text_path) if text_rows else None, 'self_image_diagnostics_not_accuracy': stats,
        'gallery_cylinder': dict(counts), 'images': entries})
    page = '<!doctype html><html lang="ru"><meta charset="utf-8"><title>Визуальный поиск — Top-10</title><style>body{font:16px system-ui;margin:30px;background:#f5f4ef;color:#172720}section{border-top:2px solid #999;padding:20px 0}.views,.candidates{display:flex;gap:12px;overflow:auto}.views img{max-height:280px;max-width:280px}.candidates article{min-width:180px;width:180px;background:white;padding:12px;overflow-wrap:anywhere}figure{margin:8px 0}img{max-width:160px;height:210px;object-fit:contain}summary{cursor:pointer;padding:12px}small{font-size:11px}</style><h1>Визуальный поиск — Top-10</h1><p>Полный каталог. DINO/SigLIP — визуальные кандидаты, не подтверждённая идентификация. Повороты свёрнуты в один slug. Совпадения на своих исходниках не измеряют качество телефонного сценария. Вода/масло могут давать похожие вина: детектор упаковки не определяет содержимое.</p>'
    (args.output / 'review.html').write_text(page + ''.join(sections) + '</html>')
    print(json.dumps({'self_image_diagnostics': stats, 'cylinder': dict(counts)}, indent=2), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=['encode', 'report'])
    parser.add_argument('--model', choices=[*ARCHITECTURES, 'siglip2'], default='dinov3_timm')
    parser.add_argument('--gallery', type=Path, default=BASE / 'gallery_v2')
    parser.add_argument('--output', type=Path, default=None)
    parser.add_argument('--batch-size', type=int, default=16)
    args = parser.parse_args()
    if args.batch_size < 1:
        parser.error('batch-size must be positive')
    args.output = args.output or BASE / (args.model + '_v1')
    globals()[args.command](args)


if __name__ == '__main__':
    main()

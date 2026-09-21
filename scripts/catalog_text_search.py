"""CLI and diagnostic HTML for catalogue text retrieval from multiple OCR views."""
from __future__ import annotations
import argparse
from collections import Counter
import hashlib
import html
import importlib.metadata
import json
import os
from pathlib import Path
import sys
import time

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'worker'))
from pipeline.text_search import CatalogTextIndex, VERSION

CATALOG=ROOT/'data/catalog/curated/catalog.jsonl'
RECT=ROOT/'data/audit/label_rectification/pilot12_v4'
BODY=ROOT/'data/audit/bottle_ocr/pilot14_v1'
OUTPUT=ROOT/'data/audit/text_search/pilot14_v1'


def read(path: Path) -> dict:
    return json.loads(path.read_text())


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write(path: Path, value: dict) -> None:
    path.parent.mkdir(parents=True,exist_ok=True)
    temporary=path.with_suffix(path.suffix+'.tmp');temporary.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n');temporary.replace(path)


def load_lines(path: Path, source_prefix: str) -> list[dict]:
    document=read(path)
    return [{**line,'source':source_prefix+':'+str(i)} for i,line in enumerate(document['lines'])]


def query(args: argparse.Namespace) -> None:
    index=CatalogTextIndex.from_file(args.catalog)
    observations=[]
    if args.text:observations.append({'text':args.text,'source':'typed','confidence':1.})
    source_ids=set();source_hashes=set()
    for path in args.ocr or []:
        document=read(path)
        if document.get('image_id'):source_ids.add(document['image_id'])
        if document.get('source_sha256'):source_hashes.add(document['source_sha256'])
        observations.extend(load_lines(path,str(path)))
    if len(source_ids)>1 or len(source_hashes)>1:
        raise ValueError('OCR inputs describe different photographs; query them separately')
    if not observations:raise ValueError('Provide --text or --ocr')
    start=time.perf_counter();result=index.search(observations,args.limit);result['search_ms']=(time.perf_counter()-start)*1000
    result['catalog_sha256']=sha(args.catalog)
    if args.save:write(args.save,result)
    print(json.dumps(result,ensure_ascii=False,indent=2))


def report(args: argparse.Namespace) -> None:
    from PIL import Image,ImageOps
    args.output.mkdir(parents=True,exist_ok=True)
    start=time.perf_counter();index=CatalogTextIndex.from_file(args.catalog);build_ms=(time.perf_counter()-start)*1000
    lookup={r['slug']:r for r in index.rows}
    body_manifest=read(args.bottle/'manifest.json') if (args.bottle/'manifest.json').exists() else {'images':[]}
    rect_manifest=read(args.rectification/'manifest.json') if (args.rectification/'manifest.json').exists() else {'images':[]}
    rect_lookup={e['id']:e for e in rect_manifest['images']}
    entries=body_manifest['images'] or rect_manifest['images']
    input_files={}
    result_rows=[];sections=[];measurements=[]
    modes=[('label_crop','Исходная этикетка'),('label_multi','Все варианты этикетки'),('bottle','Вся бутылка'),('bottle_regions','Бутылка + отдельные области'),('combined','Все источники')]
    for entry in entries:
        identifier=entry['id'];groups={key:[] for key,_ in modes}
        rect=rect_lookup.get(identifier)
        if rect:
            if rect['source_sha256']!=entry['source_sha256']:
                raise ValueError('Same image id has different source hashes in label and bottle runs')
            if sha(ROOT/rect['source_path'])!=rect['source_sha256']:raise ValueError('Original image changed')
            for variant in rect['variants']:
                for language in ('russian','latin'):
                    path=args.rectification/'results'/f"{identifier}_{variant['name']}_{language}.json"
                    if not path.exists():continue
                    value=read(path)
                    if value['input_sha256']!=variant['sha256'] or sha(args.rectification/variant['path'])!=variant['sha256']:raise ValueError('Stale label OCR')
                    if value['config_sha256']!=sha(args.rectification/'ocr_config.json'):raise ValueError('Stale OCR config')
                    input_files[str(path.relative_to(ROOT))]=sha(path)
                    lines=load_lines(path,'label:'+path.stem)
                    groups['label_multi'].extend(lines)
                    if variant['name']=='B':groups['label_crop'].extend(lines)
        for variant in entry.get('variants',[]):
            # Only a body manifest has whole/additional_label variants.
            if variant['name'] in ('B','C','D'):continue
            for language in ('russian','latin'):
                path=args.bottle/'results'/f"{identifier}_{variant['name']}_{language}.json"
                if not path.exists():continue
                value=read(path)
                if value['input_sha256']!=variant['sha256'] or sha(args.bottle/variant['path'])!=variant['sha256']:raise ValueError('Stale bottle OCR')
                if value['config_sha256']!=sha(args.bottle/'ocr_config.json'):raise ValueError('Stale OCR config')
                input_files[str(path.relative_to(ROOT))]=sha(path)
                lines=load_lines(path,'bottle:'+path.stem)
                if entry.get('status')=='bottle_label_disagreement':
                    lines=[{**l,'on_target_bottle':False} for l in lines]
                groups['bottle_regions'].extend(lines)
                if variant['name']=='whole':groups['bottle'].extend(lines)
        groups['combined']=groups['label_multi']+groups['bottle_regions']
        searches={}
        for key,_ in modes:
            if not groups[key]:continue
            start=time.perf_counter();searches[key]=index.search(groups[key],args.limit);elapsed=(time.perf_counter()-start)*1000
            searches[key]['search_ms']=elapsed;measurements.append(elapsed)
        expected=entry.get('catalog_source_slug')
        if not expected and entry.get('group')=='catalog' and identifier.startswith('cat_'):expected=identifier[4:]
        # The expected source slug is used ONLY after ranking, for self-image diagnostics.
        ranks={key:next((i+1 for i,c in enumerate(result['candidates']) if c['slug']==expected),None) for key,result in searches.items()} if expected else {}
        result_rows.append({'id':identifier,'group':entry.get('group'),'source_catalog_slug_for_diagnostic_only':expected,
            'expected_rank_self_image':ranks,'searches':searches,'observations':groups['combined']})
        header_rows=[]
        for key,label in modes:
            result=searches.get(key)
            if not result:header_rows.append(f'<tr><td>{label}</td><td colspan="3">Нет OCR этого вида</td></tr>');continue
            candidates=result['candidates'];first=candidates[0] if candidates else None
            first_name=first['title']+' — '+str(first['winery']) if first else 'Нет текстовых кандидатов'
            rank=str(ranks[key]) if ranks.get(key) else ('вне top-'+str(args.limit) if expected else 'нет подтверждённого ответа')
            status_label={'tentative_match':'предварительный кандидат','needs_review':'неоднозначно / недостаточно данных','no_text_candidates':'нет текстовых совпадений'}[result['status']]
            header_rows.append(f'<tr><td>{label}</td><td>{html.escape(first_name)}</td><td>{status_label}</td><td>{rank}</td></tr>')
        source_preview=args.bottle/entry['source_preview'] if entry.get('source_preview') else args.rectification/entry['source_preview']
        if not source_preview.exists() and rect:source_preview=args.rectification/rect['source_preview']
        source_link=os.path.relpath(source_preview,args.output)
        size=entry.get('source_size') or (rect or {})['source_size']
        top=searches.get('combined',{}).get('candidates',[])
        candidate_cards=[]
        for rank,candidate in enumerate(top,1):
            slug=candidate['slug'];row=lookup[slug]
            thumbnail=args.output/'thumbnails'/f'{slug}.jpg'
            if not thumbnail.exists() and row.get('reference_path'):
                with Image.open(ROOT/row['reference_path']) as opened:
                    im=ImageOps.exif_transpose(opened).convert('RGBA');white=Image.new('RGBA',im.size,'white');white.alpha_composite(im);im=white.convert('RGB');im.thumbnail((110,220));thumbnail.parent.mkdir(exist_ok=True);im.save(thumbnail,quality=85)
            raw_evidence=[{'points':e['polygon_original'],'text':e['raw_line']} for e in candidate['evidence'] if e.get('polygon_original')]
            encoded=html.escape(json.dumps(raw_evidence,ensure_ascii=False),quote=True)
            evidence=''.join(f'<li><code>{html.escape(e["raw_word"])}</code> → <b>{html.escape(e["term"])}</b> · {e["field"]} · правок {e["distance"]}<br><small>{html.escape(e["raw_line"])} · {html.escape(e["source"])}</small></li>' for e in candidate['evidence'])
            conflicts=html.escape(json.dumps(candidate['conflicts'],ensure_ascii=False))
            missing=html.escape(', '.join(candidate['missing_query_terms']))
            thumb=f'<img class="thumb" src="{thumbnail.relative_to(args.output)}" alt="Эталон"/>' if thumbnail.exists() else ''
            candidate_cards.append(f'<details class="candidate" {"open" if rank==1 else ""}><summary>{rank}. {html.escape(candidate["title"])} — {html.escape(str(candidate["winery"]))} · score {candidate["score"]:.2f}</summary>{thumb}<p><code>{html.escape(slug)}</code></p><button class="show" data-polys="{encoded}">Подсветить совпавший текст на фотографии</button><p>Совпавшие слова: каталоговая нормализация, не переписанный OCR.</p><ul>{evidence}</ul><p>Противоречия: {conflicts}</p><p>Другие гипотезы OCR, не поддержанные этой карточкой: {missing or "—"}. Среди них возможны ошибки чтения.</p></details>')
        all_lines=''.join(f'<tr><td>{html.escape(o["text"])}</td><td>{o.get("confidence",0):.2f}</td><td>{"исключено: вне бутылки" if o.get("on_target_bottle") is False else "учтено"}</td><td><small>{html.escape(o["source"])}</small></td></tr>' for o in groups['combined'])
        bottle_box=entry.get('bottle_box_original')
        box_svg=''
        if bottle_box:
            x,y,z,t=bottle_box;box_svg=f'<rect x="{x}" y="{y}" width="{z-x}" height="{t-y}" fill="none" stroke="#19ad68" stroke-width="3" vector-effect="non-scaling-stroke"/>'
        note='Каталожное фото: проверка на собственном эталоне, не независимая оценка.' if expected else ('Иностранное вино: диагностический пример; подтверждённого slug нет.' if entry.get('group')=='grain_labels' else 'Реальная фотография: подтверждённого организаторами slug нет.')
        finding_path=args.output/'catalog_findings.json'
        if finding_path.exists() and any(f['slug']==expected for f in read(finding_path).get('findings',[])):
            note += ' ВНИМАНИЕ: на фото KD читается РИСЛИНГ, в карточке указан Шардоне. Первое место этой карточки не подтверждает правильность данных.'
        sections.append(f'<section id="{identifier}"><h2>{html.escape(identifier)}</h2><p>{note}</p><table><tr><th>Вход поиска</th><th>Первый кандидат</th><th>Статус</th><th>Место исходной карточки</th></tr>{"".join(header_rows)}</table><div class="layout"><div class="photo"><svg viewBox="0 0 {size[0]} {size[1]}"><image href="{source_link}" width="{size[0]}" height="{size[1]}"/>{box_svg}<g class="evidence"></g></svg></div><div>{"".join(candidate_cards) or "Нет кандидатов"}</div></div><details><summary>Все исходные строки OCR ({len(groups["combined"])})</summary><table>{all_lines}</table></details></section>')
    expected_ocr=sum(len(e['variants'])*2 for e in body_manifest['images'])
    completed_ocr=sum((args.bottle/'results'/f"{e['id']}_{v['name']}_{language}.json").exists() for e in body_manifest['images'] for v in e['variants'] for language in ('russian','latin'))
    counts={}
    for key,label in modes:
        evaluated=[r for r in result_rows if r['source_catalog_slug_for_diagnostic_only'] and key in r['searches']]
        counts[key]={'evaluated_self_images':len(evaluated),'rank1':sum(r['expected_rank_self_image'][key]==1 for r in evaluated),
            'rank5':sum(r['expected_rank_self_image'][key] is not None and r['expected_rank_self_image'][key]<=5 for r in evaluated)}
    provenance={'catalog_path':str(args.catalog),'catalog_sha256':sha(args.catalog),'catalog_cards':len(index.rows),
        'vocabulary_terms':len(index.vocabulary),'index_build_ms':build_ms,'algorithm':VERSION,
        'algorithm_sha256':sha(ROOT/'worker/pipeline/text_search.py'),'report_script_sha256':sha(Path(__file__)),
        'rapidfuzz_version':importlib.metadata.version('rapidfuzz'),'rectification_manifest_sha256':sha(args.rectification/'manifest.json') if rect_lookup else None,
        'bottle_manifest_sha256':sha(args.bottle/'manifest.json') if body_manifest['images'] else None,'ocr_inputs_sha256':input_files,
        'whole_bottle_ocr_completed':completed_ocr,'whole_bottle_ocr_expected':expected_ocr,'complete':completed_ocr==expected_ocr,
        'self_image_diagnostics':counts,'search_ms_p50':sorted(measurements)[len(measurements)//2] if measurements else None,
        'field_weights':__import__('pipeline.text_search',fromlist=['FIELDS']).FIELDS,
        'winery_aliases':__import__('pipeline.text_search',fromlist=['WINERY_ALIASES']).WINERY_ALIASES,
        'catalog_findings_sha256':sha(args.output/'catalog_findings.json') if (args.output/'catalog_findings.json').exists() else None,
        'evaluation_warning':'Ranks on catalogue reference images are self-image diagnostics; no real-photo exact-slug accuracy is measured.'}
    write(args.output/'results.json',{'provenance':provenance,'images':result_rows})
    (args.output/'provenance').mkdir(exist_ok=True)
    (args.output/'provenance/text_search.py').write_bytes((ROOT/'worker/pipeline/text_search.py').read_bytes())
    (args.output/'provenance/report_script.py').write_bytes(Path(__file__).read_bytes())
    metric_rows=''.join(f'<tr><td>{label}</td><td>{counts[key]["rank1"]} / {counts[key]["evaluated_self_images"]}</td><td>{counts[key]["rank5"]} / {counts[key]["evaluated_self_images"]}</td></tr>' for key,label in modes)
    page='''<!doctype html><html lang="ru"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Текстовый поиск вина</title><style>body{font:16px system-ui;color:#1e3045;margin:24px}h2,code,small{overflow-wrap:anywhere}section{border-top:2px solid #c2d1df;margin:40px 0;padding-top:16px}table{border-collapse:collapse;width:100%;font-size:14px}td,th{border-bottom:1px solid #ccd;padding:9px;text-align:left}.layout{display:grid;grid-template-columns:minmax(280px,.85fr) minmax(0,1.15fr);gap:24px;margin-top:20px}.photo{position:sticky;top:12px;align-self:start}svg{width:100%;max-height:720px}.candidate{padding:12px;background:#f4f7fa;margin-bottom:10px;border-radius:8px}summary,button{cursor:pointer}summary{font-weight:600}button{padding:8px;background:#e0edff;border:1px solid #7c9fbf;border-radius:5px}small{color:#596b80}.thumb{float:right;max-height:190px;max-width:90px;margin:10px}li{margin:10px 0}details{margin:15px 0}@media(max-width:800px){.layout{grid-template-columns:1fr}.photo{position:static}}</style><h1>Текстовый поиск по каталогу: этикетка и вся бутылка</h1><p>Поиск использует только OCR и поля каталога: исходные названия, производителя, сорта, категорию, регион и вспомогательные слова slug. Имя файла, ожидаемая карточка и ручная транскрипция запроса в ранжирование не поступают. Score — эвристическая оценка, не вероятность. Зелёная рамка — выбранная бутылка, синие полигоны — исходные области текста, поддержавшего кандидата.</p><p>Сохранены альтернативы русского и латинского OCR. Повторное прочтение того же каталожного слова не умножает его вес. TM и служебные слова игнорируются. Это текстовый прототип: визуальная поисковая модель пока не подключена, а отказ от неизвестного вина ещё не откалиброван.</p>'''
    page+=f'<p><b>Каталог: {len(index.rows)} карточки. OCR бутылки: {completed_ocr}/{expected_ocr} результатов.</b></p><h2>Диагностика на каталожных эталонах</h2><p>Доли ниже не являются точностью на реальных телефонных снимках. Для двух новых случаев отдельный исходный кроп этикетки не прогонялся.</p><table><tr><th>Вход</th><th>Первое место</th><th>В первых пяти</th></tr>{metric_rows}</table><p><a href="results.json">Все результаты и происхождение данных (JSON)</a></p>'
    if (args.output/'catalog_findings.json').exists():
        page+='<p><b>Есть противоречие фото и метаданных KD и два несвязанных реальных запроса.</b> <a href="catalog_findings.json">Находки для ревью</a>. Каталог не изменён.</p>'
    page+=''.join(sections)+'''<script>document.querySelectorAll('section').forEach(section=>{const show=button=>{const layer=section.querySelector('g.evidence');layer.replaceChildren();JSON.parse(button.dataset.polys).forEach(item=>{const p=document.createElementNS('http://www.w3.org/2000/svg','polygon');p.setAttribute('points',item.points.map(x=>x.join(',')).join(' '));p.setAttribute('fill','#3373df22');p.setAttribute('stroke','#3373df');p.setAttribute('stroke-width','2');p.setAttribute('vector-effect','non-scaling-stroke');const t=document.createElementNS('http://www.w3.org/2000/svg','title');t.textContent=item.text;p.appendChild(t);layer.appendChild(p);});};section.querySelectorAll('button.show').forEach(b=>b.addEventListener('click',()=>show(b)));const first=section.querySelector('button.show');if(first)show(first);});</script></html>'''
    (args.output/'review.html').write_text(page)
    print(json.dumps(provenance['self_image_diagnostics'],ensure_ascii=False))
    print('Report:',args.output/'review.html')


def main() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['query','report'])
    parser.add_argument('--catalog',type=Path,default=CATALOG)
    parser.add_argument('--text')
    parser.add_argument('--ocr',type=Path,nargs='+')
    parser.add_argument('--save',type=Path)
    parser.add_argument('--limit',type=int,default=10)
    parser.add_argument('--output',type=Path,default=OUTPUT)
    parser.add_argument('--bottle',type=Path,default=BODY)
    parser.add_argument('--rectification',type=Path,default=RECT)
    args=parser.parse_args()
    if args.limit<1:parser.error('--limit must be positive')
    {'query':query,'report':report}[args.command](args)


if __name__=='__main__':main()

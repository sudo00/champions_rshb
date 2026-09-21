"""Separate SAM-mask -> cylinder rectification/augmentation -> paired OCR study."""
from __future__ import annotations

import argparse
import html
import importlib.metadata
import json
import os
from pathlib import Path
import re
import sys
import time

import cv2
import numpy as np
from PIL import Image, ImageDraw

from label_rectification_pilot import (ROOT, DETECTOR, RECOGNIZERS,
    digest, read, write, immutable, rgb_oriented, normalized, edit_distance)
from worker.pipeline.rectification import GeometryRejected, scaled_contour, points_h
from worker.pipeline.cylinder_geometry import fit_geometry, rectify, input_to_original, densify_polygon, render_view, rim_basis

BASE = ROOT/'data/audit/label_rectification/pilot12_v4'
BODY = ROOT/'data/audit/bottle_ocr/pilot14_v1'
DEFAULT = ROOT/'data/audit/cylinder_labels/pilot12_v1'
NAMES = {'B':'Исходный кроп', 'D':'Гомография', 'E':'Выравнивание дуг', 'F':'Дуги + цилиндрическая развёртка'}


def checked(path: Path, expected: str) -> None:
    if digest(path) != expected:
        raise ValueError(f'Changed input: {path}')


def prepare(args: argparse.Namespace) -> None:
    if (args.output/'manifest.json').exists():
        raise ValueError('Use a new output directory for a new preparation')
    baseline = read(BASE/'manifest.json')
    bodies = {e['id']:e for e in read(BODY/'manifest.json')['images']}
    records = []
    for old in baseline['images']:
        entry = {k:old[k] for k in ('id','group','source_path','source_sha256','source_size','mask_path','mask_sha256','localization_failure')}
        checked(ROOT/old['source_path'],old['source_sha256'])
        checked(ROOT/old['mask_path'],old['mask_sha256'])
        if old['localization_failure']:
            records.append({**entry,'status':'skipped','reason':'existing_incomplete_label_localization','variants':[]})
            continue
        image,_ = rgb_oriented(ROOT/old['source_path'])
        array = np.array(image)
        mask = np.array(Image.open(ROOT/old['mask_path']).convert('L')) > 0
        contour,fraction = scaled_contour(mask,image.size)
        body = bodies[old['id']]
        if body['source_sha256'] != old['source_sha256'] or body.get('status')=='bottle_label_disagreement':
            raise ValueError('Bottle and label source/selection disagree')
        checked(BODY/body['mask_path'],body['mask_sha256'])
        bottle,_ = scaled_contour(np.array(Image.open(BODY/body['mask_path']).convert('L'))>0,image.size)
        align = np.array(next(v for v in old['variants'] if v['name']=='C').get('matrix',np.eye(3)))
        entry.update(bottle_mask_path=str((BODY/body['mask_path']).relative_to(ROOT)),bottle_mask_sha256=body['mask_sha256'])
        variants = []
        for v in old['variants']:
            if v['name'] in ('B','D'):
                checked(BASE/v['path'],v['sha256'])
                variants.append({**v,'path':str((BASE/v['path']).relative_to(ROOT)),'existing_ocr':True})
        start=time.perf_counter()
        try:
            if fraction < .97:
                raise GeometryRejected('disconnected_mask')
            model=fit_geometry(contour,bottle,align)
            entry.update(status='applied',model=model)
            for name in ('E','F'):
                pixels,grid=rectify(array,model,name)
                path=args.output/'images'/f"{old['id']}_{name}.png"
                path.parent.mkdir(parents=True,exist_ok=True)
                Image.fromarray(pixels).save(path)
                variants.append({'name':name,'path':str(path.relative_to(ROOT)),'sha256':digest(path),
                                 'size':list(pixels.shape[1::-1]),'existing_ocr':False,'status':'applied'})
                if name=='F':
                    full_mask=cv2.resize(mask.astype('uint8')*255,image.size,interpolation=cv2.INTER_NEAREST)
                    texture_mask=cv2.remap(full_mask,grid[:,:,0],grid[:,:,1],cv2.INTER_NEAREST)
                    aug=[]
                    for yaw,pitch,roll in [(0,0,0),(-20,0,0),(20,0,0),(0,-15,-8),(0,15,8)]:
                        rgba=render_view(pixels,texture_mask,model['theta_range'],model['height']*1.08/model['radius'],yaw,pitch,roll)
                        target=args.output/'augmentation'/f"{old['id']}_{yaw}_{pitch}_{roll}.png"
                        target.parent.mkdir(parents=True,exist_ok=True)
                        Image.fromarray(rgba).save(target)
                        aug.append({'path':str(target.relative_to(ROOT)),'sha256':digest(target),'yaw':yaw,'pitch':pitch,'roll':roll})
                    entry['augmentation']=aug
            # Show the actual segmentation plus fitted rims on original RGB.
            overlay=image.copy();draw=ImageDraw.Draw(overlay)
            draw.line([tuple(p) for p in contour[::max(1,len(contour)//600)]],fill='#24d984',width=max(2,image.width//350))
            x=np.linspace(*model['x_range'],160)
            for coefficients in (model['top'],model['bottom']):
                y=rim_basis(x,model['center'],model['radius'])@coefficients
                pts=points_h(np.c_[x,y],np.array(model['aligned_to_original']))
                draw.line([tuple(p) for p in pts],fill='#ff466e',width=max(2,image.width//350))
            overlay.thumbnail((900,900))
            path=args.output/'images'/f"{old['id']}_fit.jpg";overlay.save(path,quality=90)
            entry['overlay']=str(path.relative_to(ROOT))
        except GeometryRejected as exc:
            entry.update(status='rejected',reason=str(exc))
            # Explicit original-crop fallback keeps a paired all-case comparison.
            for name in ('E','F'):
                variants.append({**variants[0],'name':name,'status':'fallback_B','existing_ocr':True,'reuse_variant':'B'})
        entry.update(geometry_seconds=time.perf_counter()-start,variants=variants)
        records.append(entry)
        print(entry['id'],entry['status'],entry.get('reason',''),flush=True)
    write(args.output/'manifest.json',{'method':'weak_perspective_elliptical_rims_v1','max_side':1600,
        'baseline_manifest_sha256':digest(BASE/'manifest.json'),'bottle_manifest_sha256':digest(BODY/'manifest.json'),
        'scripts_sha256':{p.name:digest(p) for p in (Path(__file__),ROOT/'worker/pipeline/cylinder_geometry.py', ROOT/'worker/pipeline/rectification.py')},
        'images':records})
    for file in (Path(__file__),ROOT/'worker/pipeline/cylinder_geometry.py', ROOT/'worker/pipeline/rectification.py'):
        p=args.output/'provenance'/file.name;p.parent.mkdir(exist_ok=True);p.write_bytes(file.read_bytes())


def run(args: argparse.Namespace) -> None:
    manifest=read(args.output/'manifest.json')
    cache=ROOT/'weights/cache/ocr'
    for name,value in {'PADDLE_PDX_CACHE_HOME':cache/'paddlex','PADDLE_HOME':cache/'paddle',
                       'HF_HOME':cache/'huggingface','XDG_CACHE_HOME':cache/'xdg'}.items():
        os.environ.setdefault(name,str(value))
    os.environ.setdefault('PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK','True')
    for e in manifest['images']:
        for key in ('source','mask','bottle_mask'):
            if key+'_path' in e:checked(ROOT/e[key+'_path'],e[key+'_sha256'])
        for v in e['variants']:checked(ROOT/v['path'],v['sha256'])
    models=cache/'paddlex/official_models'
    baseline_config=read(BASE/'ocr_config.json')
    for model,files in baseline_config['models_sha256'].items():
        for file,expected in files.items():checked(models/model/file,expected)
    config={**baseline_config,'manifest_sha256':digest(args.output/'manifest.json'),
            'versions':{n:importlib.metadata.version(n) for n in ('paddleocr','paddlepaddle','paddlex','numpy','Pillow')},
            'mapping':'nonlinear_dense_polygon_12_samples_per_edge'}
    immutable(args.output/'ocr_config.json',config)
    from paddleocr import PaddleOCR
    for language,model in RECOGNIZERS.items():
        pending=[]
        for e in manifest['images']:
            for v in e['variants']:
                if v['existing_ocr']:continue
                path=args.output/'results'/f"{e['id']}_{v['name']}_{language}.json"
                if path.exists():
                    val=read(path)
                    if val['config_sha256']!=digest(args.output/'ocr_config.json') or val['input_sha256']!=v['sha256']:
                        raise ValueError('Stale OCR cache')
                else:pending.append((e,v,path))
        if not pending:continue
        start=time.perf_counter()
        engine=PaddleOCR(text_detection_model_name=DETECTOR,text_detection_model_dir=str(models/DETECTOR),
            text_recognition_model_name=model,text_recognition_model_dir=str(models/model),
            use_doc_orientation_classify=False,use_doc_unwarping=False,use_textline_orientation=False,
            text_rec_score_thresh=0.,text_det_limit_side_len=1600,text_det_limit_type='max',
            device='cpu',cpu_threads=4,enable_mkldnn=False)
        init=time.perf_counter()-start
        sample=np.array(Image.open(ROOT/pending[0][1]['path']).convert('RGB'))[:,:,::-1].copy()
        start=time.perf_counter();list(engine.predict(sample));warmup=time.perf_counter()-start
        write(args.output/'timing'/f'{language}.json',{'initialization_seconds':init,'warmup_seconds':warmup})
        for e,v,path in pending:
            pixels=np.array(Image.open(ROOT/v['path']).convert('RGB'))[:,:,::-1].copy()
            start=time.perf_counter();predictions=list(engine.predict(pixels));seconds=time.perf_counter()-start
            if len(predictions)!=1:raise ValueError('Expected one OCR result')
            raw=predictions[0].json;raw=json.loads(raw) if isinstance(raw,str) else raw
            data=raw.get('res',raw);lines=[]
            if not len(data['rec_texts'])==len(data['rec_scores'])==len(data['rec_polys']):raise ValueError('OCR arrays disagree')
            for text,score,polygon in zip(data['rec_texts'],data['rec_scores'],data['rec_polys']):
                mapped=input_to_original(densify_polygon(polygon),e['model'],v['name'],v['size'])
                center=input_to_original(np.mean(polygon,axis=0)[None,:],e['model'],v['name'],v['size'])[0]
                lines.append({'text':text,'confidence':float(score),'polygon_input':polygon,
                              'polygon_original':mapped.tolist(),'center_original':center.tolist()})
            write(path,{'image_id':e['id'],'variant':v['name'],'language':language,'source_sha256':e['source_sha256'],
                        'config_sha256':digest(args.output/'ocr_config.json'),'input_sha256':v['sha256'],
                        'inference_seconds':seconds,'lines':lines,'raw':raw})
            print(e['id'],v['name'],language,len(lines),round(seconds,2),flush=True)
        del engine


def load_result(output: Path, entry: dict, variant: dict, language: str) -> tuple[dict,Path]:
    name=variant.get('reuse_variant',variant['name'])
    directory=BASE if variant['existing_ocr'] else output
    path=directory/'results'/f"{entry['id']}_{name}_{language}.json"
    value=read(path)
    if value['input_sha256']!=variant['sha256'] or value['config_sha256']!=digest(directory/'ocr_config.json'):
        raise ValueError('Stale OCR result')
    checked(ROOT/variant['path'],variant['sha256'])
    return value,path


def identity_normalized(text: str) -> str:
    return normalized(re.sub(r'\bTM\b|[™®]', '', text, flags=re.IGNORECASE))


def report(args: argparse.Namespace) -> None:
    sys.path.insert(0,str(ROOT/'worker'))
    from pipeline.text_search import CatalogTextIndex
    index=CatalogTextIndex.from_file(ROOT/'data/catalog/curated/catalog.jsonl')
    manifest=read(args.output/'manifest.json')
    checked(BASE/'manifest.json',manifest['baseline_manifest_sha256'])
    baseline={e['id']:e for e in read(BASE/'manifest.json')['images']}
    audit=read(BASE/'evaluation_annotations.json');checked(BASE/'manifest.json',audit['manifest_sha256'])
    bodies={e['id']:e for e in read(BODY/'manifest.json')['images']}
    sections=[];measurements=[];searches=[];input_hashes={}
    def url(path):return html.escape(os.path.relpath(ROOT/path,args.output))
    def result_lines(value,path):
        input_hashes[str(path.relative_to(ROOT))]=digest(path)
        return [{**l,'source':path.stem+':'+str(i)} for i,l in enumerate(value['lines'])]
    for e in manifest['images']:
        if not e['variants']:
            sections.append(f"<section><h2>{html.escape(e['id'])}</h2><p>Пропуск: неполная локализация основной этикетки; требуется OCR бутылки.</p></section>")
            continue
        cards=[];new_lines=[];fields=audit['corrected_fields'].get(e['id'],baseline[e['id']]['fields'])
        for v in e['variants']:
            values={};lines=[]
            for language in RECOGNIZERS:
                val,path=load_result(args.output,e,v,language);values[language]=val
                lines.extend(result_lines(val,path))
            if v['name'] in ('E','F'):new_lines.extend(lines)
            field_rows=[]
            for field in fields:
                x0,y0,x1,y1=field['box_original_xyxy'];words=[]
                for line in values[field['language']]['lines']:
                    x,y=line.get('center_original',np.mean(line['polygon_original'],axis=0))
                    if x0<=x<=x1 and y0<=y<=y1:words.append(line['text'])
                prediction=' '.join(words)
                expected=identity_normalized(field['text']);actual=identity_normalized(prediction)
                error=edit_distance(expected,actual)
                measurements.append({'id':e['id'],'variant':v['name'],'expected':field['text'],'predicted':prediction,
                    'language':field['language'],'errors':error,'characters':len(expected),'exact':expected==actual,
                    'strict_errors':edit_distance(normalized(field['text']),normalized(prediction)),
                    'strict_characters':len(normalized(field['text'])),'status':v['status']})
                field_rows.append(f"<tr><td>{html.escape(field['text'])}</td><td>{html.escape(prediction)}</td></tr>")
            result=index.search(lines,5)
            searches.append({'id':e['id'],'variant':v['name'],'result':result})
            first=result['candidates'][0]['title'] if result['candidates'] else 'Нет кандидатов'
            raw='<br>'.join(html.escape(l['text']) for l in lines)
            cards.append(f'<article><h3>{v["name"]}: {NAMES[v["name"]]}</h3><a href="{url(v["path"])}"><img src="{url(v["path"])}"></a><p>{v["status"]}</p><table><tr><th>Эталон</th><th>OCR</th></tr>{"".join(field_rows)}</table><p>Первый кандидат: {html.escape(first)}</p><details><summary>Все строки RU + Latin</summary>{raw}</details></article>')
        # Test adding the new branch to all available existing OCR; no GT in ranking.
        old_lines=[]
        for v in baseline[e['id']]['variants']:
            for language in RECOGNIZERS:
                p=BASE/'results'/f"{e['id']}_{v['name']}_{language}.json"
                val=read(p)
                if val['input_sha256']!=v['sha256'] or val['config_sha256']!=digest(BASE/'ocr_config.json'):raise ValueError('Stale baseline OCR')
                old_lines.extend(result_lines(val,p))
        body=bodies[e['id']]
        if body['source_sha256']!=e['source_sha256']:raise ValueError('Different bottle photo')
        for v in body['variants']:
            for language in RECOGNIZERS:
                p=BODY/'results'/f"{e['id']}_{v['name']}_{language}.json";val=read(p)
                if val['input_sha256']!=v['sha256'] or val['config_sha256']!=digest(BODY/'ocr_config.json'):raise ValueError('Stale bottle OCR')
                old_lines.extend(result_lines(val,p))
        combined_rows=[]
        for name,lines in [('combined_before',old_lines),('combined_with_cylinder',old_lines+new_lines)]:
            result=index.search(lines,10)
            expected=e['id'][4:] if e['group']=='catalog' else None
            rank=next((i+1 for i,c in enumerate(result['candidates']) if c['slug']==expected),None) if expected else None
            searches.append({'id':e['id'],'variant':name,'result':result,'source_card_rank_diagnostic_only':rank})
            first=result['candidates'][0]['title'] if result['candidates'] else 'Нет текстовых кандидатов'
            label='Все прежние OCR + бутылка' if name=='combined_before' else 'То же + E/F'
            rank_text=str(rank) if expected else 'Нет эталонного slug'
            combined_rows.append(f'<tr><td>{label}</td><td>{html.escape(first)}</td><td>{result["status"]}</td><td>{rank_text}</td></tr>')
        augment=''.join(f'<figure><img src="{url(a["path"])}"><figcaption>yaw {a["yaw"]}°, pitch {a["pitch"]}°, roll {a["roll"]}°</figcaption></figure>' for a in e.get('augmentation',[]))
        overlay=f'<details><summary>Маска SAM 3 (зелёный) и аппроксимация дуг (красный)</summary><img class="overlay" src="{url(e["overlay"])}"></details>' if e.get('overlay') else ''
        comparison='<h3>Добавление ветви к общему поиску</h3><table><tr><th>Источники</th><th>Первый кандидат</th><th>Статус</th><th>Место исходной карточки</th></tr>'+''.join(combined_rows)+'</table>'
        sections.append(f'<section><h2>{html.escape(e["id"])}</h2><p>{e["status"]}: {html.escape(e.get("reason",""))}</p>{overlay}<div class="grid">{"".join(cards)}</div>{comparison}<details><summary>Синтетические ракурсы — только видимая текстура</summary><div class="aug">{augment}</div></details></section>')
    summary={}
    for name in NAMES:
        rows=[r for r in measurements if r['variant']==name]
        summary[name]={'fields':len(rows),'exact':sum(r['exact'] for r in rows),
            'cer':sum(r['errors'] for r in rows)/sum(r['characters'] for r in rows),
            'strict_cer':sum(r['strict_errors'] for r in rows)/sum(r['strict_characters'] for r in rows)}
    result={'summary':summary,'measurements':measurements,'searches':searches,'ocr_input_sha256':input_hashes,
            'manifest_sha256':digest(args.output/'manifest.json'),'annotation_sha256':digest(BASE/'evaluation_annotations.json'),
            'catalog_sha256':digest(ROOT/'data/catalog/curated/catalog.jsonl'),
            'search_code_sha256':digest(ROOT/'worker/pipeline/text_search.py'),
            'report_code_sha256':digest(Path(__file__))}
    write(args.output/'metrics.json',result)
    (args.output/'provenance/report_script.py').write_bytes(Path(__file__).read_bytes())
    rows=''.join(f'<tr><td>{k}: {NAMES[k]}</td><td>{v["exact"]}/{v["fields"]}</td><td>{100*v["cer"]:.1f}%</td></tr>' for k,v in summary.items())
    page='''<!doctype html><html lang="ru"><meta charset="utf-8"><title>Цилиндрическая ректификация</title><style>
    body{font:16px system-ui;background:#f3f5f8;color:#18263a;margin:28px}section,header{background:white;padding:22px;margin-bottom:24px;border-radius:12px}h2{overflow-wrap:anywhere}table{border-collapse:collapse;width:100%;font-size:14px}td,th{border:1px solid #d7dce4;padding:7px;text-align:left;overflow-wrap:anywhere}.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:16px}article img{width:100%;height:400px;object-fit:contain;background:#e9edf2}article{min-width:0}details{margin:14px 0}.aug{display:flex;flex-wrap:wrap}.aug figure{width:210px;margin:8px}.aug img{width:100%;background:repeating-conic-gradient(#ddd 0% 25%,#fff 0% 50%) 50%/18px 18px}.overlay{max-height:650px;max-width:100%}@media(max-width:1000px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}}</style>'''
    page+=f'<header><h1>Цилиндрическая ректификация: отдельный опыт</h1><p>SAM 3 → эллиптические дуги → развёртка → OCR. Приближённая модель исходного кадра: слабая перспектива и параллельные образующие. Это не полное воспроизведение статьи с точкой схода и cross-ratio. Синтетические ракурсы используют виртуальную перспективную камеру.</p><p>10 оцениваемых изображений, 31 поле; ещё 2 — контроль неполной локализации. Язык каждого поля и транскрипции зафиксированы ранее. TM игнорируется в основной текстовой метрике; ошибки года сохранены. Меньше CER — лучше. Метрики на знакомых диагностических примерах; независимая точность приложения не измерена.</p><table><tr><th>Вариант</th><th>Полей прочитано точно</th><th>Ошибки символов (CER)</th></tr>{rows}</table><p>Выбор лучшей ветви по эталонному тексту не выполняется. Исходный кроп и OCR всей бутылки сохраняются. Аугментации ещё не использовались для обучения.</p></header>'+''.join(sections)+'</html>'
    (args.output/'review.html').write_text(page)
    print(json.dumps(summary,ensure_ascii=False,indent=2))


def main() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['prepare','run','report'])
    parser.add_argument('--output',type=Path,default=DEFAULT)
    args=parser.parse_args();args.output=args.output.resolve()
    globals()[args.command](args)


if __name__=='__main__':main()

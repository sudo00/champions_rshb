"""SAM 3 capture diagnostics: presence, packaging hypotheses and possible hand occlusion.

Exploratory outputs, not a calibrated wine/material/physical-size classifier.
All detected hand instances are retained. Original images are never changed.
"""
from __future__ import annotations
import argparse
import html
import json
from pathlib import Path
import time

import cv2
import numpy as np
from PIL import Image, ImageDraw

from label_rectification_pilot import ROOT, read, write, digest, immutable, rgb_oriented
from label_segmentation_pilot import choose_main_label, coverage
from sam3_gpu_study import load_runtime

DEFAULT=ROOT/'data/audit/capture_quality/pilot20_v1'
PROMPTS=['bottle','label','hand','glass bottle','plastic bottle','can','wine box','beverage carton','pouch','small bottle']
STRONG=.5


def hand_relation(label: np.ndarray, hands: np.ndarray) -> dict:
    """Visible masks need not overlap. Hull is an uncertain completion hypothesis."""
    label=(label>0).astype(np.uint8);hands=hands>0
    if not label.any():return {'status':'unassessable','reason':'no_visible_label'}
    contours,_=cv2.findContours(label,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
    envelope=np.zeros_like(label)
    for contour in contours:cv2.fillConvexPoly(envelope,cv2.convexHull(contour),1)
    ys,xs=np.nonzero(label);radius=max(1,round(min(np.ptp(xs)+1,np.ptp(ys)+1)*.02))
    near=cv2.dilate(label,cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(2*radius+1,2*radius+1)))>0
    intrusion=int(np.count_nonzero((envelope>0)&hands))
    direct=int(np.count_nonzero((label>0)&hands))
    contact=int(np.count_nonzero(near&hands))
    ratio=intrusion/max(1,int(envelope.sum()))
    status='possible_occlusion' if ratio>=.01 else ('near_label' if contact else 'no_contact_evidence')
    return {'status':status,'visible_mask_overlap_pixels':direct,'hull_hand_fraction':ratio,
            'near_label_hand_pixels':contact,'note':'Heuristic, not hidden text coverage. A missed hand or missing label can hide occlusion.'}


def prepare(args):
    if (args.output/'manifest.json').exists():raise ValueError('Choose a new output directory')
    if args.input:
        if len({p.stem for p in args.input})!=len(args.input):raise ValueError('Input stems must be unique')
        selected=[{'id':p.stem,'path':str(p.resolve()),'source_sha256':digest(p),'group':'user_example',
                   'title':p.name,'origin':'existing_original'} for p in args.input]
        write(args.output/'manifest.json',{'selection':'User-provided examples; not representative accuracy','images':selected})
        print('Prepared',len(selected),'images,',len(selected)*len(PROMPTS),'prompt calls')
        return
    entries=read(ROOT/'data/audit/sam3_gpu/manifest.json')['entries']
    ids=['019c68d0','02eef911','096ca74e']
    ids += ['grain_labels_Good_Lighting_'+str(i) for i in [244,1150,1779,1653,286]]
    ids += ['cat_vinodelnya-konstantina-dzitoeva-kd-bryut-shardone-beloe-115','cat_agora-yachting-cabernet-sauvignon']
    packs=[e for e in entries if e.get('packaging')]
    ids += [e['id'] for e in packs if 'boks' in e['id']][:2]
    ids += [e['id'] for e in packs if 'banke' in e['id']][:2]
    ids += ['grain_negative_Good_Lighting_'+str(i) for i in [548,532,1243,39]]
    lookup={e['id']:e for e in entries};selected=[]
    for identifier in ids:
        e=lookup[identifier]
        selected.append({'id':identifier,'path':e['path'],'source_sha256':digest(Path(e['path'])),
                         'group':e['group'],'title':e.get('title',identifier),'origin':'existing_original'})
    # Controlled no-object probes, explicitly not a natural-scene validation set.
    source=lookup['grain_negative_Good_Lighting_548'];im,_=rgb_oriented(Path(source['path']))
    box=[0,0,max(1,round(im.width*.20)),im.height]
    crop=im.crop(box);path=args.output/'controls/empty_wall_crop.png';path.parent.mkdir(parents=True,exist_ok=True);crop.save(path)
    selected.append({'id':'control_wall_crop','path':str(path),'source_sha256':digest(path),'group':'control','title':'Background crop: no target',
                     'origin':'derived_crop','parent_path':source['path'],'parent_sha256':digest(Path(source['path'])),'crop_box':box})
    path=args.output/'controls/blank.png';Image.new('RGB',(640,800),'#d6d6d6').save(path)
    selected.append({'id':'control_blank','path':str(path),'source_sha256':digest(path),'group':'control','title':'Synthetic blank', 'origin':'synthetic'})
    write(args.output/'manifest.json',{'selection':'Diagnostic fixed cases; not representative accuracy','images':selected})
    print('Prepared',len(selected),'images,',len(selected)*len(PROMPTS),'prompt calls')


def run(args):
    import torch
    manifest=read(args.output/'manifest.json')
    config={'manifest_sha256':digest(args.output/'manifest.json'),'prompts':PROMPTS,'save_threshold':.2,'strong_threshold':STRONG,
            'mask_threshold':.5,'max_mask_side':768,'precision':'bf16','script_sha256':digest(Path(__file__))}
    immutable(args.output/'config.json',config)
    for e in manifest['images']:
        if digest(Path(e['path']))!=e['source_sha256']:raise ValueError('Source changed')
    model,processor,runtime=load_runtime('bf16');write(args.output/'runtime.json',runtime)
    (args.output/'provenance').mkdir(exist_ok=True)
    (args.output/'provenance/run_script.py').write_bytes(Path(__file__).read_bytes())
    for e in manifest['images']:
        image,_=rgb_oriented(Path(e['path']));w,h=image.size;scale=min(1.,768/max(w,h));mw,mh=round(w*scale),round(h*scale)
        preview=image.copy();preview.thumbnail((768,768));p=args.output/'previews'/f"{e['id']}.jpg";p.parent.mkdir(exist_ok=True);preview.save(p,quality=90)
        for prompt in PROMPTS:
            stem=e['id']+'__'+prompt.replace(' ','_');path=args.output/'results'/f'{stem}.json'
            if path.exists():
                old=read(path)
                if old['config_sha256']!=digest(args.output/'config.json'):raise ValueError('Stale cache')
                for c in old['candidates']:
                    if digest(args.output/c['mask_path'])!=c['mask_sha256']:raise ValueError('Mask changed')
                continue
            torch.cuda.synchronize();start=time.perf_counter()
            inputs=processor(images=image,text=prompt,return_tensors='pt').to('cuda')
            with torch.inference_mode(),torch.autocast('cuda',dtype=torch.bfloat16):out=model(**inputs)
            parsed=processor.post_process_instance_segmentation(out,threshold=.2,mask_threshold=.5,target_sizes=[[mh,mw]])[0]
            scores=parsed['scores'].float().cpu().tolist();boxes=parsed['boxes'].float().cpu().tolist()
            masks=parsed['masks'].to(torch.uint8).cpu().numpy()
            presence=float(out.presence_logits.sigmoid().float().cpu().flatten()[0])
            maximum=float((out.pred_logits.sigmoid()*out.presence_logits.sigmoid()).max().float().cpu())
            torch.cuda.synchronize();seconds=time.perf_counter()-start;candidates=[]
            for i,(score,box,mask) in enumerate(zip(scores,boxes,masks)):
                mp=args.output/'masks'/f'{stem}_{i}.png';mp.parent.mkdir(exist_ok=True);Image.fromarray(mask*255).save(mp)
                candidates.append({'score':score,'box_mask_xyxy':box,'box_xyxy':[box[0]*w/mw,box[1]*h/mh,box[2]*w/mw,box[3]*h/mh],
                                   'mask_path':str(mp.relative_to(args.output)),'mask_sha256':digest(mp),'area_fraction':float(mask.mean())})
            write(path,{'id':e['id'],'prompt':prompt,'source_sha256':e['source_sha256'],'source_size':[w,h],'mask_size':[mw,mh],
                        'config_sha256':digest(args.output/'config.json'),'presence_score':presence,'max_detection_score':maximum,
                        'inference_seconds':seconds,'candidates':candidates})
        print(e['id'],'complete',flush=True)


def report(args):
    manifest=read(args.output/'manifest.json');records=[];sections=[]
    if read(args.output/'config.json')['manifest_sha256']!=digest(args.output/'manifest.json'):raise ValueError('Manifest changed')
    for e in manifest['images']:
        if digest(Path(e['path']))!=e['source_sha256']:raise ValueError('Source changed')
        results={p:read(args.output/'results'/(e['id']+'__'+p.replace(' ','_')+'.json')) for p in PROMPTS}
        for result in results.values():
            if result['source_sha256']!=e['source_sha256'] or result['config_sha256']!=digest(args.output/'config.json'):raise ValueError('Stale result')
        w,h=results['label']['source_size'];mw,mh=results['label']['mask_size']
        def strong(prompt):return [c for c in results[prompt]['candidates'] if c['score']>=STRONG and c['area_fraction']>.001]
        def mask(c):
            path=args.output/c['mask_path']
            if digest(path)!=c['mask_sha256']:raise ValueError('Mask changed')
            return np.array(Image.open(path))>0
        objects=[{**c,'concept':p} for p in ['bottle','can','wine box','beverage carton','pouch'] for c in strong(p)]
        choice=choose_main_label(objects,w,h)['label_index'];target=objects[choice] if choice is not None else None
        target_support=cv2.dilate(mask(target).astype(np.uint8),np.ones((5,5),np.uint8))>0 if target else None
        labels=[];associations=[]
        for candidate in strong('label'):
            candidate_mask=mask(candidate)
            fraction=float(np.count_nonzero(candidate_mask&target_support)/max(1,np.count_nonzero(candidate_mask))) if target else None
            eligible=target is None or (coverage(candidate['box_xyxy'],target['box_xyxy'])>=.75 and fraction>=.75)
            associations.append({'mask_path':candidate['mask_path'],'target_mask_coverage':fraction,'eligible':eligible})
            if eligible:labels.append(candidate)
        # Preserve separate label regions; never hull the space between two stickers.
        hands=np.zeros((mh,mw),dtype=bool)
        for c in strong('hand'):hands|=mask(c)
        relations=[hand_relation(mask(c),hands) for c in labels]
        hand_state='possible_occlusion' if any(r['status']=='possible_occlusion' for r in relations) else ('near_label' if any(r['status']=='near_label' for r in relations) else ('unassessable' if not labels else 'no_contact_evidence'))
        pack=[{'concept':p,'score':c['score']} for p in ['bottle','can','wine box','beverage carton','pouch','glass bottle','plastic bottle','small bottle'] for c in strong(p)
              if target and coverage(c['box_xyxy'],target['box_xyxy'])>=.65 and coverage(target['box_xyxy'],c['box_xyxy'])>=.65]
        object_state='detected' if target else ('uncertain' if max(results[p]['max_detection_score'] for p in ['bottle','can','wine box','beverage carton','pouch'])>=.2 else 'not_detected')
        messages=[]
        if not target and not labels:messages.append('Поместите бутылку или другую упаковку перед камерой.')
        elif not labels:messages.append('Поверните упаковку названием к камере.')
        if hand_state=='possible_occlusion':messages.append('Возможно, рука закрывает часть этикетки. Держите упаковку за горлышко или основание.')
        row={'id':e['id'],'object_state':object_state,'bottle_state':'detected' if strong('bottle') else ('uncertain' if results['bottle']['max_detection_score']>=.2 else 'not_detected'),
             'label_state':'detected' if labels else 'not_detected','hand_state':hand_state,'hand_detected':bool(strong('hand')),
             'packaging_hypotheses':pack,'physical_size':'unknown','material':'unknown','wine_identity':'unknown',
             'relations':relations,'label_associations':associations,'messages':messages,'target':target,'warning':'Exploratory scores/geometry; not calibrated presence or occlusion probability.'}
        records.append(row)
        image,_=rgb_oriented(Path(e['path']));pixels=np.array(image.resize((mw,mh)))
        for selected,color in [([target] if target else [],[0,180,240]),(labels,[20,220,100]),(strong('hand'),[255,110,25])]:
            union=np.zeros((mh,mw),dtype=bool)
            for c in selected:union|=mask(c)
            pixels[union]=(pixels[union]*.65+np.array(color)*.35).astype(np.uint8)
        op=args.output/'overlays'/f"{e['id']}.jpg";op.parent.mkdir(exist_ok=True);Image.fromarray(pixels).save(op,quality=90)
        cells=''.join(f'<tr><td>{html.escape(p)}</td><td>{results[p]["presence_score"]:.3f}</td><td>{results[p]["max_detection_score"]:.3f}</td><td>{len(strong(p))}</td></tr>' for p in PROMPTS)
        sections.append(f'<section><h2>{html.escape(e["id"])}</h2><div class="grid"><img src="overlays/{e["id"]}.jpg"><div><p>{html.escape("; ".join(messages) or "Нет предупреждения по текущим эвристикам")}</p><p>Рука: {hand_state}; объект: {object_state}</p><p>Предположения: {html.escape(str([(x["concept"],round(x["score"],2)) for x in pack]))}</p><table><tr><th>Промпт</th><th>Presence</th><th>Max score</th><th>≥0.5</th></tr>{cells}</table><details><summary>Диагностика</summary><pre>{html.escape(json.dumps(row,ensure_ascii=False,indent=2))}</pre></details></div></div></section>')
    write(args.output/'assessment.json',{'config_sha256':digest(args.output/'config.json'),'report_code_sha256':digest(Path(__file__)),'images':records})
    (args.output/'provenance/report_script.py').write_bytes(Path(__file__).read_bytes())
    (args.output/'review.html').write_text('<!doctype html><html lang="ru"><meta charset="utf-8"><title>Проверка кадра SAM 3</title><style>body{font:16px system-ui;background:#edf0f4;margin:24px;color:#203040}section,header{background:white;padding:20px;margin:20px 0;border-radius:12px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:24px}.grid img{width:100%;max-height:700px;object-fit:contain}td,th{border:1px solid #ddd;padding:6px}table{border-collapse:collapse}pre{white-space:pre-wrap;overflow-wrap:anywhere}h2{overflow-wrap:anywhere}</style><header><h1>Наличие объекта, упаковка и рука: диагностический пилот</h1><p>Голубой: выбранный объект; зелёный: видимые этикетки; оранжевый: руки. Presence относится ко всему кадру. Порог 0.5 не откалиброван. Отсутствие маски не доказывает отсутствие объекта; label может выделять печать на банке. Материал, объём и скрытый текст неизвестны.</p><p>Перекрытие — осторожная гипотеза по геометрии видимых контуров, не восстановление закрытой этикетки. Ни одно сообщение не блокирует поиск. Состав диагностической выборки сохранён в manifest.json; точность на независимых кадрах не измерена.</p></header>'+''.join(sections)+'</html>')
    for e in records:print(e['id'],e['object_state'],e['hand_state'],[p['concept'] for p in e['packaging_hypotheses']])


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('command',choices=['prepare','run','report']);parser.add_argument('--output',type=Path,default=DEFAULT)
    parser.add_argument('--input',type=Path,nargs='+',help='Optional specific original images for prepare')
    args=parser.parse_args();args.output=args.output.resolve();globals()[args.command](args)


if __name__=='__main__':main()

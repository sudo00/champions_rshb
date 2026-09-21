"""Prepare full-catalogue/query visual views with SAM 3 and cylindrical views.

Independent per-image checkpoints permit resumption. No model training or OCR.
Catalogue metadata never selects regions of query photographs.
"""
from __future__ import annotations
import argparse
from collections import Counter
import json
from pathlib import Path
import time

import cv2
import numpy as np
from PIL import Image

from label_rectification_pilot import ROOT,read,write,digest,immutable,rgb_oriented,scaled_contour,geometry,GeometryRejected
from label_segmentation_pilot import choose_main_label
from cylinder_geometry import fit_geometry,rectify,render_view
from sam3_gpu_study import load_runtime

BASE=ROOT/'data/audit/visual_search/gallery_v2'
CATALOG=ROOT/'data/catalog/curated/catalog.jsonl'
POSES=[[0,0,0],[-20,0,0],[20,0,0],[0,-15,-8],[0,15,8]]


def explicit_packaging(row):
    import re
    # Names/slug are stronger than incidental mentions in serving descriptions.
    text=(str(row.get('title',''))+' '+str(row.get('slug',''))).lower()
    for kind,pattern in [('box',r'б[эе]г.?ин.?бокс|beg-in-boks|bag.in.box'),
                         ('can',r'в банке|v-banke'),('carton',r'тетрапак|tetra.?pak'),('pouch',r'дой.?пак|doy.?pack')]:
        if re.search(pattern,text):return {'kind':kind,'source':'explicit_title_or_slug','certainty':'metadata_hint'}
    return {'kind':'unknown','source':None,'certainty':'unknown'}


def plan(args):
    rows=[json.loads(l) for l in CATALOG.read_text().splitlines() if l.strip()]
    grouped={}
    for row in rows:
        path=ROOT/row['reference_path'];fallback=None
        if digest(path)!=row['reference_sha256']:
            source=ROOT/row['reference_source_path']
            if not source.exists() or digest(source)!=row['reference_sha256']:
                raise ValueError(f"Neither reference nor source matches recorded SHA: {row['slug']}")
            fallback={'derived_path':str(path),'derived_actual_sha256':digest(path),
                      'reason':'derived_reference_changed; original_source_matches_recorded_sha'}
            path=source
        sha=row['reference_sha256'];record=grouped.setdefault(sha,{'id':'ref_'+sha[:20],'role':'gallery',
            'path':str(path),'source_sha256':sha,'source_fallback':fallback,'slugs':[],'packaging_hints':[]})
        record['slugs'].append(row['slug']);record['packaging_hints'].append(explicit_packaging(row))
    queries={}
    for manifest_path in [ROOT/'data/audit/bottle_ocr/pilot14_v1/manifest.json',
                          ROOT/'data/audit/capture_quality/pilot20_v1/manifest.json',
                          ROOT/'data/audit/capture_quality/user_examples2_v1/manifest.json']:
        if not manifest_path.exists():continue
        for entry in read(manifest_path)['images']:
            sha=entry['source_sha256']
            if sha in queries:continue
            path=Path(entry.get('source_path',entry.get('path')));path=path if path.is_absolute() else ROOT/path
            identifier=entry['id'];expected=identifier[4:] if identifier.startswith('cat_') else None
            queries[sha]={'id':'query_'+identifier,'role':'query','path':str(path),'source_sha256':sha,
                          'slugs':[],'packaging_hints':[], 'source_card_for_diagnostic_only':expected,
                          'group':entry.get('group'),'origin':entry.get('origin','existing_original')}
    # Queries first: inspect the complete query preparation before full gallery work.
    entries=list(queries.values())+list(grouped.values())
    for e in entries:
        if digest(Path(e['path']))!=e['source_sha256']:raise ValueError(f"Source reference changed: {e['path']}")
    immutable(args.output/'manifest.json',{'catalog_sha256':digest(CATALOG),'catalog_cards':len(rows),
        'gallery_references':len(grouped),'query_count':len(queries),'entries':entries})
    print('Planned',len(grouped),'references,',len(queries),'queries',flush=True)


def cache_index():
    cache={}
    study=ROOT/'data/audit/sam3_gpu/bf16_v1'
    for path in (study/'results').glob('*__label.json'):
        value=read(path)
        if value.get('status')!='mask':continue
        mp=study/value['mask_path']
        cache.setdefault(value['source_sha256'],{})['label']={'path':str(mp),'sha256':value['mask_sha256'],
            'source_size':value['source_size'],'evidence_path':str(path),'evidence_sha256':digest(path),
            'score':value['candidates'][value['selected_index']]['score']}
    body=ROOT/'data/audit/bottle_ocr/pilot14_v1'
    for entry in read(body/'manifest.json')['images']:
        if entry.get('status')!='mask':continue
        cache.setdefault(entry['source_sha256'],{})['bottle']={'path':str(body/entry['mask_path']),
            'sha256':entry['mask_sha256'],'source_size':entry['source_size'],'evidence_path':str(body/'manifest.json'),
            'evidence_sha256':digest(body/'manifest.json'),'score':None}
    return cache


def box_from_mask(mask):
    ys,xs=np.nonzero(mask)
    return [int(xs.min()),int(ys.min()),int(xs.max()+1),int(ys.max()+1)] if len(xs) else None


def crop_region(array,mask,neutralize=False):
    full=cv2.resize(mask.astype(np.uint8),(array.shape[1],array.shape[0]),interpolation=cv2.INTER_NEAREST)
    box=box_from_mask(full)
    if box is None:raise GeometryRejected('empty_mask')
    x0,y0,x1,y1=box;margin=max(3,round(min(x1-x0,y1-y0)*.04))
    x0=max(0,x0-margin);y0=max(0,y0-margin);x1=min(array.shape[1],x1+margin);y1=min(array.shape[0],y1+margin)
    rgb=array[y0:y1,x0:x1].copy()
    if neutralize:rgb[full[y0:y1,x0:x1]==0]=255
    return Image.fromarray(rgb),[x0,y0,x1,y1]


def run(args):
    import torch
    manifest=read(args.output/'manifest.json');cache=cache_index()
    config={'manifest_sha256':digest(args.output/'manifest.json'),'threshold':.5,'mask_threshold':.5,'mask_side':768,
            'rgb_side':768,'augmentation_side':384,'poses':POSES,'precision':'bf16','reuse_vision_embeddings':True,
            'scripts':{p.name:digest(p) for p in [Path(__file__),Path(__file__).with_name('cylinder_geometry.py'),Path(__file__).with_name('label_rectification_pilot.py')]}}
    immutable(args.output/'config.json',config)
    model,processor,runtime=load_runtime('bf16');write(args.output/'runtime.json',runtime)
    for name in config['scripts']:
        dest=args.output/'provenance'/name;dest.parent.mkdir(exist_ok=True);dest.write_bytes(Path(__file__).with_name(name).read_bytes())
    entries=manifest['entries'];entries=entries[:args.limit] if args.limit else entries
    text_inputs={p:processor(text=p,return_tensors='pt').to('cuda') for p in ['bottle','label','wine box','can']}
    processed=0;started=time.perf_counter();equivalence_checked=False
    for n,entry in enumerate(entries):
        record_path=args.output/'records'/f"{entry['id']}.json"
        if record_path.exists():
            previous=read(record_path)
            if previous['config_sha256']!=digest(args.output/'config.json') or previous['source_sha256']!=entry['source_sha256']:raise ValueError('Stale record')
            for v in previous['views']:
                if digest(args.output/v['path'])!=v['sha256']:raise ValueError('View changed')
            continue
        if digest(Path(entry['path']))!=entry['source_sha256']:raise ValueError('Source changed')
        image,_=rgb_oriented(Path(entry['path']));array=np.array(image);w,h=image.size
        ratio=min(1.,768/max(w,h));mw,mh=round(w*ratio),round(h*ratio);masks={};evidence={};vision=None
        hints={h['kind'] for h in entry['packaging_hints'] if h['kind']!='unknown'}
        object_prompt='wine box' if hints=={'box'} else ('can' if hints=={'can'} else 'bottle')
        def infer(prompt):
            nonlocal vision,equivalence_checked
            cached=cache.get(entry['source_sha256'],{}).get(prompt)
            if cached and list(image.size)==cached['source_size']:
                if digest(Path(cached['path']))!=cached['sha256']:raise ValueError('Cached SAM mask changed')
                arr=np.array(Image.open(cached['path']).convert('L'))>0
                return cv2.resize(arr.astype(np.uint8),(mw,mh),interpolation=cv2.INTER_NEAREST),{'reused':cached}
            with torch.inference_mode(),torch.autocast('cuda',dtype=torch.bfloat16):
                if vision is None:
                    pixels=processor(images=image,return_tensors='pt')['pixel_values'].to('cuda')
                    vision=model.vision_encoder(pixels)
                output=model(vision_embeds=vision,**text_inputs[prompt])
                if not equivalence_checked:
                    direct=model(pixel_values=pixels,**text_inputs[prompt])
                    torch.testing.assert_close(output.pred_logits,direct.pred_logits,rtol=.005,atol=.005)
                    torch.testing.assert_close(output.pred_boxes,direct.pred_boxes,rtol=.005,atol=.005)
                    torch.testing.assert_close(output.pred_masks,direct.pred_masks,rtol=.005,atol=.005)
                    write(args.output/'vision_reuse_check.json',{'id':entry['id'],'prompt':prompt,'passed':True,
                        'max_logit_difference':float((output.pred_logits-direct.pred_logits).abs().max())})
                    equivalence_checked=True;del direct
                parsed=processor.post_process_instance_segmentation(output,threshold=.5,mask_threshold=.5,target_sizes=[[mh,mw]])[0]
            scores=parsed['scores'].float().cpu().tolist();boxes=parsed['boxes'].float().cpu().tolist()
            candidates=[{'score':s,'box_xyxy':b} for s,b in zip(scores,boxes)]
            if prompt=='label' and 'object' in masks:
                target=cv2.dilate(masks['object'],np.ones((5,5),np.uint8))>0
                accepted=[]
                for i,c in enumerate(candidates):
                    current=parsed['masks'][i].cpu().numpy()>0
                    fraction=np.count_nonzero(current&target)/max(1,np.count_nonzero(current))
                    if fraction>=.75:accepted.append(i)
            else:accepted=list(range(len(candidates)))
            selected=choose_main_label([candidates[i] for i in accepted],mw,mh)['label_index']
            selected=accepted[selected] if selected is not None else None
            ev={'prompt':prompt,'presence':float(output.presence_logits.sigmoid().flatten()[0]),'candidates':candidates,'selected_index':selected}
            return (parsed['masks'][selected].to(torch.uint8).cpu().numpy() if selected is not None else None),ev
        object_mask,object_evidence=infer(object_prompt)
        if object_mask is None and not hints:
            alternatives=[]
            for p in ['wine box','can']:
                mask,ev=infer(p)
                if mask is not None:alternatives.append((mask,ev,p))
            if alternatives:object_mask,object_evidence,object_prompt=max(alternatives,key=lambda v:float(v[0].mean()))
        if object_mask is not None:masks['object']=object_mask
        evidence['object']=object_evidence
        label_mask,label_evidence=infer('label');evidence['label']=label_evidence
        if label_mask is not None:masks['label']=label_mask
        # Reused label masks must also be associated with the selected object.
        if 'label' in masks and 'object' in masks:
            support=cv2.dilate(masks['object'],np.ones((5,5),np.uint8))>0
            associated=float(np.count_nonzero((masks['label']>0)&support)/max(1,np.count_nonzero(masks['label'])))
            evidence['label_target_coverage']=associated
            if associated<.75:del masks['label'];label_mask=None
        views=[]
        def save_view(im,name,family,**meta):
            im=im.copy();im.thumbnail((768,768),Image.Resampling.LANCZOS)
            path=args.output/'images'/entry['id']/(name+'.png');path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
            views.append({'name':name,'family':family,'path':str(path.relative_to(args.output)),
                          'sha256':digest(path),'size':list(im.size),**meta})
        save_view(image,'reference','reference')
        mask_records={}
        for kind,mask in masks.items():
            path=args.output/'masks'/f"{entry['id']}_{kind}.png";path.parent.mkdir(exist_ok=True);Image.fromarray(mask*255).save(path)
            mask_records[kind]={'path':str(path.relative_to(args.output)),'sha256':digest(path),'size':[mw,mh]}
            im,box=crop_region(array,mask,neutralize=kind=='object');save_view(im,kind,'body' if kind=='object' else 'label',crop_box_original=box)
        cylindrical={'status':'skipped','reason':'missing_label_or_bottle'}
        if object_prompt=='bottle' and 'object' in masks and 'label' in masks:
            try:
                label,fraction=scaled_contour(masks['label'],image.size);bottle,_=scaled_contour(masks['object'],image.size)
                if fraction<.97:raise GeometryRejected('disconnected_label')
                decisions=geometry(label,fraction);align=np.array(decisions['C'].get('matrix',np.eye(3)))
                cylinder=fit_geometry(label,bottle,align);texture,grid=rectify(array,cylinder,'F',max_side=768)
                save_view(Image.fromarray(texture),'flat','flat')
                full_mask=cv2.resize(masks['label']*255,image.size,interpolation=cv2.INTER_NEAREST)
                tm=cv2.remap(full_mask,grid[:,:,0],grid[:,:,1],cv2.INTER_NEAREST)
                for yaw,pitch,roll in POSES:
                    rgba=render_view(texture,tm,cylinder['theta_range'],cylinder['height']*1.08/cylinder['radius'],yaw,pitch,roll,max_side=384)
                    alpha=rgba[:,:,3];bbox=box_from_mask(alpha)
                    if bbox is None:raise GeometryRejected('empty_render')
                    x0,y0,x1,y1=bbox
                    rgb=np.full_like(rgba[:,:,:3],255);valid=alpha>0;rgb[valid]=rgba[:,:,:3][valid]
                    save_view(Image.fromarray(rgb[y0:y1,x0:x1]),f'pose_{yaw}_{pitch}_{roll}','augmented',yaw=yaw,pitch=pitch,roll=roll)
                cylindrical={'status':'applied','model':cylinder}
            except GeometryRejected as exc:cylindrical={'status':'skipped','reason':str(exc)}
        result={**entry,'config_sha256':digest(args.output/'config.json'),'source_size':[w,h],'object_prompt':object_prompt,
                'masks':mask_records,'evidence':evidence,'views':views,'cylinder':cylindrical,
                'target_detected':bool(masks),'prepared_seconds_elapsed':time.perf_counter()-started}
        write(record_path,result);processed+=1
        if processed%25==0 or entry['role']=='query':print(n+1,'/',len(entries),entry['id'],len(views),'views',cylindrical['status'],round(time.perf_counter()-started,1),'s',flush=True)
        del vision
    print('Prepared new records:',processed,'elapsed',round(time.perf_counter()-started,1),flush=True)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('command',choices=['plan','run']);p.add_argument('--output',type=Path,default=BASE);p.add_argument('--limit',type=int)
    args=p.parse_args();args.output=args.output.resolve();globals()[args.command](args)


if __name__=='__main__':main()

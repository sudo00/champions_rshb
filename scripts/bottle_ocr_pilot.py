"""Whole-target-bottle OCR with saved SAM 3 masks and original-coordinate evidence."""
from __future__ import annotations
import argparse
import importlib.metadata
import json
import os
from pathlib import Path
import time

import numpy as np
from PIL import Image
from label_rectification_pilot import ROOT, digest, read, write, immutable, rgb_oriented, points_h

DEFAULT_OUTPUT = ROOT / 'data/audit/bottle_ocr/pilot14_v1'
STUDY = ROOT / 'data/audit/sam3_gpu'
RECT = ROOT / 'data/audit/label_rectification/pilot12_v4'
DETECTOR = 'PP-OCRv5_server_det'
RECOGNIZERS = {'russian':'eslav_PP-OCRv5_mobile_rec','latin':'latin_PP-OCRv5_mobile_rec'}


def coverage(inner: list, outer: list) -> float:
    a,b,c,d=inner; x,y,z,t=outer
    return max(0,min(c,z)-max(a,x))*max(0,min(d,t)-max(b,y))/max(1,(c-a)*(d-b))


def source_entries(args: argparse.Namespace) -> list[dict]:
    if args.input:
        paths = [args.input] if args.input.is_file() else sorted(p for p in args.input.iterdir() if p.suffix.lower() in ('.jpg','.jpeg','.png','.webp'))
        if not paths:
            raise ValueError('No input images')
        if len({p.stem for p in paths}) != len(paths):
            raise ValueError('Image stems must be unique')
        return [{'id':p.stem,'path':str(p.resolve()),'sha256':digest(p),'group':'user','gt_boxes':[]} for p in paths]
    ids = [e['id'] for e in read(RECT/'manifest.json')['images']]
    ids += ['cat_myshako-green-cape-shardone-beloe-bryut', 'cat_vinodelnya-konstantina-dzitoeva-kion-kaberne-sovinon-krasnoe-suhoe-145']
    lookup = {e['id']:e for e in read(STUDY/'manifest.json')['entries']}
    return [{**lookup[i], 'gt_boxes':[]} for i in ids]


def prepare(args: argparse.Namespace) -> None:
    from sam3_gpu_study import load_runtime, infer
    sources = source_entries(args)
    settings = {'sources':[{'id':e['id'],'path':e['path'],'sha256':e['sha256']} for e in sources],
        'prompt':'bottle','precision':'bf16','max_side':1600,'margin_fraction':.025,
        'selection':'SAM3 centrality + area; cached selected label is a consistency check, not ground truth',
        'script_sha256':digest(Path(__file__))}
    if (args.output/'manifest.json').exists():
        raise ValueError('Prepared run exists; use ocr/search or a new output')
    immutable(args.output/'prepare_config.json',settings)
    pending = [e for e in sources if not (args.output/'segmentation'/f"{e['id']}.json").exists()]
    if pending:
        model,processor,runtime=load_runtime('bf16')
        write(args.output/'sam_runtime.json',runtime)
        for entry in pending:
            if digest(Path(entry['path']))!=entry['sha256']:
                raise ValueError('Source changed')
            result,mask,_=infer(model,processor,entry,'bottle','bf16')
            result['source_sha256']=entry['sha256']
            result['prepare_config_sha256']=digest(args.output/'prepare_config.json')
            mask_path=args.output/'masks'/f"{entry['id']}.png"
            mask_path.parent.mkdir(parents=True,exist_ok=True)
            Image.fromarray(mask*255).save(mask_path)
            result.update(mask_path=str(mask_path.relative_to(args.output)),mask_sha256=digest(mask_path))
            write(args.output/'segmentation'/f"{entry['id']}.json",result)
            print(entry['id'],result['status'],len(result['candidates']),'bottles',flush=True)
        del model,processor
    images=[]
    for source in sources:
        result=read(args.output/'segmentation'/f"{source['id']}.json")
        if result['source_sha256']!=source['sha256'] or result['prepare_config_sha256']!=digest(args.output/'prepare_config.json'):
            raise ValueError('Stale segmentation')
        image,exif=rgb_oriented(Path(source['path']))
        if list(image.size)!=result['source_size']:
            raise ValueError('EXIF/source size mismatch')
        record={'id':source['id'],'source_path':source['path'],'source_sha256':source['sha256'],
            'group':source['group'],'catalog_source_slug':source.get('slug'), 'source_size':list(image.size),**exif,
            'segmentation_path':str((args.output/'segmentation'/f"{source['id']}.json").relative_to(args.output)),
            'mask_path':result['mask_path'],'mask_sha256':result['mask_sha256'],'mask_size':result['mask_size'],
            'status':result['status'],'variants':[]}
        args.output.joinpath('images').mkdir(exist_ok=True)
        preview=image.copy();preview.thumbnail((1000,1000));preview_path=args.output/'images'/f"{source['id']}_source.jpg";preview.save(preview_path,quality=90)
        record['source_preview']=str(preview_path.relative_to(args.output))
        if result['selected_index'] is not None:
            box=result['candidates'][result['selected_index']]['box_xyxy']
            record['bottle_box_original']=box
            label_path=STUDY/'bf16_v1/results'/f"{source['id']}__label.json"
            labels=read(label_path) if label_path.exists() else None
            if labels and labels['source_sha256']!=source['sha256']:
                raise ValueError('Cached label belongs to another image')
            selected_label=labels['candidates'][labels['selected_index']]['box_xyxy'] if labels and labels.get('selected_index') is not None else None
            record['label_anchor_coverage']=coverage(selected_label,box) if selected_label else None
            if selected_label and record['label_anchor_coverage']<.5:
                record['status']='bottle_label_disagreement'
            regions=[('whole',box)]
            if labels:
                for i,candidate in enumerate(labels['candidates']):
                    region=candidate['box_xyxy']
                    if coverage(region,box)>=.85 and i!=labels['selected_index']:
                        regions.append((f'additional_label_{i}',region))
            for name,region in regions:
                x,y,z,t=region;pad=max(8,min(z-x,t-y)*settings['margin_fraction'])
                crop=[max(0,int(np.floor(x-pad))),max(0,int(np.floor(y-pad))),min(image.width,int(np.ceil(z+pad))),min(image.height,int(np.ceil(t+pad)))]
                if crop[2]<=crop[0] or crop[3]<=crop[1]:
                    raise ValueError('Invalid bottle crop')
                pixels=image.crop(crop)
                scale=min(1.,settings['max_side']/max(pixels.size))
                size=[max(1,round(d*scale)) for d in pixels.size]
                if list(pixels.size)!=size:pixels=pixels.resize(size,Image.Resampling.LANCZOS)
                transform=np.array([[size[0]/(crop[2]-crop[0]),0,-crop[0]*size[0]/(crop[2]-crop[0])],
                    [0,size[1]/(crop[3]-crop[1]),-crop[1]*size[1]/(crop[3]-crop[1])],[0,0,1]])
                path=args.output/'images'/f"{source['id']}_{name}.png";pixels.save(path)
                record['variants'].append({'name':name,'path':str(path.relative_to(args.output)),'sha256':digest(path),
                    'size':size,'crop_box_original':crop,'input_to_original':np.linalg.inv(transform).tolist(),
                    'original_to_input':transform.tolist()})
        images.append(record)
    write(args.output/'manifest.json',{'prepare_config_sha256':digest(args.output/'prepare_config.json'),'images':images})
    (args.output/'prepare_script.py').write_bytes(Path(__file__).read_bytes())
    print('Prepared',len(images),'bottles,',sum(len(e['variants']) for e in images),'OCR inputs',flush=True)


def in_bottle(polygon: list, mask: np.ndarray, source_size: list) -> bool:
    import cv2
    point=np.mean(polygon,axis=0)
    mx=(point[0]+.5)*mask.shape[1]/source_size[0]-.5
    my=(point[1]+.5)*mask.shape[0]/source_size[1]-.5
    x,y=int(round(mx)),int(round(my))
    return 0<=x<mask.shape[1] and 0<=y<mask.shape[0] and bool(mask[y,x])


def ocr(args: argparse.Namespace) -> None:
    import cv2
    cv2.setNumThreads(1)
    manifest=read(args.output/'manifest.json')
    cache=ROOT/'weights/cache/ocr'
    for k,v in {'PADDLE_PDX_CACHE_HOME':cache/'paddlex','PADDLE_HOME':cache/'paddle','HF_HOME':cache/'huggingface','XDG_CACHE_HOME':cache/'xdg'}.items():os.environ.setdefault(k,str(v))
    os.environ.setdefault('PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK','True')
    models=cache/'paddlex/official_models'
    weights={name:{str(p.relative_to(models/name)):digest(p) for p in sorted((models/name).rglob('*')) if p.is_file()} for name in [DETECTOR,*RECOGNIZERS.values()]}
    if any(not files for files in weights.values()):raise ValueError('Missing OCR weights')
    for e in manifest['images']:
        if digest(Path(e['source_path']))!=e['source_sha256'] or digest(args.output/e['mask_path'])!=e['mask_sha256']:raise ValueError('Source/mask changed')
        for v in e['variants']:
            if digest(args.output/v['path'])!=v['sha256']:raise ValueError('Input changed')
    config={'manifest_sha256':digest(args.output/'manifest.json'),'weights_sha256':weights,'max_side':1600,'device':'cpu',
        'threads':4,'mkldnn':False,'doc_unwarp':False,'doc_orientation':False,'line_orientation':False,'threshold':0.,
        'color':'BGR','bottle_membership':'line centre inside selected SAM mask dilated by 3 mask pixels',
        'versions':{n:importlib.metadata.version(n) for n in ['paddleocr','paddlepaddle','numpy','Pillow']}}
    immutable(args.output/'ocr_config.json',config)
    from paddleocr import PaddleOCR
    for language,model_name in RECOGNIZERS.items():
        pending=[]
        for e in manifest['images']:
            for v in e['variants']:
                path=args.output/'results'/f"{e['id']}_{v['name']}_{language}.json"
                if path.exists():
                    old=read(path)
                    if old['input_sha256']!=v['sha256'] or old['config_sha256']!=digest(args.output/'ocr_config.json'):raise ValueError('Stale OCR result')
                else:pending.append((e,v,path))
        if not pending:continue
        start=time.perf_counter()
        engine=PaddleOCR(text_detection_model_name=DETECTOR,text_detection_model_dir=str(models/DETECTOR),
            text_recognition_model_name=model_name,text_recognition_model_dir=str(models/model_name),
            use_doc_orientation_classify=False,use_doc_unwarping=False,use_textline_orientation=False,text_rec_score_thresh=0.,
            text_det_limit_side_len=1600,text_det_limit_type='max',device='cpu',cpu_threads=4,enable_mkldnn=False)
        load=time.perf_counter()-start
        warm_image=np.asarray(Image.open(args.output/pending[0][1]['path']).convert('RGB'))[:,:,::-1].copy()
        start=time.perf_counter();list(engine.predict(warm_image));warm=time.perf_counter()-start
        write(args.output/'timing'/f'{language}.json',{'load_seconds':load,'warmup_seconds':warm})
        for e,v,path in pending:
            mask=np.array(Image.open(args.output/e['mask_path']).convert('L'))>0
            mask=cv2.dilate(mask.astype(np.uint8),np.ones((7,7),np.uint8))
            image=np.array(Image.open(args.output/v['path']).convert('RGB'))[:,:,::-1].copy()
            start=time.perf_counter();prediction=list(engine.predict(image));elapsed=time.perf_counter()-start
            if len(prediction)!=1:raise ValueError('Expected one result')
            raw=prediction[0].json;raw=json.loads(raw) if isinstance(raw,str) else raw;data=raw.get('res',raw)
            if not len(data['rec_texts'])==len(data['rec_scores'])==len(data['rec_polys']):raise ValueError('OCR arrays disagree')
            lines=[]
            for text,score,polygon in zip(data['rec_texts'],data['rec_scores'],data['rec_polys']):
                original=points_h(polygon,np.array(v['input_to_original'])).tolist()
                lines.append({'text':text,'confidence':float(score),'polygon_input':polygon,'polygon_original':original,
                    'on_target_bottle':in_bottle(original,mask,e['source_size'])})
            write(path,{'image_id':e['id'],'variant':v['name'],'language':language,'input_sha256':v['sha256'],
                'source_sha256':e['source_sha256'],'config_sha256':digest(args.output/'ocr_config.json'),
                'inference_seconds':elapsed,'lines':lines,'raw':raw})
            print(e['id'],v['name'],language,len(lines),'lines',round(elapsed,2),'s',flush=True)
        del engine
    print('Whole-bottle OCR complete',flush=True)


def main() -> None:
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=['prepare','ocr'])
    parser.add_argument('--output',type=Path,default=DEFAULT_OUTPUT)
    parser.add_argument('--input',type=Path,help='Optional new image or directory; default: the 14 diagnostic images')
    args=parser.parse_args();args.output=args.output.resolve()
    {'prepare':prepare,'ocr':ocr}[args.command](args)


if __name__=='__main__':main()

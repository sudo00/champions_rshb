"""Build a self-contained, searchable catalogue for collecting phone photographs."""
from __future__ import annotations

import argparse
import base64
import hashlib
import io
import json
from pathlib import Path

from PIL import Image
from label_rectification_pilot import ROOT, rgb_oriented


def sha256(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def build(catalog: Path, output: Path) -> dict:
    rows = [json.loads(line) for line in catalog.read_text().splitlines() if line.strip()]
    thumbnails, cards, fallbacks = {}, [], []
    findings_path = ROOT / 'data/audit/text_search/pilot14_v1/catalog_findings.json'
    findings = {r['slug']: r for r in json.loads(findings_path.read_text()).get('findings', [])} if findings_path.exists() else {}
    for row in rows:
        checksum = row['reference_sha256']
        if checksum not in thumbnails:
            source = ROOT / row['reference_path']
            if sha256(source) != checksum:
                source = ROOT / row['reference_source_path']
                if sha256(source) != checksum:
                    raise ValueError('Unverified catalogue image: ' + row['slug'])
                fallbacks.append(row['slug'])
            image, _ = rgb_oriented(source)
            image.thumbnail((140, 210), Image.Resampling.LANCZOS)
            buffer = io.BytesIO()
            image.save(buffer, format='JPEG', quality=65, optimize=True)
            thumbnails[checksum] = 'data:image/jpeg;base64,' + base64.b64encode(buffer.getvalue()).decode('ascii')
        note = ''
        if row['slug'] in findings:
            finding = findings[row['slug']]
            note = f"Проверить карточку: на фото {finding['visible_print']}; в каталоге сорт {finding['catalog_grapes']}."
        cards.append({**{key: row.get(key) or '' for key in ('slug', 'title', 'winery', 'grapes', 'category', 'region')},
                      'photo': checksum, 'ambiguous': bool(row.get('exact_slug_ambiguity')), 'note': note})
    cards.sort(key=lambda r: (r['winery'].casefold(), r['title'].casefold(), r['slug']))
    manifest = {'catalog_path': str(catalog.relative_to(ROOT)), 'catalog_sha256': sha256(catalog),
                'cards': len(cards), 'unique_photos': len(thumbnails), 'verified_source_fallbacks': fallbacks,
                'builder_sha256': sha256(Path(__file__)),
                'findings_sha256': sha256(findings_path) if findings_path.exists() else None}
    payload = json.dumps({'cards': cards, 'photos': thumbnails, 'provenance': manifest}, ensure_ascii=False, separators=(',', ':')).replace('<', '\\u003c').replace('&', '\\u0026')
    page = TEMPLATE.replace('__PAYLOAD__', payload).replace('__COUNT__', str(len(cards)))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(page, encoding='utf-8')
    manifest['output_sha256'] = sha256(output)
    output.with_suffix('.manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
    return manifest


TEMPLATE = '''<!doctype html>
<html lang="ru"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Каталог вин для магазина</title>
<style>
*{box-sizing:border-box}body{margin:0;background:#f5f3ee;color:#22362d;font:16px system-ui,sans-serif}header,main{max-width:1100px;margin:auto;padding:18px}h1{font-size:25px;margin:0 0 8px}p{line-height:1.45}.hint{font-size:14px;color:#566158}details{margin:12px 0}summary{cursor:pointer}.filters{position:sticky;top:0;background:#f5f3eef5;padding:12px 0;z-index:1;display:flex;gap:8px;flex-wrap:wrap}input,select,button{font:inherit;border:1px solid #b5beb6;border-radius:7px;padding:10px;background:white;color:#22362d}input{flex:2;min-width:180px}select{flex:1;min-width:140px}button{cursor:pointer;min-height:44px}button:focus-visible,input:focus-visible,select:focus-visible{outline:3px solid #28764f}#cards{display:grid;grid-template-columns:repeat(auto-fill,minmax(290px,1fr));gap:12px}article{background:white;border:1px solid #dce1da;border-radius:10px;padding:14px;display:grid;grid-template-columns:95px 1fr;gap:12px;align-content:start}article img{width:95px;height:180px;object-fit:contain}h2{font-size:17px;margin:0 0 6px}article p{margin:5px 0;font-size:14px}.slug{grid-column:1/-1;font-size:11px;overflow-wrap:anywhere;user-select:all;color:#536659}.copy{grid-column:1/-1}.warning{grid-column:1/-1;background:#fff2cf;padding:9px;border-radius:5px;font-size:13px}.empty{padding:25px}#more{display:block;margin:20px auto}#status{min-height:24px}#toast{position:fixed;bottom:15px;left:50%;transform:translateX(-50%);background:#244932;color:white;padding:12px;border-radius:8px;z-index:2}#toast:empty{display:none}@media(max-width:400px){header,main{padding:12px}#cards{grid-template-columns:1fr}}
</style></head><body>
<header><h1>Каталог вин для магазина</h1><p>__COUNT__ карточки · фотографии, названия и slug · работает без интернета</p>
<details><summary>Как собирать фотографии</summary><p>Найдите производителя и точное вино: сравните название, сорт, цвет и сладость. Похожая этикетка сама по себе не подтверждает совпадение. Год можно не учитывать, если он не различает отдельные позиции.</p><p>Сделайте 3–5 отдельных снимков выбранной бутылки: прямо, с небольшим поворотом, с обычным магазинным освещением. Этикетка должна быть крупно и примерно по центру. Сохраните оригиналы и запишите slug. Например: <code>slug__01.jpg</code>.</p><p>Снимок задней этикетки можно сохранить отдельно для проверки названия и состава. Не включайте его в тест фронтального распознавания без соответствующей отметки.</p><p>Карточки с общей фотографией или замечанием требуют уточнения перед назначением правильного ответа. Это рабочий каталог с внесёнными исправлениями; все 2 103 позиции не проходили полное ручное ревью.</p></details>
</header><main><div class="filters"><input id="search" type="search" placeholder="Название, производитель, сорт или slug" aria-label="Поиск по каталогу" autocomplete="off"><select id="winery" aria-label="Винодельня"><option value="">Все винодельни</option></select><select id="category" aria-label="Категория"><option value="">Все категории</option></select><button id="reset" type="button">Сбросить</button></div>
<p id="status" role="status" aria-live="polite"></p><div id="cards"></div><button id="more" type="button">Показать ещё</button>
<noscript>Для поиска откройте этот HTML-файл в браузере с JavaScript. Все фотографии уже находятся внутри файла.</noscript>
<details><summary>Источник каталога</summary><p id="source" class="hint"></p></details>
</main><div id="toast" role="status" aria-live="polite"></div><script id="catalog-data" type="application/json">__PAYLOAD__</script>
<script>
'use strict';
const data=JSON.parse(document.getElementById('catalog-data').textContent);
const $=id=>document.getElementById(id), normalize=s=>String(s).toLocaleLowerCase('ru').replaceAll('ё','е').replace(/[^a-zа-я0-9]+/g,' ').trim();
const escape=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const searchable=data.cards.map(c=>normalize([c.title,c.winery,c.grapes,c.category,c.region,c.slug].join(' ')));
for(const key of ['winery','category']) for(const value of [...new Set(data.cards.map(c=>c[key]).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'ru'))) {const option=document.createElement('option');option.value=value;option.textContent=value;$(key).append(option);}
let selected=[],shown=48,timer;
function filter(){const words=normalize($('search').value).split(' ').filter(Boolean);selected=data.cards.map((card,i)=>({card,i})).filter(({card,i})=>(!$('winery').value||card.winery===$('winery').value)&&(!$('category').value||card.category===$('category').value)&&words.every(w=>searchable[i].includes(w)));shown=48;render();}
function render(){const visible=selected.slice(0,shown);$('status').textContent=`Найдено ${selected.length} из ${data.cards.length} · показано ${visible.length}`;$('cards').innerHTML=visible.map(({card:c,i})=>`<article><img loading="lazy" decoding="async" src="${data.photos[c.photo]}" alt="${escape(c.title)}"><div><h2>${escape(c.title)}</h2><p><strong>${escape(c.winery)}</strong></p><p>${escape(c.category)}</p><p>${escape(c.grapes)}</p><p class="hint">${escape(c.region)}</p></div>${c.ambiguous?'<div class="warning">Общее фото у нескольких slug: уточните соответствие перед разметкой.</div>':''}${c.note?`<div class="warning">${escape(c.note)}</div>`:''}<div class="slug">${escape(c.slug)}</div><button class="copy" type="button" data-index="${i}">Скопировать slug</button></article>`).join('')||'<p class="empty">Совпадений нет. Попробуйте искать по одному названию или производителю.</p>';$('more').hidden=shown>=selected.length;$('more').style.display=shown>=selected.length?'none':'block';}
$('search').addEventListener('input',()=>{clearTimeout(timer);timer=setTimeout(filter,120);});for(const id of ['winery','category'])$(id).addEventListener('change',filter);
$('reset').onclick=()=>{$('search').value='';$('winery').value='';$('category').value='';filter();};$('more').onclick=()=>{shown+=48;render();};
$('cards').addEventListener('click',async e=>{const button=e.target.closest('button[data-index]');if(!button)return;const slug=data.cards[Number(button.dataset.index)].slug;try{await navigator.clipboard.writeText(slug);$('toast').textContent='Slug скопирован';setTimeout(()=>{$('toast').textContent='';},1800);}catch{window.prompt('Скопируйте slug:',slug);}});
$('source').textContent=`${data.provenance.catalog_path} · SHA-256: ${data.provenance.catalog_sha256}`;filter();
</script></body></html>'''


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', type=Path, default=ROOT / 'data/catalog/curated/catalog.jsonl')
    parser.add_argument('--output', type=Path, default=ROOT / 'data/catalog/curated/catalog_for_shop.html')
    args = parser.parse_args()
    print(json.dumps(build(args.catalog.resolve(), args.output.resolve()), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()

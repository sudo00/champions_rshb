"""Catalogue-constrained text retrieval. Scores are heuristic, not probabilities.

Independent of image filenames, expected slugs and manual query transcriptions.
Keeps raw OCR evidence; this module does not validate catalogue correctness.
"""
from __future__ import annotations

from collections import Counter, defaultdict
import json
import math
from pathlib import Path
import re
import unicodedata

from rapidfuzz import process
from rapidfuzz.distance import DamerauLevenshtein

VERSION = 'text-retrieval-v2'
TRANSLIT = dict(zip('абвгдеёжзийклмнопрстуфхцчшщъыьэюя',
    ['a','b','v','g','d','e','e','zh','z','i','y','k','l','m','n','o','p','r','s','t','u','f','kh','ts','ch','sh','sch','','y','','e','yu','ya']))
LOOKALIKE = str.maketrans({'a':'а','b':'в','c':'с','e':'е','h':'н','k':'к','m':'м','o':'о','p':'р','t':'т','x':'х','y':'у'})
ALIASES = {
    'cabernet':'kaberne','sauvignon':'sovinon','chardonnay':'shardone','pinot':'pino','noir':'nuar',
    'riesling':'risling','merlot':'merlo','muscat':'muskat','muscatel':'muskatel','syrah':'sira',
    'shiraz':'sira','sira':'sira','tempranillo':'tempranilo','brut':'bryut','brutty':'bryut',
    'blanc':'blan','gris':'gri','grigio':'gridzhio','rose':'rozovoe','roze':'rozovoe',
    'white':'beloe','belyy':'beloe','belyj':'beloe','belyi':'beloe','beliy':'beloe','belaya':'beloe',
    'red':'krasnoe','krasnyy':'krasnoe','krasnyj':'krasnoe','rozovyy':'rozovoe','rozovyj':'rozovoe',
    'dry':'sukhoe','suhoe':'sukhoe','sukhoy':'sukhoe','sladkiy':'sladkoe','sweet':'sladkoe',
    'semisweet':'polusladkoe','semidry':'polusukhoe','polusuhoe':'polusukhoe',
    'reserv':'rezerv','reserve':'rezerv','reserva':'rezerv','reserves':'rezerv',
    'aristov':'aristov', 'yachting':'yakhting', 'yahting':'yakhting',
    'franc':'fran', 'viognier':'vione', 'chateau':'shato',
}
STOP = set('tm r vino wine wines vin winery vinodelnya vinodelni vinograd vinograda sorta sort belye krasnye '
           'rossii rossiya rossiyskoe rossiyskaya federalnoe gosudarstvennoe god goda godu urozhaya '
           'osnovaniya product produced bottled estate protected indication quality deniminacao denominacao '
           'de di da do the and in of by na iz s v i en ao naimenovanie zashischennym geograficheskim '
           'ukazaniem soderzhit ob ml cl vol alc www http https ru com semeynaya semeynyy klassicheskim metodom ruchnoy sbor vyderzhka vinogradniki premium'.split())
FIELDS = {'title':1.2,'winery':1.35,'grapes':1.05,'category':.7,'region':.25,'winery_alias':.65,'slug_terms':.45}
# Initial curated producer alias, supported by KD titles and bottle logos in this catalogue.
WINERY_ALIASES = {'Винодельня Константина Дзитоева':['KD','КД']}
SHORT_IDENTIFIERS = {'kd','az','pn'}
TECHNICAL_TERMS = {'sur','lie','rezerv','premium','klassik','classic','collection','kollektsiya'}
COLORS = {'beloe','krasnoe','rozovoe','oranzhevoe'}
SWEETNESS = {'sukhoe','polusukhoe','sladkoe','polusladkoe','bryut','ekstrabryut'}


def ascii_word(word: str) -> str:
    return ''.join(c for c in unicodedata.normalize('NFKD',''.join(TRANSLIT.get(c,c) for c in word.casefold())) if not unicodedata.combining(c))


def raw_words(text: str) -> list[str]:
    return re.findall(r'[^\W_]+',unicodedata.normalize('NFKC',text).casefold(),flags=re.UNICODE)


def canonical(word: str) -> str:
    value=ascii_word(word)
    return ALIASES.get(value,value)


def usable(word: str) -> bool:
    return len(word)>=2 and word not in STOP and not (word.isdigit() and (len(word)==4 or len(word)>4))


def tokens(text: str) -> list[str]:
    return [t for t in (canonical(w) for w in raw_words(text)) if usable(t)]


def word_variants(word: str) -> list[tuple[str,str,float]]:
    direct=canonical(word)
    variants=[(direct,'unicode/transliteration/alias',1.)]
    alternate=canonical(word.casefold().translate(LOOKALIKE))
    if alternate!=direct:
        variants.append((alternate,'OCR Latin/Cyrillic lookalikes',.96))
    # OCR may split the Cyrillic Ю glyph into IO/1O; preserve the original alternative.
    joined=re.sub(r'(?:io|1o|10)', 'ю', word.casefold()).translate(str.maketrans({'6':'б','3':'з','4':'ч','0':'о'})).translate(LOOKALIKE)
    joined=canonical(joined)
    if joined not in {v[0] for v in variants}:variants.append((joined,'OCR split glyph/digit hypothesis',.9))
    return [(t,why,prior) for t,why,prior in variants if usable(t)]


class CatalogTextIndex:
    def __init__(self, rows: list[dict]):
        if not rows or len({r['slug'] for r in rows})!=len(rows):
            raise ValueError('Catalogue requires unique slugs')
        self.rows=rows
        self.documents=[]
        self.postings=defaultdict(list)
        self.winery_terms=defaultdict(set)
        self.grape_terms=set()
        joined_phrases=defaultdict(set)
        self.mean_title_length=sum(max(1,len(set(tokens(str(r.get('title') or ''))))) for r in rows)/len(rows)
        for i,row in enumerate(rows):
            fields={key:str(row.get(key) or '') for key in FIELDS if key!='slug_terms'}
            # Metadata is primary; slug supplements e.g. sweetness omitted from title.
            fields['winery_alias']=' '.join(WINERY_ALIASES.get(fields['winery'],[]))
            fields['slug_terms']=re.sub(r'\d+(?:-\d+)*',' ',row['slug'].replace('-',' '))
            # A joined OCR word can represent consecutive catalogue words, e.g. ALMAVALLEY.
            # Derive the dictionary from metadata, never from query filenames/ground truth.
            for field in ('title','winery','grapes'):
                words=tokens(fields[field])
                for length in (2,3):
                    for offset in range(len(words)-length+1):
                        phrase=tuple(words[offset:offset+length]);joined=''.join(phrase)
                        if len(joined)>=7 and all(len(t)>=3 for t in phrase):
                            joined_phrases[joined].add(phrase)
            terms={}
            for field,value in fields.items():
                for term in set(tokens(value)):
                    if term not in terms or FIELDS[field]>terms[term]['field_weight']:
                        terms[term]={'field':field,'value':value,'field_weight':FIELDS[field]}
                    if field in ('winery','winery_alias'):self.winery_terms[term].add(fields['winery'])
                    if field=='grapes' and len(term)>=5 and term not in COLORS|SWEETNESS:self.grape_terms.add(term)
            self.documents.append({'terms':terms,'fields':fields})
            for term in terms:self.postings[term].append(i)
        self.vocabulary=sorted(self.postings)
        self.joined_phrases={key:next(iter(values)) for key,values in joined_phrases.items()
                             if len(values)==1 and key not in self.postings}
        self.idf={t:min(5.,1+math.log((len(rows)+1)/(len(indices)+1))) for t,indices in self.postings.items()}
        self._match_cache={}

    @classmethod
    def from_file(cls, path: str | Path) -> 'CatalogTextIndex':
        return cls([json.loads(line) for line in Path(path).read_text().splitlines() if line.strip()])

    def term_matches(self, word: str) -> list[dict]:
        if word in self._match_cache:return self._match_cache[word]
        matches={}
        for query,method,prior in word_variants(word):
            if query in self.joined_phrases:
                for term in self.joined_phrases[query]:
                    matches[term]={'term':term,'normalized_query':query,'normalization':'joined catalogue words',
                                   'distance':0,'similarity':1.,'match_strength':.96*prior}
            cutoff=0 if len(query)<=3 or query.isdigit() else (1 if len(query)<=5 else 2)
            for target,distance,_ in process.extract(query,self.vocabulary,scorer=DamerauLevenshtein.distance,score_cutoff=cutoff,limit=4):
                similarity=1-distance/max(len(query),len(target))
                if similarity<.73:continue
                score=similarity*prior
                match={'term':target,'normalized_query':query,'normalization':method,'distance':distance,'similarity':similarity,'match_strength':score}
                if target not in matches or score>matches[target]['match_strength']:matches[target]=match
        ranked=sorted(matches.values(),key=lambda m:(-m['match_strength'],m['term']))
        result=[m for m in ranked if m['match_strength'] >= ranked[0]['match_strength']-.08][:5] if ranked else []
        self._match_cache[word]=result
        return result

    def search(self, observations: str | list[dict], limit: int=10) -> dict:
        if limit<1:raise ValueError('limit must be positive')
        if isinstance(observations,str):observations=[{'text':observations,'source':'text','confidence':1.}]
        if any('text' not in o for o in observations):raise ValueError('Observation requires text')
        best_terms={}
        source_ids=defaultdict(set)
        unmatched={}
        for number,observation in enumerate(observations):
            if observation.get('on_target_bottle') is False:continue
            confidence=max(0.,min(1.,float(observation.get('confidence',1.))))
            if confidence<.2:continue
            source=observation.get('source',str(number))
            for word in raw_words(observation['text']):
                if not word_variants(word):continue
                matches=self.term_matches(word)
                if not matches:
                    unmatched[word]=max(unmatched.get(word,0.),confidence)
                for match in matches:
                    term=match['term']
                    # Do not multiply evidence by the number of OCR variants.
                    strength=match['match_strength']*(.6+.4*confidence)
                    source_ids[term].add(source)
                    evidence={**match,'strength':strength,'raw_word':word,'raw_line':observation['text'],
                        'source':source,'confidence':confidence,'polygon_original':observation.get('polygon_original')}
                    if term not in best_terms or strength>best_terms[term]['strength']:best_terms[term]=evidence
        candidate_ids=set()
        for term in best_terms:candidate_ids.update(self.postings[term])
        if not candidate_ids:
            return {'version':VERSION,'status':'no_text_candidates','candidates':[],'unmatched_words':sorted(unmatched),'known_terms':[]}
        # Only a strong, unambiguous winery word may penalise another producer.
        producer_evidence={t:e for t,e in best_terms.items() if t in self.winery_terms and e['strength']>=.83 and (len(t)>=4 or t in SHORT_IDENTIFIERS) and len(self.winery_terms[t])<=2}
        observed_colors={t for t,e in best_terms.items() if t in COLORS and e['distance']==0 and e['confidence']>=.65}
        observed_sweetness={t for t,e in best_terms.items() if t in SWEETNESS and e['distance']==0 and e['confidence']>=.65}
        observed_grapes={t for t,e in best_terms.items() if t in self.grape_terms and e['distance']==0 and e['confidence']>=.9 and e['match_strength']>=.96}
        denominator=sum(self.idf[t]*e['strength'] for t,e in best_terms.items())
        ranked=[]
        for index in candidate_ids:
            row=self.rows[index];doc=self.documents[index];evidence=[];score=0.;matched_weight=0.
            for term,metadata in doc['terms'].items():
                if term not in best_terms:continue
                match=best_terms[term]
                contribution=self.idf[term]*metadata['field_weight']*match['strength']**2
                if metadata['field']=='title':
                    contribution /= .7+.3*max(1,len(set(tokens(doc['fields']['title']))))/self.mean_title_length
                if term in TECHNICAL_TERMS:contribution*=.35
                if term.isdigit() or (len(term)==2 and term not in SHORT_IDENTIFIERS):contribution*=.2
                score+=contribution
                matched_weight+=self.idf[term]*match['strength']
                evidence.append({**match,**metadata,'contribution':contribution,'supporting_sources':sorted(source_ids[term])})
            conflicts=[]
            winery=set(tokens(doc['fields']['winery']+' '+doc['fields']['winery_alias']))
            if producer_evidence and not any(t in winery for t in producer_evidence):
                penalty=max(self.idf[t]*e['strength']*1.4 for t,e in producer_evidence.items())
                score-=penalty;conflicts.append({'field':'winery','reason':'strong OCR producer word absent from this winery','penalty':penalty})
            for field,observed,actual in [('category',observed_colors,set(tokens(doc['fields']['category']))),
                ('sweetness',observed_sweetness,set(tokens(doc['fields']['title']+' '+doc['fields']['slug_terms'])) & SWEETNESS),
                ('grapes',observed_grapes,set(tokens(doc['fields']['grapes'])))]:
                if len(observed)==1 and actual and not observed & actual:
                    penalty=3.;score-=penalty;conflicts.append({'field':field,'observed':sorted(observed),'catalog':sorted(actual),'penalty':penalty})
            evidence.sort(key=lambda x:-x['contribution'])
            # A named variety on the label supports a varietal title more directly than
            # an incidental ingredient of a blend. Bound the bonus; absence is not a hard veto.
            title_identity=set(tokens(doc['fields']['title']))-winery-COLORS-SWEETNESS-TECHNICAL_TERMS
            title_identity={t for t in title_identity if not t.isdigit() and len(t)>=3}
            title_identity_coverage=sum(best_terms[t]['strength'] for t in title_identity if t in best_terms)/max(1,len(title_identity))
            score+=2.*title_identity_coverage
            ranked.append({'slug':row['slug'],'title':row['title'],'winery':row.get('winery'),'grapes':row.get('grapes'),
                'category':row.get('category'),'score':round(score,5),'query_term_coverage':round(matched_weight/max(denominator,1e-9),4),
                'title_identity_coverage':round(title_identity_coverage,4),
                'evidence':evidence,'conflicts':conflicts,
                'missing_query_terms':sorted((t for t in best_terms if t not in doc['terms']), key=lambda t:-self.idf[t]*best_terms[t]['strength'])[:12],
                'exact_slug_ambiguity':bool(row.get('exact_slug_ambiguity')),
                'same_reference_slugs':row.get('same_reference_slugs',[])})
        ranked.sort(key=lambda r:(-r['score'],r['slug']))
        first=ranked[0]
        margin=(first['score']-ranked[1]['score'])/max(abs(first['score']),1) if len(ranked)>1 else 1.
        identity_terms=[e for e in first['evidence'] if e['field'] in ('title','winery','grapes') and e['term'] not in COLORS|SWEETNESS|TECHNICAL_TERMS and (len(e['term'])>=3 or (len(e['term'])==2 and not e['term'].isdigit() and len(self.postings[e['term']])<=5))]
        status='tentative_match' if first['score']>0 and margin>=.15 and first['query_term_coverage']>=.55 and len(identity_terms)>=2 and not first['conflicts'] and not first['exact_slug_ambiguity'] else 'needs_review'
        return {'version':VERSION,'status':status,'score_is_probability':False,'relative_top_margin':round(margin,4),
            'candidates':ranked[:limit],'known_terms':sorted(best_terms),'unmatched_words':sorted(unmatched),
            'candidate_count':len(ranked),'observation_count':len(observations),
            'limitations':['Text-only heuristic; no calibrated unknown detector or exact-slug accuracy claim.','Evidence deduplicates by catalogue term; OCR variants are correlated.']}

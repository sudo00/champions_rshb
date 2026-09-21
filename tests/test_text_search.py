import sys
from pathlib import Path
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'worker'))
from pipeline.text_search import CatalogTextIndex, tokens, word_variants


def card(slug,title,winery,grapes='',category='Белое',**extra):
    return dict(slug=slug,title=title,winery=winery,grapes=grapes,category=category,region='',**extra)


class TextSearchTests(unittest.TestCase):
    def setUp(self):
        self.index=CatalogTextIndex([
            card('white','Мускатель белый','Массандра'),card('pink','Мускатель розовый','Массандра',category='Розовое'),
            card('other','Мускатель белый','Другая винодельня'),card('agora','AGORA Каберне Совиньон','AGORA WINERY','Каберне Совиньон','Красное'),
            card('cape-ch','Green Cape Шардоне','Мысхако','Шардоне'),card('cape-sb','Green Cape Совиньон Блан','Мысхако','Совиньон Блан'),
            card('kd','KD брют','Винодельня Константина Дзитоева','Шардоне'),card('kid','Кион','Винодельня Константина Дзитоева','Каберне Совиньон','Красное')])

    def test_inserted_letter_and_misread_letter(self):
        for q in ['масскандра мускатель белый','массанара мускатель белый']:
            result=self.index.search(q)
            self.assertEqual(result['candidates'][0]['slug'],'white')
            self.assertTrue(any(e['distance']==1 for e in result['candidates'][0]['evidence']))

    def test_mixed_script_hypothesis_is_preserved(self):
        variants={v[0] for v in word_variants('Hyap')}
        self.assertIn('nuar',variants)
        self.assertIn('hyap',variants)
        self.assertEqual(self.index.search('MACCAHAPA МУСКАТЕЛЬ БЕЛЫЙ')['candidates'][0]['slug'],'white')

    def test_languages_match_catalogue_grapes(self):
        result=self.index.search('AGORA CABERNET SAUVIGNON')
        self.assertEqual(result['candidates'][0]['slug'],'agora')

    def test_tm_and_year_do_not_change_identity_scores(self):
        a=self.index.search('Green Cape Chardonnay')['candidates']
        b=self.index.search('GREEN CAPE CHARDONNAY TM 2024')['candidates']
        self.assertEqual(a[0]['slug'],b[0]['slug'])
        self.assertEqual(a[0]['score'],b[0]['score'])

    def test_repeated_ocr_is_not_repeated_evidence(self):
        observation={'text':'Массандра мускатель белый','source':'B','confidence':.9}
        a=self.index.search([observation])
        b=self.index.search([dict(observation,source=str(i)) for i in range(5)])
        self.assertEqual(a['candidates'][0]['score'],b['candidates'][0]['score'])

    def test_neighbor_text_is_excluded(self):
        result=self.index.search([{'text':'Green Cape Chardonnay','on_target_bottle':True},
            {'text':'МАССАНДРА МУСКАТЕЛЬ БЕЛЫЙ','on_target_bottle':False}])
        self.assertEqual(result['candidates'][0]['slug'],'cape-ch')
        self.assertNotIn('massandra',result['known_terms'])

    def test_known_producer_does_not_force_one_product(self):
        self.assertEqual(self.index.search('МАССАНДРА')['status'],'needs_review')

    def test_gibberish_and_empty_text_have_no_candidates(self):
        for q in ['', '☃', 'zxqwvplkjhg', 'TM 2025']:
            self.assertEqual(self.index.search(q)['status'],'no_text_candidates')

    def test_descriptions_and_expected_slug_are_not_search_inputs(self):
        index=CatalogTextIndex([card('one','Белое','Бренд',description='зебрапингвин'),card('two','Красное','Другой')])
        self.assertEqual(index.search('зебрапингвин')['status'],'no_text_candidates')
        self.assertEqual(index.search([{'text':'Белое Бренд','expected_slug':'two','filename':'two.jpg'}])['candidates'][0]['slug'],'one')

    def test_explicit_grape_conflict_is_explained(self):
        result=self.index.search('Green Cape Chardonnay BLANC')
        self.assertEqual(result['candidates'][0]['slug'],'cape-ch')
        other=next(c for c in result['candidates'] if c['slug']=='cape-sb')
        self.assertTrue(any(c['field']=='grapes' for c in other['conflicts']))

    def test_generic_family_word_is_not_a_producer(self):
        self.assertEqual(self.index.search('СЕМЕЙНАЯ ВИНОДЕЛЬНЯ')['status'],'no_text_candidates')

    def test_split_cyrillic_glyph_is_an_alternative(self):
        self.assertIn('bryut',{v[0] for v in word_variants('6P1OT')})
        self.assertEqual(self.index.search('KD 6P1OT')['candidates'][0]['slug'],'kd')

    def test_producer_alias_does_not_conflict_with_its_own_winery(self):
        result=self.index.search('KD БРЮТ')
        first=result['candidates'][0]
        self.assertEqual(first['slug'],'kd')
        self.assertFalse(any(c['field']=='winery' for c in first['conflicts']))

    def test_generic_reserve_and_producer_do_not_prove_product_identity(self):
        self.assertEqual(self.index.search('KD РЕЗЕРВ')['status'],'needs_review')

    def test_duplicate_slug_is_rejected(self):
        with self.assertRaises(ValueError):CatalogTextIndex([card('x','А','Б'),card('x','В','Г')])

    def test_shared_reference_does_not_become_certain_slug(self):
        index=CatalogTextIndex([card('a','Green Cape Chardonnay','Мысхако',exact_slug_ambiguity=True),card('b','Другое','Другой')])
        self.assertEqual(index.search('Green Cape Chardonnay')['status'],'needs_review')

    def test_joined_producer_and_english_variety(self):
        index = CatalogTextIndex([
            card('franc', 'Каберне Фран', 'ALMA VALLEY', 'Каберне Фран'),
            card('chardonnay', 'Шардоне', 'ALMA VALLEY', 'Шардоне'),
            card('other', 'Каберне Фран', 'Другой производитель', 'Каберне Фран'),
        ])
        result = index.search('ALMAVALLEY CABERNET FRANC')
        self.assertEqual(result['candidates'][0]['slug'], 'franc')
        self.assertTrue({'alma', 'valley', 'fran'}.issubset(result['known_terms']))

    def test_named_varietal_beats_incidental_blend_ingredient(self):
        index = CatalogTextIndex([
            card('varietal', 'Вионье', 'BURNIER', 'Вионье'),
            card('blend', 'Люблю', 'BURNIER', 'Вионье Шардоне'),
            card('other', 'Вионье', 'Другой', 'Вионье'),
        ])
        self.assertEqual(index.search('BURNIER VIOGNIER')['candidates'][0]['slug'], 'varietal')


if __name__=='__main__':unittest.main()

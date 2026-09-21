import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'worker'))
from pipeline.hybrid_search import HybridIndex, rerank_candidates
from pipeline.text_search import CatalogTextIndex


class HybridSearchTests(unittest.TestCase):
    def setUp(self):
        self.cards = [
            dict(slug='chardonnay', title='Шардоне', winery='BURNIER', grapes='Шардоне'),
            dict(slug='viognier', title='Вионье', winery='BURNIER', grapes='Вионье'),
        ]
        self.lookup = {c['slug']: c for c in self.cards}
        self.text = CatalogTextIndex(self.cards)

    def test_brand_and_generic_color_cannot_overrule_visual_evidence(self):
        text = self.text.search('BURNIER БЕЛОЕ СУХОЕ')['candidates']
        result = rerank_candidates({'chardonnay': .82, 'viognier': .8}, text, self.lookup)
        self.assertEqual(result[0]['slug'], 'chardonnay')
        self.assertTrue(all(c['text_bonus'] == 0 for c in result))

    def test_explicit_variety_can_resolve_close_visual_candidates(self):
        text = self.text.search('BURNIER VIOGNIER')['candidates']
        result = rerank_candidates({'chardonnay': .82, 'viognier': .8}, text, self.lookup)
        self.assertEqual(result[0]['slug'], 'viognier')
        self.assertFalse(result[0]['score_is_probability'])
        self.assertTrue(result[0]['evidence'])

    def test_unreadable_text_preserves_visual_order(self):
        result = rerank_candidates({'chardonnay': .82, 'viognier': .8}, [], self.lookup)
        self.assertEqual([c['slug'] for c in result], ['chardonnay', 'viognier'])
        self.assertEqual(rerank_candidates({}, [], self.lookup), [])
        with self.assertRaises(ValueError):
            rerank_candidates({'chardonnay': float('nan')}, [], self.lookup)
        with self.assertRaises(ValueError):
            rerank_candidates({'chardonnay': .8}, [], self.lookup, limit=0)

    def test_end_to_end_missing_label_and_annotation_independence(self):
        gallery = np.array([[.82, np.sqrt(1-.82**2)], [.8, .6]])
        views = [dict(record_id=c['slug'], family='body', slugs=[c['slug']]) for c in self.cards]
        index = HybridIndex(gallery, views, self.cards)
        query = np.array([[1., 0.]])
        observations = [dict(text='BURNIER VIOGNIER', confidence=1., expected_slug='chardonnay',
                             filename='chardonnay.jpg')]
        result = index.search(query, [dict(family='body', expected_slug='chardonnay')], observations, limit=1)
        self.assertEqual(result['candidates'][0]['slug'], 'viognier')
        self.assertEqual(len(result['candidates']), 1)
        self.assertEqual(result['status'], 'candidates_unverified')
        self.assertAlmostEqual(result['visual_continuous'][0]['score'], .82, places=6)
        no_target = index.search(np.empty((0, 2)), [], observations, target_detected=False)
        self.assertEqual(no_target['status'], 'no_target')
        self.assertEqual(no_target['candidates'], [])


if __name__ == '__main__':
    unittest.main()

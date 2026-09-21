import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'worker'))
from pipeline.visual_search import VisualIndex, fuse_rankings, packaging_adjustment, packaging_hint


class VisualSearchTests(unittest.TestCase):
    def test_many_views_have_one_vote_and_one_slot(self):
        views = [{'record_id': 'a', 'family': 'label', 'slugs': ['a']}] * 20
        views += [{'record_id': 'b', 'family': 'label', 'slugs': ['b']}]
        vectors = np.array([[1., 0.]] * 20 + [[0., 1.]])
        cards = [{'slug': 'a'}, {'slug': 'b'}]
        result = VisualIndex(vectors, views, cards).search(np.array([[1., 0.]]), [{'family': 'label'}])
        self.assertEqual([r['slug'] for r in result['candidates']], ['a', 'b'])
        self.assertAlmostEqual(result['candidates'][0]['score'], 1 / 61)

    def test_augmentation_is_optional_and_not_an_extra_vote(self):
        cards = [{'slug': 'a'}, {'slug': 'b'}]
        views = [{'record_id': 'a', 'family': 'label', 'slugs': ['a']},
                 {'record_id': 'b', 'family': 'label', 'slugs': ['b']},
                 {'record_id': 'a', 'family': 'augmented', 'slugs': ['a']}]
        index = VisualIndex(np.array([[0., 1.], [.8, .2], [1., 0.]]), views, cards)
        args = (np.array([[1., 0.]]), [{'family': 'label', 'expected_slug': 'b'}])
        self.assertEqual(index.search(*args)['candidates'][0]['slug'], 'b')
        result = index.search(*args, augment=True)
        self.assertEqual(result['candidates'][0]['slug'], 'a')
        self.assertAlmostEqual(result['candidates'][0]['score'], 1 / 61)

    def test_no_target_has_no_candidates(self):
        index = VisualIndex(np.array([[1., 0.]]), [{'record_id': 'a', 'family': 'body', 'slugs': ['a']}], [{'slug': 'a'}])
        self.assertEqual(index.search(np.empty((0, 2)), [], target_detected=False)['candidates'], [])
        with self.assertRaises(ValueError):
            index.search(np.array([[float('nan'), 0.]]), [{'family': 'body'}])

    def test_shared_reference_keeps_identity_ambiguity(self):
        index = VisualIndex(np.array([[1., 0.]]), [{'record_id': 'a', 'family': 'body', 'slugs': ['a', 'b']}], [{'slug': 'a'}, {'slug': 'b'}])
        result = index.search(np.array([[1., 0.]]), [{'family': 'body'}])
        self.assertEqual(len(result['candidates']), 2)
        self.assertEqual([r['cosine'] for r in result['branches']['body']], [1., 1.])
        self.assertEqual(result['candidates'][0]['score'], result['candidates'][1]['score'])

    def test_metadata_and_uncertainty(self):
        can = {'slug': 'v-banke', 'title': 'Вино в банке'}
        self.assertEqual(packaging_adjustment({'kind': 'bottle', 'reliable': False}, can)['factor'], 1.)
        self.assertEqual(packaging_adjustment({'kind': 'bottle', 'reliable': True}, {})['factor'], 1.)
        self.assertLess(packaging_adjustment({'kind': 'bottle', 'reliable': True}, can)['factor'], 1.)
        self.assertGreater(packaging_adjustment({'kind': 'can', 'reliable': True}, can)['factor'], 1.)
        self.assertEqual(packaging_hint({'description': 'Храните открытую стеклянную бутылку в холодильнике'})['kind'], 'unknown')
        self.assertEqual(packaging_hint({'description': 'Вино выпускается в упаковке тетрапак'})['kind'], 'carton')

    def test_duplicate_rank_entries_do_not_boost(self):
        self.assertEqual(fuse_rankings({'a': [{'slug': 'x'}, {'slug': 'x'}, {'slug': 'y'}]}),
                         fuse_rankings({'a': [{'slug': 'x'}, {'slug': 'y'}]}))


if __name__ == '__main__':
    unittest.main()

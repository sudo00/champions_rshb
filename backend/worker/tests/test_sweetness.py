import copy
import unittest
from unittest.mock import MagicMock, patch
from backend.worker.sweetness import observed_sweetness, refine_sweetness
from backend.worker import recognition
from backend.worker.tests.test_recognition import prediction


def fixture():
    cards = {slug: dict(title='Линейка', winery='Производитель', grapes='Пино', category='Розовое',
                       metadata_review={'status': 'confirmed', 'confirmed_attributes': {'sweetness': sugar}})
             for slug, sugar in [('a', 'Полусухое'), ('b', 'Полусладкое')]}
    result = prediction()
    result.update(candidates=[{'slug': 'a', 'score': 1.05}, {'slug': 'b', 'score': 1.045}],
                  observations=[{'text': 'РОЗОВОЕ ПОЛУСЛАДКОЕ', 'confidence': .97}])
    return result, cards


class SweetnessTests(unittest.TestCase):
    def test_reviewed_near_tie_is_resolved_without_mutating_inputs(self):
        raw, cards = fixture()
        before = copy.deepcopy(raw)
        out = refine_sweetness(raw, cards)
        self.assertEqual(out['candidates'][0]['slug'], 'b')
        self.assertEqual(raw, before)
        self.assertEqual(out['observations'], raw['observations'])

    def test_uncertain_or_conflicting_readings_preserve_ranking(self):
        for observations in [[], [{'text': 'полусладкое', 'confidence': .8}],
                             [{'text': 'полусладкое', 'confidence': .98, 'on_target_bottle': False}],
                             [{'text': 'полусладкое сухое', 'confidence': .98}]]:
            with self.subTest(observations=observations):
                raw, cards = fixture()
                raw['observations'] = observations
                self.assertEqual(refine_sweetness(raw, cards)['candidates'], raw['candidates'])

    def test_longer_sugar_terms_do_not_match_dry_or_sweet(self):
        for text, value in [('semi-dry', 'Полусухое'), ('полусладкое', 'Полусладкое'),
                            ('extra brut', 'Экстра брют')]:
            self.assertEqual(observed_sweetness([{'text': text, 'confidence': .99}])['value'], value)

    def test_requires_reviewed_same_family_near_tie(self):
        for case in ('unreviewed', 'other_family', 'large_gap'):
            raw, cards = fixture()
            if case == 'unreviewed': cards['b']['metadata_review']['status'] = 'not_reviewed'
            if case == 'other_family': cards['b']['winery'] = 'Другая'
            if case == 'large_gap': raw['candidates'][1]['score'] = .9
            self.assertEqual(refine_sweetness(raw, cards)['candidates'], raw['candidates'])

    def test_api_eval_and_mobile_share_ranking_and_legacy_is_explicit(self):
        raw, cards = fixture()
        model = MagicMock(lookup=cards)
        model.predict.side_effect = lambda *a, **kw: copy.deepcopy(raw)
        policy = MagicMock()
        policy.evaluate.return_value = {'reject': False, 'reason': 'keep_candidates'}
        with patch.object(recognition, 'initialize', return_value=model), \
             patch.object(recognition, '_refusal', policy), patch.object(recognition, '_scorer', None):
            for alternatives in (True, False):
                self.assertEqual(recognition.run(b'photo', alternatives)['slug'], 'b')
            self.assertEqual(recognition.run(b'photo', False, applyCatalogRefusal=False)['slug'], 'b')
            self.assertEqual(recognition.run(b'photo', False, useReviewedSweetness=False)['slug'], 'a')
        # Refusal consumes the original order, never the sugar-adjusted one.
        self.assertTrue(all(c.args[0]['candidates'][0]['slug'] == 'a' for c in policy.evaluate.call_args_list))

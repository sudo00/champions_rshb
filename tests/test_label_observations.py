import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'worker'))
from pipeline.label_observations import extract_observed_fields


class LabelObservationTests(unittest.TestCase):
    def test_fields_require_visible_evidence(self):
        polygon = [[0, 0], [10, 0], [10, 10], [0, 10]]
        result = extract_observed_fields([
            dict(text='CHARDONNAY WHITE 2022', confidence=.95, source='body:latin', polygon_original=polygon),
            dict(text='MERLOT RED', confidence=.99, on_target_bottle=False),
            dict(text='РИСЛИНГ', confidence=.3),
            dict(text='Established 1870', confidence=.99),
        ])
        self.assertEqual([v['value'] for v in result['grapes']], ['Шардоне'])
        self.assertEqual([v['value'] for v in result['colors']], ['Белое'])
        self.assertEqual([v['value'] for v in result['printed_year_candidates']], ['2022'])
        self.assertEqual(result['grapes'][0]['evidence'][0]['polygon_original'], polygon)
        self.assertIsNone(result['product_name'])

    def test_no_readings_do_not_invent_product_fields(self):
        result = extract_observed_fields([])
        self.assertIsNone(result['product_name'])
        for field in ('raw_label_lines', 'grapes', 'colors', 'printed_year_candidates'):
            self.assertEqual(result[field], [])

    def test_repeated_ocr_keeps_best_line_without_duplicate_fields(self):
        result = extract_observed_fields([
            dict(text='Viognier', confidence=.7, source='label'),
            dict(text='VIOGNIER', confidence=.95, source='body'),
        ])
        self.assertEqual(len(result['raw_label_lines']), 1)
        self.assertEqual(result['raw_label_lines'][0]['source'], 'body')
        self.assertEqual(len(result['grapes']), 1)


if __name__ == '__main__':
    unittest.main()

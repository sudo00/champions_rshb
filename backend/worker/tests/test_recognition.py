import unittest
from unittest.mock import MagicMock, patch

from backend.worker import recognition


def prediction():
    return dict(candidates=[{'slug':f'wine-{i}', 'score':1-i/20} for i in range(10)],
                status='candidates_unverified', observations=[{'text':'Рислинг'}],
                observed_fields={'vintages':[2022]}, regions=[], image_size=[100,200],
                coordinate_system='original', target={}, warnings=[], version='v4',
                catalog_sha256='hash', timings_seconds={})


class AdapterTests(unittest.TestCase):
    def test_flag_only_changes_output_count_and_keeps_evidence(self):
        model=MagicMock()
        model.predict.return_value=prediction()
        with patch.object(recognition, 'initialize', return_value=model):
            top5=recognition.run(b'image', True)
            top1=recognition.run(b'image', False)
        self.assertEqual(len(top5['candidates']),5)
        self.assertEqual(top1['candidates'],top5['candidates'][:1])
        self.assertEqual(top5['observations'],prediction()['observations'])
        self.assertEqual(top1['slug'],top5['slug'])
        self.assertFalse(top5['scoreIsProbability'])
        self.assertNotIn('f1_top1',top5)
        self.assertEqual(model.predict.call_args_list[0],model.predict.call_args_list[1])
        model.predict.assert_called_with(b'image',top_k=10)

    def test_missing_target_has_no_candidates(self):
        raw=prediction(); raw.update(candidates=[],status='no_target')
        with patch.object(recognition, 'initialize') as init:
            init.return_value.predict.return_value=raw
            result=recognition.run(b'image')
        self.assertEqual(result['slug'],'unknown')
        self.assertEqual(result['candidates'],[])

    def test_invalid_inputs_do_not_initialize_gpu(self):
        with patch.object(recognition, 'initialize') as init:
            for image,flag in [(b'',True),('path.jpg',True),(b'valid','false')]:
                with self.assertRaises(ValueError): recognition.run(image,flag)
            init.assert_not_called()

    def test_initialize_loads_once_and_shutdown_clears_instance(self):
        with patch.object(recognition,'_recognizer',None), patch.object(recognition,'WineRecognizer') as cls, patch.object(recognition.CatalogRefusal,'from_bundle',return_value=None), patch.object(recognition.CandidateScorer,'from_bundle',return_value=None):
            first=recognition.initialize()
            self.assertIs(first,recognition.initialize())
            cls.assert_called_once()
            recognition.shutdown()
            first.close.assert_called_once()
            self.assertIsNone(recognition._recognizer)

    def test_refusal_precedes_output_limit_and_preserves_ocr(self):
        model=MagicMock(); model.predict.side_effect=lambda *a,**kw: prediction()
        policy=MagicMock(); policy.evaluate.return_value={'reject':True,'reason':'below_threshold'}
        with patch.object(recognition,'initialize',return_value=model), patch.object(recognition,'_refusal',policy):
            for flag in (True,False):
                result=recognition.run(b'image',flag)
                self.assertEqual(result['slug'],'unknown')
                self.assertEqual(result['recognitionStatus'],'not_in_catalog')
                self.assertEqual(result['candidates'],[])
                self.assertEqual(result['observations'],prediction()['observations'])
                self.assertEqual(len(policy.evaluate.call_args.args[0]['candidates']),10)

    def test_uncertain_refusal_keeps_original_order_and_scores(self):
        model=MagicMock(); model.predict.return_value=prediction()
        policy=MagicMock(); policy.evaluate.return_value={'reject':False,'reason':'keep_candidates'}
        with patch.object(recognition,'initialize',return_value=model), patch.object(recognition,'_refusal',policy):
            result=recognition.run(b'image',True)
        self.assertEqual(result['slug'],'wine-0')
        self.assertEqual([c['slug'] for c in result['candidates']],[f'wine-{i}' for i in range(5)])
        self.assertEqual([c['score'] for c in result['candidates']],[c['score'] for c in prediction()['candidates'][:5]])

    def test_evaluation_returns_top1_even_when_mobile_policy_would_reject(self):
        model=MagicMock(); model.predict.return_value=prediction()
        policy=MagicMock(); policy.evaluate.return_value={'reject':True,'reason':'below_threshold'}
        with patch.object(recognition,'initialize',return_value=model), patch.object(recognition,'_refusal',policy):
            result=recognition.run(b'image',False,applyCatalogRefusal=False)
        self.assertEqual(result['slug'],'wine-0')
        self.assertEqual(result['catalogRefusal']['reason'],'disabled_for_evaluation')
        self.assertEqual(result['recognitionStatus'],'candidates_unverified')
        policy.evaluate.assert_not_called()


if __name__=='__main__': unittest.main()

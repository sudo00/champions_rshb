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
        with patch.object(recognition,'_recognizer',None), patch.object(recognition,'WineRecognizer') as cls:
            first=recognition.initialize()
            self.assertIs(first,recognition.initialize())
            cls.assert_called_once()
            recognition.shutdown()
            first.close.assert_called_once()
            self.assertIsNone(recognition._recognizer)


if __name__=='__main__': unittest.main()

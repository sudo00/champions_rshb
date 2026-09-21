import sys
from pathlib import Path
import unittest
import numpy as np

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from bottle_ocr_pilot import coverage, in_bottle


class BottleOcrTests(unittest.TestCase):
    def test_related_label_is_inside_bottle(self):
        self.assertEqual(coverage([30,60,80,90],[20,10,90,100]),1)
        self.assertEqual(coverage([100,60,130,90],[20,10,90,100]),0)

    def test_mask_excludes_neighbor_even_inside_bounding_rectangle(self):
        mask=np.zeros((100,60),dtype=np.uint8)
        mask[10:90,20:40]=1
        inside=[[90,110],[150,110],[150,170],[90,170]]
        outside=[[10,110],[30,110],[30,170],[10,170]]
        self.assertTrue(in_bottle(inside,mask,[240,400]))
        self.assertFalse(in_bottle(outside,mask,[240,400]))

    def test_point_outside_frame_cannot_wrap_numpy_index(self):
        mask=np.ones((100,60),dtype=np.uint8)
        polygon=[[-20,20],[-10,20],[-10,30],[-20,30]]
        self.assertFalse(in_bottle(polygon,mask,[240,400]))

    def test_empty_mask_does_not_accept_any_text(self):
        self.assertFalse(in_bottle([[10,10],[20,10],[20,20],[10,20]],np.zeros((100,60),np.uint8),[240,400]))


if __name__=='__main__':unittest.main()

import sys
from pathlib import Path
import unittest
import numpy as np

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from capture_quality_pilot import hand_relation


class CaptureQualityTests(unittest.TestCase):
    def test_hand_in_notch_can_have_zero_visible_mask_overlap(self):
        label=np.zeros((100,100),np.uint8);label[20:80,20:80]=1;label[20:55,42:58]=0
        hand=np.zeros_like(label);hand[20:52,44:56]=1
        result=hand_relation(label,hand)
        self.assertEqual(result['visible_mask_overlap_pixels'],0)
        self.assertEqual(result['status'],'possible_occlusion')

    def test_hand_away_from_label_does_not_warn(self):
        label=np.zeros((100,100),np.uint8);label[30:70,30:70]=1
        hand=np.zeros_like(label);hand[1:15,1:20]=1
        self.assertEqual(hand_relation(label,hand)['status'],'no_contact_evidence')

    def test_missing_label_is_unassessable_not_clear(self):
        z=np.zeros((100,100),np.uint8)
        self.assertEqual(hand_relation(z,np.ones_like(z))['status'],'unassessable')

    def test_space_between_separate_visible_labels_is_not_a_label(self):
        label=np.zeros((100,100),np.uint8);label[10:30,20:80]=1;label[70:90,20:80]=1
        hand=np.zeros_like(label);hand[40:60,30:70]=1
        self.assertEqual(hand_relation(label,hand)['status'],'no_contact_evidence')


if __name__=='__main__':unittest.main()

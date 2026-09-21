import importlib.util
from pathlib import Path
import tempfile
import unittest

import cv2
import numpy as np
from PIL import Image

SPEC = importlib.util.spec_from_file_location('rectification', Path(__file__).resolve().parents[1]/'scripts/label_rectification_pilot.py')
P = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(P)


class RectificationTests(unittest.TestCase):
    def test_exif_orientation_precedes_coordinate_mapping(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)/'orientation.jpg'
            image = Image.new('RGB',(60,40),'white')
            exif = Image.Exif()
            exif[274] = 6
            image.save(path,exif=exif)
            oriented, info = P.rgb_oriented(path)
            self.assertEqual(oriented.size,(40,60))
            self.assertEqual(info['raw_size'],[60,40])
            self.assertEqual(info['exif_orientation'],6)

    def test_mask_uses_independent_xy_pixel_center_scale(self):
        mask = np.zeros((80,30),np.uint8)
        mask[10:60,5:25] = 1
        contour,fraction = P.scaled_contour(mask,(100,250))
        np.testing.assert_allclose(contour.min(axis=0),[(5.5)*100/30-.5,10.5*250/80-.5])
        self.assertEqual(fraction,1)

    def test_homography_roundtrip_and_straight_grid(self):
        quad = np.array([[20.,30.],[210.,50.],[180.,290.],[40.,260.]])
        matrix = P.quad_homography(quad)
        corners = P.points_h(quad,matrix)
        self.assertAlmostEqual(corners[0,1],corners[1,1],places=4)
        self.assertAlmostEqual(corners[1,0],corners[2,0],places=4)
        grid = np.array([[x,y] for x in range(60,160,15) for y in range(80,240,20)])
        np.testing.assert_allclose(P.points_h(P.points_h(grid,matrix),np.linalg.inv(matrix)),grid,atol=1e-7)

    def test_crop_identity_preserves_original_pixels(self):
        image = np.random.default_rng(1).integers(0,256,(80,90,3),dtype=np.uint8)
        output,matrix = P.warp_crop(image,[10,20,60,70],np.eye(3),1600)
        np.testing.assert_array_equal(output,image[20:70,10:60])
        np.testing.assert_allclose(P.points_h([[0,0],[49,49]],np.linalg.inv(matrix)),[[10,20],[59,69]])

    def test_rotation_retains_crop_corners_in_output(self):
        matrix = np.vstack([cv2.getRotationMatrix2D((50,50),20,1),[0,0,1]])
        output, transform = P.warp_crop(np.zeros((100,100,3),np.uint8),[10,20,80,90],matrix,1600)
        points = P.points_h([[10,20],[79,20],[79,89],[10,89]],transform)
        self.assertGreaterEqual(points.min(),0)
        self.assertLessEqual(points[:,0].max(),output.shape[1]-1)
        self.assertLessEqual(points[:,1].max(),output.shape[0]-1)

    def test_rotation_does_not_import_pixels_outside_selected_crop(self):
        image = np.full((100,100,3),[255,0,0],dtype=np.uint8)
        image[20:80,20:80] = 255
        matrix = np.vstack([cv2.getRotationMatrix2D((50,50),20,1),[0,0,1]])
        output, _ = P.warp_crop(image,[20,20,80,80],matrix,1600)
        np.testing.assert_array_equal(output,np.full_like(output,255))

    def test_output_limit_survives_floating_point_rounding(self):
        image = np.zeros((201,101,3),np.uint8)
        for limit in (32,57,100):
            output, _ = P.warp_crop(image,[0,0,101,201],np.eye(3),limit)
            self.assertLessEqual(max(output.shape[:2]),limit)

    def test_rejects_bowtie_and_horizon(self):
        with self.assertRaises(P.GeometryRejected):
            P.quad_homography(np.array([[0.,0.],[100,100],[0,100],[100,0]]))
        with self.assertRaises(P.GeometryRejected):
            P.warp_crop(np.zeros((100,100,3),np.uint8),[0,0,100,100],np.array([[1.,0,0],[0,1,0],[.02,0,-1]]),1600)

    def test_rotated_rectangle_recovers_angle(self):
        mask = np.zeros((400,400),np.uint8)
        quad = cv2.boxPoints(((200,200),(210,260),12))
        cv2.fillConvexPoly(mask,quad.astype(np.int32),1)
        contour,fraction = P.scaled_contour(mask,(400,400))
        result = P.geometry(contour,fraction)
        self.assertEqual(result['C']['status'],'applied')
        self.assertAlmostEqual(result['C']['angle_deg'],12,delta=1)
        self.assertEqual(result['D']['status'],'applied')

    def test_disconnected_mask_falls_back(self):
        result = P.geometry(np.array([[0.,0.],[100,0],[100,100],[0,100]]),.6)
        self.assertEqual(result['D']['reason'],'disconnected_mask')
        self.assertEqual(result['C']['status'],'skipped')

    def test_field_evaluation_excludes_neighbor_and_penalizes_missing_text(self):
        field={'box_original_xyxy':[10,10,50,50]}
        lines=[{'text':'Пино','polygon_original':[[20,20],[40,20],[40,30],[20,30]]},
               {'text':'СОСЕД','polygon_original':[[70,20],[90,20],[90,30],[70,30]]}]
        self.assertEqual(P.field_prediction(field,lines),'Пино')
        self.assertEqual(P.edit_distance('пино',''),4)
        self.assertEqual(P.normalized('DANÚBIO! 2025'),'danúbio2025')
        self.assertNotEqual(P.normalized('DANÚBIO'),P.normalized('DANUBIO'))

    def test_cached_configuration_cannot_be_replaced(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'config.json'
            P.immutable(path,{'model':'old'})
            with self.assertRaises(ValueError):
                P.immutable(path,{'model':'new'})
            self.assertEqual(P.read(path),{'model':'old'})


if __name__=='__main__':
    unittest.main()

import sys
from pathlib import Path
import unittest

import numpy as np

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import cylinder_geometry as g


class CylinderGeometryTests(unittest.TestCase):
    def model(self):
        return {'center':200.,'radius':150.,'top':[40.,0.,30.],'bottom':[280.,0.,30.],
                'theta_range':[-1.,1.],'x_range':[200-150*np.sin(1),200+150*np.sin(1)],
                'height':240.,'v_range':[-.04,1.04],
                'original_to_aligned':np.eye(3).tolist(),'aligned_to_original':np.eye(3).tolist()}

    def test_nonlinear_mapping_roundtrip(self):
        points=np.random.default_rng(4).uniform([0,0],[449,699],size=(60,2))
        for variant in ('E','F'):
            original=g.input_to_original(points,self.model(),variant,[450,700])
            restored=g.original_to_input(original,self.model(),variant,[450,700])
            np.testing.assert_allclose(restored,points,atol=1e-8)

    def test_known_curved_text_row_becomes_horizontal(self):
        m=self.model();x=np.linspace(*m['x_range'],40)
        y=g.rim_basis(x,m['center'],m['radius'])@m['top']+m['height']*.4
        flat=g.original_to_input(np.c_[x,y],m,'F',[600,700])
        self.assertLess(np.ptp(flat[:,1]),1e-8)

    def test_rim_fit_recovers_known_ellipse_with_outliers(self):
        x=np.linspace(70,330,100);basis=g.rim_basis(x,200,150)
        y=basis@np.array([40.,5.,30.]);y[::17]+=20
        fit,_=g.fit_rim(x,y,200,150)
        self.assertLess(np.max(np.abs(basis@fit-basis@[40.,5.,30.])),.1)

    def test_radius_domain_is_checked(self):
        with self.assertRaises(g.GeometryRejected):g.rim_basis(np.array([0,200]),100,100)

    def test_mask_disagreement_rejected(self):
        theta=np.linspace(-1,1,100);x=150*np.sin(theta)+200
        label=np.r_[np.c_[x,40+30*np.cos(theta)],np.c_[x,280+30*np.cos(theta)]]
        bottle=np.r_[np.c_[np.full(100,800),np.linspace(0,400,100)],np.c_[np.full(100,1100),np.linspace(0,400,100)]]
        with self.assertRaises(g.GeometryRejected):g.fit_geometry(label,bottle,np.eye(3))

    def test_nonlinear_polygons_need_more_than_four_corners(self):
        poly=g.densify_polygon([[0,0],[400,0],[400,600],[0,600]])
        mapped=g.input_to_original(poly,self.model(),'F',[401,601])
        self.assertEqual(mapped.shape,(48,2))
        self.assertGreater(np.ptp(mapped[:12,1]),5)

    def test_augmented_camera_does_not_invent_unseen_texture(self):
        texture=np.full((180,180,3),150,np.uint8);mask=np.full((180,180),255,np.uint8)
        front=g.render_view(texture,mask,[-.5,.5],2,0,0,0,150)
        back=g.render_view(texture,mask,[-.5,.5],2,180,0,0,150)
        self.assertGreater(np.count_nonzero(front[...,3]),100)
        self.assertEqual(np.count_nonzero(back[...,3]),0)
        empty=g.render_view(texture,np.zeros_like(mask),[-.5,.5],2,0,0,0,150)
        self.assertEqual(np.count_nonzero(empty),0)


if __name__=='__main__':unittest.main()

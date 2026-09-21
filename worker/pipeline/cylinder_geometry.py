"""Experimental cylinder geometry; this is not a full reproduction of Huang et al.

Source rectification uses a weak-perspective approximation: elliptical rims and
parallel generators after deskew. Synthetic views use a pinhole virtual camera.
No text, catalogue identities, or evaluation annotations enter the geometry.
"""
from __future__ import annotations

import math
import cv2
import numpy as np

from .rectification import GeometryRejected, points_h


def rim_basis(x: np.ndarray, center: float, radius: float) -> np.ndarray:
    u = (np.asarray(x) - center) / radius
    if np.any(np.abs(u) >= 1):
        raise GeometryRejected("rim_outside_cylinder")
    return np.stack([np.ones_like(u), u, np.sqrt(1 - u*u)], axis=-1)


def fit_rim(x: np.ndarray, y: np.ndarray, center: float, radius: float) -> tuple[list, float]:
    basis = rim_basis(x, center, radius)
    weights = np.ones(len(x))
    for _ in range(5):
        coefficients = np.linalg.lstsq(basis * weights[:, None], y * weights, rcond=None)[0]
        residual = np.abs(basis @ coefficients - y)
        weights = np.minimum(1, max(1., 2.5 * float(np.median(residual))) / np.maximum(residual, 1e-6))
    return coefficients.tolist(), float(np.quantile(residual, .9))


def fit_geometry(label: np.ndarray, bottle: np.ndarray, original_to_aligned: np.ndarray) -> dict:
    """Estimate radius from bottle silhouette at label height, then fit two rims."""
    label = points_h(label, original_to_aligned)
    bottle = points_h(bottle, original_to_aligned)
    lo, hi = label.min(axis=0), label.max(axis=0)
    width, height = hi-lo
    if min(width, height) < 30:
        raise GeometryRejected("small_label")
    # Restrict the silhouette to the label body: neither shoulder nor neck sets R.
    body = bottle[(bottle[:, 1] > lo[1]+.3*height) & (bottle[:, 1] < hi[1]-.3*height)]
    if len(body) < 15:
        raise GeometryRejected("missing_bottle_sides")
    left, right = np.quantile(body[:, 0], [.03, .97])
    center, radius = float((left+right)/2), float((right-left)/2)
    if radius < width*.35 or radius > width*3:
        raise GeometryRejected("implausible_bottle_radius")
    # Small silhouette/mask disagreement must not lead to arcsin at the horizon.
    required = float(max(abs(lo[0]-center), abs(hi[0]-center)) / .96)
    inflated = max(radius, required)
    if inflated / radius > 1.3:
        raise GeometryRejected("label_bottle_axis_disagreement")
    samples = []
    for x in np.linspace(lo[0]+.06*width, hi[0]-.06*width, 64):
        part = label[np.abs(label[:, 0]-x) < max(2., width/100)]
        if len(part):
            samples.append([x, part[:, 1].min(), part[:, 1].max()])
    if len(samples) < 35:
        raise GeometryRejected("insufficient_rim_support")
    samples = np.array(samples)
    top, e_top = fit_rim(samples[:, 0], samples[:, 1], center, inflated)
    bottom, e_bottom = fit_rim(samples[:, 0], samples[:, 2], center, inflated)
    basis = rim_basis(samples[:, 0], center, inflated)
    heights = basis @ (np.array(bottom)-top)
    median_height = float(np.median(heights))
    if median_height < 20 or min(heights) < median_height*.65 or max(heights) > median_height*1.35:
        raise GeometryRejected("crossing_or_divergent_rims")
    if max(e_top, e_bottom)/median_height > .045:
        raise GeometryRejected("irregular_rim_fit")
    if max(abs(top[2]), abs(bottom[2])) > inflated*1.2:
        raise GeometryRejected("extreme_rim_curvature")
    padding = .04*width
    x0 = max(float(lo[0]-padding), center-.975*inflated)
    x1 = min(float(hi[0]+padding), center+.975*inflated)
    return {
        "center": center, "radius": inflated, "silhouette_radius": radius,
        "radius_inflation": inflated/radius, "top": top, "bottom": bottom,
        "x_range": [x0, x1], "theta_range": [math.asin((x0-center)/inflated), math.asin((x1-center)/inflated)],
        "v_range": [-.04, 1.04], "height": median_height,
        "rim_residual_fraction": [e_top/median_height, e_bottom/median_height],
        "original_to_aligned": original_to_aligned.tolist(),
        "aligned_to_original": np.linalg.inv(original_to_aligned).tolist(),
        "sampled_rims": samples.tolist(),
    }


def input_to_original(points: np.ndarray, model: dict, variant: str, size: list[int]) -> np.ndarray:
    points = np.asarray(points, dtype=float).reshape(-1, 2)
    u, v = points[:, 0]/(size[0]-1), points[:, 1]/(size[1]-1)
    if variant == "F":
        theta = model['theta_range'][0] + u * np.ptp(model['theta_range'])
        x = model['center'] + model['radius']*np.sin(theta)
    elif variant == "E":
        x = model['x_range'][0] + u*np.ptp(model['x_range'])
    else:
        raise ValueError(variant)
    vertical = model['v_range'][0] + v*np.ptp(model['v_range'])
    basis = rim_basis(x, model['center'], model['radius'])
    top, bottom = basis @ model['top'], basis @ model['bottom']
    y = top + vertical*(bottom-top)
    return points_h(np.c_[x, y], np.array(model['aligned_to_original']))


def original_to_input(points: np.ndarray, model: dict, variant: str, size: list[int]) -> np.ndarray:
    aligned = points_h(points, np.array(model['original_to_aligned']))
    x, y = aligned.T
    if variant == 'F':
        u = (np.arcsin((x-model['center'])/model['radius']) - model['theta_range'][0])/np.ptp(model['theta_range'])
    else:
        u = (x-model['x_range'][0])/np.ptp(model['x_range'])
    basis = rim_basis(x, model['center'], model['radius'])
    top, bottom = basis @ model['top'], basis @ model['bottom']
    v = ((y-top)/(bottom-top)-model['v_range'][0])/np.ptp(model['v_range'])
    return np.c_[u*(size[0]-1), v*(size[1]-1)]


def rectify(image: np.ndarray, model: dict, variant: str, max_side: int = 1600) -> tuple[np.ndarray, np.ndarray]:
    width = model['radius']*np.ptp(model['theta_range']) if variant == 'F' else np.ptp(model['x_range'])
    height = model['height']*np.ptp(model['v_range'])
    scale = min(1., max_side/max(width, height))
    size = [max(2, round(width*scale)), max(2, round(height*scale))]
    yy, xx = np.indices(size[::-1], dtype=np.float32)
    grid = input_to_original(np.c_[xx.ravel(), yy.ravel()], model, variant, size).reshape(*yy.shape, 2).astype(np.float32)
    pixels = cv2.remap(image, grid[:, :, 0], grid[:, :, 1], cv2.INTER_LINEAR,
                       borderMode=cv2.BORDER_CONSTANT, borderValue=(255, 255, 255))
    return pixels, grid


def densify_polygon(polygon: list, steps: int = 12) -> np.ndarray:
    points = np.asarray(polygon, dtype=float)
    return np.concatenate([a[None, :] + np.linspace(0, 1, steps, endpoint=False)[:, None]*(b-a)
                           for a,b in zip(points, np.roll(points, -1, axis=0))])


def render_view(texture: np.ndarray, texture_mask: np.ndarray, theta_range: list, height_radius: float,
                yaw: float, pitch: float, roll: float, max_side: int = 700) -> np.ndarray:
    """Pinhole ray/cylinder intersection; unseen source texture stays transparent.

    Cylinder radius=1, camera at z=-6, focal length in output pixels. Rotation
    values describe the synthetic camera/object, not recovered source pose.
    """
    pitch, roll, yaw = np.radians([pitch, roll, yaw])
    cp,sp,cr,sr = math.cos(pitch),math.sin(pitch),math.cos(roll),math.sin(roll)
    rotate = np.array([[cr,-sr,0],[sr,cr,0],[0,0,1]]) @ np.array([[1,0,0],[0,cp,-sp],[0,sp,cp]])
    distance = 6.
    focal = (distance-1)*max_side/(max(2., height_radius)*1.5)
    yy,xx = np.indices((max_side,max_side), dtype=float)
    rays = np.stack([(xx-(max_side-1)/2)/focal, (yy-(max_side-1)/2)/focal, np.ones_like(xx)],axis=-1) @ rotate
    origin = np.array([0.,0.,-distance]) @ rotate
    a = rays[...,0]**2+rays[...,2]**2
    b = 2*(origin[0]*rays[...,0]+origin[2]*rays[...,2])
    c = origin[0]**2+origin[2]**2-1
    discriminant = b*b-4*a*c
    length = (-b-np.sqrt(np.maximum(0,discriminant)))/(2*a)
    hit = origin+rays*length[...,None]
    theta = np.arctan2(hit[...,0], -hit[...,2])-yaw
    u = (theta-theta_range[0])/np.ptp(theta_range)
    v = hit[...,1]/height_radius+.5
    valid = (discriminant>=0)&(length>0)&(u>=0)&(u<=1)&(v>=0)&(v<=1)
    mx = (u*(texture.shape[1]-1)).astype(np.float32)
    my = (v*(texture.shape[0]-1)).astype(np.float32)
    rgb = cv2.remap(texture,mx,my,cv2.INTER_LINEAR,borderMode=cv2.BORDER_CONSTANT)
    alpha = cv2.remap(texture_mask,mx,my,cv2.INTER_NEAREST,borderMode=cv2.BORDER_CONSTANT)
    alpha[~valid] = 0
    rgb[alpha==0] = 0
    return np.dstack([rgb,alpha])

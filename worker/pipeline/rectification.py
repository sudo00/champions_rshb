"""Label contours, planar rectification and coordinate transforms."""
from __future__ import annotations

import math

import cv2
import numpy as np

GEOMETRY = {"margin_fraction": 0.04, "max_side": 1600, "minimum_angle_deg": 0.5,
            "maximum_angle_deg": 35, "max_fit_residual_fraction": 0.045,
            "min_component_fraction": 0.97, "max_local_anisotropy": 2.5,
            "local_scale_range": [0.45, 2.2], "max_perturbation_fraction": 0.035}


class GeometryRejected(ValueError):
    """An expected geometric degeneracy, not a programming/runtime failure."""


def points_h(points: np.ndarray, matrix: np.ndarray) -> np.ndarray:
    points = np.asarray(points, dtype=np.float64).reshape(-1, 2)
    homogeneous = np.c_[points, np.ones(len(points))] @ matrix.T
    if np.any(np.abs(homogeneous[:, 2]) < 1e-8):
        raise GeometryRejected("homography_horizon")
    return homogeneous[:, :2] / homogeneous[:, 2:]


def scaled_contour(mask: np.ndarray, source_size: tuple[int, int]) -> tuple[np.ndarray, float]:
    contours, _ = cv2.findContours(mask.astype(np.uint8), cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    if not contours:
        raise GeometryRejected("empty_mask")
    largest = max(contours, key=cv2.contourArea)
    filled = np.zeros(mask.shape, dtype=np.uint8)
    cv2.drawContours(filled, [largest], -1, 1, -1)
    fraction = float(np.count_nonzero(filled & (mask > 0)) / np.count_nonzero(mask))
    # Pixel-centre mapping; x/y factors differ when the short dimension is rounded.
    scale = np.array(source_size, dtype=float) / np.array(mask.shape[::-1])
    contour = (largest[:, 0, :].astype(float) + 0.5) * scale - 0.5
    return contour, fraction


def robust_line(points: np.ndarray, independent: int) -> tuple[np.ndarray, float]:
    x, y = points[:, independent], points[:, 1 - independent]
    if len(x) < 10 or np.ptp(x) < 5:
        raise GeometryRejected("insufficient_edge_support")
    selected = np.ones(len(x), dtype=bool)
    for _ in range(3):
        a, b = np.polyfit(x[selected], y[selected], 1)
        residual = np.abs(y - (a * x + b))
        threshold = max(1.0, float(np.median(residual) * 2.5))
        selected = residual <= threshold
    # Residual includes the full edge support, so trimming cannot hide curvature.
    error = float(np.quantile(np.abs(y - (a * x + b)), 0.9))
    return (np.array([a, -1., b]) if independent == 0 else np.array([-1., a, b])), error


def boundary_lines(contour: np.ndarray) -> tuple[list[np.ndarray], list[float]]:
    low, high = contour.min(axis=0), contour.max(axis=0)
    size = high - low
    lines, errors = [], []
    for axis, upper in [(0, False), (1, True), (0, True), (1, False)]:
        # Outer envelope in 32 bins; omit corners, retain the middle 70% of each edge.
        samples = []
        for center in np.linspace(low[axis] + size[axis] * .15, high[axis] - size[axis] * .15, 32):
            part = contour[np.abs(contour[:, axis] - center) <= max(1.5, size[axis] / 55)]
            if len(part):
                samples.append(part[np.argmax(part[:, 1-axis]) if upper else np.argmin(part[:, 1-axis])])
        line, error = robust_line(np.array(samples), axis)
        lines.append(line)
        errors.append(error / max(1, size[1-axis]))
    return lines, errors


def quad_check(quad: np.ndarray) -> None:
    if not np.isfinite(quad).all() or not cv2.isContourConvex(quad.astype(np.float32)):
        raise GeometryRejected("nonconvex_quad")
    u, v = quad[1]-quad[0], quad[2]-quad[1]
    cross = u[0]*v[1] - u[1]*v[0]
    if cross <= 0 or cv2.contourArea(quad.astype(np.float32)) < 400:
        raise GeometryRejected("degenerate_or_reversed_quad")
    lengths = np.linalg.norm(np.roll(quad, -1, axis=0) - quad, axis=1)
    if min(lengths) < 20 or max(lengths) / min(lengths) > 10:
        raise GeometryRejected("extreme_quad_aspect")


def quad_homography(quad: np.ndarray) -> np.ndarray:
    quad_check(quad)
    edges = np.linalg.norm(np.roll(quad, -1, axis=0) - quad, axis=1)
    width, height = float((edges[0]+edges[2])/2), float((edges[1]+edges[3])/2)
    return cv2.getPerspectiveTransform(quad.astype(np.float32),
        np.array([[0, 0], [width, 0], [width, height], [0, height]], dtype=np.float32))


def validate_warp(matrix: np.ndarray, contour: np.ndarray, quad: np.ndarray) -> dict:
    minimum, maximum = contour.min(axis=0), contour.max(axis=0)
    samples = np.array([[x, y] for x in np.linspace(*[minimum[0], maximum[0]], 5)
                        for y in np.linspace(*[minimum[1], maximum[1]], 5)])
    denominators = np.c_[samples, np.ones(len(samples))] @ matrix[2]
    if np.min(denominators) * np.max(denominators) <= 0:
        raise GeometryRejected("homography_horizon")
    projected = points_h(samples, matrix)
    dx = points_h(samples + [1, 0], matrix) - projected
    dy = points_h(samples + [0, 1], matrix) - projected
    determinants = np.linalg.det(np.stack([dx, dy], axis=2))
    singular = np.linalg.svd(np.stack([dx, dy], axis=2), compute_uv=False)
    lo, hi = GEOMETRY["local_scale_range"]
    if min(determinants) <= 0 or singular.min() < lo or singular.max() > hi:
        raise GeometryRejected("excessive_or_negative_local_scale")
    anisotropy = float(max(singular[:, 0] / singular[:, 1]))
    if anisotropy > GEOMETRY["max_local_anisotropy"]:
        raise GeometryRejected("excessive_anisotropy")
    diagonal = np.linalg.norm(maximum-minimum)
    delta = min(maximum-minimum) * .005
    worst = 0.
    for i in range(4):
        for axis in range(2):
            for sign in (-1, 1):
                perturbed = quad.copy()
                perturbed[i, axis] += sign * delta
                mapped = points_h(samples, quad_homography(perturbed))
                worst = max(worst, float(np.max(np.linalg.norm(mapped-projected, axis=1))/diagonal))
    if worst > GEOMETRY["max_perturbation_fraction"]:
        raise GeometryRejected("unstable_corners")
    return {"local_scale_min": float(singular.min()), "local_scale_max": float(singular.max()),
            "max_anisotropy": anisotropy, "corner_perturbation_max_fraction": worst}


def geometry(contour: np.ndarray, fraction: float) -> dict:
    answer = {"C": {"status": "skipped"}, "D": {"status": "skipped"}}
    try:
        if fraction < GEOMETRY["min_component_fraction"]:
            raise GeometryRejected("disconnected_mask")
        if len(contour) < 30 or min(np.ptp(contour, axis=0)) < 20:
            raise GeometryRejected("small_mask")
        rect = cv2.boxPoints(cv2.minAreaRect(contour.astype(np.float32)))
        edges = np.roll(rect, -1, axis=0) - rect
        angles = (np.degrees(np.arctan2(edges[:, 1], edges[:, 0])) + 45) % 90 - 45
        angle = float(np.median(angles))
        if abs(angle) > GEOMETRY["maximum_angle_deg"]:
            raise GeometryRejected("ambiguous_orientation")
        initial = np.vstack([cv2.getRotationMatrix2D(tuple(contour.mean(axis=0)), angle, 1), [0, 0, 1]])
        aligned = points_h(contour, initial)
        lines, errors = boundary_lines(aligned)
        # Top/bottom robust lines refine the minimum rectangle's initial angle.
        slopes = [math.degrees(math.atan(lines[i][0])) for i in (0, 2)]
        if abs(slopes[0] - slopes[1]) > 12:
            raise GeometryRejected("inconsistent_top_bottom_directions")
        angle += sum(slopes)/2
        answer["C"]["angle_deg"] = angle
        if abs(angle) <= GEOMETRY["maximum_angle_deg"] and abs(angle) >= GEOMETRY["minimum_angle_deg"]:
            matrix = np.vstack([cv2.getRotationMatrix2D(tuple(contour.mean(axis=0)), angle, 1), [0, 0, 1]])
            answer["C"].update(status="applied", matrix=matrix.tolist(), reason="boundary_deskew")
        else:
            answer["C"]["reason"] = "near_upright" if abs(angle) < .5 else "ambiguous_orientation"
        answer["D"]["edge_residual_fractions"] = errors
        if max(errors) > GEOMETRY["max_fit_residual_fraction"]:
            raise GeometryRejected("curved_or_irregular_boundary")
        corners = []
        for a, b in [(3, 0), (0, 1), (1, 2), (2, 3)]:
            intersection = np.cross(lines[a], lines[b])
            if abs(intersection[2]) < 1e-8:
                raise GeometryRejected("parallel_adjacent_edges")
            corners.append(intersection[:2] / intersection[2])
        quad = points_h(np.array(corners), np.linalg.inv(initial))
        matrix = quad_homography(quad)
        checks = validate_warp(matrix, contour, quad)
        answer["D"].update(status="applied", reason="four_fitted_boundary_lines", matrix=matrix.tolist(),
                           quad_original=quad.tolist(), checks=checks)
    except GeometryRejected as exc:
        for value in answer.values():
            if value["status"] == "skipped" and "reason" not in value:
                value["reason"] = str(exc)
    return answer


def warp_crop(image: np.ndarray, crop: list[int], matrix: np.ndarray, max_side: int) -> tuple[np.ndarray, np.ndarray]:
    x0, y0, x1, y1 = crop
    # Preserve the entire RGB crop, including margin; do not clip to the fitted quad/mask.
    corners = np.array([[x0, y0], [x1-1, y0], [x1-1, y1-1], [x0, y1-1]], dtype=float)
    den = np.c_[corners, np.ones(4)] @ matrix[2]
    if min(den) * max(den) <= 0:
        raise GeometryRejected("crop_crosses_horizon")
    mapped = points_h(corners, matrix)
    lower, upper = np.floor(mapped.min(axis=0)), np.ceil(mapped.max(axis=0))
    size = upper - lower + 1
    factor = min(1., max_side / max(size))
    shift = np.array([[1, 0, -lower[0]], [0, 1, -lower[1]], [0, 0, 1]], dtype=float)
    scale = np.diag([factor, factor, 1])
    transform = scale @ shift @ matrix
    # Avoid 1600.0000000000002 rounding up to 1601.
    output_size = tuple(np.minimum(max_side, np.ceil(size * factor - 1e-9)).astype(int))
    # One interpolation, directly from original pixels.
    crop_to_original = np.array([[1., 0, x0], [0, 1., y0], [0, 0, 1.]])
    result = cv2.warpPerspective(image[y0:y1, x0:x1], transform @ crop_to_original, output_size, flags=cv2.INTER_LINEAR,
                                 borderMode=cv2.BORDER_CONSTANT, borderValue=(255, 255, 255))
    return result, transform



"""Reusable offline image-to-candidates prototype. One serialized instance per GPU.

Requires a versioned resource bundle and this repository's scripts/ geometry helpers.
No filename, annotation or expected identity participates in inference.
"""
from __future__ import annotations

import base64
import hashlib
import io
import json
import os
from pathlib import Path
import selectors
import subprocess
import sys
import tempfile
import threading
import time

import cv2
import numpy as np
from PIL import Image, ImageOps

from .hybrid_search import HybridIndex
from .label_observations import extract_observed_fields
from .prototype_ocr import PREFIX

ROOT = Path(__file__).resolve().parents[2]
VERSION = "wine-prototype-v4"


def file_hash(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_image(source: bytes | str | Path | Image.Image, max_pixels: int = 25_000_000) -> Image.Image:
    if isinstance(source, Image.Image):
        opened = source
    else:
        opened = Image.open(io.BytesIO(source) if isinstance(source, bytes) else source)
    try:
        if opened.width * opened.height > max_pixels:
            raise ValueError(f"Image exceeds {max_pixels} pixels")
        if getattr(opened, "n_frames", 1) != 1:
            raise ValueError("Expected a single-frame photograph")
        oriented = ImageOps.exif_transpose(opened).convert("RGBA")
        background = Image.new("RGBA", oriented.size, "white")
        background.alpha_composite(oriented)
        return background.convert("RGB")
    finally:
        if not isinstance(source, Image.Image):
            opened.close()


def crop_box(mask: np.ndarray, size: tuple[int, int]) -> list[int]:
    full = cv2.resize(mask.astype(np.uint8), size, interpolation=cv2.INTER_NEAREST)
    ys, xs = np.nonzero(full)
    if not len(xs):
        raise ValueError("Empty region")
    x0, y0, x1, y1 = int(xs.min()), int(ys.min()), int(xs.max()+1), int(ys.max()+1)
    margin = max(3, round(min(x1-x0, y1-y0)*.04))
    return [max(0, x0-margin), max(0, y0-margin), min(size[0], x1+margin), min(size[1], y1+margin)]


def select_region(candidates: list[dict], size: tuple[int, int], *, whole_object: bool) -> tuple[np.ndarray | None, dict]:
    """Choose a plausible whole object using confidence, area and camera priority.

    Use mask containment, not overlapping boxes: adjacent bottles must remain
    independent. A larger hypothesis can suppress a fragment only when its SAM
    score is at least as high. Do not apply this rule to separate paper labels.
    """
    width, height = size
    areas = [int(np.count_nonzero(c["mask"])) for c in candidates]
    suppressed = {}
    if whole_object:
        for i, candidate in enumerate(candidates):
            if not areas[i]:
                continue
            parents = []
            for j, parent in enumerate(candidates):
                if areas[j] < 1.5*areas[i] or parent["score"] < candidate["score"]:
                    continue
                overlap = np.count_nonzero((candidate["mask"] > 0) & (parent["mask"] > 0))/areas[i]
                if overlap >= .9:
                    parents.append(j)
            if parents:
                suppressed[i] = max(parents, key=lambda j: (candidates[j]["score"], areas[j]))
    def priority(i):
        b = candidates[i]["box"]
        centrality = max(0, 1-abs((b[0]+b[2])/2-width/2)/(width/2))
        area = max(0, b[2]-b[0])*max(0, b[3]-b[1])
        if whole_object:
            # A central sliver must not beat a full, confident bottle solely
            # because its box centre is a few pixels closer to the camera axis.
            value = .5*centrality+.3*np.sqrt(min(1, areas[i]/(width*height)))+.2*candidates[i]["score"]
        else:
            value = .7*centrality+.3*np.sqrt(min(1, area/(width*height)))
        return (value, candidates[i]["score"])
    eligible = [i for i in range(len(candidates)) if areas[i] and i not in suppressed]
    best = max(eligible, key=priority, default=None)
    evidence = dict(score=candidates[best]["score"] if best is not None else None,
                    selection_rule="whole_object_confidence_area_v1" if whole_object else "centrality_area_v1",
                    candidate_count=len(candidates), selected_index=best,
                    selected_box_xyxy=candidates[best]["box"] if best is not None else None,
                    suppressed_fragments=[dict(index=i, parent_index=j) for i, j in suppressed.items()])
    return (candidates[best]["mask"] if best is not None else None), evidence


class OCRProcess:
    def __init__(self, python: Path, models: Path, threads: int, timeout: float = 180., *, device: str = "cpu", release_cache: bool = False):
        self.timeout = timeout
        self.buffer = b""
        self.stderr = tempfile.TemporaryFile(mode="w+")
        env = dict(os.environ, PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK="True", FLAGS_allocator_strategy="auto_growth")
        env["WINE_OCR_RELEASE_CACHE"] = "1" if release_cache else "0"
        if device == "cpu":
            env["CUDA_VISIBLE_DEVICES"] = ""
        self.process = subprocess.Popen([str(python), "-u", str(Path(__file__).with_name("prototype_ocr.py")),
                                         str(models), str(threads), device], stdin=subprocess.PIPE,
                                        stdout=subprocess.PIPE, stderr=self.stderr, bufsize=0, env=env)
        try:
            self.runtime = self.receive()
            if not self.runtime.get("ready"):
                raise RuntimeError("OCR not ready")
        except Exception:
            self.close()
            raise

    def receive(self) -> dict:
        deadline = time.monotonic() + self.timeout
        with selectors.DefaultSelector() as selector:
            selector.register(self.process.stdout, selectors.EVENT_READ)
            while time.monotonic() < deadline:
                if b"\n" in self.buffer:
                    line, self.buffer = self.buffer.split(b"\n", 1)
                    line = line.decode("utf-8")
                    if line.startswith(PREFIX):
                        result = json.loads(line[len(PREFIX):])
                        if result.get("error"):
                            raise RuntimeError("OCR: " + result["error"])
                        return result
                    continue
                if not selector.select(max(0., deadline-time.monotonic())):
                    break
                chunk = os.read(self.process.stdout.fileno(), 65536)
                if not chunk:
                    raise RuntimeError("OCR subprocess exited unexpectedly")
                self.buffer += chunk
        raise TimeoutError("OCR subprocess timed out")

    def recognize(self, views: list[dict]) -> list[dict]:
        encoded = []
        for view in views:
            stream = io.BytesIO()
            view["ocr"].save(stream, format="PNG")
            encoded.append({"name": view["family"], "png": base64.b64encode(stream.getvalue()).decode("ascii")})
        payload = (json.dumps({"views": encoded})+"\n").encode("utf-8")
        pending = memoryview(payload)
        while pending:
            written = os.write(self.process.stdin.fileno(), pending)
            pending = pending[written:]
        result = self.receive()
        self.last_gpu_memory = result.get("gpu_memory_bytes", {})
        return result["lines"]

    def close(self) -> None:
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=5)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait()
        self.process.stdin.close()
        self.process.stdout.close()
        self.stderr.close()


class WineRecognizer:
    """Load once at worker startup; call predict(bytes), then close on shutdown.

    Scores rank cards and are NOT calibrated confidence probabilities.
    Concurrent callers are serialized; queue/backpressure belongs to the application.
    """
    def __init__(self, bundle_dir: str | Path, *, ocr_python: str | Path,
                 device: str = "cuda:0", ocr_threads: int = 4, ocr_device: str = "gpu:0",
                 memory_policy: str = "release"):
        started = time.perf_counter()
        self.lock = threading.Lock()
        self.closed = False
        self.ocr = None
        self.sam = self.encoder = self.text_inputs = None
        if memory_policy not in ("release", "retain"):
            raise ValueError("memory_policy must be release or retain")
        self.memory_policy = memory_policy
        if ocr_device != "cpu" and not (ocr_device.startswith("gpu:") and ocr_device[4:].isdigit()):
            raise ValueError("ocr_device must be cpu or gpu:N")
        self.bundle = Path(bundle_dir).resolve()
        self.manifest = json.loads((self.bundle / "manifest.json").read_text())
        if self.manifest["version"] != VERSION:
            raise ValueError("Unsupported resource bundle")
        for relative, expected in self.manifest["files_sha256"].items():
            path = (self.bundle / relative).resolve()
            if not path.is_relative_to(self.bundle) or file_hash(path) != expected:
                raise ValueError("Bundle integrity mismatch: " + relative)
        if file_hash(self.bundle/"catalog.jsonl") != self.manifest["catalog_sha256"]:
            raise ValueError("Catalogue version differs from bundle")
        for relative, expected in self.manifest["code_sha256"].items():
            path = (ROOT/relative).resolve()
            if not path.is_relative_to(ROOT) or file_hash(path) != expected:
                raise ValueError("Code differs from exported bundle: " + relative)
        # Geometry helpers remain repository-owned; deployment includes scripts/.
        scripts = str(ROOT / "scripts")
        if scripts not in sys.path:
            sys.path.insert(0, scripts)
        from label_rectification_pilot import scaled_contour, geometry, GeometryRejected, points_h
        from cylinder_geometry import fit_geometry, rectify, input_to_original, densify_polygon
        self.geometry = (scaled_contour, geometry, GeometryRejected, points_h,
                         fit_geometry, rectify, input_to_original, densify_polygon)
        import torch
        from transformers import Sam3Model, Sam3Processor, SiglipVisionModel
        if not torch.cuda.is_available() or not device.startswith("cuda"):
            raise RuntimeError("This prototype requires a CUDA GPU")
        if ocr_threads < 1:
            raise ValueError("ocr_threads must be positive")
        torch.set_num_threads(4)
        torch.backends.cuda.matmul.allow_tf32 = False
        torch.backends.cudnn.allow_tf32 = False
        cv2.setNumThreads(1)
        self.device = torch.device(device)
        self.cards = [json.loads(line) for line in (self.bundle/"catalog.jsonl").read_text().splitlines()]
        views = json.loads((self.bundle/"views.json").read_text())
        vectors = np.load(self.bundle/"features.npy", allow_pickle=False)
        self.index = HybridIndex(vectors, views, self.cards)
        self.lookup = {c["slug"]: c for c in self.cards}
        try:
            self.processor = Sam3Processor.from_pretrained(self.bundle/"sam3", local_files_only=True)
            self.sam = Sam3Model.from_pretrained(self.bundle/"sam3", local_files_only=True).eval().to(self.device)
            self.encoder, loading = SiglipVisionModel.from_pretrained(self.bundle/"siglip2", local_files_only=True, output_loading_info=True)
            if any(loading.get(key) for key in ("missing_keys", "mismatched_keys", "error_msgs")):
                raise ValueError("Incomplete SigLIP weights")
            self.encoder = self.encoder.eval().to(self.device)
            self.model_parameter_bytes = {
                name: sum(p.numel()*p.element_size() for p in model.parameters())
                for name, model in (("sam3", self.sam), ("siglip2_vision", self.encoder))}
            self.text_inputs = {p: self.processor(text=p, return_tensors="pt").to(self.device)
                                for p in ("bottle", "label", "wine box", "can")}
            # Do not resolve the executable symlink: that would bypass its virtualenv.
            self.ocr = OCRProcess(Path(ocr_python).absolute(), self.bundle/"ocr", ocr_threads,
                                  device=ocr_device, release_cache=memory_policy == "release")
            if memory_policy == "release":
                with torch.cuda.device(self.device):
                    torch.cuda.empty_cache()
        except Exception:
            self.close()
            raise
        torch.cuda.synchronize(self.device)
        self.load_seconds = time.perf_counter()-started

    def _regions(self, image: Image.Image) -> tuple[dict, dict, str]:
        import torch
        w, h = image.size
        ratio = min(1., 768/max(w, h))
        mw, mh = max(1, round(w*ratio)), max(1, round(h*ratio))
        masks, evidence = {}, {}
        with torch.inference_mode(), torch.autocast("cuda", dtype=torch.bfloat16):
            pixels = self.processor(images=image, return_tensors="pt")["pixel_values"].to(self.device)
            vision = self.sam.vision_encoder(pixels)
            def infer(prompt):
                output = self.sam(vision_embeds=vision, **self.text_inputs[prompt])
                parsed = self.processor.post_process_instance_segmentation(
                    output, threshold=.5, mask_threshold=.5, target_sizes=[[mh, mw]])[0]
                candidates = []
                for score, box, mask in zip(parsed["scores"], parsed["boxes"], parsed["masks"]):
                    mask = mask.to(torch.uint8).cpu().numpy()
                    if prompt == "label" and "object" in masks:
                        support = cv2.dilate(masks["object"], np.ones((5, 5), np.uint8)) > 0
                        if np.count_nonzero((mask > 0) & support)/max(1, np.count_nonzero(mask)) < .75:
                            continue
                    b = box.float().cpu().tolist()
                    candidates.append(dict(box=b, score=float(score), mask=mask))
                chosen, detail = select_region(candidates, (mw, mh), whole_object=prompt != "label")
                return chosen, dict(prompt=prompt, **detail)
            prompt = "bottle"
            mask, ev = infer(prompt)
            if mask is None:
                alternatives = [(m, e, p) for p in ("wine box", "can") for m, e in [infer(p)] if m is not None]
                if alternatives:
                    mask, ev, prompt = max(alternatives, key=lambda x: float(x[0].mean()))
            if mask is not None:
                masks["object"] = mask
            evidence["object"] = ev
            label, evidence["label"] = infer("label")
            if label is not None:
                masks["label"] = label
        return masks, evidence, prompt

    def _views(self, image: Image.Image, masks: dict, prompt: str) -> tuple[list, dict]:
        contour, geometry, rejected, _, fit, rectify, _, _ = self.geometry
        views = []
        for kind, mask in masks.items():
            box = crop_box(mask, image.size)
            full = image.crop(box)
            visual, ocr = full.copy(), full.copy()
            visual.thumbnail((768, 768), Image.Resampling.LANCZOS)
            ocr.thumbnail((1600, 1600), Image.Resampling.LANCZOS)
            views.append(dict(family="body" if kind == "object" else "label", visual=visual, ocr=ocr,
                              box=box, matrix=[[ (box[2]-box[0])/ocr.width, 0, box[0]],
                                               [0, (box[3]-box[1])/ocr.height, box[1]], [0, 0, 1]]))
        cylindrical = {"status": "skipped", "reason": "missing_label_or_bottle"}
        if prompt == "bottle" and "object" in masks and "label" in masks:
            try:
                label, fraction = contour(masks["label"], image.size)
                bottle, _ = contour(masks["object"], image.size)
                if fraction < .97:
                    raise rejected("disconnected_label")
                decisions = geometry(label, fraction)
                model = fit(label, bottle, np.array(decisions["C"].get("matrix", np.eye(3))))
                visual, _ = rectify(np.asarray(image), model, "F", max_side=768)
                ocr, _ = rectify(np.asarray(image), model, "F", max_side=1600)
                views.append(dict(family="flat", visual=Image.fromarray(visual), ocr=Image.fromarray(ocr), cylinder=model))
                cylindrical = {"status": "applied"}
            except rejected as exc:
                cylindrical = {"status": "skipped", "reason": str(exc)}
        return views, cylindrical

    def predict(self, photo: bytes | str | Path | Image.Image, *, top_k: int = 10) -> dict:
        if not 1 <= top_k <= 10:
            raise ValueError("top_k must be between 1 and 10")
        with self.lock:
            if self.closed:
                raise RuntimeError("Recognizer is closed")
            return self._predict(photo, top_k)

    def _predict(self, photo, top_k: int) -> dict:
        import torch
        started = time.perf_counter()
        torch.cuda.reset_peak_memory_stats(self.device)
        timings = {}
        image = load_image(photo)
        timings["decode"] = time.perf_counter()-started
        stamp = time.perf_counter()
        masks, evidence, prompt = self._regions(image)
        torch.cuda.synchronize(self.device)
        timings["segmentation"] = time.perf_counter()-stamp
        result = dict(version=VERSION, catalog_sha256=self.manifest["catalog_sha256"],
                      memory_policy=self.memory_policy,
                      ocr_runtime=self.ocr.runtime,
                      bundle_id=self.manifest["bundle_id"], image_size=list(image.size),
                      coordinate_system="pixels_after_exif_orientation", score_is_probability=False,
                      target=dict(object_detected="object" in masks, label_detected="label" in masks,
                                  packaging_prompt=prompt, evidence=evidence), candidates=[])
        warnings = ["unknown_rejection_not_calibrated"]
        if not masks:
            result.update(status="no_target", observations=[], observed_fields=extract_observed_fields([]), regions=[],
                          message="Поместите бутылку или упаковку с этикеткой перед камерой.")
        else:
            stamp = time.perf_counter()
            views, cylindrical = self._views(image, masks, prompt)
            timings["geometry"] = time.perf_counter()-stamp
            stamp = time.perf_counter()
            batch = []
            for view in views:
                padded = ImageOps.pad(view["visual"], (384, 384), method=Image.Resampling.BICUBIC, color="white")
                tensor = torch.from_numpy(np.array(padded).copy()).permute(2, 0, 1).float()/255
                batch.append((tensor-.5)/.5)
            with torch.inference_mode(), torch.autocast("cuda", dtype=torch.bfloat16):
                vectors = self.encoder(pixel_values=torch.stack(batch).to(self.device)).pooler_output.float().cpu().numpy()
            torch.cuda.synchronize(self.device)
            timings["embedding"] = time.perf_counter()-stamp
            # All GPU feature tensors are gone here; the NumPy vectors are on
            # CPU. Return unused Torch blocks before the Paddle process runs.
            if self.memory_policy == "release":
                cleanup_started = time.perf_counter()
                with torch.cuda.device(self.device):
                    torch.cuda.empty_cache()
                timings["memory_cleanup"] = time.perf_counter()-cleanup_started
            stamp = time.perf_counter()
            try:
                observations = self.ocr.recognize(views)
                result["ocr_gpu_memory_bytes"] = self.ocr.last_gpu_memory
            except Exception:
                # A failed protocol must not leak a late response into the next request.
                self.ocr.close()
                self.closed = True
                raise
            _, _, _, points_h, _, _, inverse, densify = self.geometry
            by_family = {v["family"]: v for v in views}
            mask = masks["object"] if "object" in masks else masks["label"]
            support = cv2.dilate(mask, np.ones((7, 7), np.uint8))
            for line in observations:
                view = by_family[line.pop("view")]
                polygon = (inverse(densify(line["polygon_input"]), view["cylinder"], "F", list(view["ocr"].size))
                           if "cylinder" in view else points_h(line["polygon_input"], np.array(view["matrix"])))
                point = polygon.mean(axis=0)
                x = round((point[0]+.5)*support.shape[1]/image.width-.5)
                y = round((point[1]+.5)*support.shape[0]/image.height-.5)
                line.update(polygon_original=polygon.tolist(), on_target_bottle=bool(
                    0 <= x < support.shape[1] and 0 <= y < support.shape[0] and support[y, x]))
            timings["ocr"] = time.perf_counter()-stamp
            stamp = time.perf_counter()
            search = self.index.search(vectors, [{"family": v["family"]} for v in views], observations, limit=top_k)
            candidates = []
            for candidate in search["candidates"]:
                card = self.lookup[candidate["slug"]]
                candidates.append({**candidate, **{key: card.get(key) for key in ("title", "winery", "grapes", "category", "region")}})
            timings["search"] = time.perf_counter()-stamp
            regions = []
            for kind, mask in masks.items():
                contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
                polygons = [(c.reshape(-1, 2)*[image.width/mask.shape[1], image.height/mask.shape[0]]).tolist() for c in contours]
                regions.append(dict(kind=kind, box_xyxy=crop_box(mask, image.size), polygons=polygons))
            result.update(status="candidates_unverified", candidates=candidates, observations=observations,
                          observed_fields=extract_observed_fields(observations), regions=regions, cylinder=cylindrical,
                          message="Найдены кандидаты; соответствие и наличие вина в каталоге автоматически не подтверждены.")
        result["warnings"] = warnings
        if not masks and self.memory_policy == "release":
            cleanup_started = time.perf_counter()
            with torch.cuda.device(self.device):
                torch.cuda.empty_cache()
            timings["memory_cleanup"] = time.perf_counter()-cleanup_started
        timings["total"] = time.perf_counter()-started
        result["timings_seconds"] = timings
        result["gpu_memory_bytes"] = dict(allocated=torch.cuda.memory_allocated(self.device),
                                          peak_allocated=torch.cuda.max_memory_allocated(self.device),
                                          reserved=torch.cuda.memory_reserved(self.device),
                                          peak_reserved=torch.cuda.max_memory_reserved(self.device))
        return result

    def close(self) -> None:
        with self.lock:
            if self.ocr is not None and self.ocr.process.poll() is None:
                self.ocr.close()
            self.closed = True
            self.sam = self.encoder = self.text_inputs = None
            import torch
            torch.cuda.empty_cache()

    def __enter__(self):
        return self

    def __exit__(self, *_):
        self.close()

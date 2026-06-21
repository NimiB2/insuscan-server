import uuid
import base64
import os
import io
import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel
from typing import List, Optional
from PIL import Image
import torch
from mobile_sam import sam_model_registry, SamPredictor

app = FastAPI()

MODEL_TYPE = "vit_t"
CHECKPOINT = os.getenv("SAM_CHECKPOINT", "mobile_sam.pt")
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"

DEBUG_DIR = os.getenv("SAM_DEBUG_DIR", "/app/debug/masks")


def save_mask_debug(image_np, mask, label, scan_id):
    if not os.getenv("SAM_DEBUG_ENABLED", "false").lower() == "true":
        return
    os.makedirs(DEBUG_DIR, exist_ok=True)
    overlay = image_np.copy()
    colored = np.zeros_like(overlay)
    colored[mask] = [255, 0, 0]
    blended = (overlay * 0.6 + colored * 0.4).astype(np.uint8)
    safe_label = "".join(c if c.isalnum() else "_" for c in label)
    filename = f"{scan_id}_{safe_label}.png"
    Image.fromarray(blended).save(os.path.join(DEBUG_DIR, filename))


def save_depth_debug(depth_np, scan_id):
    if not os.getenv("SAM_DEBUG_ENABLED", "false").lower() == "true":
        return
    os.makedirs(DEBUG_DIR, exist_ok=True)
    d_min = float(depth_np.min())
    d_max = float(depth_np.max())
    if d_max - d_min < 1e-6:
        normalized = np.zeros_like(depth_np, dtype=np.uint8)
    else:
        normalized = ((depth_np - d_min) / (d_max - d_min) * 255.0).astype(np.uint8)
    filename = f"{scan_id}_depth.png"
    Image.fromarray(normalized).save(os.path.join(DEBUG_DIR, filename))

sam = sam_model_registry[MODEL_TYPE](checkpoint=CHECKPOINT)
sam.to(device=DEVICE)
sam.eval()
predictor = SamPredictor(sam)
from transformers import pipeline as hf_pipeline

DEPTH_MODEL = os.getenv("DEPTH_MODEL", "depth-anything/Depth-Anything-V2-Small-hf")
depth_estimator = hf_pipeline(task="depth-estimation", model=DEPTH_MODEL, device=DEVICE)

class BoundingBox(BaseModel):
    x_pct: float
    y_pct: float
    w_pct: float
    h_pct: float


class PlateCircle(BaseModel):
    cx: float
    cy: float
    r: float


class SegmentRequest(BaseModel):
    image_base64: str
    bboxes: List[BoundingBox]
    plate_circle: Optional[PlateCircle] = None


class SegmentResult(BaseModel):
    mask_pixel_counts: List[int]
    mask_scores: List[float]
    image_pixel_count: int


class HeightResult(BaseModel):
    height_cm: float
    mask_pixel_count: int
    image_pixel_count: int


class SideSegmentRequest(BaseModel):
    image_base64: str
    bboxes: List[BoundingBox]
    pixel_to_cm_ratio: float


class SideSegmentResult(BaseModel):
    heights: List[HeightResult]


def decode_image(image_base64: str):
    image_bytes = base64.b64decode(image_base64)
    image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    return image, np.array(image)


def run_sam(image_np, bbox, W, H):
    x1 = (bbox.x_pct / 100.0) * W
    y1 = (bbox.y_pct / 100.0) * H
    x2 = ((bbox.x_pct + bbox.w_pct) / 100.0) * W
    y2 = ((bbox.y_pct + bbox.h_pct) / 100.0) * H
    box = np.array([x1, y1, x2, y2])
    masks, scores, _ = predictor.predict(box=box, multimask_output=False)
    return masks[0], float(scores[0])


def build_plate_mask(plate_circle, H, W):
    if plate_circle is None or plate_circle.r <= 0:
        return None
    yy, xx = np.ogrid[:H, :W]
    dist_sq = (xx - plate_circle.cx) ** 2 + (yy - plate_circle.cy) ** 2
    return dist_sq <= (plate_circle.r ** 2)


def resolve_exclusive_counts(masks, scores, H, W):
    if not masks:
        return []

    owner = np.full((H, W), -1, dtype=np.int32)
    best_score = np.full((H, W), -1.0, dtype=np.float32)

    for idx, mask in enumerate(masks):
        m = mask.astype(bool)
        takeover = m & (scores[idx] > best_score)
        owner[takeover] = idx
        best_score[takeover] = scores[idx]

    counts = []
    for idx in range(len(masks)):
        counts.append(int(np.count_nonzero(owner == idx)))
    return counts


def dilate_mask(mask, iterations):
    m = mask.copy()
    for _ in range(iterations):
        shifted = m.copy()
        shifted[1:, :] |= m[:-1, :]
        shifted[:-1, :] |= m[1:, :]
        shifted[:, 1:] |= m[:, :-1]
        shifted[:, :-1] |= m[:, 1:]
        m = shifted
    return m


@app.post("/segment", response_model=SegmentResult)
def segment(request: SegmentRequest):
    image, image_np = decode_image(request.image_base64)
    W = image.width
    H = image.height
    image_pixel_count = W * H

    predictor.set_image(image_np)

    plate_mask = build_plate_mask(request.plate_circle, H, W)

    scan_id = uuid.uuid4().hex[:8]
    masks = []
    scores = []
    for idx, bbox in enumerate(request.bboxes):
        raw_mask, score = run_sam(image_np, bbox, W, H)
        save_mask_debug(image_np, raw_mask, f"{idx}_raw", scan_id)
        if plate_mask is not None:
            final_mask = raw_mask & plate_mask
        else:
            final_mask = raw_mask
        save_mask_debug(image_np, final_mask, f"{idx}_final", scan_id)
        masks.append(final_mask)
        scores.append(score)

    counts = resolve_exclusive_counts(masks, scores, H, W)

    return SegmentResult(
        mask_pixel_counts=counts,
        mask_scores=[round(s, 4) for s in scores],
        image_pixel_count=image_pixel_count
    )


@app.post("/segment_side", response_model=SideSegmentResult)
def segment_side(request: SideSegmentRequest):
    image, image_np = decode_image(request.image_base64)
    W = image.width
    H = image.height

    predictor.set_image(image_np)

    scan_id = uuid.uuid4().hex[:8]
    heights = []
    for idx, bbox in enumerate(request.bboxes):
        mask, _ = run_sam(image_np, bbox, W, H)
        save_mask_debug(image_np, mask, f"side_{idx}", scan_id)

        rows = np.any(mask, axis=1)
        if rows.any():
            top_row = np.argmax(rows)
            bottom_row = H - 1 - np.argmax(rows[::-1])
            height_px = bottom_row - top_row
        else:
            height_px = 0

        height_cm = height_px * request.pixel_to_cm_ratio

        heights.append(HeightResult(
            height_cm=round(float(height_cm), 2),
            mask_pixel_count=int(mask.sum()),
            image_pixel_count=W * H
        ))

    return SideSegmentResult(heights=heights)


class DepthRequest(BaseModel):
    image_base64: str


class DepthResult(BaseModel):
    width: int
    height: int
    depth_min: float
    depth_max: float


@app.post("/depth", response_model=DepthResult)
def depth(request: DepthRequest):
    image, image_np = decode_image(request.image_base64)
    result = depth_estimator(image)
    depth_np = np.array(result["depth"], dtype=np.float32)

    scan_id = uuid.uuid4().hex[:8]
    save_depth_debug(depth_np, scan_id)

    return DepthResult(
        width=image.width,
        height=image.height,
        depth_min=float(depth_np.min()),
        depth_max=float(depth_np.max())
    )

class DepthItemsRequest(BaseModel):
    image_base64: str
    bboxes: List[BoundingBox]
    ring_width: int = 20
    plate_circle: Optional[PlateCircle] = None


class DepthItemStat(BaseModel):
    item_depth_median: float
    ring_depth_median: float
    protrusion: float
    mask_pixel_count: int


class DepthItemsResult(BaseModel):
    width: int
    height: int
    items: List[DepthItemStat]


@app.post("/depth_items", response_model=DepthItemsResult)
def depth_items(request: DepthItemsRequest):
    image, image_np = decode_image(request.image_base64)
    W = image.width
    H = image.height

    predictor.set_image(image_np)

    result = depth_estimator(image)
    depth_np = np.array(result["depth"], dtype=np.float32)

    scan_id = uuid.uuid4().hex[:8]
    save_depth_debug(depth_np, scan_id)

    masks = []
    for bbox in request.bboxes:
        mask, _ = run_sam(image_np, bbox, W, H)
        masks.append(mask.astype(bool))

    all_food = np.zeros((H, W), dtype=bool)
    for m in masks:
        all_food |= m

    plate_mask = build_plate_mask(request.plate_circle, H, W)

    items = []
    for idx, mask in enumerate(masks):
        others = all_food & (~mask)
        dilated = dilate_mask(mask, request.ring_width)
        ring = dilated & (~mask) & (~others)
        if plate_mask is not None:
            ring = ring & plate_mask

        item_vals = depth_np[mask]
        ring_vals = depth_np[ring]

        item_med = float(np.median(item_vals)) if item_vals.size > 0 else 0.0
        ring_med = float(np.median(ring_vals)) if ring_vals.size > 0 else 0.0

        items.append(DepthItemStat(
            item_depth_median=round(item_med, 2),
            ring_depth_median=round(ring_med, 2),
            protrusion=round(item_med - ring_med, 2),
            mask_pixel_count=int(mask.sum())
        ))

    return DepthItemsResult(width=W, height=H, items=items)
    
    
@app.get("/health")
def health():
    return {"status": "ok", "device": DEVICE}
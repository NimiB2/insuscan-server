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

sam = sam_model_registry[MODEL_TYPE](checkpoint=CHECKPOINT)
sam.to(device=DEVICE)
sam.eval()
predictor = SamPredictor(sam)


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


@app.post("/segment", response_model=SegmentResult)
def segment(request: SegmentRequest):
    image, image_np = decode_image(request.image_base64)
    W = image.width
    H = image.height
    image_pixel_count = W * H

    predictor.set_image(image_np)

    plate_mask = build_plate_mask(request.plate_circle, H, W)

    masks = []
    scores = []
    for bbox in request.bboxes:
        mask, score = run_sam(image_np, bbox, W, H)
        if plate_mask is not None:
            mask = mask & plate_mask
        masks.append(mask)
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

    heights = []
    for bbox in request.bboxes:
        mask, _ = run_sam(image_np, bbox, W, H)

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


@app.get("/health")
def health():
    return {"status": "ok", "device": DEVICE}
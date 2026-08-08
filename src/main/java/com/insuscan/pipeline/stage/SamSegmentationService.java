package com.insuscan.pipeline.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import com.insuscan.pipeline.model.PipelineWarning;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Stages 3.5 and 5.5 — Calls the external SAM (Segment Anything Model) microservice to refine food-item masks.
 * Top-view segmentation replaces bounding-box area estimates with pixel-accurate masks; side-view segmentation
 * fuses SAM's height measurement with Gemini's effective-height estimate, preferring Gemini on disagreement
 * since it accounts for the food's "effective" cylindrical height rather than raw pixel extent.
 */
@Service
public class SamSegmentationService {

    private static final Logger log = LoggerFactory.getLogger(SamSegmentationService.class);

    /** Confidence recorded when top-view SAM segmentation succeeds. */
    private static final float SAM_TOP_SUCCESS_CONFIDENCE = 0.9f;

    /** Fallback bounding box (as % of image) used when an item has no detected bounding box. */
    private static final float FALLBACK_BBOX_PCT = 80f;
    private static final float FALLBACK_BBOX_OFFSET_PCT = 10f;

    /** Height (cm) and confidence used when both Gemini and SAM report zero height. */
    private static final float FALLBACK_HEIGHT_CM = 1.0f;
    private static final float FALLBACK_HEIGHT_CONFIDENCE = 0.3f;

    /** Confidence used when only one of Gemini/SAM produced a height. */
    private static final float GEMINI_ONLY_CONFIDENCE = 0.85f;
    private static final float SAM_ONLY_CONFIDENCE = 0.85f;

    /** Confidence used when Gemini and SAM heights agree within {@link #AGREEMENT_RATIO_THRESHOLD}. */
    private static final float FUSED_AGREE_CONFIDENCE = 0.95f;

    /** Confidence used when Gemini and SAM heights disagree and Gemini's estimate is preferred. */
    private static final float GEMINI_PREFERRED_CONFIDENCE = 0.80f;

    /** Height ratio above which Gemini and SAM are considered to disagree. */
    private static final float AGREEMENT_RATIO_THRESHOLD = 1.5f;

    @Value("${sam.service.url:http://localhost:8001}")
    private String samServiceUrl;

    private final PipelineWarningCollector warningCollector;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SamSegmentationService(PipelineWarningCollector warningCollector,
                                   ObjectMapper objectMapper) {
        this.warningCollector = warningCollector;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public void segmentFoodItems(PipelineContext ctx) {
        List<PipelineFoodItem> items = ctx.getFoodItems();

        if (items == null || items.isEmpty()) {
            log.info("[SAM] No food items to segment.");
            ctx.recordConfidence("SAM_SEGMENTATION", 1.0f);
            return;
        }

        if (ctx.getImageTopBase64() == null) {
            log.warn("[SAM] No top image in context, skipping segmentation.");
            ctx.recordConfidence("SAM_SEGMENTATION", 0f);
            return;
        }

        boolean anyHasBbox = items.stream().anyMatch(PipelineFoodItem::hasBoundingBox);
        if (!anyHasBbox) {
            log.warn("[SAM] No food items have bounding boxes, skipping segmentation.");
            ctx.recordConfidence("SAM_SEGMENTATION", 0f);
            return;
        }

        try {
            String requestBody = buildRequestBody(ctx, items);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(samServiceUrl + "/segment"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[SAM] Service returned status {}", response.statusCode());
                warningCollector.add(ctx, PipelineWarning.medium(
                    "SAM_SEGMENTATION", "SAM_HTTP_ERROR",
                    "SAM service returned status " + response.statusCode() + ". Using bbox fallback."
                ));
                return;
            }

            applyResults(response.body(), items, ctx);

        } catch (Exception e) {
            String errorDetails = formatException(e);
            log.warn("[SAM] Failed to call SAM service at {}: {}", samServiceUrl, errorDetails);
            warningCollector.add(ctx, PipelineWarning.medium(
                "SAM_SEGMENTATION", "SAM_UNAVAILABLE",
                "SAM service unavailable. Using bbox fallback for area calculation."
            ));
            ctx.recordConfidence("SAM_SEGMENTATION", 0f);
        }
    }

    private String buildRequestBody(PipelineContext ctx, List<PipelineFoodItem> items) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("session_id", ctx.getSessionId());
        root.put("image_base64", ctx.getImageTopBase64());

        ArrayNode bboxes = objectMapper.createArrayNode();
        for (PipelineFoodItem item : items) {
            ObjectNode bbox = objectMapper.createObjectNode();
            if (item.hasBoundingBox()) {
                float[] box = item.getBoundingBoxPct();
                bbox.put("x_pct", box[0]);
                bbox.put("y_pct", box[1]);
                bbox.put("w_pct", box[2]);
                bbox.put("h_pct", box[3]);
            } else {
                bbox.put("x_pct", FALLBACK_BBOX_OFFSET_PCT);
                bbox.put("y_pct", FALLBACK_BBOX_OFFSET_PCT);
                bbox.put("w_pct", FALLBACK_BBOX_PCT);
                bbox.put("h_pct", FALLBACK_BBOX_PCT);
            }
            bboxes.add(bbox);
        }
        root.set("bboxes", bboxes);
        return objectMapper.writeValueAsString(root);
    }

    private void applyResults(String responseBody, List<PipelineFoodItem> items, PipelineContext ctx) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode counts = root.path("mask_pixel_counts");
            int imagePixelCount = root.path("image_pixel_count").asInt(0);

            if (!counts.isArray() || counts.size() != items.size()) {
                log.warn("[SAM] Response size mismatch: expected {} got {}", items.size(), counts.size());
                ctx.recordConfidence("SAM_SEGMENTATION", 0f);
                return;
            }

            JsonNode scores = root.path("mask_scores");
            for (int i = 0; i < items.size(); i++) {
                int maskPixels = counts.get(i).asInt(0);
                items.get(i).setMaskPixelCount(maskPixels);
                items.get(i).setImagePixelCount(imagePixelCount);

                if (scores.isArray() && i < scores.size()) {
                    items.get(i).setSamMaskScore((float) scores.get(i).asDouble(0));
                }

                log.info("[SAM] {} -> maskPixels={} / totalPixels={} score={}",
                    items.get(i).getName(), maskPixels, imagePixelCount,
                    items.get(i).getSamMaskScore() != null
                        ? String.format("%.3f", items.get(i).getSamMaskScore()) : "N/A");
            }

            ctx.recordConfidence("SAM_SEGMENTATION", SAM_TOP_SUCCESS_CONFIDENCE);

        } catch (Exception e) {
            log.warn("[SAM] Failed to parse SAM response: {}", e.getMessage());
            ctx.recordConfidence("SAM_SEGMENTATION", 0f);
        }
    }
    public void segmentSideImage(PipelineContext ctx) {
        List<PipelineFoodItem> items = ctx.getFoodItems();

        if (items == null || items.isEmpty()) {
            log.info("[SAM-SIDE] No food items to segment.");
            return;
        }

        if (ctx.getImageSideBase64() == null) {
            log.warn("[SAM-SIDE] No side image in context, skipping.");
            return;
        }

        if (ctx.getPixelToCmRatioSide() == null || ctx.getPixelToCmRatioSide() <= 0) {
            log.warn("[SAM-SIDE] Missing side calibration ratio, skipping.");
            return;
        }

        boolean anyHasBbox = items.stream().anyMatch(PipelineFoodItem::hasSideBoundingBox);
        if (!anyHasBbox) {
            log.warn("[SAM-SIDE] No food items have side bounding boxes, skipping.");
            return;
        }

        try {
            String requestBody = buildSideRequestBody(ctx, items);
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(samServiceUrl + "/segment_side"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("[SAM-SIDE] Service returned status {}", response.statusCode());
                return;
            }

            applySideResults(response.body(), items);

        } catch (Exception e) {
            log.warn("[SAM-SIDE] Failed to call SAM side service at {}: {}", samServiceUrl, formatException(e));
        }
    }

    private String buildSideRequestBody(PipelineContext ctx, List<PipelineFoodItem> items) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("session_id", ctx.getSessionId());
        root.put("image_base64", ctx.getImageSideBase64());
        root.put("pixel_to_cm_ratio", ctx.getPixelToCmRatioSide());

        ArrayNode bboxes = objectMapper.createArrayNode();
        for (PipelineFoodItem item : items) {
            ObjectNode bbox = objectMapper.createObjectNode();
            if (item.hasSideBoundingBox()) {
                float[] box = item.getBoundingBoxSidePct();
                bbox.put("x_pct", box[0]);
                bbox.put("y_pct", box[1]);
                bbox.put("w_pct", box[2]);
                bbox.put("h_pct", box[3]);
            } else {
                bbox.put("x_pct", FALLBACK_BBOX_OFFSET_PCT);
                bbox.put("y_pct", FALLBACK_BBOX_OFFSET_PCT);
                bbox.put("w_pct", FALLBACK_BBOX_PCT);
                bbox.put("h_pct", FALLBACK_BBOX_PCT);
            }
            bboxes.add(bbox);
        }
        root.set("bboxes", bboxes);

        float[] circle = ctx.getPlateCircleTopPx();
        if (circle != null && circle.length == 3 && circle[2] > 0) {
            ObjectNode plate = objectMapper.createObjectNode();
            plate.put("cx", circle[0]);
            plate.put("cy", circle[1]);
            plate.put("r", circle[2]);
            root.set("plate_circle", plate);
            log.info("[SAM] Sending plate circle: cx={} cy={} r={}",
                (int) circle[0], (int) circle[1], (int) circle[2]);
        }

        return objectMapper.writeValueAsString(root);
    }

    private void applySideResults(String responseBody, List<PipelineFoodItem> items) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode heights = root.path("heights");
            if (!heights.isArray() || heights.size() != items.size()) {
                log.warn("[SAM-SIDE] Response size mismatch: expected {} got {}", items.size(), heights.size());
                return;
            }
            for (int i = 0; i < items.size(); i++) {
                applySideResultForItem(heights.get(i), items.get(i));
            }
        } catch (Exception e) {
            log.warn("[SAM-SIDE] Failed to parse side SAM response: {}", e.getMessage());
        }
    }

    private void applySideResultForItem(JsonNode heightNode, PipelineFoodItem item) {
        float samHeightCm = (float) heightNode.path("height_cm").asDouble(0);
        int maskPixels = heightNode.path("mask_pixel_count").asInt(0);
        int imagePixels = heightNode.path("image_pixel_count").asInt(0);

        item.setMaskSidePixelCount(maskPixels);
        item.setImageSidePixelCount(imagePixels);

        float geminiHeightCm = item.getEffectiveHeightCm();
        HeightFusionResult fused = fuseHeights(geminiHeightCm, samHeightCm, item.getName());

        item.setEffectiveHeightCm(fused.height());
        item.setHeightConfidence(fused.confidence());
    }

    private HeightFusionResult fuseHeights(float geminiHeightCm, float samHeightCm, String itemName) {
        boolean hasGemini = geminiHeightCm > 0;
        boolean hasSam = samHeightCm > 0;

        if (!hasGemini && !hasSam) {
            log.warn("[SAM-SIDE] {} -> both heights are 0, defaulting to {}cm", itemName, FALLBACK_HEIGHT_CM);
            return new HeightFusionResult(FALLBACK_HEIGHT_CM, FALLBACK_HEIGHT_CONFIDENCE, "FALLBACK_DEFAULT");
        }

        if (!hasSam) {
            log.info("[SAM-SIDE] {} -> SAM=0, keeping Gemini={}cm", itemName, String.format("%.2f", geminiHeightCm));
            return new HeightFusionResult(geminiHeightCm, GEMINI_ONLY_CONFIDENCE, "GEMINI_ONLY");
        }

        if (!hasGemini) {
            log.info("[SAM-SIDE] {} -> Gemini=0, using SAM={}cm", itemName, String.format("%.2f", samHeightCm));
            return new HeightFusionResult(samHeightCm, SAM_ONLY_CONFIDENCE, "SAM_ONLY");
        }

        // Both available - compare them
        float ratio = Math.max(geminiHeightCm, samHeightCm) / Math.min(geminiHeightCm, samHeightCm);

        if (ratio < AGREEMENT_RATIO_THRESHOLD) {
            // They agree — use weighted average
            float avg = (geminiHeightCm + samHeightCm) / 2f;
            log.info("[SAM-SIDE] {} -> Gemini={}cm, SAM={}cm, AGREE (ratio={}) -> fused={}cm",
                itemName,
                String.format("%.2f", geminiHeightCm),
                String.format("%.2f", samHeightCm),
                String.format("%.2f", ratio),
                String.format("%.2f", avg));
            return new HeightFusionResult(avg, FUSED_AGREE_CONFIDENCE, "FUSED_AGREE");
        }

        // They disagree - trust Gemini (it knows "effective" height)
        log.warn("[SAM-SIDE] {} -> Gemini={}cm, SAM={}cm, DISAGREE (ratio={}) -> preferring Gemini",
            itemName,
            String.format("%.2f", geminiHeightCm),
            String.format("%.2f", samHeightCm),
            String.format("%.2f", ratio));
        return new HeightFusionResult(geminiHeightCm, GEMINI_PREFERRED_CONFIDENCE, "GEMINI_PREFERRED");
    }

    private record HeightFusionResult(float height, float confidence, String source) {}

    private String formatException(Exception e) {
        String msg = e.getMessage();
        String type = e.getClass().getSimpleName();
        return (msg != null && !msg.isBlank()) ? type + ": " + msg : type + " (no message)";
    }
}
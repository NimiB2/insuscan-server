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

@Service
public class SamSegmentationService {

    private static final Logger log = LoggerFactory.getLogger(SamSegmentationService.class);

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
                bbox.put("x_pct", 10f);
                bbox.put("y_pct", 10f);
                bbox.put("w_pct", 80f);
                bbox.put("h_pct", 80f);
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

            for (int i = 0; i < items.size(); i++) {
                int maskPixels = counts.get(i).asInt(0);
                items.get(i).setMaskPixelCount(maskPixels);
                items.get(i).setImagePixelCount(imagePixelCount);
                log.info("[SAM] {} -> maskPixels={} / totalPixels={}",
                    items.get(i).getName(), maskPixels, imagePixelCount);
            }

            ctx.recordConfidence("SAM_SEGMENTATION", 0.9f);

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
                bbox.put("x_pct", 10f);
                bbox.put("y_pct", 10f);
                bbox.put("w_pct", 80f);
                bbox.put("h_pct", 80f);
            }
            bboxes.add(bbox);
        }
        root.set("bboxes", bboxes);
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
            log.warn("[SAM-SIDE] {} -> both heights are 0, defaulting to 1cm", itemName);
            return new HeightFusionResult(1.0f, 0.3f, "FALLBACK_DEFAULT");
        }

        if (!hasSam) {
            log.info("[SAM-SIDE] {} -> SAM=0, keeping Gemini={:.2f}cm", itemName, geminiHeightCm);
            return new HeightFusionResult(geminiHeightCm, 0.85f, "GEMINI_ONLY");
        }

        if (!hasGemini) {
            log.info("[SAM-SIDE] {} -> Gemini=0, using SAM={}cm", itemName, String.format("%.2f", samHeightCm));
            return new HeightFusionResult(samHeightCm, 0.85f, "SAM_ONLY");
        }

        // Both available — compare them
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
            return new HeightFusionResult(avg, 0.95f, "FUSED_AGREE");
        }

        // They disagree — trust Gemini (it knows "effective" height)
        log.warn("[SAM-SIDE] {} -> Gemini={}cm, SAM={}cm, DISAGREE (ratio={}) -> preferring Gemini",
            itemName,
            String.format("%.2f", geminiHeightCm),
            String.format("%.2f", samHeightCm),
            String.format("%.2f", ratio));
        return new HeightFusionResult(geminiHeightCm, 0.80f, "GEMINI_PREFERRED");
    }

    private static final float AGREEMENT_RATIO_THRESHOLD = 1.5f;

    private record HeightFusionResult(float height, float confidence, String source) {}
    
    private String formatException(Exception e) {
        String msg = e.getMessage();
        String type = e.getClass().getSimpleName();
        return (msg != null && !msg.isBlank()) ? type + ": " + msg : type + " (no message)";
    }
}
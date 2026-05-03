package com.insuscan.pipeline.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import com.insuscan.pipeline.support.ReferenceObjectRegistry;
import com.insuscan.util.GeminiApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CalibrationService {

    private static final Logger log = LoggerFactory.getLogger(CalibrationService.class);

    private final GeminiApiClient geminiApiClient;
    private final ReferenceObjectRegistry referenceObjectRegistry;
    private final PipelineWarningCollector warningCollector;
    private final ObjectMapper objectMapper;

    public CalibrationService(GeminiApiClient geminiApiClient,
                              ReferenceObjectRegistry referenceObjectRegistry,
                              PipelineWarningCollector warningCollector,
                              ObjectMapper objectMapper) {
        this.geminiApiClient = geminiApiClient;
        this.referenceObjectRegistry = referenceObjectRegistry;
        this.warningCollector = warningCollector;
        this.objectMapper = objectMapper;
    }

    public void calibrate(PipelineContext ctx) {
        ReferenceObjectRegistry.Dimensions dims =
            referenceObjectRegistry.getDimensions(ctx.getReferenceObjectType());

        DetectionResult topResult  = detectTopImage(ctx.getImageTopBase64(), dims, ctx.getReferenceObjectType());
        DetectionResult sideResult = detectSideImage(ctx.getImageSideBase64(), dims, ctx.getReferenceObjectType());

        if (!topResult.found && !sideResult.found) {
            warningCollector.calibrationFailed(ctx,
                "Top: " + topResult.reasonIfNotFound + " | Side: " + sideResult.reasonIfNotFound);
            ctx.recordConfidence("CALIBRATION", 0f);
            return;
        }

        Float ratioTop  = topResult.found  ? topResult.pixelToCmRatio  : null;
        Float ratioSide = sideResult.found ? sideResult.pixelToCmRatio : null;

        if (!topResult.found) {
            warningCollector.referenceObjectNotFound(ctx, "top");
            ratioTop = ratioSide;
        } else if (!sideResult.found) {
            warningCollector.referenceObjectNotFound(ctx, "side");
            ratioSide = ratioTop;
        }

        ctx.setPixelToCmRatioTop(ratioTop);
        ctx.setPixelToCmRatioSide(ratioSide);

        if (topResult.found)  ctx.setReferenceBoundsTopPx(topResult.bboxPx);
        if (sideResult.found) ctx.setReferenceBoundsSidePx(sideResult.bboxPx);

        float confidence = computeConfidence(topResult, sideResult);
        ctx.recordConfidence("CALIBRATION", confidence);

        log.info("[Calibration] ratioTop={} ratioSide={} confidence={}",
            String.format("%.4f", ratioTop), String.format("%.4f", ratioSide), String.format("%.2f", confidence));
    }

    private DetectionResult detectTopImage(String base64Image,
                                           ReferenceObjectRegistry.Dimensions dims,
                                           String refType) {
        String prompt = buildTopPrompt(referenceObjectRegistry.getDescription(refType), dims);
        try {
            String response = geminiApiClient.callVisionModel(prompt, base64Image);
            if (response == null || response.isBlank())
                return DetectionResult.notFound("Empty response from vision model");
            return parseTopResponse(response, dims);
        } catch (Exception e) {
            log.warn("[Calibration][TOP] Vision model call failed: {}", e.getMessage());
            return DetectionResult.notFound("Vision model error: " + e.getMessage());
        }
    }

    private DetectionResult detectSideImage(String base64Image,
                                            ReferenceObjectRegistry.Dimensions dims,
                                            String refType) {
        String prompt = buildSidePrompt(referenceObjectRegistry.getDescription(refType), dims);
        try {
            String response = geminiApiClient.callVisionModel(prompt, base64Image);
            if (response == null || response.isBlank())
                return DetectionResult.notFound("Empty response from vision model");
            return parseSideResponse(response, dims);
        } catch (Exception e) {
            log.warn("[Calibration][SIDE] Vision model call failed: {}", e.getMessage());
            return DetectionResult.notFound("Vision model error: " + e.getMessage());
        }
    }

    private String buildTopPrompt(String description, ReferenceObjectRegistry.Dimensions dims) {
        return String.format(
            "Find the following reference object in this TOP-DOWN image for scale calibration.\n\n" +
            "Reference object: %s\n" +
            "Known physical dimensions: %.2fcm long × %.2fcm wide.\n\n" +
            "The object is placed OUTSIDE the food container, next to the plate.\n" +
            "Return its bounding box in pixels. Report it even if partially visible.\n\n" +
            "Return ONLY valid JSON with no markdown:\n" +
            "{\n" +
            "  \"found\": <true or false>,\n" +
            "  \"bbox_px\": { \"x\": <int>, \"y\": <int>, \"width\": <int>, \"height\": <int> },\n" +
            "  \"confidence\": <float 0.0-1.0>,\n" +
            "  \"reason_if_not_found\": \"<string>\"\n" +
            "}",
            description, dims.lengthCm(), dims.widthCm());
    }

    private String buildSidePrompt(String description, ReferenceObjectRegistry.Dimensions dims) {
        return String.format(
            "Find the following reference object in this SIDE-VIEW image for scale calibration.\n\n" +
            "Reference object: %s\n" +
            "Known physical dimension visible from the side: %.2fcm.\n\n" +
            "The object is placed OUTSIDE the food container, next to the plate.\n" +
            "Return its bounding box in pixels. Report it even if partially visible.\n\n" +
            "Return ONLY valid JSON with no markdown:\n" +
            "{\n" +
            "  \"found\": <true or false>,\n" +
            "  \"bbox_px\": { \"x\": <int>, \"y\": <int>, \"width\": <int>, \"height\": <int> },\n" +
            "  \"confidence\": <float 0.0-1.0>,\n" +
            "  \"reason_if_not_found\": \"<string>\"\n" +
            "}",
            description, dims.sideViewDimensionCm());
    }

    private DetectionResult parseTopResponse(String response, ReferenceObjectRegistry.Dimensions dims) {
        try {
            JsonNode root = objectMapper.readTree(response);
            boolean found = root.path("found").asBoolean(false);
            if (!found) {
                String reason = root.path("reason_if_not_found").asText("not found");
                log.warn("[Calibration][TOP] Not found: {}", reason);
                return DetectionResult.notFound(reason);
            }

            JsonNode bbox = root.path("bbox_px");
            float bboxW = (float) bbox.path("width").asDouble(0);
            float bboxH = (float) bbox.path("height").asDouble(0);

            if (bboxW <= 0 || bboxH <= 0)
                return DetectionResult.notFound("Bounding box has zero dimensions");

            float bboxX = (float) bbox.path("x").asDouble(0);
            float bboxY = (float) bbox.path("y").asDouble(0);
            float confidence = (float) root.path("confidence").asDouble(0.5);

            float bboxAreaPx = bboxW * bboxH;
            float realAreaCm = dims.lengthCm() * dims.widthCm();
            float pixelToCmRatio = (float) Math.sqrt(realAreaCm / bboxAreaPx);

            log.info("[Calibration][TOP] Found: bbox=[{},{},{},{}] conf={} ratio={}",
                (int)bboxX, (int)bboxY, (int)bboxW, (int)bboxH,
                String.format("%.2f", confidence), String.format("%.4f", pixelToCmRatio));

            return new DetectionResult(true, new float[]{bboxX, bboxY, bboxW, bboxH},
                pixelToCmRatio, confidence, null);

        } catch (Exception e) {
            log.warn("[Calibration][TOP] Failed to parse response: {}", e.getMessage());
            return DetectionResult.notFound("JSON parse error: " + e.getMessage());
        }
    }

    private DetectionResult parseSideResponse(String response, ReferenceObjectRegistry.Dimensions dims) {
        try {
            JsonNode root = objectMapper.readTree(response);
            boolean found = root.path("found").asBoolean(false);
            if (!found) {
                String reason = root.path("reason_if_not_found").asText("not found");
                log.warn("[Calibration][SIDE] Not found: {}", reason);
                return DetectionResult.notFound(reason);
            }

            JsonNode bbox = root.path("bbox_px");
            float bboxW = (float) bbox.path("width").asDouble(0);
            float bboxH = (float) bbox.path("height").asDouble(0);

            if (bboxW <= 0 || bboxH <= 0)
                return DetectionResult.notFound("Bounding box has zero dimensions");

            float bboxX = (float) bbox.path("x").asDouble(0);
            float bboxY = (float) bbox.path("y").asDouble(0);
            float confidence = (float) root.path("confidence").asDouble(0.5);

            float visibleDimPx = Math.min(bboxW, bboxH);
            float pixelToCmRatio = dims.sideViewDimensionCm() / visibleDimPx;

            log.info("[Calibration][SIDE] Found: bbox=[{},{},{},{}] conf={} ratio={}",
                (int)bboxX, (int)bboxY, (int)bboxW, (int)bboxH,
                String.format("%.2f", confidence), String.format("%.4f", pixelToCmRatio));

            return new DetectionResult(true, new float[]{bboxX, bboxY, bboxW, bboxH},
                pixelToCmRatio, confidence, null);

        } catch (Exception e) {
            log.warn("[Calibration][SIDE] Failed to parse response: {}", e.getMessage());
            return DetectionResult.notFound("JSON parse error: " + e.getMessage());
        }
    }

    private float computeConfidence(DetectionResult top, DetectionResult side) {
        if (top.found && side.found) return (top.confidence + side.confidence) / 2f;
        if (top.found)  return top.confidence * 0.75f;
        if (side.found) return side.confidence * 0.75f;
        return 0f;
    }

    record DetectionResult(boolean found, float[] bboxPx, float pixelToCmRatio,
            float confidence, String reasonIfNotFound) {
static DetectionResult notFound(String reason) {
return new DetectionResult(false, null, 0f, 0f, reason);
}
}
}
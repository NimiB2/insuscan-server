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

/**
 * Stage 1 — detects the reference object in both images via Gemini Pro,
 * computes pixel-to-cm ratios, and cross-validates them.
 *
 * <p>Writes to: {@code ctx.pixelToCmRatioTop}, {@code ctx.pixelToCmRatioSide},
 * {@code ctx.referenceBoundsTopPx}, {@code ctx.referenceBoundsSidePx}.</p>
 *
 * <p>Fatal condition: reference object not detected in BOTH images → adds CRITICAL warning.</p>
 */
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

        DetectionResult topResult = detectReferenceObject(ctx.getImageTopBase64(), dims, ctx.getReferenceObjectType());
        DetectionResult sideResult = detectReferenceObject(ctx.getImageSideBase64(), dims, ctx.getReferenceObjectType());

        if (!topResult.found && !sideResult.found) {
            warningCollector.calibrationFailed(ctx,
                "Top: " + topResult.reasonIfNotFound + " | Side: " + sideResult.reasonIfNotFound);
            ctx.recordConfidence("CALIBRATION", 0f);
            return;
        }

        Float ratioTop = topResult.found ? topResult.pixelToCmRatio : null;
        Float ratioSide = sideResult.found ? sideResult.pixelToCmRatio : null;

        if (!topResult.found) {
            warningCollector.referenceObjectNotFound(ctx, "top");
            ratioTop = ratioSide;
        } else if (!sideResult.found) {
            warningCollector.referenceObjectNotFound(ctx, "side");
            ratioSide = ratioTop;
        } else {
            float gap = Math.abs(ratioTop - ratioSide) / Math.max(ratioTop, ratioSide);
            if (gap > 0.30f) {
                warningCollector.calibrationRatioMismatch(ctx, ratioTop, ratioSide, gap * 100);
            }
        }

        ctx.setPixelToCmRatioTop(ratioTop);
        ctx.setPixelToCmRatioSide(ratioSide);

        if (topResult.found) ctx.setReferenceBoundsTopPx(topResult.bboxPx);
        if (sideResult.found) ctx.setReferenceBoundsSidePx(sideResult.bboxPx);

        float confidence = computeConfidence(topResult, sideResult);
        ctx.recordConfidence("CALIBRATION", confidence);

        log.info("[Calibration] ratioTop={} ratioSide={} confidence={}",
            String.format("%.2f", ratioTop), String.format("%.2f", ratioSide), String.format("%.2f", confidence));
    }

    private DetectionResult detectReferenceObject(String base64Image,
                                                  ReferenceObjectRegistry.Dimensions dims,
                                                  String refType) {
        String description = referenceObjectRegistry.getDescription(refType);
        String prompt = buildDetectionPrompt(description, dims);

        try {
            String response = geminiApiClient.callVisionModel(prompt, base64Image);
            if (response == null || response.isBlank()) {
                return DetectionResult.notFound("Empty response from vision model");
            }
            return parseDetectionResponse(response, dims);
        } catch (Exception e) {
            log.warn("[Calibration] Vision model call failed: {}", e.getMessage());
            return DetectionResult.notFound("Vision model error: " + e.getMessage());
        }
    }

    private String buildDetectionPrompt(String description, ReferenceObjectRegistry.Dimensions dims) {
        return String.format(
            "Find the following reference object in this image for scale calibration.\n\n" +
            "Reference object: %s\n" +
            "Known physical dimensions: %.2fcm long × %.2fcm wide.\n\n" +
            "Locate the object and return its bounding box in pixels.\n" +
            "Also include the total image dimensions in pixels.\n\n" +
            "Return ONLY valid JSON with no markdown or extra text:\n" +
            "{\n" +
            "  \"image_width_px\": <integer>,\n" +
            "  \"image_height_px\": <integer>,\n" +
            "  \"found\": <true or false>,\n" +
            "  \"orientation\": \"horizontal\" or \"vertical\",\n" +
            "  \"bbox_px\": {\n" +
            "    \"x\": <integer, left edge>,\n" +
            "    \"y\": <integer, top edge>,\n" +
            "    \"width\": <integer>,\n" +
            "    \"height\": <integer>\n" +
            "  },\n" +
            "  \"confidence\": <float between 0.0 and 1.0>,\n" +
            "  \"reason_if_not_found\": \"<reason string if found is false, else empty string>\"\n" +
            "}",
            description, dims.lengthCm(), dims.widthCm());
    }

    private DetectionResult parseDetectionResponse(String response, ReferenceObjectRegistry.Dimensions dims) {
        try {
            JsonNode root = objectMapper.readTree(response);

            boolean found = root.path("found").asBoolean(false);
            if (!found) {
                return DetectionResult.notFound(root.path("reason_if_not_found").asText("not found"));
            }

            JsonNode bbox = root.path("bbox_px");
            if (bbox.isMissingNode()) {
                return DetectionResult.notFound("Bounding box missing in response");
            }

            float bboxW = (float) bbox.path("width").asDouble(0);
            float bboxH = (float) bbox.path("height").asDouble(0);

            if (bboxW <= 0 || bboxH <= 0) {
                return DetectionResult.notFound("Bounding box has zero dimensions");
            }

            float bboxX = (float) bbox.path("x").asDouble(0);
            float bboxY = (float) bbox.path("y").asDouble(0);

            String orientation = root.path("orientation").asText("horizontal");
            float refLengthPx = "horizontal".equalsIgnoreCase(orientation) ? bboxW : bboxH;
            float pixelToCmRatio = refLengthPx / dims.lengthCm();

            float confidence = (float) root.path("confidence").asDouble(0.5);

            return new DetectionResult(true, new float[]{bboxX, bboxY, bboxW, bboxH},
                pixelToCmRatio, confidence, orientation, null);

        } catch (Exception e) {
            log.warn("[Calibration] Failed to parse detection response: {}", e.getMessage());
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
                           float confidence, String orientation, String reasonIfNotFound) {
        static DetectionResult notFound(String reason) {
            return new DetectionResult(false, null, 0f, 0f, null, reason);
        }
    }
}

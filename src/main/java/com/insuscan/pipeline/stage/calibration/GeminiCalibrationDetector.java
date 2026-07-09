package com.insuscan.pipeline.stage.calibration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.pipeline.support.ReferenceObjectRegistry;
import com.insuscan.util.GeminiApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Calls Gemini to detect the reference object in the top or side image and parses the JSON response into a {@link DetectionResult}.
 * Falls back to an alternative-object parse if the primary object was not found but an alternative was reported.
 */
@Component
public class GeminiCalibrationDetector {

    private static final Logger log = LoggerFactory.getLogger(GeminiCalibrationDetector.class);

    private final GeminiApiClient geminiApiClient;
    private final ObjectMapper objectMapper;
    private final CalibrationPromptBuilder promptBuilder;

    public GeminiCalibrationDetector(GeminiApiClient geminiApiClient,
            ObjectMapper objectMapper,
            CalibrationPromptBuilder promptBuilder) {
        this.geminiApiClient = geminiApiClient;
        this.objectMapper = objectMapper;
        this.promptBuilder = promptBuilder;
    }

    public DetectionResult detectTop(String base64Image, ReferenceObjectRegistry.Dimensions dims, String refType) {
        String prompt = (dims == null)
            ? promptBuilder.topPromptNone()
            : promptBuilder.topPrompt(refType, dims);
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

    public DetectionResult detectSide(String base64Image, ReferenceObjectRegistry.Dimensions dims, String refType) {
        String prompt = (dims == null)
            ? promptBuilder.sidePromptNone()
            : promptBuilder.sidePrompt(refType, dims);
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

    private DetectionResult parseTopResponse(String response, ReferenceObjectRegistry.Dimensions dims) {
        try {
            JsonNode root = objectMapper.readTree(response);
            boolean found = root.path("found").asBoolean(false);
            if (!found) {
                String reason = root.path("reason_if_not_found").asText("not found");
                DetectionResult alt = tryParseAlternative(root, reason, "TOP");
                if (alt != null) return alt;
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

            log.info("[Calibration][TOP] Found: bbox=[{},{},{},{}] conf={} ratio={}", (int) bboxX, (int) bboxY,
                    (int) bboxW, (int) bboxH, String.format("%.2f", confidence), String.format("%.4f", pixelToCmRatio));

            return new DetectionResult(true, new float[] { bboxX, bboxY, bboxW, bboxH }, pixelToCmRatio, confidence,
                    null, root.path("detected_object_type").asText(null),
                    root.path("alternative_object_type").asText(null));

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
                DetectionResult alt = tryParseAlternative(root, reason, "SIDE");
                if (alt != null) return alt;
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

            log.info("[Calibration][SIDE] Found: bbox=[{},{},{},{}] conf={} ratio={}", (int) bboxX, (int) bboxY,
                    (int) bboxW, (int) bboxH, String.format("%.2f", confidence), String.format("%.4f", pixelToCmRatio));

            return new DetectionResult(true, new float[] { bboxX, bboxY, bboxW, bboxH }, pixelToCmRatio, confidence,
                    null, root.path("detected_object_type").asText(null),
                    root.path("alternative_object_type").asText(null));

        } catch (Exception e) {
            log.warn("[Calibration][SIDE] Failed to parse response: {}", e.getMessage());
            return DetectionResult.notFound("JSON parse error: " + e.getMessage());
        }
    }

    private DetectionResult tryParseAlternative(JsonNode root, String reason, String viewTag) {
        String altType = root.path("alternative_object_type").asText(null);
        if (altType == null || altType.isBlank() || "null".equalsIgnoreCase(altType)) {
            return null;
        }
        JsonNode bbox = root.path("bbox_px");
        float bboxX = (float) bbox.path("x").asDouble(0);
        float bboxY = (float) bbox.path("y").asDouble(0);
        float bboxW = (float) bbox.path("width").asDouble(0);
        float bboxH = (float) bbox.path("height").asDouble(0);
        if (bboxW <= 0 || bboxH <= 0) return null;
        float altConfidence = (float) root.path("confidence").asDouble(0.5);
        log.info("[Calibration][{}] Primary not found, alternative '{}' detected: bbox=[{},{},{},{}]",
            viewTag, altType, (int) bboxX, (int) bboxY, (int) bboxW, (int) bboxH);
        return new DetectionResult(false, new float[]{bboxX, bboxY, bboxW, bboxH},
            0f, altConfidence, reason, null, altType);
    }

    public record DetectionResult(boolean found, float[] bboxPx, float pixelToCmRatio, float confidence,
            String reasonIfNotFound, String detectedObjectType, String alternativeObjectType) {
        public static DetectionResult notFound(String reason) {
            return new DetectionResult(false, null, 0f, 0f, reason, null, null);
        }
    }
}
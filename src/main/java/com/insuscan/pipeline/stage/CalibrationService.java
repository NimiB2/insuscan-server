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
import com.insuscan.enums.ReferenceObjectDimensions;
import com.insuscan.pipeline.stage.calibration.CalibrationFallbackResolver;

@Service
public class CalibrationService {

	private static final Logger log = LoggerFactory.getLogger(CalibrationService.class);

	private final GeminiApiClient geminiApiClient;
	private final ReferenceObjectRegistry referenceObjectRegistry;
	private final PipelineWarningCollector warningCollector;
	private final ObjectMapper objectMapper;
	private final CalibrationFallbackResolver fallbackResolver;


	public CalibrationService(GeminiApiClient geminiApiClient,
            ReferenceObjectRegistry referenceObjectRegistry,
            PipelineWarningCollector warningCollector,
            ObjectMapper objectMapper,
            CalibrationFallbackResolver fallbackResolver) {
this.geminiApiClient = geminiApiClient;
this.referenceObjectRegistry = referenceObjectRegistry;
this.warningCollector = warningCollector;
this.objectMapper = objectMapper;
this.fallbackResolver = fallbackResolver;
}

public void calibrate(PipelineContext ctx) {
	    String selectedType = ctx.getReferenceObjectType();
	    ReferenceObjectRegistry.Dimensions dims =
	        referenceObjectRegistry.getDimensions(selectedType);

	    DetectionResult topResult  = detectTopImage(ctx.getImageTopBase64(), dims, selectedType);
	    DetectionResult sideResult = detectSideImage(ctx.getImageSideBase64(), dims, selectedType);

	    CalibrationFallbackResolver.FallbackResolution topResolution  = fallbackResolver.resolve(topResult, "top", selectedType);
	    CalibrationFallbackResolver.FallbackResolution sideResolution = fallbackResolver.resolve(sideResult, "side", selectedType);

	    applyFallbackWarnings(ctx, topResolution, sideResolution);

	    ctx.setPixelToCmRatioTop(topResolution.pixelToCmRatio());
	    ctx.setPixelToCmRatioSide(sideResolution.pixelToCmRatio());

	    if (topResult.found())  ctx.setReferenceBoundsTopPx(topResult.bboxPx());
	    if (sideResult.found()) ctx.setReferenceBoundsSidePx(sideResult.bboxPx());

	    boolean bothFellBackToPlate =
	        topResolution.source()  == CalibrationFallbackResolver.FallbackSource.PLATE_SIZE_ESTIMATE &&
	        sideResolution.source() == CalibrationFallbackResolver.FallbackSource.PLATE_SIZE_ESTIMATE;
	    ctx.setUseStandardPlateFallback(bothFellBackToPlate);

	    float confidence = (topResolution.confidence() + sideResolution.confidence()) / 2f;
	    ctx.recordConfidence("CALIBRATION", confidence);

	    log.info("[Calibration] top={}({}) side={}({}) confidence={} plateFallback={}",
	        String.format("%.4f", topResolution.pixelToCmRatio()), topResolution.source(),
	        String.format("%.4f", sideResolution.pixelToCmRatio()), sideResolution.source(),
	        String.format("%.2f", confidence), bothFellBackToPlate);
	}

private void applyFallbackWarnings(PipelineContext ctx,
	                                   CalibrationFallbackResolver.FallbackResolution top,
	                                   CalibrationFallbackResolver.FallbackResolution side) {
	    if (top.source() == CalibrationFallbackResolver.FallbackSource.ALTERNATIVE_OBJECT_DETECTED) {
	        warningCollector.alternativeObjectUsed(ctx, "top", top.selectedType(), top.detectedObjectType());
	    } else if (top.source() == CalibrationFallbackResolver.FallbackSource.PLATE_SIZE_ESTIMATE) {
	        warningCollector.usingPlateSizeEstimate(ctx, "top", top.selectedType());
	    }

	    if (side.source() == CalibrationFallbackResolver.FallbackSource.ALTERNATIVE_OBJECT_DETECTED) {
	        warningCollector.alternativeObjectUsed(ctx, "side", side.selectedType(), side.detectedObjectType());
	    } else if (side.source() == CalibrationFallbackResolver.FallbackSource.PLATE_SIZE_ESTIMATE) {
	        warningCollector.usingPlateSizeEstimate(ctx, "side", side.selectedType());
	    }
	}

	private DetectionResult detectTopImage(String base64Image, ReferenceObjectRegistry.Dimensions dims,
			String refType) {
		String prompt = (dims == null)
		    ? buildTopPromptNone()
		    : buildTopPrompt(referenceObjectRegistry.getDescription(refType), dims);
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

	private DetectionResult detectSideImage(String base64Image, ReferenceObjectRegistry.Dimensions dims,
			String refType) {
		String prompt = (dims == null)
		    ? buildSidePromptNone()
		    : buildSidePrompt(referenceObjectRegistry.getDescription(refType), dims);
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
	        "PRIMARY object to find: %s\n" +
	        "Known physical dimensions: %.2fcm long × %.2fcm wide.\n\n" +
	        "The object is placed OUTSIDE the food container, next to the plate.\n\n" +
	        "STEP 1: Look for the PRIMARY object. If found, set 'found' to true.\n" +
	        "STEP 2: If the PRIMARY object is NOT found, scan for any of these known objects:\n%s\n" +
	        "If you find one, set 'found' to false and 'alternative_object_type' to its exact name.\n\n" +
	        "Return ONLY valid JSON with no markdown:\n" +
	        "{\n" +
	        "  \"found\": <true or false>,\n" +
	        "  \"bbox_px\": { \"x\": <int>, \"y\": <int>, \"width\": <int>, \"height\": <int> },\n" +
	        "  \"confidence\": <float 0.0-1.0>,\n" +
	        "  \"detected_object_type\": \"<PRIMARY type if found, else null>\",\n" +
	        "  \"alternative_object_type\": \"<alternative type if found, else null>\",\n" +
	        "  \"reason_if_not_found\": \"<string>\"\n" +
	        "}",
	        description, dims.lengthCm(), dims.widthCm(), buildKnownObjectsList());
	}

	private String buildSidePrompt(String description, ReferenceObjectRegistry.Dimensions dims) {
	    return String.format(
	        "Find the following reference object in this SIDE-VIEW image for scale calibration.\n\n" +
	        "PRIMARY object to find: %s\n" +
	        "Known physical dimension visible from the side: %.2fcm.\n\n" +
	        "The object is placed OUTSIDE the food container, next to the plate.\n\n" +
	        "STEP 1: Look for the PRIMARY object. If found, set 'found' to true.\n" +
	        "STEP 2: If the PRIMARY object is NOT found, scan for any of these known objects:\n%s\n" +
	        "If you find one, set 'found' to false and 'alternative_object_type' to its exact name.\n\n" +
	        "Return ONLY valid JSON with no markdown:\n" +
	        "{\n" +
	        "  \"found\": <true or false>,\n" +
	        "  \"bbox_px\": { \"x\": <int>, \"y\": <int>, \"width\": <int>, \"height\": <int> },\n" +
	        "  \"confidence\": <float 0.0-1.0>,\n" +
	        "  \"detected_object_type\": \"<PRIMARY type if found, else null>\",\n" +
	        "  \"alternative_object_type\": \"<alternative type if found, else null>\",\n" +
	        "  \"reason_if_not_found\": \"<string>\"\n" +
	        "}",
	        description, dims.sideViewDimensionCm(), buildKnownObjectsList());
	}


	private String buildTopPromptNone() {
	    return String.format(
	        "Find any reference object in this TOP-DOWN image for scale calibration.\n\n" +
	        "The user did NOT select a primary reference object. " +
	        "Scan the entire image for any of these known objects:\n%s\n" +
	        "The object is typically placed OUTSIDE the food container, next to the plate.\n\n" +
	        "If you find one, set 'found' to false, set 'alternative_object_type' to its exact name, " +
	        "and fill 'bbox_px' with its bounding box.\n" +
	        "If you find none, set 'found' to false and 'alternative_object_type' to null.\n\n" +
	        "Return ONLY valid JSON with no markdown:\n" +
	        "{\n" +
	        "  \"found\": false,\n" +
	        "  \"bbox_px\": { \"x\": <int>, \"y\": <int>, \"width\": <int>, \"height\": <int> },\n" +
	        "  \"confidence\": <float 0.0-1.0>,\n" +
	        "  \"detected_object_type\": null,\n" +
	        "  \"alternative_object_type\": \"<type if found, else null>\",\n" +
	        "  \"reason_if_not_found\": \"<string>\"\n" +
	        "}",
	        buildKnownObjectsList());
	}

	private String buildSidePromptNone() {
	    return String.format(
	        "Find any reference object in this SIDE-VIEW image for scale calibration.\n\n" +
	        "The user did NOT select a primary reference object. " +
	        "Scan the entire image for any of these known objects:\n%s\n" +
	        "The object is typically placed OUTSIDE the food container, next to the plate.\n\n" +
	        "If you find one, set 'found' to false, set 'alternative_object_type' to its exact name, " +
	        "and fill 'bbox_px' with its bounding box.\n" +
	        "If you find none, set 'found' to false and 'alternative_object_type' to null.\n\n" +
	        "Return ONLY valid JSON with no markdown:\n" +
	        "{\n" +
	        "  \"found\": false,\n" +
	        "  \"bbox_px\": { \"x\": <int>, \"y\": <int>, \"width\": <int>, \"height\": <int> },\n" +
	        "  \"confidence\": <float 0.0-1.0>,\n" +
	        "  \"detected_object_type\": null,\n" +
	        "  \"alternative_object_type\": \"<type if found, else null>\",\n" +
	        "  \"reason_if_not_found\": \"<string>\"\n" +
	        "}",
	        buildKnownObjectsList());
	}


	private String buildKnownObjectsList() {
	    StringBuilder sb = new StringBuilder();
	    for (ReferenceObjectDimensions ref : ReferenceObjectDimensions.values()) {
	        if (ref == ReferenceObjectDimensions.NONE) continue;
	        sb.append("- ").append(ref.name())
	          .append(": ").append(ref.getScaleDescription()).append("\n");
	    }
	    return sb.toString();
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
		static DetectionResult notFound(String reason) {
			return new DetectionResult(false, null, 0f, 0f, reason, null, null);
		}
	}
}
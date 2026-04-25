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
 * Stage 2 — estimates plate/container geometry using both images and the calibrated scale.
 *
 * <p>Writes to: {@code ctx.plateGeometry}.</p>
 *
 * <p>On API failure, falls back to a default flat-plate geometry with zero confidence
 * rather than stopping the pipeline.</p>
 */
@Service
public class PlateGeometryService {

    private static final Logger log = LoggerFactory.getLogger(PlateGeometryService.class);

    private static final float DEFAULT_DIAMETER_CM = 24.0f;
    private static final float DEFAULT_DEPTH_CM    = 2.5f;
    private static final float DEFAULT_FILL_PCT    = 70.0f;

    private final GeminiApiClient geminiApiClient;
    private final ReferenceObjectRegistry referenceObjectRegistry;
    private final PipelineWarningCollector warningCollector;
    private final ObjectMapper objectMapper;

    public PlateGeometryService(GeminiApiClient geminiApiClient,
                                ReferenceObjectRegistry referenceObjectRegistry,
                                PipelineWarningCollector warningCollector,
                                ObjectMapper objectMapper) {
        this.geminiApiClient = geminiApiClient;
        this.referenceObjectRegistry = referenceObjectRegistry;
        this.warningCollector = warningCollector;
        this.objectMapper = objectMapper;
    }

    public void measurePlate(PipelineContext ctx) {
        String prompt = buildPrompt(ctx);

        PipelineContext.PlateGeometry geometry;
        float confidence;

        try {
            String response = geminiApiClient.callVisionModelWithMultipleImages(
                prompt, ctx.getImageTopBase64(), ctx.getImageSideBase64());

            if (response == null || response.isBlank()) {
                log.warn("[PlateGeometry] Empty response, using defaults");
                geometry = buildDefaultGeometry();
                confidence = 0.3f;
            } else {
                geometry = parseGeometryResponse(response);
                confidence = (geometry.getDiameterConfidence() + geometry.getDepthConfidence()) / 2f;
            }
        } catch (Exception e) {
            log.warn("[PlateGeometry] API call failed: {}, using defaults", e.getMessage());
            geometry = buildDefaultGeometry();
            confidence = 0.3f;
        }

        if ("FLAT_PLATE".equals(geometry.getContainerType()) && geometry.getInnerDepthCm() > 3.0f) {
            warningCollector.plateTypeDepthMismatch(ctx, geometry.getContainerType(), geometry.getInnerDepthCm());
        }

        ctx.setPlateGeometry(geometry);
        ctx.recordConfidence("PLATE_GEOMETRY", confidence);

        log.info("[PlateGeometry] type={} diameter={}cm depth={}cm fill={}% confidence={}",
            geometry.getContainerType(),
            String.format("%.1f", geometry.getInnerDiameterCm()),
            String.format("%.1f", geometry.getInnerDepthCm()),
            String.format("%.0f", geometry.getFillPercent()),
            String.format("%.2f", confidence));
    }

    private String buildPrompt(PipelineContext ctx) {
        ReferenceObjectRegistry.Dimensions dims =
            referenceObjectRegistry.getDimensions(ctx.getReferenceObjectType());
        String description = referenceObjectRegistry.getDescription(ctx.getReferenceObjectType());

        String topRatio = ctx.getPixelToCmRatioTop() != null
            ? String.format("%.2f", ctx.getPixelToCmRatioTop())
            : "not available";
        String sideRatio = ctx.getPixelToCmRatioSide() != null
            ? String.format("%.2f", ctx.getPixelToCmRatioSide())
            : "not available";

        return String.format(
            "You are measuring a food container in two calibrated images.\n" +
            "Image 1 (first image) = top-down view.\n" +
            "Image 2 (second image) = side view.\n\n" +
            "Scale calibration:\n" +
            "  Top image:  %s pixels per cm\n" +
            "  Side image: %s pixels per cm\n\n" +
            "Reference object visible in images: %s (%.2fcm long x %.2fcm wide)\n" +
            "Use this reference object as your scale anchor to estimate real-world dimensions.\n\n" +
            "Estimate the inner dimensions of the plate or bowl.\n\n" +
            "Return ONLY valid JSON with no markdown or extra text:\n" +
            "{\n" +
            "  \"container_type\": \"FLAT_PLATE\" or \"REGULAR_BOWL\" or \"DEEP_BOWL\",\n" +
            "  \"inner_diameter_cm\": <float>,\n" +
            "  \"inner_depth_cm\": <float>,\n" +
            "  \"fill_percent\": <integer 0-100>,\n" +
            "  \"diameter_confidence\": <float 0.0-1.0>,\n" +
            "  \"depth_confidence\": <float 0.0-1.0>,\n" +
            "  \"reasoning_notes\": \"<brief explanation of how you estimated the dimensions>\"\n" +
            "}",
            topRatio, sideRatio, description, dims.lengthCm(), dims.widthCm());
    }

    private PipelineContext.PlateGeometry parseGeometryResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            PipelineContext.PlateGeometry g = new PipelineContext.PlateGeometry();
            g.setContainerType(root.path("container_type").asText("FLAT_PLATE"));
            g.setInnerDiameterCm((float) root.path("inner_diameter_cm").asDouble(DEFAULT_DIAMETER_CM));
            g.setInnerDepthCm((float) root.path("inner_depth_cm").asDouble(DEFAULT_DEPTH_CM));
            g.setFillPercent((float) root.path("fill_percent").asDouble(DEFAULT_FILL_PCT));
            g.setDiameterConfidence((float) root.path("diameter_confidence").asDouble(0.5));
            g.setDepthConfidence((float) root.path("depth_confidence").asDouble(0.5));
            g.setReasoningNotes(root.path("reasoning_notes").asText(""));
            return g;

        } catch (Exception e) {
            log.warn("[PlateGeometry] Failed to parse response: {}", e.getMessage());
            return buildDefaultGeometry();
        }
    }

    private PipelineContext.PlateGeometry buildDefaultGeometry() {
        PipelineContext.PlateGeometry g = new PipelineContext.PlateGeometry();
        g.setContainerType("FLAT_PLATE");
        g.setInnerDiameterCm(DEFAULT_DIAMETER_CM);
        g.setInnerDepthCm(DEFAULT_DEPTH_CM);
        g.setFillPercent(DEFAULT_FILL_PCT);
        g.setDiameterConfidence(0.0f);
        g.setDepthConfidence(0.0f);
        g.setReasoningNotes("Default values — geometry estimation failed");
        return g;
    }
}

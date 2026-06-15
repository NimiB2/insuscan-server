package com.insuscan.pipeline.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import com.insuscan.pipeline.support.ReferenceObjectRegistry;
import com.insuscan.pipeline.support.StandardPlateConfig;
import com.insuscan.util.GeminiApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class PlateGeometryService {

    private static final Logger log = LoggerFactory.getLogger(PlateGeometryService.class);

    private static final float DEFAULT_DIAMETER_CM = 22.0f;
    private static final float DEFAULT_DEPTH_CM    = 2.0f;
    private static final float DEFAULT_FILL_PCT    = 70.0f;

    @Value("${plate.geometry.debug.override:false}")
    private boolean debugOverrideGeometry;

    @Value("${plate.geometry.debug.diameter:22.0}")
    private float debugDiameterCm;

    @Value("${plate.geometry.debug.depth:2.0}")
    private float debugDepthCm;

    @Value("${plate.geometry.force-diameter.enabled:false}")
    private boolean forceDiameterEnabled;

    @Value("${plate.geometry.force-diameter.cm:22.0}")
    private float forceDiameterCm;

    private final GeminiApiClient geminiApiClient;
    private final ReferenceObjectRegistry referenceObjectRegistry;
    private final PipelineWarningCollector warningCollector;
    private final ObjectMapper objectMapper;
    private final StandardPlateConfig standardPlateConfig;

    public PlateGeometryService(GeminiApiClient geminiApiClient,
                                ReferenceObjectRegistry referenceObjectRegistry,
                                PipelineWarningCollector warningCollector,
                                ObjectMapper objectMapper,
                                StandardPlateConfig standardPlateConfig) {
        this.geminiApiClient = geminiApiClient;
        this.referenceObjectRegistry = referenceObjectRegistry;
        this.warningCollector = warningCollector;
        this.objectMapper = objectMapper;
        this.standardPlateConfig = standardPlateConfig;
    }

    public void measurePlate(PipelineContext ctx) {

        if (debugOverrideGeometry) {
            applyDebugOverride(ctx);
            return;
        }

        CompletableFuture<DiameterResult> diameterFuture = CompletableFuture.supplyAsync(
                () -> fetchDiameter(ctx));

        CompletableFuture<DepthResult> depthFuture = CompletableFuture.supplyAsync(
                () -> fetchDepth(ctx));

        DiameterResult diameterResult = diameterFuture.join();
        DepthResult depthResult = depthFuture.join();

        PipelineContext.PlateGeometry geometry = merge(diameterResult, depthResult);
        float confidence = (diameterResult.confidence + depthResult.confidence) / 2f;

        if (ctx.isUseStandardPlateFallback()) {
            applyStandardPlateFallback(geometry);
            confidence = StandardPlateConfig.FALLBACK_CONFIDENCE;
        }

        if (forceDiameterEnabled) {
            applyForcedDiameter(geometry);
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
    
    

    private void applyDebugOverride(PipelineContext ctx) {
        PipelineContext.PlateGeometry override = new PipelineContext.PlateGeometry();
        override.setContainerType("FLAT_PLATE");
        override.setInnerDiameterCm(debugDiameterCm);
        override.setInnerDepthCm(debugDepthCm);
        override.setFillPercent(DEFAULT_FILL_PCT);
        override.setDiameterConfidence(1.0f);
        override.setDepthConfidence(1.0f);
        override.setReasoningNotes("DEBUG override — fixed geometry from application.properties");
        ctx.setPlateGeometry(override);
        ctx.recordConfidence("PLATE_GEOMETRY", 1.0f);
        log.info("[PlateGeometry] DEBUG OVERRIDE active — diameter={}cm depth={}cm",
                debugDiameterCm, debugDepthCm);
    }

    private void applyStandardPlateFallback(PipelineContext.PlateGeometry geometry) {
        geometry.setInnerDiameterCm(StandardPlateConfig.DIAMETER_CM);
        geometry.setInnerDepthCm(StandardPlateConfig.DEPTH_CM);
        geometry.setDiameterConfidence(StandardPlateConfig.FALLBACK_CONFIDENCE);
        geometry.setDepthConfidence(StandardPlateConfig.FALLBACK_CONFIDENCE);
        geometry.setReasoningNotes(geometry.getReasoningNotes()
                + " | size overridden to standard plate (no reference object)");
        log.info("[PlateGeometry] Standard plate fallback active — overriding diameter={}cm depth={}cm",
                StandardPlateConfig.DIAMETER_CM, StandardPlateConfig.DEPTH_CM);
    }

    private void applyForcedDiameter(PipelineContext.PlateGeometry geometry) {
        float original = geometry.getInnerDiameterCm();
        geometry.setInnerDiameterCm(forceDiameterCm);
        geometry.setReasoningNotes(geometry.getReasoningNotes()
                + " | TEST: diameter forced from " + String.format("%.2f", original)
                + "cm to " + String.format("%.2f", forceDiameterCm) + "cm");
        log.info("[PlateGeometry] TEST force-diameter active — overriding diameter {}cm -> {}cm",
                String.format("%.2f", original), String.format("%.2f", forceDiameterCm));
    }

    private DiameterResult fetchDiameter(PipelineContext ctx) {
        try {
            boolean hasReferenceRatio = ctx.getPixelToCmRatioTop() != null && ctx.getPixelToCmRatioTop() > 0;
            String prompt = hasReferenceRatio
                    ? buildDiameterPromptWithReference(ctx)
                    : buildDiameterPromptNoReference();

            String response = geminiApiClient.callVisionModel(prompt, ctx.getImageTopBase64());
            if (response == null || response.isBlank()) {
                log.warn("[PlateGeometry] Empty diameter response, using default");
                return DiameterResult.defaultResult();
            }

            return hasReferenceRatio
                    ? parseDiameterResponseWithReference(response, ctx.getPixelToCmRatioTop())
                    : parseDiameterResponseNoReference(response);

        } catch (Exception e) {
            log.warn("[PlateGeometry] Diameter call failed: {}", e.getMessage());
            return DiameterResult.defaultResult();
        }
    }

    private DepthResult fetchDepth(PipelineContext ctx) {
        try {
            String prompt = buildDepthPrompt(ctx);
            String response = geminiApiClient.callVisionModel(prompt, ctx.getImageSideBase64());
            if (response == null || response.isBlank()) {
                log.warn("[PlateGeometry] Empty depth response, using default");
                return DepthResult.defaultResult();
            }
            return parseDepthResponse(response);
        } catch (Exception e) {
            log.warn("[PlateGeometry] Depth call failed: {}", e.getMessage());
            return DepthResult.defaultResult();
        }
    }

    private String buildDiameterPromptWithReference(PipelineContext ctx) {
        ReferenceObjectRegistry.Dimensions dims =
                referenceObjectRegistry.getDimensions(ctx.getReferenceObjectType());
        String description = referenceObjectRegistry.getDescription(ctx.getReferenceObjectType());

        return String.format(
                "You are measuring a round plate from a TOP-DOWN view.\n\n" +
                "A reference object is present in the image: %s (%.2fcm long x %.2fcm wide).\n" +
                "The reference object is placed NEXT TO the plate (to its left or right), NOT above or below it.\n\n" +
                "TASK: Identify the vertical extent of the plate in the image.\n" +
                "- top_y_px: pixel Y coordinate of the TOP edge of the plate (highest point of the plate rim).\n" +
                "- bottom_y_px: pixel Y coordinate of the BOTTOM edge of the plate (lowest point of the plate rim).\n" +
                "Use the VERTICAL axis (Y) because the reference object lies horizontally next to the plate " +
                "and would interfere with horizontal (X) measurements.\n\n" +
                "Also identify the container type and fill percentage.\n\n" +
                "Return ONLY valid JSON with no markdown or extra text:\n" +
                "{\n" +
                "  \"container_type\": \"FLAT_PLATE\" or \"REGULAR_BOWL\" or \"DEEP_BOWL\",\n" +
                "  \"top_y_px\": <integer>,\n" +
                "  \"bottom_y_px\": <integer>,\n" +
                "  \"fill_percent\": <integer 0-100>,\n" +
                "  \"diameter_confidence\": <float 0.0-1.0>,\n" +
                "  \"reasoning_notes\": \"<max 20 words>\"\n" +
                "}",
                description, dims.lengthCm(), dims.widthCm());
    }

    private String buildDiameterPromptNoReference() {
        return String.format(
                "You are measuring a round plate from a TOP-DOWN view.\n\n" +
                "NO reference object is available for scale calibration.\n" +
                "Estimate the plate's inner diameter based on visual cues alone.\n\n" +
                "Typical plate sizes in the world are:\n" +
                "- Small (%.0fcm): side plate / dessert plate\n" +
                "- Standard (%.0fcm): regular dinner plate\n" +
                "- Large (%.0fcm): large dinner plate / serving plate\n\n" +
                "Estimate the diameter as accurately as possible, and rate your confidence honestly.\n" +
                "Higher confidence is appropriate only when visual cues strongly suggest a specific size.\n\n" +
                "Return ONLY valid JSON with no markdown or extra text:\n" +
                "{\n" +
                "  \"container_type\": \"FLAT_PLATE\" or \"REGULAR_BOWL\" or \"DEEP_BOWL\",\n" +
                "  \"inner_diameter_cm\": <float>,\n" +
                "  \"fill_percent\": <integer 0-100>,\n" +
                "  \"diameter_confidence\": <float 0.0-1.0>,\n" +
                "  \"reasoning_notes\": \"<max 20 words>\"\n" +
                "}",
                standardPlateConfig.getSmallDiameterCm(),
                standardPlateConfig.getStandardDiameterCm(),
                standardPlateConfig.getLargeDiameterCm());
    }

    private String buildDepthPrompt(PipelineContext ctx) {
        ReferenceObjectRegistry.Dimensions dims =
                referenceObjectRegistry.getDimensions(ctx.getReferenceObjectType());
        String description = referenceObjectRegistry.getDescription(ctx.getReferenceObjectType());

        String sideRatio = ctx.getPixelToCmRatioSide() != null
                ? String.format("%.2f", ctx.getPixelToCmRatioSide())
                : "not available";

        return String.format(
                "You are measuring a food container from a SIDE view.\n\n" +
                "Scale calibration: %s pixels per cm\n" +
                "Reference object in image: %s (%.2fcm long x %.2fcm wide)\n\n" +
                "Estimate ONLY the inner depth of the plate or bowl (from rim to bottom, in cm).\n" +
                "Do NOT estimate diameter or width — that is measured from a top-down image.\n\n" +
                "Return ONLY valid JSON with no markdown or extra text:\n" +
                "{\n" +
                "  \"inner_depth_cm\": <float>,\n" +
                "  \"depth_confidence\": <float 0.0-1.0>,\n" +
                "  \"reasoning_notes\": \"<max 20 words>\"\n" +
                "}",
                sideRatio, description, dims.lengthCm(), dims.widthCm());
    }

    private DiameterResult parseDiameterResponseWithReference(String response, float pixelToCmRatio) {
        try {
            JsonNode root = objectMapper.readTree(sanitizeJson(response));
            DiameterResult r = new DiameterResult();
            r.containerType  = root.path("container_type").asText("FLAT_PLATE");
            r.fillPercent    = (float) root.path("fill_percent").asDouble(DEFAULT_FILL_PCT);
            r.confidence     = (float) root.path("diameter_confidence").asDouble(0.5);
            r.reasoningNotes = root.path("reasoning_notes").asText("");

            float topY    = (float) root.path("top_y_px").asDouble(0);
            float bottomY = (float) root.path("bottom_y_px").asDouble(0);
            float diameterPx = Math.abs(bottomY - topY);

            if (diameterPx <= 0) {
                log.warn("[PlateGeometry] Invalid plate Y range (top={}, bottom={}), using default", topY, bottomY);
                r.innerDiameterCm = DEFAULT_DIAMETER_CM;
            } else {
                r.innerDiameterCm = diameterPx * pixelToCmRatio;
                log.info("[PlateGeometry] Diameter from reference: top_y={}px, bottom_y={}px, diameter_px={}, ratio={}, diameter={}cm",
                        (int) topY, (int) bottomY, (int) diameterPx,
                        String.format("%.4f", pixelToCmRatio),
                        String.format("%.2f", r.innerDiameterCm));
            }

            return r;
        } catch (Exception e) {
            log.warn("[PlateGeometry] Failed to parse diameter (with reference) response: {}", e.getMessage());
            return DiameterResult.defaultResult();
        }
    }

    private DiameterResult parseDiameterResponseNoReference(String response) {
        try {
            JsonNode root = objectMapper.readTree(sanitizeJson(response));
            DiameterResult r = new DiameterResult();
            r.containerType   = root.path("container_type").asText("FLAT_PLATE");
            r.innerDiameterCm = (float) root.path("inner_diameter_cm").asDouble(DEFAULT_DIAMETER_CM);
            r.fillPercent     = (float) root.path("fill_percent").asDouble(DEFAULT_FILL_PCT);
            r.confidence      = (float) root.path("diameter_confidence").asDouble(0.5);
            r.reasoningNotes  = root.path("reasoning_notes").asText("");

            float originalDiameter = r.innerDiameterCm;
            float threshold = standardPlateConfig.getConfidenceThreshold();

            if (r.confidence < threshold) {
                float snapped = standardPlateConfig.snapToClosestSize(originalDiameter);
                log.info("[PlateGeometry] No reference, confidence {} < {}, snapping {}cm -> {}cm",
                        String.format("%.2f", r.confidence),
                        String.format("%.2f", threshold),
                        String.format("%.2f", originalDiameter),
                        String.format("%.1f", snapped));
                r.innerDiameterCm = snapped;
                r.reasoningNotes += " | snapped from " + String.format("%.1f", originalDiameter) + "cm to " + String.format("%.1f", snapped) + "cm";
            } else {
                log.info("[PlateGeometry] No reference, confidence {} >= {}, keeping {}cm",
                        String.format("%.2f", r.confidence),
                        String.format("%.2f", threshold),
                        String.format("%.2f", originalDiameter));
            }

            return r;
        } catch (Exception e) {
            log.warn("[PlateGeometry] Failed to parse diameter (no reference) response: {}", e.getMessage());
            return DiameterResult.defaultResult();
        }
    }

    private DepthResult parseDepthResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(sanitizeJson(response));
            DepthResult r = new DepthResult();
            r.innerDepthCm   = (float) root.path("inner_depth_cm").asDouble(DEFAULT_DEPTH_CM);
            r.confidence     = (float) root.path("depth_confidence").asDouble(0.5);
            r.reasoningNotes = root.path("reasoning_notes").asText("");
            return r;
        } catch (Exception e) {
            log.warn("[PlateGeometry] Failed to parse depth response: {}", e.getMessage());
            return DepthResult.defaultResult();
        }
    }

    private String sanitizeJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.strip();
        int braceDepth = 0;
        int lastValidClose = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') braceDepth++;
            else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0) lastValidClose = i;
            }
        }
        if (lastValidClose != -1 && lastValidClose < trimmed.length() - 1) {
            log.warn("[PlateGeometry] JSON was truncated, trimming at position {}", lastValidClose + 1);
            return trimmed.substring(0, lastValidClose + 1);
        }
        return trimmed;
    }

    private PipelineContext.PlateGeometry merge(DiameterResult d, DepthResult dep) {
        PipelineContext.PlateGeometry g = new PipelineContext.PlateGeometry();
        g.setContainerType(d.containerType);
        g.setInnerDiameterCm(d.innerDiameterCm);
        g.setFillPercent(d.fillPercent);
        g.setDiameterConfidence(d.confidence);
        g.setInnerDepthCm(dep.innerDepthCm);
        g.setDepthConfidence(dep.confidence);
        g.setReasoningNotes(d.reasoningNotes + " | depth: " + dep.reasoningNotes);
        return g;
    }

    private static class DiameterResult {
        String containerType  = "FLAT_PLATE";
        float innerDiameterCm = DEFAULT_DIAMETER_CM;
        float fillPercent     = DEFAULT_FILL_PCT;
        float confidence      = 0.0f;
        String reasoningNotes = "Default values — diameter estimation failed";

        static DiameterResult defaultResult() { return new DiameterResult(); }
    }

    private static class DepthResult {
        float innerDepthCm   = DEFAULT_DEPTH_CM;
        float confidence     = 0.0f;
        String reasoningNotes = "Default values — depth estimation failed";

        static DepthResult defaultResult() { return new DepthResult(); }
    }
}
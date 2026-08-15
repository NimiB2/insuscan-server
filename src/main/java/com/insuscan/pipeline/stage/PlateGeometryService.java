package com.insuscan.pipeline.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import com.insuscan.pipeline.support.StandardPlateConfig;
import com.insuscan.util.GeminiApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Stage 2 - Measures plate diameter (top-down image) and depth (side image) in parallel via Gemini.
 * Applies debug overrides, standard-plate fallback, and forced-diameter test flag as configured.
 * Derives a top-view pixel-to-cm ratio from the measured diameter when no reference object was calibrated.
 */
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

    @Value("${plate.geometry.no-reference.assumed-plate-pixel-fraction:0.6}")
    private float assumedPlatePixelFraction;

    private final GeminiApiClient geminiApiClient;
    private final PipelineWarningCollector warningCollector;
    private final ObjectMapper objectMapper;
    private final StandardPlateConfig standardPlateConfig;
    private final PlateGeometryPromptBuilder promptBuilder;

    public PlateGeometryService(GeminiApiClient geminiApiClient,
                                PipelineWarningCollector warningCollector,
                                ObjectMapper objectMapper,
                                StandardPlateConfig standardPlateConfig,
                                PlateGeometryPromptBuilder promptBuilder) {
        this.geminiApiClient = geminiApiClient;
        this.warningCollector = warningCollector;
        this.objectMapper = objectMapper;
        this.standardPlateConfig = standardPlateConfig;
        this.promptBuilder = promptBuilder;
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

        if (!standardPlateConfig.isPlausibleDiameter(geometry.getInnerDiameterCm())) {
            warningCollector.plateDiameterOutOfRange(ctx, geometry.getInnerDiameterCm());
            log.warn("[PlateGeometry] Diameter {}cm is outside the plausible range — calibration is likely wrong",
                    String.format("%.1f", geometry.getInnerDiameterCm()));
        }

        ctx.setPlateGeometry(geometry);
        ctx.recordConfidence("PLATE_GEOMETRY", confidence);

        if (ctx.getPixelToCmRatioTop() == null || ctx.getPixelToCmRatioTop() <= 0) {
            deriveTopRatioFromDiameter(ctx, geometry.getInnerDiameterCm(), diameterResult.diameterPx);
        }

        log.info("[PlateGeometry] type={} diameter={}cm depth={}cm fill={}% confidence={}",
                geometry.getContainerType(),
                String.format("%.1f", geometry.getInnerDiameterCm()),
                String.format("%.1f", geometry.getInnerDepthCm()),
                String.format("%.0f", geometry.getFillPercent()),
                String.format("%.2f", confidence));
    }

    private void deriveTopRatioFromDiameter(PipelineContext ctx, float diameterCm, float diameterPx) {
        float effectivePx = diameterPx;

        if (effectivePx <= 0) {
            Integer imageHeightPx = ctx.getImageTopHeightPx();
            if (imageHeightPx == null || imageHeightPx <= 0) {
                log.warn("[PlateGeometry] Cannot derive top ratio — no plate pixels and no image height");
                return;
            }
            effectivePx = imageHeightPx * assumedPlatePixelFraction;
            log.warn("[PlateGeometry] No plate pixels from Gemini — assuming plate spans {}% of image height -> {}px",
                    String.format("%.0f", assumedPlatePixelFraction * 100), (int) effectivePx);
        }

        if (diameterCm <= 0 || effectivePx <= 0) {
            log.warn("[PlateGeometry] Cannot derive top ratio — invalid diameter or pixels");
            return;
        }

        float ratio = diameterCm / effectivePx;
        ctx.setPixelToCmRatioTop(ratio);
        log.info("[PlateGeometry] Derived top ratio from no-reference diameter: {}cm / {}px = {}",
                String.format("%.1f", diameterCm), (int) effectivePx, String.format("%.5f", ratio));
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
                    ? promptBuilder.diameterPromptWithReference(ctx.getReferenceObjectType())
                    : promptBuilder.diameterPromptNoReference();

            String response = geminiApiClient.callVisionModel(prompt, ctx.getImageTopBase64());
            if (response == null || response.isBlank()) {
                log.warn("[PlateGeometry] Empty diameter response, using default");
                return DiameterResult.defaultResult();
            }

            return hasReferenceRatio
                    ? parseDiameterResponseWithReference(response, ctx.getPixelToCmRatioTop(), ctx)
                    : parseDiameterResponseNoReference(response, ctx);

        } catch (Exception e) {
            log.warn("[PlateGeometry] Diameter call failed: {}", e.getMessage());
            return DiameterResult.defaultResult();
        }
    }

    private DepthResult fetchDepth(PipelineContext ctx) {
        try {
            String prompt = promptBuilder.depthPrompt(ctx.getReferenceObjectType(), ctx.getPixelToCmRatioSide());
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

    private DiameterResult parseDiameterResponseWithReference(String response, float pixelToCmRatio, PipelineContext ctx) {
        try {
            JsonNode root = objectMapper.readTree(sanitizeJson(response));
            DiameterResult r = new DiameterResult();
            r.containerType  = root.path("container_type").asText("FLAT_PLATE");
            r.fillPercent    = (float) root.path("fill_percent").asDouble(DEFAULT_FILL_PCT);
            r.confidence     = (float) root.path("diameter_confidence").asDouble(0.5);
            r.reasoningNotes = root.path("reasoning_notes").asText("");

            float topY    = (float) root.path("top_y_px").asDouble(0);
            float bottomY = (float) root.path("bottom_y_px").asDouble(0);
            float centerX = (float) root.path("plate_center_x_px").asDouble(0);
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

                float cy = (topY + bottomY) / 2f;
                float radius = diameterPx / 2f;
                if (centerX > 0) {
                    ctx.setPlateCircleTopPx(new float[]{centerX, cy, radius});
                    log.info("[PlateGeometry] Plate circle: cx={}px, cy={}px, r={}px",
                            (int) centerX, (int) cy, (int) radius);
                } else {
                    log.warn("[PlateGeometry] No plate_center_x_px from Gemini — plate circle not set");
                }
            }

            return r;
        } catch (Exception e) {
            log.warn("[PlateGeometry] Failed to parse diameter (with reference) response: {}", e.getMessage());
            return DiameterResult.defaultResult();
        }
    }

    private DiameterResult parseDiameterResponseNoReference(String response, PipelineContext ctx) {
        try {
            JsonNode root = objectMapper.readTree(sanitizeJson(response));
            DiameterResult r = new DiameterResult();
            r.containerType   = root.path("container_type").asText("FLAT_PLATE");
            r.innerDiameterCm = (float) root.path("inner_diameter_cm").asDouble(DEFAULT_DIAMETER_CM);
            r.fillPercent     = (float) root.path("fill_percent").asDouble(DEFAULT_FILL_PCT);
            r.confidence      = (float) root.path("diameter_confidence").asDouble(0.5);
            r.reasoningNotes  = root.path("reasoning_notes").asText("");

            float topY    = (float) root.path("top_y_px").asDouble(0);
            float bottomY = (float) root.path("bottom_y_px").asDouble(0);
            float centerX = (float) root.path("plate_center_x_px").asDouble(0);
            r.diameterPx  = Math.abs(bottomY - topY);

            if (r.diameterPx > 0 && centerX > 0) {
                float cy = (topY + bottomY) / 2f;
                float radius = r.diameterPx / 2f;
                ctx.setPlateCircleTopPx(new float[]{centerX, cy, radius});
                log.info("[PlateGeometry] Plate circle (no-ref): cx={}px, cy={}px, r={}px",
                        (int) centerX, (int) cy, (int) radius);
            } else {
                log.warn("[PlateGeometry] No-ref: incomplete plate geometry (diameterPx={}, centerX={}) — plate circle not set",
                        (int) r.diameterPx, (int) centerX);
            }

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
        float diameterPx      = 0f;
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
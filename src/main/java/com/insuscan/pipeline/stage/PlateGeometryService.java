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

import java.util.concurrent.CompletableFuture;

@Service
public class PlateGeometryService {

    private static final Logger log = LoggerFactory.getLogger(PlateGeometryService.class);

    private static final float DEFAULT_DIAMETER_CM = 22.0f;
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
        CompletableFuture<DiameterResult> diameterFuture = CompletableFuture.supplyAsync(
                () -> fetchDiameter(ctx));

        CompletableFuture<DepthResult> depthFuture = CompletableFuture.supplyAsync(
                () -> fetchDepth(ctx));

        DiameterResult diameterResult = diameterFuture.join();
        DepthResult depthResult = depthFuture.join();

        PipelineContext.PlateGeometry geometry = merge(diameterResult, depthResult);
        float confidence = (diameterResult.confidence + depthResult.confidence) / 2f;

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

    private DiameterResult fetchDiameter(PipelineContext ctx) {
        try {
            String prompt = buildDiameterPrompt(ctx);
            String response = geminiApiClient.callVisionModel(prompt, ctx.getImageTopBase64());
            if (response == null || response.isBlank()) {
                log.warn("[PlateGeometry] Empty diameter response, using default");
                return DiameterResult.defaultResult();
            }
            return parseDiameterResponse(response);
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

    private String buildDiameterPrompt(PipelineContext ctx) {
        ReferenceObjectRegistry.Dimensions dims =
                referenceObjectRegistry.getDimensions(ctx.getReferenceObjectType());
        String description = referenceObjectRegistry.getDescription(ctx.getReferenceObjectType());

        String topRatio = ctx.getPixelToCmRatioTop() != null
                ? String.format("%.2f", ctx.getPixelToCmRatioTop())
                : "not available";

        return String.format(
                "You are measuring a food container from a TOP-DOWN view.\n\n" +
                "Scale calibration: %s pixels per cm\n" +
                "Reference object in image: %s (%.2fcm long x %.2fcm wide)\n\n" +
                "Estimate ONLY the horizontal dimensions of the plate or bowl.\n" +
                "Do NOT estimate depth — that will be measured from a side image.\n\n" +
                "Return ONLY valid JSON with no markdown or extra text:\n" +
                "{\n" +
                "  \"container_type\": \"FLAT_PLATE\" or \"REGULAR_BOWL\" or \"DEEP_BOWL\",\n" +
                "  \"inner_diameter_cm\": <float>,\n" +
                "  \"fill_percent\": <integer 0-100>,\n" +
                "  \"diameter_confidence\": <float 0.0-1.0>,\n" +
                "  \"reasoning_notes\": \"<brief explanation>\"\n" +
                "}",
                topRatio, description, dims.lengthCm(), dims.widthCm());
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
                "  \"reasoning_notes\": \"<brief explanation>\"\n" +
                "}",
                sideRatio, description, dims.lengthCm(), dims.widthCm());
    }

    private DiameterResult parseDiameterResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            DiameterResult r = new DiameterResult();
            r.containerType     = root.path("container_type").asText("FLAT_PLATE");
            r.innerDiameterCm   = (float) root.path("inner_diameter_cm").asDouble(DEFAULT_DIAMETER_CM);
            r.fillPercent       = (float) root.path("fill_percent").asDouble(DEFAULT_FILL_PCT);
            r.confidence        = (float) root.path("diameter_confidence").asDouble(0.5);
            r.reasoningNotes    = root.path("reasoning_notes").asText("");
            return r;
        } catch (Exception e) {
            log.warn("[PlateGeometry] Failed to parse diameter response: {}", e.getMessage());
            return DiameterResult.defaultResult();
        }
    }

    private DepthResult parseDepthResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
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
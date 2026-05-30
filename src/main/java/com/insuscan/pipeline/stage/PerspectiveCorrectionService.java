package com.insuscan.pipeline.stage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineWarning;
import com.insuscan.pipeline.support.PipelineWarningCollector;
import com.insuscan.util.GeminiApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class PerspectiveCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(PerspectiveCorrectionService.class);

//    private static final float SKIP_THRESHOLD_DEG    = 5.0f;
    private static final float SKIP_THRESHOLD_DEG = 90.0f;

    private static final float WARNING_THRESHOLD_DEG = 25.0f;

    private final GeminiApiClient geminiApiClient;
    private final PipelineWarningCollector warningCollector;
    private final ObjectMapper objectMapper;

    public PerspectiveCorrectionService(GeminiApiClient geminiApiClient,
                                        PipelineWarningCollector warningCollector,
                                        ObjectMapper objectMapper) {
        this.geminiApiClient = geminiApiClient;
        this.warningCollector = warningCollector;
        this.objectMapper = objectMapper;
    }

    public void correctPerspective(PipelineContext ctx) {
        if (ctx.getImageTopBase64() == null || ctx.getPlateGeometry() == null) {
            log.warn("[Perspective] Missing image or plate geometry, skipping.");
            markSkipped(ctx, 1.0f);
            return;
        }

        if (SKIP_THRESHOLD_DEG >= 90.0f) {
            log.info("[Perspective] Skip threshold is {}° — skipping tilt estimation entirely.", SKIP_THRESHOLD_DEG);
            markSkipped(ctx, 1.0f);
            return;
        }

        TiltResult tilt = estimateTilt(ctx);

        if (tilt == null) {
            log.warn("[Perspective] Could not estimate tilt, skipping correction.");
            ctx.setPerspectiveCorrectionApplied(false);
            ctx.recordConfidence("PERSPECTIVE_CORRECTION", 0.5f);
            return;
        }

        ctx.setPerspectiveTiltDegrees(tilt.degrees);
        log.info("[Perspective] Detected tilt={}°", String.format("%.1f", tilt.degrees));

        if (tilt.degrees < SKIP_THRESHOLD_DEG) {
            log.info("[Perspective] Tilt below threshold, skipping correction.");
            ctx.setPerspectiveCorrectionApplied(false);
            ctx.recordConfidence("PERSPECTIVE_CORRECTION", 1.0f);
            return;
        }

        if (tilt.degrees > WARNING_THRESHOLD_DEG) {
        	warningCollector.add(ctx, PipelineWarning.medium(
        		    PipelineWarningCollector.STAGE_PERSPECTIVE_CORRECTION,
                "HIGH_TILT",
                String.format("Camera tilt of %.1f° detected. Perspective correction applied but accuracy may be reduced.", tilt.degrees)
            ));
        }

        boolean corrected = applyCorrection(ctx, tilt);

        ctx.setPerspectiveCorrectionApplied(corrected);
        ctx.recordConfidence("PERSPECTIVE_CORRECTION", corrected ? 0.9f : 0.5f);

        log.info("[Perspective] Correction applied={}", corrected);
    }
    
    private void markSkipped(PipelineContext ctx, float confidence) {
        ctx.setPerspectiveCorrectionApplied(false);
        ctx.recordConfidence("PERSPECTIVE_CORRECTION", confidence);
    }

    private TiltResult estimateTilt(PipelineContext ctx) {
        String prompt = buildTiltPrompt(ctx);
        try {
        	String response = geminiApiClient.callFlashModelWithImage(prompt, ctx.getImageTopBase64());
            if (response == null || response.isBlank()) return null;
            return parseTiltResponse(response);
        } catch (Exception e) {
            log.warn("[Perspective] Tilt estimation failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildTiltPrompt(PipelineContext ctx) {
        String plateInfo = ctx.getPlateGeometry() != null
            ? String.format("The plate has a known diameter of %.1fcm.", ctx.getPlateGeometry().getInnerDiameterCm())
            : "The plate diameter is unknown.";

        return "You are a camera angle analysis AI.\n\n" +
            "Look at this TOP-DOWN image of a meal on a plate.\n" +
            plateInfo + "\n\n" +
            "A perfect top-down shot shows the plate as a CIRCLE.\n" +
            "If the camera is tilted, the plate appears as an ELLIPSE.\n\n" +
            "Tasks:\n" +
            "1. Estimate the camera tilt angle in degrees (0 = perfect top-down, 90 = fully sideways).\n" +
            "2. Find the plate ellipse: report its center, semi-major axis, semi-minor axis, and rotation angle.\n\n" +
            "Return ONLY valid JSON with no markdown:\n" +
            "{\n" +
            "  \"tilt_degrees\": <float 0-90>,\n" +
            "  \"confidence\": <float 0.0-1.0>,\n" +
            "  \"ellipse\": {\n" +
            "    \"center_x_pct\": <float 0-100>,\n" +
            "    \"center_y_pct\": <float 0-100>,\n" +
            "    \"semi_major_pct\": <float 0-100>,\n" +
            "    \"semi_minor_pct\": <float 0-100>,\n" +
            "    \"rotation_deg\": <float 0-180>\n" +
            "  }\n" +
            "}";
    }

    private TiltResult parseTiltResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            float degrees = (float) root.path("tilt_degrees").asDouble(0);
            float confidence = (float) root.path("confidence").asDouble(0.5);

            JsonNode ellipse = root.path("ellipse");
            float cx  = (float) ellipse.path("center_x_pct").asDouble(50);
            float cy  = (float) ellipse.path("center_y_pct").asDouble(50);
            float a   = (float) ellipse.path("semi_major_pct").asDouble(40);
            float b   = (float) ellipse.path("semi_minor_pct").asDouble(40);
            float rot = (float) ellipse.path("rotation_deg").asDouble(0);

            return new TiltResult(degrees, confidence, cx, cy, a, b, rot);
        } catch (Exception e) {
            log.warn("[Perspective] Failed to parse tilt response: {}", e.getMessage());
            return null;
        }
    }

    private boolean applyCorrection(PipelineContext ctx, TiltResult tilt) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(ctx.getImageTopBase64());
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(
                new java.io.ByteArrayInputStream(imageBytes));

            if (image == null) {
                log.warn("[Perspective] Could not decode image for correction.");
                return false;
            }

            int W = image.getWidth();
            int H = image.getHeight();

            float cxPx  = (tilt.centerXPct / 100f) * W;
            float cyPx  = (tilt.centerYPct / 100f) * H;
            float aPx   = (tilt.semiMajorPct / 100f) * W;
            float bPx   = (tilt.semiMinorPct / 100f) * W;

            java.awt.image.BufferedImage corrected = warpPerspective(image, cxPx, cyPx, aPx, bPx, tilt.rotationDeg);

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(corrected, "jpg", baos);
            String correctedBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());

            Float newRatio = ctx.getPixelToCmRatioTop() != null
            	    ? ctx.getPixelToCmRatioTop() * ((float) W / corrected.getWidth())
            	    : null;

            	ctx.setImageTopBase64(correctedBase64);
            	ctx.setImageTopWidthPx(corrected.getWidth());
            	ctx.setImageTopHeightPx(corrected.getHeight());
            	if (newRatio != null) ctx.setPixelToCmRatioTop(newRatio);

            log.info("[Perspective] Image corrected: {}x{} → {}x{}", W, H, corrected.getWidth(), corrected.getHeight());
            return true;

        } catch (Exception e) {
            log.warn("[Perspective] warpPerspective failed: {}", e.getMessage());
            return false;
        }
    }

    private java.awt.image.BufferedImage warpPerspective(java.awt.image.BufferedImage src,
                                                          float cx, float cy,
                                                          float a, float b,
                                                          float rotDeg) {
        int size = (int) (a * 2);
        java.awt.image.BufferedImage dst = new java.awt.image.BufferedImage(size, size,
            java.awt.image.BufferedImage.TYPE_INT_RGB);

        double rotRad = Math.toRadians(rotDeg);
        double cosR   = Math.cos(rotRad);
        double sinR   = Math.sin(rotRad);

        for (int dy = 0; dy < size; dy++) {
            for (int dx = 0; dx < size; dx++) {
                double nx = (dx - size / 2.0) / a;
                double ny = (dy - size / 2.0) / a;

                double srcX = cx + (nx * cosR - ny * sinR) * a;
                double srcY = cy + (nx * sinR + ny * cosR) * b;

                int sx = (int) Math.round(srcX);
                int sy = (int) Math.round(srcY);

                if (sx >= 0 && sx < src.getWidth() && sy >= 0 && sy < src.getHeight()) {
                    dst.setRGB(dx, dy, src.getRGB(sx, sy));
                }
            }
        }
        return dst;
    }

    private record TiltResult(float degrees, float confidence,
                               float centerXPct, float centerYPct,
                               float semiMajorPct, float semiMinorPct,
                               float rotationDeg) {}
}
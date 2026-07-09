package com.insuscan.pipeline.stage;

import com.insuscan.boundary.ArcoreDepthData;
import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineFoodItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fuses ARCore depth-sensor measurements (plate depth, plate diameter, and per-item heights) with the vision pipeline's own estimates.
 * ARCore's weight in the fusion scales with its reported confidence: high-confidence readings dominate, low-confidence readings are treated as a minor correction.
 * A successful fusion also nudges up the corresponding confidence score, since a second independent measurement source increases certainty.
 */

@Service
public class ArcoreDepthFusionService {

    private static final Logger log = LoggerFactory.getLogger(ArcoreDepthFusionService.class);

    private static final float ARCORE_WEIGHT_HIGH   = 0.70f;
    private static final float ARCORE_WEIGHT_MEDIUM = 0.50f;
    private static final float ARCORE_WEIGHT_LOW    = 0.25f;
    private static final float CONFIDENCE_HIGH_THRESHOLD   = 0.80f;
    private static final float CONFIDENCE_MEDIUM_THRESHOLD = 0.50f;
    private static final float METERS_TO_CM = 100f;
    
    /** Confidence boost applied per fused measurement, scaled by ARCore's weight in the fusion. */
    private static final float ARCORE_CONFIDENCE_BOOST = 0.2f;

    public void fuseDepths(PipelineContext ctx) {
        ArcoreDepthData arcore = ctx.getArcoreDepthData();

        if (arcore == null || !arcore.isValid()) {
            log.info("[ArcoreFusion] No valid ARCore data — skipping fusion");
            return;
        }

        float arcoreWeight = resolveArcoreWeight(arcore.getArConfidence());
        float visionWeight = 1f - arcoreWeight;

        fusePlateDepth(ctx, arcore, arcoreWeight, visionWeight);
        fusePlateDiameter(ctx, arcore, arcoreWeight, visionWeight);
        fuseFoodHeights(ctx, arcore, arcoreWeight, visionWeight);

        log.info("[ArcoreFusion] Fusion complete. arcoreConf={} arcoreWeight={}",
                String.format("%.2f", arcore.getArConfidence()),
                String.format("%.2f", arcoreWeight));
    }

    private void fusePlateDepth(PipelineContext ctx, ArcoreDepthData arcore,
                                float arcoreWeight, float visionWeight) {
        if (ctx.getPlateGeometry() == null || arcore.getPlateDepthM() == null) return;

        float arcoreDepthCm = arcore.getPlateDepthM() * METERS_TO_CM;
        float visionDepthCm = ctx.getPlateGeometry().getInnerDepthCm();
        float fusedDepthCm  = (arcoreDepthCm * arcoreWeight) + (visionDepthCm * visionWeight);

        log.info("[ArcoreFusion] Plate depth: arcore={}cm vision={}cm fused={}cm",
                String.format("%.2f", arcoreDepthCm),
                String.format("%.2f", visionDepthCm),
                String.format("%.2f", fusedDepthCm));

        ctx.getPlateGeometry().setInnerDepthCm(fusedDepthCm);
        ctx.getPlateGeometry().setDepthConfidence(
                Math.min(1.0f, ctx.getPlateGeometry().getDepthConfidence() + (arcoreWeight * ARCORE_CONFIDENCE_BOOST)));
    }

    private void fusePlateDiameter(PipelineContext ctx, ArcoreDepthData arcore,
                                   float arcoreWeight, float visionWeight) {
        if (ctx.getPlateGeometry() == null || arcore.getPlateDiameterCm() == null
                || arcore.getPlateDiameterCm() <= 0f) return;

        float arcoreDiameterCm = arcore.getPlateDiameterCm();
        float visionDiameterCm = ctx.getPlateGeometry().getInnerDiameterCm();
        float fusedDiameterCm  = (arcoreDiameterCm * arcoreWeight) + (visionDiameterCm * visionWeight);

        float gap = Math.abs(arcoreDiameterCm - visionDiameterCm)
                / Math.max(arcoreDiameterCm, visionDiameterCm);

        log.info("[ArcoreFusion] Plate diameter: arcore={}cm vision={}cm fused={}cm gap={}%",
                String.format("%.2f", arcoreDiameterCm),
                String.format("%.2f", visionDiameterCm),
                String.format("%.2f", fusedDiameterCm),
                String.format("%.0f", gap * 100));

        ctx.getPlateGeometry().setInnerDiameterCm(fusedDiameterCm);
        ctx.getPlateGeometry().setDiameterConfidence(
                Math.min(1.0f, ctx.getPlateGeometry().getDiameterConfidence() + (arcoreWeight * ARCORE_CONFIDENCE_BOOST)));
    }

    private void fuseFoodHeights(PipelineContext ctx, ArcoreDepthData arcore,
                                 float arcoreWeight, float visionWeight) {
        if (arcore.getItemDepthsM() == null || arcore.getItemDepthsM().isEmpty()) return;

        for (PipelineFoodItem item : ctx.getFoodItems()) {
            Float arcoreItemDepthM = arcore.getItemDepthsM().get(item.getName());
            if (arcoreItemDepthM == null || arcoreItemDepthM <= 0f) continue;

            float arcoreHeightCm = arcoreItemDepthM * METERS_TO_CM;
            float visionHeightCm = item.getEffectiveHeightCm() != null
                    ? item.getEffectiveHeightCm() : arcoreHeightCm;

            float fusedHeightCm = (arcoreHeightCm * arcoreWeight) + (visionHeightCm * visionWeight);

            log.info("[ArcoreFusion] {} height: arcore={}cm vision={}cm fused={}cm",
                    item.getName(),
                    String.format("%.2f", arcoreHeightCm),
                    String.format("%.2f", visionHeightCm),
                    String.format("%.2f", fusedHeightCm));

            item.setEffectiveHeightCm(fusedHeightCm);
            item.setHeightConfidence(Math.min(1.0f,
                    (item.getHeightConfidence() != null ? item.getHeightConfidence() : 0f)
                    + (arcoreWeight * ARCORE_CONFIDENCE_BOOST)));
        }
    }

    private float resolveArcoreWeight(float confidence) {
        if (confidence >= CONFIDENCE_HIGH_THRESHOLD)   return ARCORE_WEIGHT_HIGH;
        if (confidence >= CONFIDENCE_MEDIUM_THRESHOLD) return ARCORE_WEIGHT_MEDIUM;
        return ARCORE_WEIGHT_LOW;
    }
}
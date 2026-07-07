package com.insuscan.pipeline.support;

import com.insuscan.pipeline.model.PipelineContext;
import com.insuscan.pipeline.model.PipelineWarning;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Computes an aggregated confidence score across all pipeline stages.
 *
 * <p>Weights are assigned based on each stage's impact on final accuracy.
 * Calibration and geometry are the foundation — errors there propagate everywhere.</p>
 */
@Component
public class ConfidenceAggregator {

    // Stage weights — must sum to 1.0
    private static final Map<String, Float> STAGE_WEIGHTS = Map.of(
        PipelineWarningCollector.STAGE_CALIBRATION,    0.20f,
        PipelineWarningCollector.STAGE_PLATE_GEOMETRY, 0.10f,
        PipelineWarningCollector.STAGE_FOOD_DETECTION, 0.15f,
        PipelineWarningCollector.STAGE_FOOD_AREA,      0.15f,
        PipelineWarningCollector.STAGE_FOOD_HEIGHT,    0.15f,
        PipelineWarningCollector.STAGE_NUTRITION,      0.15f,
        PipelineWarningCollector.STAGE_WEIGHT,         0.10f
    );

    // ── Public API ────────────────────────────────────────────────────────

    /** Minimum overall confidence required for safeForDosing=true. */
    private static final float SAFE_FOR_DOSING_THRESHOLD = 0.65f;

    /**
     * Computes weighted average of per-stage confidences.
     * Stages missing from the context are treated as confidence = 0.
     */
    public float computeOverallConfidence(PipelineContext ctx) {
        Map<String, Float> confidences = ctx.getStepConfidences();
        float weightedSum = 0f;
        float totalWeight = 0f;

        for (Map.Entry<String, Float> entry : STAGE_WEIGHTS.entrySet()) {
            String stage = entry.getKey();
            float weight = entry.getValue();
            Float stageConf = confidences.get(stage);

            if (stageConf != null) {
                weightedSum += stageConf * weight;
                totalWeight += weight;
            }
        }

        if (totalWeight == 0f) return 0f;
        return weightedSum / totalWeight;
    }

    /**
     * Applies penalties for HIGH/CRITICAL warnings.
     * Each HIGH warning reduces confidence by 5%, each CRITICAL by 15%.
     */
    public float applyWarningPenalties(float baseConfidence, PipelineContext ctx) {
        float penalty = 0f;
        for (PipelineWarning w : ctx.getWarnings()) {
            switch (w.getSeverity()) {
                case HIGH     -> penalty += 0.05f;
                case CRITICAL -> penalty += 0.15f;
                default       -> {} // INFO, LOW, MEDIUM — no penalty
            }
        }
        return Math.max(0f, baseConfidence - penalty);
    }

    /**
     * Returns true if the confidence is high enough and no CRITICAL warnings exist.
     */
    public boolean isSafeForDosing(float finalConfidence, PipelineContext ctx) {
        if (finalConfidence < SAFE_FOR_DOSING_THRESHOLD) return false;
        return ctx.getWarnings().stream()
            .noneMatch(w -> w.getSeverity() == PipelineWarning.Severity.CRITICAL);
    }
}

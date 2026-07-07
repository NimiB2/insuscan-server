package com.insuscan.pipeline.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Standard plate dimensions and confidence values used as fallback when no reference object is available or reliable.
 * Individual plate sizes (small/standard/large) are configurable via application properties.
 */
@Component
public class StandardPlateConfig {

	  /** Default plate diameter used when no reference object could be measured. */
    public static final float DIAMETER_CM         = 22.0f;

    /** Default plate depth used when no reference object could be measured. */
    public static final float DEPTH_CM            = 2.0f;

    /** Confidence value assigned to any plate measurement produced by this fallback config. */
    public static final float FALLBACK_CONFIDENCE = 0.3f;

    @Value("${plate.geometry.sizes.small:18.0}")
    private float smallDiameterCm;

    @Value("${plate.geometry.sizes.standard:22.0}")
    private float standardDiameterCm;

    @Value("${plate.geometry.sizes.large:26.0}")
    private float largeDiameterCm;

    @Value("${plate.geometry.confidence.threshold:0.85}")
    private float confidenceThreshold;

    // ── Public API ────────────────────────────────────────────────────────

    public float getSmallDiameterCm()    { return smallDiameterCm; }
    public float getStandardDiameterCm() { return standardDiameterCm; }
    public float getLargeDiameterCm()    { return largeDiameterCm; }
    public float getConfidenceThreshold(){ return confidenceThreshold; }

    /**
     * Snaps the given diameter to the nearest configured plate size (small/standard/large).
     * On ties, prefers the standard size.
     */
    public float snapToClosestSize(float diameterCm) {
        float[] sizes = { smallDiameterCm, standardDiameterCm, largeDiameterCm };
        float closest = standardDiameterCm;
        float minDiff = Float.MAX_VALUE;
        for (float size : sizes) {
            float diff = Math.abs(diameterCm - size);
            if (diff < minDiff - 0.001f) {
                minDiff = diff;
                closest = size;
            } else if (Math.abs(diff - minDiff) < 0.001f && size == standardDiameterCm) {
                closest = standardDiameterCm;
            }
        }
        return closest;
    }
}
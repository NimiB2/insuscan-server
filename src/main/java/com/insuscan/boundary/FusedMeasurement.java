package com.insuscan.boundary;

/**
 * Result of fusing multiple size measurements (AR, side image, reference object)
 * into a single value with a confidence score and the source combination used.
 */

public class FusedMeasurement {

    public enum Source {
        AR_ONLY,
        SIDE_IMAGE_ONLY,
        REFERENCE_ONLY,
        AR_AND_SIDE,
        AR_AND_REFERENCE,
        SIDE_AND_REFERENCE,
        ALL_THREE,
        FALLBACK
    }

    private final float value;
    private final float confidence;
    private final Source source;

    public FusedMeasurement(float value, float confidence, Source source) {
        this.value = value;
        this.confidence = confidence;
        this.source = source;
    }

    public float getValue() {
        return value;
    }

    public float getConfidence() {
        return confidence;
    }

    public Source getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("%.2fcm (conf=%.2f, src=%s)", value, confidence, source.name());
    }
}
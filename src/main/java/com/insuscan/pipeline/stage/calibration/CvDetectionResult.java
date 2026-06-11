package com.insuscan.pipeline.stage.calibration;

import org.opencv.core.Point;
import org.opencv.core.Rect;

public final class CvDetectionResult {

    private final boolean found;
    private final Rect boundingBox;
    private final Point center;
    private final double angle;
    private final double lengthPixels;
    private final float pixelToCmRatio;
    private final float confidence;
    private final String failureReason;

    private CvDetectionResult(boolean found, Rect boundingBox, Point center, double angle,
            double lengthPixels, float pixelToCmRatio, float confidence, String failureReason) {
        this.found = found;
        this.boundingBox = boundingBox;
        this.center = center;
        this.angle = angle;
        this.lengthPixels = lengthPixels;
        this.pixelToCmRatio = pixelToCmRatio;
        this.confidence = confidence;
        this.failureReason = failureReason;
    }

    public static CvDetectionResult found(Rect boundingBox, Point center, double angle,
            double lengthPixels, float pixelToCmRatio, float confidence) {
        return new CvDetectionResult(true, boundingBox, center, angle,
                lengthPixels, pixelToCmRatio, confidence, null);
    }

    public static CvDetectionResult notFound(String failureReason) {
        return new CvDetectionResult(false, null, null, 0, 0, 0f, 0f, failureReason);
    }

    public boolean isFound() {
        return found;
    }

    public Rect getBoundingBox() {
        return boundingBox;
    }

    public Point getCenter() {
        return center;
    }

    public double getAngle() {
        return angle;
    }

    public double getLengthPixels() {
        return lengthPixels;
    }

    public float getPixelToCmRatio() {
        return pixelToCmRatio;
    }

    public float getConfidence() {
        return confidence;
    }

    public String getFailureReason() {
        return failureReason;
    }
}
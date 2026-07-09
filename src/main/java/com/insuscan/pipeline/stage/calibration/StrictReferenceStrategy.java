package com.insuscan.pipeline.stage.calibration;

import org.opencv.core.MatOfPoint;
import org.opencv.core.RotatedRect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reference-object strategy for elongated, thin objects such as insulin pens and syringes.
 * A candidate must have a high aspect ratio, be roughly rectangular, meet a minimum length threshold,
 * and match the expected physical aspect ratio within the configured tolerance.
 */
public class StrictReferenceStrategy implements ReferenceObjectStrategy {

    private static final Logger log = LoggerFactory.getLogger(StrictReferenceStrategy.class);

    private static final double MIN_ASPECT_RATIO = 2.5;
    private static final double MAX_ASPECT_RATIO = 30.0;
    private static final double MIN_RECTANGULARITY = 0.35;
    private static final float MAX_CONFIDENCE = 0.9f;
    private static final double MIN_LENGTH_THRESHOLD_RATIO = 0.05;

    private final double aspectTolerance;

    public StrictReferenceStrategy(double aspectTolerance) {
        this.aspectTolerance = aspectTolerance;
    }

    @Override
    public CvDetectionResult evaluateContour(MatOfPoint contour, RotatedRect rotatedRect,
            double area, double imgWidth, float expectedLengthCm, float expectedWidthCm) {

        double width = rotatedRect.size.width;
        double height = rotatedRect.size.height;
        double length = Math.max(width, height);
        double thickness = Math.min(width, height);

        if (thickness == 0.0) {
            return null;
        }

        double aspectRatio = length / thickness;
        if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
            log.debug("[StrictStrategy] Rejected: aspectRatio={}", String.format("%.2f", aspectRatio));
            return null;
        }

        double rectArea = width * height;
        double rectangularity = area / rectArea;
        if (rectangularity <= MIN_RECTANGULARITY) {
            log.debug("[StrictStrategy] Rejected: rectangularity={}", String.format("%.2f", rectangularity));
            return null;
        }

        double minLengthThreshold = imgWidth * MIN_LENGTH_THRESHOLD_RATIO;
        if (length < minLengthThreshold) {
            log.debug("[StrictStrategy] Rejected: length={}px below minimum {}px",
                    (int) length, (int) minLengthThreshold);
            return null;
        }

        float confidence = MAX_CONFIDENCE;

        if (expectedWidthCm > 0) {
            double expectedAspect = expectedLengthCm / expectedWidthCm;
            double aspectDeviation = Math.abs(aspectRatio - expectedAspect) / expectedAspect;

            if (aspectDeviation > aspectTolerance) {
                log.debug("[StrictStrategy] Rejected: aspectRatio={} deviates {}% from expected {}",
                        String.format("%.2f", aspectRatio),
                        String.format("%.0f", aspectDeviation * 100),
                        String.format("%.2f", expectedAspect));
                return null;
            }

            confidence = (float) (MAX_CONFIDENCE * (1.0 - aspectDeviation / aspectTolerance));
            log.debug("[StrictStrategy] Candidate: aspectRatio={} expected={} deviation={}% confidence={}",
                    String.format("%.2f", aspectRatio), String.format("%.2f", expectedAspect),
                    String.format("%.0f", aspectDeviation * 100), String.format("%.2f", confidence));
        }

        float pixelToCmRatio = (float) (expectedLengthCm / length);

        return CvDetectionResult.found(rotatedRect.boundingRect(), rotatedRect.center,
                rotatedRect.angle, length, pixelToCmRatio, confidence);
    }
}
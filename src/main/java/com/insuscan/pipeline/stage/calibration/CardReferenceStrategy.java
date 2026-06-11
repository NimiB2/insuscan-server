package com.insuscan.pipeline.stage.calibration;

import org.opencv.core.MatOfPoint;
import org.opencv.core.RotatedRect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CardReferenceStrategy implements ReferenceObjectStrategy {

    private static final Logger log = LoggerFactory.getLogger(CardReferenceStrategy.class);

    private static final double MIN_ASPECT_RATIO = 1.2;
    private static final double MAX_ASPECT_RATIO = 2.2;
    private static final double MIN_RECTANGULARITY = 0.75;
    private static final double MIN_THICKNESS_RATIO = 0.04;
    private static final float CONFIDENCE = 0.95f;
    private static final float HOMOGRAPHY_CONFIDENCE = 0.97f;
    private static final double MIN_LENGTH_THRESHOLD_RATIO = 0.05;

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
            log.debug("[CardStrategy] Rejected: aspectRatio={} not in [{},{}]",
                    String.format("%.2f", aspectRatio), MIN_ASPECT_RATIO, MAX_ASPECT_RATIO);
            return null;
        }

        double rectArea = width * height;
        double rectangularity = area / rectArea;
        if (rectangularity <= MIN_RECTANGULARITY) {
            log.debug("[CardStrategy] Rejected: rectangularity={} <= {}",
                    String.format("%.2f", rectangularity), MIN_RECTANGULARITY);
            return null;
        }

        double minThickness = imgWidth * MIN_THICKNESS_RATIO;
        if (thickness < minThickness) {
            log.debug("[CardStrategy] Rejected: thickness={}px < min={}px",
                    (int) thickness, (int) minThickness);
            return null;
        }

        double minLengthThreshold = imgWidth * MIN_LENGTH_THRESHOLD_RATIO;
        if (length < minLengthThreshold) {
            log.debug("[CardStrategy] Rejected: length={}px < min={}px",
                    (int) length, (int) minLengthThreshold);
            return null;
        }

        double realWorldArea = expectedLengthCm * expectedWidthCm;
        float pixelToCmRatio = area > 0
                ? (float) Math.sqrt(realWorldArea / area)
                : (float) (expectedLengthCm / length);

        float finalConfidence = CONFIDENCE;

        Float homographyRatio = CardHomographyCalculator.computeRatio(contour, expectedLengthCm);
        if (homographyRatio != null) {
            pixelToCmRatio = (pixelToCmRatio + homographyRatio) / 2.0f;
            finalConfidence = HOMOGRAPHY_CONFIDENCE;
            log.debug("[CardStrategy] Homography combined ratio: {}", String.format("%.5f", pixelToCmRatio));
        }

        return CvDetectionResult.found(rotatedRect.boundingRect(), rotatedRect.center,
                rotatedRect.angle, length, pixelToCmRatio, finalConfidence);
    }
}
package com.insuscan.pipeline.stage.calibration;

import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CardHomographyCalculator {

    private static final Logger log = LoggerFactory.getLogger(CardHomographyCalculator.class);

    private static final double MIN_SOURCE_WIDTH_PX = 10.0;
    private static final double APPROX_POLY_EPSILON_MULTIPLIER = 0.02;

    private CardHomographyCalculator() {
    }

    public static Float computeRatio(MatOfPoint contour, float expectedLengthCm) {
        try {
            MatOfPoint2f contour2f = new MatOfPoint2f(contour.toArray());
            double peri = Imgproc.arcLength(contour2f, true);
            MatOfPoint2f approxCurve = new MatOfPoint2f();
            Imgproc.approxPolyDP(contour2f, approxCurve, APPROX_POLY_EPSILON_MULTIPLIER * peri, true);

            if (approxCurve.rows() != 4) {
                log.debug("[CardHomography] Expected 4 corners, got {}", approxCurve.rows());
                return null;
            }

            List<Point> points = new ArrayList<>(approxCurve.toList());
            points.sort(Comparator.comparingDouble(p -> p.y));

            List<Point> topTwo = new ArrayList<>(points.subList(0, 2));
            topTwo.sort(Comparator.comparingDouble(p -> p.x));

            Point topLeft = topTwo.get(0);
            Point topRight = topTwo.get(1);

            double srcWidthPx = Math.sqrt(
                    Math.pow(topRight.x - topLeft.x, 2.0) + Math.pow(topRight.y - topLeft.y, 2.0));

            if (srcWidthPx < MIN_SOURCE_WIDTH_PX) {
                log.debug("[CardHomography] Source card width too small ({}px)", String.format("%.1f", srcWidthPx));
                return null;
            }

            float correctedRatio = (float) (expectedLengthCm / srcWidthPx);
            log.debug("[CardHomography] srcWidthPx={} -> correctedRatio={} cm/px",
                    String.format("%.1f", srcWidthPx), String.format("%.5f", correctedRatio));

            return correctedRatio;

        } catch (Exception e) {
            log.debug("[CardHomography] Computation failed: {}", e.getMessage());
            return null;
        }
    }
}
package com.insuscan.pipeline.stage.calibration;

import org.opencv.core.MatOfPoint;
import org.opencv.core.RotatedRect;

public interface ReferenceObjectStrategy {

    CvDetectionResult evaluateContour(MatOfPoint contour, RotatedRect rotatedRect,
            double area, double imgWidth, float expectedLengthCm, float expectedWidthCm);
}